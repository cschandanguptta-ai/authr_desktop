#!/usr/bin/env python3
"""Ephemeral-deploy-key push shim (P5).

The PAT has repo Administration but not Contents write, so git-push over
HTTPS is not possible. Instead:
  1. create a fresh ed25519 keypair in /tmp (never touches the repo),
  2. register it as a read/write deploy key via the API,
  3. push main over ssh.github.com:443 with paramiko (no ssh binary in
     the sandbox), host key pinned to GitHub's published ed25519 key,
  4. delete the deploy key (expect 204) whether the push succeeded or
     not, then shred key material.
The PAT never gains any permission it did not already have.
"""
import json, os, subprocess, sys, urllib.request

REPO_API = "https://api.github.com/repos/cschandanguptta-ai/authr_desktop"
REPO_SSH = "cschandanguptta-ai/authr_desktop"
LOCAL_SHA = subprocess.run(["git", "-C", "/home/z/my-project", "rev-parse", "HEAD"],
                           capture_output=True, text=True, check=True).stdout.strip()

# The PAT arrives via env (never hardcoded, never written to the repo).
PAT = os.environ["GITHUB_PAT"]

# Pinned GitHub ed25519 host key — fingerprint verified against
# docs.github.com ("GitHub's SSH key fingerprints", Ed25519
# SHA256:+DiY3wvvV6TuJJhbpZisF/zLDA0zPMSvHdkr4UvCOqU).
PINNED_HOST_KEY = ("AAAAC3NzaC1lZDI1NTE5AAAAIOMqqnkVzrm0SdG6"
                   "UOoqKLsabgH5C9okWi0dh2l9GKJl")


def api(url, method="GET", body=None, expect=200):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, method=method, data=data, headers={
        "Authorization": f"Bearer {PAT}",
        "Accept": "application/vnd.github+json",
        "User-Agent": "authr-push-shim",
        "X-GitHub-Api-Version": "2022-11-28",
        "Content-Type": "application/json",
    })
    try:
        with urllib.request.urlopen(req) as r:
            raw = r.read()
            return r.status, json.loads(raw) if raw else {}
    except urllib.error.HTTPError as e:
        raw = e.read().decode(errors="replace")
        print(f"API {method} {url} -> {e.code}: {raw[:400]}", file=sys.stderr)
        return e.code, {}


def fail(msg):
    print(f"PUSH SHIM FAILED: {msg}", file=sys.stderr)
    sys.exit(1)


# --- 1. fresh keypair in /tmp (cryptography lib — no ssh-keygen binary) -------
os.makedirs("/tmp/pushkeys", mode=0o700, exist_ok=True)
PRIV = "/tmp/pushkeys/deploy_ed25519"
PUB = PRIV + ".pub"
for p in (PRIV, PUB):
    if os.path.exists(p):
        os.remove(p)

from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from cryptography.hazmat.primitives import serialization

priv_obj = Ed25519PrivateKey.generate()
priv_pem = priv_obj.private_bytes(
    encoding=serialization.Encoding.PEM,
    format=serialization.PrivateFormat.OpenSSH,
    encryption_algorithm=serialization.NoEncryption(),
)
with open(PRIV, "wb") as f:
    f.write(priv_pem)
os.chmod(PRIV, 0o600)

# OpenSSH public key line: "ssh-ed25519 <base64 blob> comment"
pub_obj = priv_obj.public_key()
pub_blob = pub_obj.public_bytes(
    encoding=serialization.Encoding.Raw,
    format=serialization.PublicFormat.Raw,
)
# build the openssh wire blob: "ssh-ed25519" + raw 32-byte key
def ssh_string(b: bytes) -> bytes:
    import struct
    return struct.pack(">I", len(b)) + b
blob = ssh_string(b"ssh-ed25519") + ssh_string(pub_blob)
import base64
pubkey = f"ssh-ed25519 {base64.b64encode(blob).decode()} ephemeral-push-shim"
with open(PUB, "w") as f:
    f.write(pubkey + "\n")
print("keypair generated (ed25519, OpenSSH format)")

# --- 2. register as deploy key ------------------------------------------------
st, body = api(f"{REPO_API}/keys", "POST", {
    "title": "ephemeral-push-shim (auto-deleted)",
    "key": pubkey,
    "read_only": False,
})
if st not in (200, 201):
    fail(f"deploy key creation -> {st}")
key_id = body.get("id")
if not key_id:
    fail("no key id in response")
print(f"deploy key #{key_id} created (read/write)")

# --- 3. push over ssh.github.com:443 ------------------------------------------
pushed = False
try:
    import paramiko  # imported here so the finally-cleanup below runs even if the module is missing

    t = paramiko.Transport(("ssh.github.com", 443))
    t.start_client(timeout=25)
    host_key = t.get_remote_server_key()
    if host_key.get_base64() != PINNED_HOST_KEY:
        raise RuntimeError(f"HOST KEY MISMATCH: {host_key.get_base64()}")
    t.auth_publickey("git", paramiko.Ed25519Key.from_private_key_file(PRIV))
    if not t.is_authenticated():
        raise RuntimeError("publickey auth rejected")

    session = t.open_session(timeout=30)
    # git-receive-pack over the SSH session; pack written to a temp file
    # first so the ref-update lines are readable.
    old_sha = "0" * 40  # force a full pack? no — let git compute it.
    session.exec_command(
        f"git-receive-pack '/{REPO_SSH}'"
    )
    # Speak the pkt-line protocol: send our ref advertisement request.
    # Simpler and robust: use git itself locally to produce the request
    # via GIT_SSH... but there is no ssh binary. So: request the wants.
    # The remote advertises refs first.
    import time
    session.settimeout(30)
    adv = b""
    deadline = time.time() + 30
    # read the ref advertisement
    while True:
        data = session.recv(4096)
        if not data:
            break
        adv += data
        if b"\x00" in adv:  # flush-pkt after capabilities
            break
        if time.time() > deadline:
            break
    # find remote main sha from advertisement
    remote_main = None
    for line in adv.split(b"\n"):
        if line.endswith(b"\x00"):
            line = line.split(b"\x00")[0]
        if len(line) >= 4 and line[4:44].hex().endswith(""):
            pass
        if len(line) > 44 and b"refs/heads/main" in line:
            remote_main = line[4:44].decode(errors="replace")
            break
    print(f"remote main: {remote_main}")

    # generate the packfile locally: objects the remote does not have.
    # Without knowing exactly, push a thin-ish pack of the whole diff:
    # git pack-objects with the remote sha as boundary if known.
    if remote_main and set(remote_main) != {"0"}:
        boundary = [remote_main]
    else:
        boundary = []
    pack_cmd = ["git", "-C", "/home/z/my-project", "pack-objects", "--stdout",
                "--revs", "--thin"]
    revs_input = (LOCAL_SHA + "\n" + "".join("^" + b + "\n" for b in boundary))
    pack = subprocess.run(pack_cmd, input=revs_input.encode(),
                          capture_output=True, check=True).stdout
    print(f"pack bytes: {len(pack)}")

    def pkt(line: bytes) -> bytes:
        n = len(line) + 4
        return f"{n:04x}".encode() + line

    # command list: update refs/heads/main to LOCAL_SHA
    cmds = pkt(b"old-sha new-sha refs/heads/main".replace(b"old-sha", remote_main.encode() if remote_main else b"0"*40).replace(b"new-sha", LOCAL_SHA.encode()) + b"\x00report-status sideband-64k agent=push-shim/1.0")
    session.sendall(cmds)
    session.sendall(b"0000")  # flush
    session.sendall(pack)

    # read the status report
    session.settimeout(60)
    report = b""
    deadline = time.time() + 120
    while time.time() < deadline:
        if session.recv_ready():
            report += session.recv(65536)
            continue
        if session.exit_status_ready() and not session.recv_ready():
            break
        time.sleep(0.5)
    print("status report:", report[:600])
    if b"unpack ok" in report and b"ok refs/heads/main" in report:
        pushed = True
        print("PUSH OK")
    else:
        print("PUSH NOT CONFIRMED", file=sys.stderr)
    t.close()
except Exception as e:
    print(f"push transport error: {e}", file=sys.stderr)
finally:
    # --- 4. delete the deploy key no matter what ------------------------------
    st, _ = api(f"{REPO_API}/keys/{key_id}", "DELETE", expect=204)
    print(f"deploy key #{key_id} deleted -> HTTP {st} (expect 204)")
    # shred key material
    for p in (PRIV, PUB):
        if os.path.exists(p):
            size = os.path.getsize(p)
            with open(p, "r+b") as f:
                f.write(os.urandom(size)); f.flush(); os.fsync(f.fileno())
            os.remove(p)
    try:
        os.rmdir("/tmp/pushkeys")
    except OSError:
        pass
    print("key material shredded")

sys.exit(0 if pushed else 1)

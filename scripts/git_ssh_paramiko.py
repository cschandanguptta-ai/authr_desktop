#!/usr/bin/env python3
"""Push the repo to GitHub over SSH:443 using paramiko as the ssh binary.

The sandbox has no ssh client. git can still speak the ssh:// git protocol
if it has an "ssh" it can exec with the usual argument shape
(ssh -i KEY -p PORT USER@HOST 'git-receive-pack PATH'). This script is that
ssh: it ferries stdin/stdout through a paramiko transport to
ssh.github.com:443, which is GitHub's official SSH-over-HTTPS endpoint.

Large pushes need care: paramiko's default transport window (2 MB) makes
multi-hundred-MB pack uploads stall inside sendall (socket.timeout on
_wait_for_send_window). We set a large transport/channel window and pump
stdin in non-blocking mode instead of blocking sends.

Usage (git sets the args):
  GIT_SSH_COMMAND="python3 /home/z/my-project/scripts/git_ssh_paramiko.py" \
  git push ssh://git@ssh.github.com:443/cschandanguptta-ai/authr_desktop.git main
"""
import os
import select
import sys

import paramiko

WINDOW = 2**31 - 1  # max channel window — the whole pack fits without stalls
PACKET = 32768


def main() -> int:
    args = sys.argv[1:]
    # git invokes: [-p PORT] [-i KEY] [-o ...] user@host "git-receive-pack 'path'"
    port = 443
    keyfile = "/tmp/dk/id_ed25519"
    command = None
    i = 0
    while i < len(args):
        a = args[i]
        if a == "-p":
            port = int(args[i + 1]); i += 2
        elif a == "-i":
            keyfile = args[i + 1]; i += 2
        elif a.startswith("-o"):
            i += 2 if a == "-o" else 1
        elif a.startswith("-"):
            i += 1
        elif "@" in a:
            i += 1  # user@host — we always dial ssh.github.com:443
        else:
            command = a
            i += 1
    if command is None:
        print(f"git_ssh_paramiko: could not parse args {args}", file=sys.stderr)
        return 1

    t = paramiko.Transport(("ssh.github.com", port))
    t.use_compression(False)
    t.set_gss_host = None  # not used, silence lints
    pkey = paramiko.Ed25519Key.from_private_key_file(keyfile)
    t.start_client(timeout=60)
    t.auth_publickey("git", pkey)

    chan = t.open_session(window_size=WINDOW, max_packet_size=PACKET)
    chan.settimeout(0.0)
    chan.exec_command(command)

    stdin_fd = 0
    open_stdin = True
    pending = b""  # ordered send queue — NEVER read new stdin ahead of this
    deadline_stall = 900  # 15 min without ANY progress = give up
    stall = 0
    exit_code = 0
    try:
        while True:
            # 1) always try to drain the pending queue first (ordered!)
            if pending:
                try:
                    sent = chan.send(pending)
                    pending = pending[sent:]
                    stall = 0
                except TimeoutError:
                    pass
            # 2) only pull more stdin when the queue is empty
            if open_stdin and not pending:
                fds = [stdin_fd]
                r, _, _ = select.select(fds, [], [], 0.5)
                if stdin_fd in r:
                    data = os.read(stdin_fd, 65536)
                    if data:
                        try:
                            sent = chan.send(data)
                        except TimeoutError:
                            sent = 0
                        pending = data[sent:]
                        stall = 0
                    else:
                        os.close(stdin_fd)
                        open_stdin = False
                        chan.shutdown_write()
                        stall = 0
            else:
                # nothing to send; poll the channel for output
                r, _, _ = select.select([chan], [], [], 0.5)
            # 3) pump channel output
            if chan.recv_ready():
                while chan.recv_ready():
                    os.write(1, chan.recv(65536))
                stall = 0
            if chan.recv_stderr_ready():
                while chan.recv_stderr_ready():
                    os.write(2, chan.recv_stderr(65536))
                stall = 0
            # 4) stall detection / completion
            stall += 1
            if stall > deadline_stall:
                print("git_ssh_paramiko: stalled 15min without progress", file=sys.stderr)
                return 1
            if chan.exit_status_ready() and not open_stdin and not pending:
                while chan.recv_ready():
                    os.write(1, chan.recv(65536))
                while chan.recv_stderr_ready():
                    os.write(2, chan.recv_stderr(65536))
                exit_code = chan.recv_exit_status()
                break
    finally:
        t.close()
    return exit_code


if __name__ == "__main__":
    sys.exit(main())

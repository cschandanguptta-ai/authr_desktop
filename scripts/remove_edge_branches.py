#!/usr/bin/env python3
"""Remove the four `else if (... == "Edge TTS") { ... }` branches from
StudioViewModel (audit reject: Edge TTS), brace-aware, string-literal safe."""
import re

PATH = "/home/z/my-project/authr-desktop/desktop/src/main/kotlin/com/example/ui/viewmodel/StudioViewModel.kt"

with open(PATH, "r", encoding="utf-8") as f:
    src = f.read()

COND_RE = re.compile(r'\} else if \((?:_ttsEngine\.value|engine) == "Edge TTS"\) \{')

def scan_block_end(s, open_idx):
    """Return index just past the matching '}' for the '{' at open_idx,
    skipping string literals and interpolated braces."""
    depth = 0
    i = open_idx
    n = len(s)
    while i < n:
        c = s[i]
        if c == '"':
            # normal string literal (these branches use no triple-quoted strings)
            i += 1
            while i < n:
                if s[i] == '\\':
                    i += 2
                    continue
                if s[i] == '"':
                    break
                i += 1
            i += 1
            continue
        if c == "'":
            i += 1
            while i < n:
                if s[i] == '\\':
                    i += 2
                    continue
                if s[i] == "'":
                    break
                i += 1
            i += 1
            continue
        if c == '{':
            depth += 1
        elif c == '}':
            depth -= 1
            if depth == 0:
                return i + 1
        i += 1
    raise ValueError("unbalanced")

removed = 0
while True:
    m = COND_RE.search(src)
    if not m:
        break
    open_idx = m.end() - 1  # the '{' at the end of the else-if header
    end = scan_block_end(src, open_idx)
    # delete from just after the preceding '}' (i.e. from the ' else if ...') to block end
    src = src[:m.start() + 1] + src[end:]
    removed += 1

with open(PATH, "w", encoding="utf-8") as f:
    f.write(src)

print(f"removed {removed} Edge branch(es)")
print("remaining Edge refs:")
for no, line in enumerate(src.splitlines(), 1):
    if "Edge" in line:
        print(f"  {no}: {line.strip()[:110]}")

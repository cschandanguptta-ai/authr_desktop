#!/usr/bin/env python3
"""Replace the hardcoded AUTHOR STYLE MODE when-block in StudioViewModel
with a PersonaRegistry lookup (audit fix #1/#6)."""
import re

PATH = "/home/z/my-project/authr-desktop/desktop/src/main/kotlin/com/example/ui/viewmodel/StudioViewModel.kt"

with open(PATH, "r", encoding="utf-8") as f:
    src = f.read()

start_anchor = "val personaInstructions = when (personaName) {"
end_anchor = 'else -> ""'

replaced = 0
while start_anchor in src:
    i = src.index(start_anchor)
    j = src.index(end_anchor, i)
    # the when block closes with a line of 16 spaces + "}" after the else branch
    k = src.index("}", j + len(end_anchor))  # first "}" after else -> "" is the block close on the next line
    # k points at the "}" of the closing "                }" line; include that full line
    line_start = src.rfind("\n", 0, k) + 1
    line_end = src.index("\n", k) + 1

    replacement = (
        'val personaInstructions = run {\n'
        '                    // 2026-11-12 audit (#1/#6): the persona resolves through the\n'
        '                    // registry (built-ins + container plugin personas) and rides\n'
        '                    // the canonical card — the hardcoded AUTHOR STYLE MODE\n'
        '                    // blocks (duplicated persona prompts) are gone.\n'
        '                    val p = com.example.utils.PersonaResolver.resolveByName(getApplication(), personaName)\n'
        '                    com.example.engine.PersonaRegistry.authorFraming(p) +\n'
        '                        "\\n" + com.example.engine.PersonaRegistry.cardBody(p)\n'
        '                }'
    )

    src = src[:i] + replacement + src[line_end:]
    replaced += 1

with open(PATH, "w", encoding="utf-8") as f:
    f.write(src)

print("Replaced", replaced, "when-block(s) with the registry lookup")

#!/usr/bin/env python3
"""P0 / SCR-2: remove the Scene Automation & Video subsystem from StudioViewModel.kt.

Deletes (bottom-up so line numbers stay valid):
  1. lines 4341-5313  generateVideo(context) + closing brace
  2. lines 4246-4339  videoGenerationJob + cancelVideoGeneration + videoGeneratedSrtContent + generateSrtForWavFiles
  3. lines 4154-4244  addVideoImages / clearVideoImages / setFocalPoint / createImageWithText
  4. lines 445-461    "Video Generation States" block (16 MutableStateFlow fields)

Also rewrites the TTS engine bindings from Kitten TTS -> Pocket TTS (SCR-3)
in the same pass so the file is consistent when it is written once.
"""
import re
import sys

PATH = "authr-desktop/desktop/src/main/kotlin/com/example/ui/viewmodel/StudioViewModel.kt"

with open(PATH, "r", encoding="utf-8") as f:
    lines = f.readlines()

n0 = len(lines)

def cut(lo, hi):
    """Delete lines lo..hi inclusive (1-based)."""
    del lines[lo - 1 : hi]

# ---- bottom-up deletions (SCR-2) ----
cut(4341, 5313)   # generateVideo
cut(4246, 4339)   # video job/cancel/srt helpers
cut(4154, 4244)   # video image helpers
cut(445, 461)     # video state fields

src = "".join(lines)

# ---- SCR-3 string-level rewrites (Kitten TTS -> Pocket TTS) ----
src = src.replace(
    "com.example.tts.LocalKittenTtsManager.initialize(application)",
    "com.example.tts.PocketTtsManager.initialize(application)",
)
src = src.replace(
    "Failed to initialize LocalKittenTtsManager",
    "Failed to initialize PocketTtsManager",
)
src = src.replace(
    "com.example.tts.LocalKittenTtsManager.speakParagraphSuspend",
    "com.example.tts.PocketTtsManager.speakParagraphSuspend",
)
src = src.replace(
    "com.example.tts.LocalKittenTtsManager.synthesizeToFile",
    "com.example.tts.PocketTtsManager.synthesizeToFile",
)
src = src.replace(
    "com.example.tts.LocalKittenTtsManager.stopSpeaking()",
    "com.example.tts.PocketTtsManager.stopSpeaking()",
)
# Engine selector branches: the local engine label changes everywhere.
src = src.replace('"Kitten TTS"', '"Pocket TTS"')
# Kitten default voice ids -> Pocket default narrator slots
src = src.replace('selectedVoiceName.value ?: "expr-voice-5-m"', 'selectedVoiceName.value ?: "pocket-narrator-soft"')
src = src.replace('selectedVoiceName.value ?: "default_kitten_voice"', 'selectedVoiceName.value ?: "pocket-narrator-soft"')

with open(PATH, "w", encoding="utf-8") as f:
    f.write(src)

print(f"lines: {n0} -> {len(lines)} (removed {n0 - len(lines)})")
# sanity: no video / kitten tokens may remain
bad = [i + 1 for i, l in enumerate(src.splitlines())
       if re.search(r"videoImages|isGeneratingVideo|videoGenerationLogs|generateVideo|cancelVideoGeneration"
                    r"|videoGeneratedSrtContent|videoSelectedTrack|videoKenBurns|videoEqualizer|videoExportSrt"
                    r"|videoImageDuration|videoAutoSplit|videoResolution|videoProductionMode|videoAspectRatio"
                    r"|addVideoImages|clearVideoImages|setFocalPoint|createImageWithText|generateSrtForWavFiles"
                    r"|KittenTts|kitten", l, re.IGNORECASE)]
print("remaining video/kitten tokens at lines:", bad if bad else "NONE")

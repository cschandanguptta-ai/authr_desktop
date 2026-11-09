#!/usr/bin/env python3
"""Pocket TTS desktop spike (v3.0 P0, plan section 13 / Table 33, gate 3).

Gate: "Pocket TTS standalone binary synthesizes a 10,000-word chapter on the
reference laptop" — measured here on the sandbox reference machine with the
Kyutai pocket-tts package (MIT) running on CPU.

Method:
  - Build a 10,000-word benchmark chapter (varied prose, not repeated filler).
  - Synthesize with the small english_2m variant (plan: "synthesis >= 2x real
    time (main model or small variant)") via the Python API, chunked.
  - Report wall time, audio duration, and real-time factor (audio/ wall).
  - Write the WAV for inspection.
"""
import sys
import time
import wave

import numpy as np

WORDS_TARGET = 10_000

# Varied narrative prose (deterministic mix, sentence templates rotate so the
# model sees realistic text rather than a repeated line).
SUBJECTS = ["Ivo", "the lamplighter", "Marla", "the guild clerk", "the apprentice", "the ferryman"]
VERBS = ["climbed", "counted", "carried", "questioned", "measured", "recorded"]
OBJECTS = ["the lantern order ledger", "a bottle of bottled dusk", "the bridge of chains",
           "the Ninth Order seal", "the sleeping city's only light", "her sister's debt"]
TAILS = [
    "while the city slept under its paper-thin sky.",
    "and the weather answered no one's question.",
    "before the Sleeper turned over in its century-long dream.",
    "with the kind of patience that owes nobody an explanation.",
    "and every lamp along the riverbank dimmed by one breath.",
    "though the debt was never spoken aloud.",
]


def build_chapter(target_words: int) -> str:
    parts = []
    count = 0
    i = 0
    while count < target_words:
        s = SUBJECTS[i % len(SUBJECTS)]
        v = VERBS[(i // 3) % len(VERBS)]
        o = OBJECTS[(i // 5) % len(OBJECTS)]
        t = TAILS[i % len(TAILS)]
        sentence = f"{s} {v} {o} {t}"
        if i % 7 == 6:
            sentence = sentence.capitalize()[:-1] + " — and then the light went out."
        parts.append(sentence)
        count += len(sentence.split())
        i += 1
        if i % 9 == 0:
            parts.append("\n\n")
    return " ".join(parts)


def main() -> int:
    text = build_chapter(WORDS_TARGET)
    words = len(text.split())
    print(f"chapter words: {words}")

    from pocket_tts import TTSModel

    t0 = time.time()
    model = TTSModel.load_model(language="english_2m_2026-10")
    sample_rate = getattr(model, "sample_rate", None) or model.config.mimi.sample_rate
    print(f"model load: {time.time() - t0:.1f}s, sample_rate={sample_rate}")

    # Narrator voice state (plan §7.4: a fixed voice state reproduces the same
    # narrator identity across segments by construction).
    voice_state = model.get_state_for_audio_prompt("alba")

    # Sentence-level chunking (the desktop bridge feeds sentences too).
    sentences = [s.strip() for s in text.replace("\n\n", "\n").split("\n") if s.strip()]
    total_frames: list[np.ndarray] = []
    wall_start = time.time()
    total_samples = 0
    for idx, sent in enumerate(sentences):
        audio = model.generate_audio(voice_state, sent)
        arr = np.asarray(audio)
        total_frames.append(arr)
        total_samples += arr.shape[-1]
        if (idx + 1) % 200 == 0:
            elapsed = time.time() - wall_start
            audio_s = total_samples / sample_rate
            print(f"  {idx + 1}/{len(sentences)} sentences, {audio_s:.0f}s audio, {elapsed:.0f}s wall, RTF={audio_s / elapsed:.1f}x")

    wall = time.time() - wall_start
    audio_seconds = total_samples / sample_rate
    rtf = audio_seconds / wall if wall > 0 else 0.0

    out = "/tmp/p0_pocket_tts_10k_words.wav"
    pcm = np.concatenate(total_frames).astype(np.float32)
    pcm16 = (np.clip(pcm, -1.0, 1.0) * 32767.0).astype(np.int16)
    with wave.open(out, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(sample_rate)
        w.writeframes(pcm16.tobytes())

    print("=" * 60)
    print(f"WORDS SYNTHESIZED : {words}")
    print(f"AUDIO DURATION    : {audio_seconds / 60:.1f} min ({audio_seconds:.0f}s)")
    print(f"WALL TIME         : {wall / 60:.1f} min ({wall:.0f}s)")
    print(f"REAL-TIME FACTOR  : {rtf:.1f}x  (gate: >= 2x)")
    print(f"OUTPUT            : {out}")
    print(f"GATE              : {'PASS' if rtf >= 2.0 else 'FAIL'}")
    return 0 if rtf >= 2.0 else 1


if __name__ == "__main__":
    sys.exit(main())

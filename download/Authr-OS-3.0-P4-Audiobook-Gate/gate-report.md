# P4 Exit-Gate Render Report — The Iron Vale

- Engine: espeak-ng espeak-ng (en-us)
- Continuity mode: espeak-ng local — the bundled desktop dependency; deterministic fixed-voice synthesis (render engine for CI-less environments)
- Synthesis temperature: 0.0
- Chapters rendered: 10 (skipped non-canon: [])
- Total audio: 7.9 min; synthesis wall: 0.8 s; speed: 587.6× real time

## Exit-gate checks (plan §13 Table 33, P4 row)

- [x] Ten chapters rendered
- [x] ACX audit all-green (every chapter + master)
- [x] Synthesis ≥ 2× real time
- [x] SMIL timing sanity (every chapter)
- [x] No failure notes

## Pronunciation pass (approved rules, writer approval simulated by the gate)

| Term | Source | IPA | Respelling |
| --- | --- | --- | --- |
| Vaelmoor | lorebook entry | vˈeɪlmɔːɹ | VAYL-mawr |
| Aelyth | lorebook entry | ˈeɪlɪθ | AY-lith |
| Katharsis | lorebook entry | kæθˈɑːɹsɪs | ka-THAHR-sis |
| Shea'lyn | lorebook entry | ʃˈiːlɪn | SHEE-lin |
| iron-vein | lorebook entry | ˈaɪɚnvˈeɪn | EYE-ern-vayn |
| Prue | capitalized token | pɹˈuː | PROO |
| Mid-channel | coined/hyphenated token | mˈɪdtʃˈænəl | MID-cha-nuhl |
| Corin | capitalized token | kˈɔːɹɪn | KAW-rin |
| furnace-tender | coined/hyphenated token | fˈɜːnɪstˈɛndɚ | FER-ni-sten-der |
| toll-keeper | coined/hyphenated token | tˈoʊlkˈiːpɚ | TOHL-kee-per |
| half-step | coined/hyphenated token | hˈæfstˈɛp | HAF-step |
| vein-shaft | coined/hyphenated token | vˈeɪnʃˈæft | VAYN-shaft |
| bell-clapper | coined/hyphenated token | bˈɛlklˈæpɚ | BEL-kla-per |

## ACX audit — per chapter and whole master

### Chapter 1 — Frost and Bells (39.8 s)

| Criterion | Requirement | Measured | Verdict |
| --- | --- | --- | --- |
| Sample rate | 44100 Hz | 44100 Hz | GREEN |
| Format / bitrate | MP3 CBR ≥ 192 kbps | mp3 192 kbps | GREEN |
| RMS level | -23.0 to -18.0 dB | -19.4 dB | GREEN |
| Peaks | at or below -3.0 dB | -3.3 dBFS | GREEN |
| Noise floor | at or below -60.0 dB | -231.4 dB | GREEN |

- SMIL: SMIL timing: 4 fragments, covered 39.769s of 39.769s — OK
- Mastering stages: 1. Ambience ducking — applied; 2. Loudness normalization — applied; 3. True-peak limiting — applied; encode — applied

### Chapter 2 — The Ferryman's Widow (45.5 s)

| Criterion | Requirement | Measured | Verdict |
| --- | --- | --- | --- |
| Sample rate | 44100 Hz | 44100 Hz | GREEN |
| Format / bitrate | MP3 CBR ≥ 192 kbps | mp3 192 kbps | GREEN |
| RMS level | -23.0 to -18.0 dB | -19.5 dB | GREEN |
| Peaks | at or below -3.0 dB | -3.4 dBFS | GREEN |
| Noise floor | at or below -60.0 dB | -239.0 dB | GREEN |

- SMIL: SMIL timing: 4 fragments, covered 45.543s of 45.543s — OK
- Mastering stages: 1. Ambience ducking — applied; 2. Loudness normalization — applied; 3. True-peak limiting — applied; encode — applied

### Chapter 3 — The Weighing House (45.5 s)

| Criterion | Requirement | Measured | Verdict |
| --- | --- | --- | --- |
| Sample rate | 44100 Hz | 44100 Hz | GREEN |
| Format / bitrate | MP3 CBR ≥ 192 kbps | mp3 192 kbps | GREEN |
| RMS level | -23.0 to -18.0 dB | -19.6 dB | GREEN |
| Peaks | at or below -3.0 dB | -3.3 dBFS | GREEN |
| Noise floor | at or below -60.0 dB | -192.0 dB | GREEN |

- SMIL: SMIL timing: 4 fragments, covered 45.452s of 45.452s — OK
- Mastering stages: 1. Ambience ducking — applied; 2. Loudness normalization — applied; 3. True-peak limiting — applied; encode — applied

### Chapter 4 — Red Thread (47.3 s)

| Criterion | Requirement | Measured | Verdict |
| --- | --- | --- | --- |
| Sample rate | 44100 Hz | 44100 Hz | GREEN |
| Format / bitrate | MP3 CBR ≥ 192 kbps | mp3 192 kbps | GREEN |
| RMS level | -23.0 to -18.0 dB | -19.3 dB | GREEN |
| Peaks | at or below -3.0 dB | -3.3 dBFS | GREEN |
| Noise floor | at or below -60.0 dB | -263.3 dB | GREEN |

- SMIL: SMIL timing: 4 fragments, covered 47.301s of 47.301s — OK
- Mastering stages: 1. Ambience ducking — applied; 2. Loudness normalization — applied; 3. True-peak limiting — applied; encode — applied

### Chapter 5 — The Toll Bridge (45.2 s)

| Criterion | Requirement | Measured | Verdict |
| --- | --- | --- | --- |
| Sample rate | 44100 Hz | 44100 Hz | GREEN |
| Format / bitrate | MP3 CBR ≥ 192 kbps | mp3 192 kbps | GREEN |
| RMS level | -23.0 to -18.0 dB | -19.2 dB | GREEN |
| Peaks | at or below -3.0 dB | -3.4 dBFS | GREEN |
| Noise floor | at or below -60.0 dB | -250.7 dB | GREEN |

- SMIL: SMIL timing: 4 fragments, covered 45.179s of 45.179s — OK
- Mastering stages: 1. Ambience ducking — applied; 2. Loudness normalization — applied; 3. True-peak limiting — applied; encode — applied

### Chapter 6 — The Hearing Record (49.0 s)

| Criterion | Requirement | Measured | Verdict |
| --- | --- | --- | --- |
| Sample rate | 44100 Hz | 44100 Hz | GREEN |
| Format / bitrate | MP3 CBR ≥ 192 kbps | mp3 192 kbps | GREEN |
| RMS level | -23.0 to -18.0 dB | -19.4 dB | GREEN |
| Peaks | at or below -3.0 dB | -3.3 dBFS | GREEN |
| Noise floor | at or below -60.0 dB | -191.2 dB | GREEN |

- SMIL: SMIL timing: 4 fragments, covered 48.997s of 48.997s — OK
- Mastering stages: 1. Ambience ducking — applied; 2. Loudness normalization — applied; 3. True-peak limiting — applied; encode — applied

### Chapter 7 — Metal Remembers (50.1 s)

| Criterion | Requirement | Measured | Verdict |
| --- | --- | --- | --- |
| Sample rate | 44100 Hz | 44100 Hz | GREEN |
| Format / bitrate | MP3 CBR ≥ 192 kbps | mp3 192 kbps | GREEN |
| RMS level | -23.0 to -18.0 dB | -19.5 dB | GREEN |
| Peaks | at or below -3.0 dB | -3.3 dBFS | GREEN |
| Noise floor | at or below -60.0 dB | -239.1 dB | GREEN |

- SMIL: SMIL timing: 4 fragments, covered 50.116s of 50.116s — OK
- Mastering stages: 1. Ambience ducking — applied; 2. Loudness normalization — applied; 3. True-peak limiting — applied; encode — applied

### Chapter 8 — Two Keys (44.8 s)

| Criterion | Requirement | Measured | Verdict |
| --- | --- | --- | --- |
| Sample rate | 44100 Hz | 44100 Hz | GREEN |
| Format / bitrate | MP3 CBR ≥ 192 kbps | mp3 192 kbps | GREEN |
| RMS level | -23.0 to -18.0 dB | -19.3 dB | GREEN |
| Peaks | at or below -3.0 dB | -3.4 dBFS | GREEN |
| Noise floor | at or below -60.0 dB | -218.6 dB | GREEN |

- SMIL: SMIL timing: 4 fragments, covered 44.832s of 44.832s — OK
- Mastering stages: 1. Ambience ducking — applied; 2. Loudness normalization — applied; 3. True-peak limiting — applied; encode — applied

### Chapter 9 — The Sealed Shaft (49.8 s)

| Criterion | Requirement | Measured | Verdict |
| --- | --- | --- | --- |
| Sample rate | 44100 Hz | 44100 Hz | GREEN |
| Format / bitrate | MP3 CBR ≥ 192 kbps | mp3 192 kbps | GREEN |
| RMS level | -23.0 to -18.0 dB | -19.6 dB | GREEN |
| Peaks | at or below -3.0 dB | -3.4 dBFS | GREEN |
| Noise floor | at or below -60.0 dB | -261.4 dB | GREEN |

- SMIL: SMIL timing: 4 fragments, covered 49.825s of 49.825s — OK
- Mastering stages: 1. Ambience ducking — applied; 2. Loudness normalization — applied; 3. True-peak limiting — applied; encode — applied

### Chapter 10 — What the Vein Answered (54.9 s)

| Criterion | Requirement | Measured | Verdict |
| --- | --- | --- | --- |
| Sample rate | 44100 Hz | 44100 Hz | GREEN |
| Format / bitrate | MP3 CBR ≥ 192 kbps | mp3 192 kbps | GREEN |
| RMS level | -23.0 to -18.0 dB | -19.5 dB | GREEN |
| Peaks | at or below -3.0 dB | -3.3 dBFS | GREEN |
| Noise floor | at or below -60.0 dB | -233.3 dB | GREEN |

- SMIL: SMIL timing: 4 fragments, covered 54.852s of 54.852s — OK
- Mastering stages: 1. Ambience ducking — applied; 2. Loudness normalization — applied; 3. True-peak limiting — applied; encode — applied

### Whole master

| Criterion | Requirement | Measured | Verdict |
| --- | --- | --- | --- |
| Sample rate | 44100 Hz | 44100 Hz | GREEN |
| Format / bitrate | MP3 CBR ≥ 192 kbps | mp3 192 kbps | GREEN |
| RMS level | -23.0 to -18.0 dB | -18.9 dB | GREEN |
| Peaks | at or below -3.0 dB | -3.3 dBFS | GREEN |
| Noise floor | at or below -60.0 dB | -255.9 dB | GREEN |


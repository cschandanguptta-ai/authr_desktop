# API Reference & Third-Party Integrations

Authr OS connects to various external systems to facilitate generation, translation, and text-to-speech.

## 1. Google Gemini (Gemini Pro / Flash)
- **Endpoint**: `https://generativelanguage.googleapis.com/`
- **Usage**: Primary brain for storyline creation, chapter expansion, and character consistency.
- **Client implementation**: `GeminiService`

## 2. Mistral AI
- **Endpoint**: `https://api.mistral.ai/`
- **Usage**: Alternative LLM provider, strong at European language nuances and logical plotting.
- **Client implementation**: `MistralService`

## 3. Ollama (Local)
- **Endpoint**: Configurable (Default: `http://localhost:11434/` or exposed local network IP)
- **Usage**: Complete offline inference for privacy-first storytelling.
- **Client implementation**: `OllamaService`

## 4. Hugging Face Inference API
- **Endpoint**: `https://api-inference.huggingface.co/`
- **Usage**: Used for specific cloud-based TTS models (e.g., Bark, MMS-TTS).
- **Client implementation**: `HuggingFaceService`

## 5. Android Native TTS
- **Usage**: Built-in Android Text-to-Speech engine, operating offline.
- **Client implementation**: `AndroidTtsManager`

## Audio Utilities API
- `WavUtils.mergeWavFiles()`: Combines multiple WAV files, applies fading, ducking, and ambient themes.
- `AudioEncoder.encodeWavToM4a()`: Transcodes WAV outputs into compressed AAC (M4A) format for EPUB and export.
- `AudioMixer.mixWithDucking()`: Advanced audio ducking algorithm to reduce background music when narration is active.

## Export & File Generation API
- `EpubUtils.generateEpub()`: Generates an EPUB3 compliant e-book directly from Room Database entities (`Book`, `Chapter`).

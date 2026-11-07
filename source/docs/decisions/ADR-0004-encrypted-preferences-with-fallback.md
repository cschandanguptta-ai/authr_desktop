# ADR-0004: Encrypted SharedPreferences with Unencrypted Fallback

## Status
Accepted

## Context
Users configure sensitive LLM API keys (Gemini, Groq, NVIDIA, Mistral, OpenRouter). Standard Android `SharedPreferences` stores values as cleartext XML files accessible on rooted devices or via system backups. However, Android `MasterKey` and `EncryptedSharedPreferences` frequently throw runtime crypto initialization exceptions on certain Android device forks, custom ROMs, or emulator environments with missing hardware keystores.

## Decision
Attempt initialization of `EncryptedSharedPreferences` using `AES256_GCM` scheme for keys and values (`LlmOrchestrator.kt:30-46`). Wrap initialization in a `try-catch` block: upon any exception, fallback to standard `SharedPreferences` named `secure_studio_settings_fallback`.

## Alternatives (Evidenced in Code)
- *Strict Keystore Enforcement*: Crashing the app or preventing key storage if hardware keystore is unavailable. Discarded to maintain app availability across diverse emulator and test environments.

## Consequences
- **Positive**: App never crashes on device startup due to Keystore initialization errors.
- **Negative**: API keys on non-compliant devices are stored in cleartext in fallback preferences.

## Do Not Change Unless
- Google Play policies mandate hardware-backed KeyStore encryption without fallback, or credentials are moved to Jetpack Credential Manager.

## Evidence
- `app/src/main/java/com/example/data/api/LlmOrchestrator.kt:30-46` [FACT]
- `app/src/main/java/com/example/ui/viewmodel/SettingsViewModel.kt:22-30` [FACT]

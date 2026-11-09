# Development Guide

## Setting up the Environment

### Prerequisites
- Android Studio Ladybug or later
- JDK 17+
- Android SDK 34+

### Building the Project
1. Clone the repository.
2. Open the project in Android Studio.
3. Sync project with Gradle files.
4. Run the app on an emulator or physical device.

### API Keys
Authr OS relies on several external AI and TTS services. For the full experience, provide API keys in the app's Settings tab:
- **Google Gemini API Key**: Required for advanced reasoning and story generation.
- **Mistral API Key**: Optional, alternative LLM provider.
- **Hugging Face API Key**: Used for external TTS and specialized models.

Alternatively, Authr OS supports **Ollama** for local, offline LLM inference. Set the Ollama URL in the settings.

## Project Structure

- `app/src/main/java/com/example/`
  - `data/`
    - `api/` - Network clients and API definitions
    - `local/` - Room database, DAOs
    - `model/` - Data entities and domain models
    - `repository/` - Repository pattern implementation
  - `di/` - Koin Dependency Injection modules
  - `service/` - Foreground services for background processing
  - `tts/` - Text-to-Speech engines and management
  - `ui/`
    - `screens/` - Jetpack Compose screens
    - `theme/` - Material 3 theme definitions
    - `viewmodel/` - UI state management
  - `utils/` - Helper functions (Audio, EPUB, IO)
- `gradle/libs.versions.toml` - Version catalog for managing dependencies

## Dependency Injection (Koin)

We migrated from Hilt to Koin for dependency injection.
To add a new dependency:
1. Define it in `app/src/main/java/com/example/di/AppModule.kt`.
2. Inject it into a ViewModel or component using `get()` or by constructor injection.

Example adding a new ViewModel:
```kotlin
viewModel { MyNewViewModel(get()) }
```

## Adding New Features
1. Define any new models in `data/model`.
2. Update the Room Database if persistence is needed (`AppDatabase.kt`, and DAOs).
3. Create necessary Repository methods.
4. Update or create ViewModels to handle the logic.
5. Create Composable screens in `ui/screens`.
6. Add the new screen to the navigation/tab bar in `MainActivity.kt`.

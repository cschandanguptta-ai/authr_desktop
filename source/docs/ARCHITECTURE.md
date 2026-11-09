# Architecture Overview

## Authr OS Architecture

Authr OS is built with modern Android development practices, emphasizing clean architecture, modularity, and offline-first capabilities.

### Core Technologies
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Dependency Injection**: Koin
- **Local Database**: Room (SQLite)
- **Concurrency**: Kotlin Coroutines & Flow
- **Audio Processing**: MediaCodec, RxFFmpeg
- **AI Integration**: Gemini, Mistral, Ollama, Hugging Face

### Application Layers

#### 1. UI Layer (Presentation)
- Located in `com.example.ui`
- Built entirely with Jetpack Compose.
- **ViewModels**: Manage state and UI logic (e.g., `StudioViewModel`). They consume data from the Repository layer and expose it to the UI via `StateFlow`.
- **Screens**: Composable functions that represent distinct parts of the application (Story, Audio, Translate, Metadata, Settings).

#### 2. Domain / Repository Layer
- Located in `com.example.data.repository`
- Acts as a single source of truth for the application.
- Mediates between the local database, external APIs, and the UI layer.

#### 3. Data Layer (Local & Remote)
- **Local Database**: Room is used for persistence. Entities include `Story`, `AudioTrack`, `Book`, `Chapter`, and `Lorebook`. DAOs handle data access operations. Full-Text Search (FTS4) is implemented for fast querying.
- **Remote APIs**: Handles communication with LLM providers (Gemini, Mistral, Ollama) and TTS services.

#### 4. Utilities and Services
- **Audio Processing**: `AudioEncoder`, `AudioMixer`, `WavUtils` handle complex audio operations such as ducking, merging, and encoding to M4A.
- **EPUB Generation**: `EpubUtils` manages packaging stories into standard EPUB3 formats.
- **Background Work**: `StudioForegroundService` ensures long-running tasks like audio generation and story writing continue when the app is backgrounded.

### Dependency Injection
We use **Koin** for dependency injection. The DI setup is initialized in `StudioApplication` and the modules are defined in `com.example.di.AppModule`. This provides view models, repositories, and DAOs throughout the application.

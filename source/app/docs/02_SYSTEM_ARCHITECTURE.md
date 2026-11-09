# 02_SYSTEM_ARCHITECTURE

## Overview
Authr OS uses an MVVM (Model-View-ViewModel) plus Clean Architecture setup.

## UI Layer
- **Jetpack Compose**: All screens (Story, TTS, Translate, SEO, Settings, Guide) are built using Compose.
- **StateFlow**: Manages state between the ViewModel and the UI to ensure reactive UI updates.

## Data Layer
- **Room DB**: Stores entities like `Book`, `Chapter`, `Character`, etc. 
- **SharedPreferences**: Stores user configuration, API keys, and draft/autosave settings.

## Integration
Room DAOs and SharedPreferences are injected or instantiated in the AppRepository, which is then managed by the `StudioViewModel`.

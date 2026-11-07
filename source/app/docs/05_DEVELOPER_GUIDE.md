# 05_DEVELOPER_GUIDE

## UI/UX Conventions
- Use Jetpack Compose Material 3 components exclusively.
- All primary actions should be styled with a `contentDescription` for accessibility.

## Extending the AI Pipeline
- Add new agents by subclassing or extending the orchestrator functions in `LlmOrchestrator.kt`.
- When adding a new prompt structure, ensure it passes through the standard Markdown parsing functions.

## Injecting New Plot Frameworks
- Add the new framework to `StoryScreen.kt` in the Structured Plot Arc options.
- Update `DraftingWorker.kt` or the equivalent AI prompt construction block to inject the distinct rules for the new framework.

# Testing Guide

Authr OS employs testing strategies to ensure the reliability of the narrative pipeline, database migrations, and audio processing.

## Current Testing Setup

The project uses JUnit 4 and Robolectric for local unit tests. Since the application is UI-heavy, we prioritize testing ViewModels, Database DAOs, and Utility classes.

### Running Tests
To run tests from the command line:
```bash
./gradlew testDebugUnitTest
```

## Writing Tests

### Unit Tests
Unit tests should be placed in `app/src/test/java/com/example/`.

**What to Test:**
- **ViewModels**: Verify state transitions. E.g., Triggering story generation updates `currentTab` or UI state.
- **Utils**: Ensure `WavUtils`, `EpubUtils`, and `AudioEncoder` process data correctly without crashing.

### Database Testing (Room)
Room DAO testing should be done using Android's testing framework or Robolectric.
1. Create an in-memory database using `Room.inMemoryDatabaseBuilder`.
2. Insert test data.
3. Query the data and assert results.
4. Close the database.

### UI Testing (Jetpack Compose)
Compose testing can be added using the `ui-test-junit4` artifact.
- Ensure buttons have `Modifier.testTag()`.
- Use `composeTestRule.onNodeWithTag("submit_button").performClick()`.

## Screenshot Testing (Roborazzi)
We use Roborazzi for snapshot testing to prevent visual regressions in Jetpack Compose screens.

- Run screenshot verification:
  ```bash
  ./gradlew verifyRoborazziDebug
  ```
- Record new screenshots (when UI changes are intentional):
  ```bash
  ./gradlew recordRoborazziDebug
  ```

Note: Screenshot testing requires specific environment configuration and is best run locally or in a controlled CI environment.

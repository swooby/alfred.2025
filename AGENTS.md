# Alfred 2025 – Contributor Guide

Welcome! This document explains how the Alfred 2025 Android project is organized and the conventions to follow when editing or adding files. Unless a nested `AGENTS.md` overrides these rules, everything in this file applies repo-wide.

## Project overview
- **Type:** Android app built with Kotlin, Jetpack Compose, and AndroidX libraries.
- **Minimum/target SDK:** 34 / 36 (`app/build.gradle.kts`).
- **App entry points:** `AlfredApp` sets up singletons (database, ingest pipeline, rules, summarizer, settings, and media source). `MainActivity` hosts the Compose UI and starts the foreground `PipelineService` once required permissions are granted.
- **Core loop:** `PipelineService` ingests device events, persists them, lets `RulesEngine` decide whether to speak, and drives TTS through `SpeakerImpl`.

## Module and package map
The repo currently has a single Gradle module: `app`.

### `app/src/main/java/com/swooby/alfred`
- `core/ingest` – event normalization, de-duplication, and fan-out (`EventIngest`).
- `core/rules` – decision engine (`RulesEngine`, `RulesConfig`, rate-limits, quiet hours, etc.).
- `core/summary` – summary contracts and templated summarizer used for TTS.
- `data` – Room database definitions (`AlfredDb`, entities, DAOs). Use suspend DAO methods where possible.
- `pipeline` – Android service wiring together sources, rules, persistence, and speech (`PipelineService`).
- `sources` – OS integrations (notification listener, media sessions, display/network observers, etc.).
- `settings` – DataStore-backed repository plus Compose UI for editing settings.
- `tts` – Text-to-speech abstraction (`Speaker`, `SpeakerImpl`).
- `ui` – Compose UI (permissions, settings, etc.).
- `support` & `util` – helper classes, extension functions, and shared utilities.

### `app/src/main/res`
- Standard Android resources. Hard-coded strings should be moved into `values/strings.xml` as part of the TODO in `README.md`.

## Coding conventions
- **Language level:** Kotlin 2.0 targeting JVM 17. Use idiomatic Kotlin features (data classes, sealed interfaces, extensions). Keep coroutine dispatchers explicit.
- **Compose UI:**
  - Prefer small, focused composables. Hoist state to callers when reasonable.
  - Use Material 3 components (`androidx.compose.material3`).
  - Keep previews in the same file, guarded by `@Preview(showBackground = true)` and `@Composable`.
- **Android services/components:**
  - Foreground services must create a notification channel before calling `startForeground` (see `PipelineService`).
  - Check runtime permissions before accessing protected APIs. Follow the pattern in `PermissionsScreen` & `PipelineService` (preflight checks before `start()`).
- **Coroutines & flows:**
  - Favor structured concurrency using the provided scopes (`appScope` in `AlfredApp`, `scope` in services). Cancel scopes in lifecycle callbacks (`onDestroy`).
  - Use cold `Flow` operators (`map`, `collectLatest`) with care to avoid leaking contexts. Prefer `launchIn(scope)` / `onEach` to keep pipelines readable.
- **Room & persistence:**
  - Add migrations whenever altering schemas. Provide DAO methods as suspend functions returning domain models.
  - Normalize timestamps (`Clock.System.now()`) and durations like `EventIngestImpl` to keep persistence consistent.
- **Settings/DataStore:**
  - When storing sets in DataStore, serialize to comma-separated lists like the existing implementation. Mirror the defaults defined in `SettingsRepository`.
- **String resources:**
  - Do not add new literal UI strings directly in Kotlin. Place them in `res/values/strings.xml` and load via `stringResource` or `context.getString`.
- **Testing new sources:**
  - Wrap platform interactions behind interfaces in `sources/` so they can be mocked or disabled when permissions are missing.

## Build & test commands
Run these from the repo root unless noted otherwise.

| Purpose | Command |
| --- | --- |
| Lint and static analysis | `./gradlew lint` |
| Run unit tests | `./gradlew :app:testDebugUnitTest` |
| Assemble debug APK | `./gradlew :app:assembleDebug` |
| (Optional) Instrumentation tests* | `./gradlew :app:connectedDebugAndroidTest` |

\* Instrumentation tests require a connected device or emulator; they may not be available in CI.

Always ensure the relevant lint/tests succeed locally before opening a PR. If you skip a check (e.g., no emulator available), explain why in your PR description.

## Dependency management
- Add new libraries via the Gradle Kotlin DSL files (`build.gradle.kts`, `app/build.gradle.kts`). Keep versions centralized and favor stable releases.
- If you introduce annotation processors, prefer KSP (already enabled) over KAPT.

## Pull request expectations
1. Update or add tests when behavior changes (unit tests, instrumentation, or verification logic in `core` modules).
2. Document user-facing changes in `README.md` or the relevant UI docs, and always ensure all documentation (README, KDoc/Javadoc, inline docs, etc.) stays up to date with both the modifications you make and any out-of-sync changes you observe.
3. Maintain consistent Kotlin formatting (use `ktlint` defaults; Android Studio’s “Reformat Code” is acceptable).
4. Keep commits focused and include descriptive messages.
5. If you add new features that require runtime permissions, update the permission onboarding flow in `ui/permissions`.

## Additional notes
- The project is an AI-generated prototype; when in doubt, favor clarity over premature abstraction.
- Review existing patterns (e.g., dedupe logic in `EventIngestImpl`, rule configuration in `SettingsRepository`) before reinventing solutions.
- Watch for TODOs in code/README and clean them up as you touch related areas.

Happy hacking!

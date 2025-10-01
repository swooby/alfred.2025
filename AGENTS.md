# Alfred 2025 – Contributor Guide

Welcome! This document captures the expectations for working in the Alfred 2025 Android project. Unless a nested `AGENTS.md` overrides them, every rule below applies repo-wide.

## 1. Project quick facts
- **Type:** Android app written in Kotlin, built with Gradle Kotlin DSL, targeting Compose UI.
- **Modules:** Single Gradle module `app` that contains all Android code and resources.
- **Entry points:**
  - `AlfredApp` wires dependency singletons (database, ingest pipeline, rules, summarizer, settings, TTS).
  - `MainActivity` hosts the Compose UI and launches the foreground `PipelineService` once permissions are granted.
  - `PipelineService` ingests events, applies rules via `RulesEngine`, persists data, and triggers speech output through `SpeakerImpl`.

## 2. Repository layout
```
app/
  src/
    main/
      java/com/swooby/alfred/
        core/ingest      // Event normalization and fan-out (`EventIngest`)
        core/rules       // Decision engine (`RulesEngine`, configs, throttling)
        core/summary     // Summary contracts and templated summarizer
        data             // Room database (`AlfredDb`, entities, DAOs)
        pipeline         // Android service orchestration (`PipelineService`)
        sources          // Platform integrations (notifications, media, etc.)
        settings         // DataStore-backed repositories and Compose UI
        tts              // Text-to-speech abstraction (`Speaker`, `SpeakerImpl`)
        ui               // Compose screens (permissions, settings, etc.)
        support, util    // Shared helpers and extension utilities
      res/               // Android resources (strings, themes, drawables)
```

## 3. Coding conventions
### Kotlin & coroutines
- Target Kotlin 2.0 / JVM 21; prefer idiomatic constructs (data classes, sealed types, extension functions).
- Keep coroutine dispatchers explicit. Use structured concurrency—cancel scopes in lifecycle callbacks (`onDestroy`, etc.).
- Prefer suspend DAO functions returning domain models. Normalize timestamps via `Clock.System.now()`.

### Compose UI
- Keep composables small and state-hoist when possible.
- Use Material 3 components (`androidx.compose.material3`).
- Place previews in the same file, annotated with `@Preview(showBackground = true)`.
- Move user-facing strings into `res/values/strings.xml`; avoid hard-coded literals.

### Android components & services
- Foreground services must register notification channels before `startForeground`.
- Guard privileged APIs with runtime permission checks modeled after `PermissionsScreen`/`PipelineService`.
- Wrap platform integrations inside `sources/` interfaces to keep them mockable and permission-aware.

### Testing expectations
- Add or update unit/instrumentation tests when behavior changes.
- Favor `Flow` operators like `map`, `onEach`, `launchIn(scope)` to avoid leaking contexts.

## 4. Documentation duties
- Keep **all** documentation synchronized with your changes and fix mismatches you discover, including:
  - This `AGENTS.md` and any nested variants.
  - `README.md`, changelogs, architectural docs.
  - KDoc/Javadoc, inline comments, and UI text docs.
- When adding features that alter user flow or permissions, update `README.md` and relevant UI docs/screenshots.
- Document new configuration flags, database schema updates, and background behaviors.

## 5. Build & test commands
Run from the repo root unless stated otherwise.

| Purpose | Command |
| --- | --- |
| Lint & static analysis | `./gradlew lint` |
| Unit tests | `./gradlew :app:testDebugUnitTest` |
| Assemble debug APK | `./gradlew :app:assembleDebug` |
| Instrumentation tests* | `./gradlew :app:connectedDebugAndroidTest` |

\* Requires an attached device or emulator; explain in PRs when not run.

## 6. Dependency guidelines
- Manage dependencies via Gradle Kotlin DSL (`build.gradle.kts`, `app/build.gradle.kts`).
- Prefer stable releases and centralize version constants. Use KSP over KAPT when possible.
- If adding annotation processors or libraries, include rationale in the PR.

## 7. Pull request expectations
1. Keep commits focused with descriptive messages.
2. Ensure lint/tests pass or document why they were skipped.
3. Update onboarding flows if new permissions or capabilities are introduced.
4. Favor clarity over premature abstraction; reuse existing patterns (`EventIngestImpl` dedupe, `SettingsRepository` defaults, etc.).

Happy building!

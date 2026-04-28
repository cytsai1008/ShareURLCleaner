# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Assemble debug APK
./gradlew assembleDebug

# Run unit tests (JVM, no device needed)
./gradlew test

# Run a single unit test class
./gradlew test --tests "com.cytsai.urlclean.UrlCleanerTest"

# Run instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest

# Clean build
./gradlew clean assembleDebug
```

## Dependency Management

All versions are centralized in `gradle/libs.versions.toml`. Never edit `app/build.gradle.kts` dependency versions directly — update the catalog instead. Use `npm` / `yarn` rules do not apply here; this is an Android project managed by Gradle.

- Add a new library: add a version to `[versions]`, a library alias to `[libraries]`, then reference it via `libs.<alias>` in `app/build.gradle.kts`.
- OkHttp (`libs.okhttp`) is the HTTP client — do not use `HttpURLConnection`.
- OkHttp's `HttpUrl` is used for URL parsing/building in `UrlCleaner` — do not use `android.net.Uri` for URL manipulation.

## Architecture

### Two-activity design

**`MainActivity`** — settings screen only. Never receives share intents.

**`ShareActivity`** — headless (`Theme.NoDisplay`, `noHistory=true`). Declared with `ACTION_SEND / text/plain` intent-filter so other apps can share URLs to it. It loads filter rules, cleans the URL, fires a system share chooser with the cleaned URL, then calls `finish()` — no UI of its own.

### Data flow for URL cleaning

```
ShareActivity.onCreate()
  → FilterRepository.loadRules()       # synchronous read from filesDir/filter_rules.txt
  → UrlCleaner.clean(url, rules)       # pure function, no Android deps
  → Intent.createChooser(cleanedUrl)  # re-share
```

### Data flow for filter updates

```
User taps "Update Now" / WorkManager daily trigger
  → FilterRepository.downloadAndUpdate(url)   # OkHttp GET → parse → atomic write to .tmp then rename
  → SettingsDataStore.setLastUpdated / setRuleCount
```

### Key classes

| File | Role |
|------|------|
| `data/SettingsDataStore.kt` | DataStore Preferences wrapper — single source of truth for filter URL, auto-update toggle, last-updated timestamp, rule count |
| `data/FilterRepository.kt` | Downloads and parses AdGuard `$removeparam` filter; reads/writes `filesDir/filter_rules.txt` |
| `core/UrlCleaner.kt` | Pure `object` — `clean(rawUrl, rules): String`. No Android deps; unit-testable with plain JUnit |
| `worker/FilterUpdateWorker.kt` | `CoroutineWorker` — scheduled once daily via `WorkManager` with `NETWORK_CONNECTED` constraint; companion provides `schedule(ctx)` / `cancel(ctx)` |
| `MainViewModel.kt` | `AndroidViewModel`; exposes `StateFlow<SettingsUiState>` combining four DataStore flows |
| `App.kt` | `Application` subclass implementing `Configuration.Provider` for on-demand WorkManager init |

### Filter rules file format (`filesDir/filter_rules.txt`)

One rule per line:
- Global rule: `param` (e.g., `utm_source`)
- Domain-scoped rule: `domain\tparam` (tab-separated, e.g., `facebook.com\tfbclid`)

### AdGuard filter parsing

The source filter (`$removeparam` syntax from filter_17_TrackParam) is parsed in `FilterRepository`:
- Skip lines starting with `!` (comments), `@@` (exceptions), or blank lines
- `$removeparam=PARAM` → global `FilterRule(null, param)`
- `||domain.com^$removeparam=PARAM` → domain-scoped `FilterRule("domain.com", param)`
- `$removeparam` with no `=` → skip

### WorkManager initialization

WorkManager auto-init is suppressed in `AndroidManifest.xml` (the `WorkManagerInitializer` meta-data entry is removed). `App` implements `Configuration.Provider` instead — this is intentional. Do not re-add the default initializer.

### ViewModel wiring

No DI framework. `MainViewModel` is instantiated via a nested `Factory(application: Application)` class passed to `by viewModels { MainViewModel.Factory(application) }` in `MainActivity`. `ShareActivity` instantiates `FilterRepository` and `UrlCleaner` directly from `applicationContext`.

## Package & SDK

- Package: `com.cytsai.urlclean`
- minSdk 26 (Android 8.0) — `java.time` APIs (e.g., `Instant`, `ZoneId`) are safe to use without desugaring
- compileSdk / targetSdk 36
- Kotlin 2.2.10, AGP 9.2.0, Compose BOM 2026.02.01

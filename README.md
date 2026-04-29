# Share URL Cleaner

Share URL Cleaner is a small Android share-target app that removes tracking query parameters from
URLs before you pass them on.

It is meant for the normal Android share flow: share a link to Share URL Cleaner, the app cleans the
URL with local filter rules, then immediately opens the system share sheet again with the cleaned
link.

## Features

- Cleans HTTP and HTTPS URLs shared from other apps.
- Removes common tracking parameters such as campaign IDs and click IDs.
- Uses filter rules parsed from AdGuard `$removeparam` filter lists.
- Stores rules locally on device.
- Supports manual rule updates.
- Supports optional daily background rule updates with WorkManager.
- No account system, ads, or analytics.

## How It Works

The app has two activities:

- `MainActivity`: settings screen for filter URL, manual update, auto-update, status, and
  third-party licenses.
- `ShareActivity`: headless share target for `ACTION_SEND` / `text/plain`.

Share flow:

```text
Share URL from another app
  -> ShareActivity
  -> load local filter_rules.txt
  -> UrlCleaner.clean(...)
  -> Android share chooser with cleaned URL
```

The share target can appear for plain text shares because Android intent filters cannot restrict
`EXTRA_TEXT` to URLs only. `ShareActivity` only cleans text that starts with `http://` or
`https://`; other shared text is passed through unchanged.

## Filter Rules

The default filter list is:

```text
https://raw.githubusercontent.com/AdguardTeam/FiltersRegistry/master/filters/filter_17_TrackParam/filter.txt
```

Rules are downloaded by `FilterRepository`, parsed, and stored at:

```text
filesDir/filter_rules.txt
```

Internal rule file format:

```text
param
domain.com<TAB>param
```

Examples:

```text
utm_source
facebook.com	fbclid
```

## Privacy

Share URL Cleaner processes shared URLs locally on device. The app does not use accounts, ads, or
analytics.

Network access is used to download the configured filter rule list over HTTPS. Shared URLs are not
sent to the filter list host by the app.

## Development

This is a Kotlin Android project using Gradle, Jetpack Compose, DataStore, WorkManager, and OkHttp.

Common commands:

```bash
./gradlew assembleDebug
./gradlew test
./gradlew connectedAndroidTest
./gradlew clean assembleDebug
```

Build an Android App Bundle for release:

```bash
./gradlew bundleRelease
```

Versions are centralized in:

```text
gradle/libs.versions.toml
```

Do not hardcode dependency versions in `app/build.gradle.kts`.

## Project Structure

```text
app/src/main/java/com/cytsai/urlclean/
  MainActivity.kt              Settings UI
  ShareActivity.kt             Headless share target
  MainViewModel.kt             Settings state and update actions
  App.kt                       WorkManager configuration provider
  core/UrlCleaner.kt           Pure URL cleaning logic
  data/FilterRepository.kt     Filter download, parse, read/write
  data/SettingsDataStore.kt    DataStore settings wrapper
  worker/FilterUpdateWorker.kt Daily background update worker
```

## Third-Party Licenses

Third-party license information is available inside the app settings.

The app icon is based on Phosphor Icons, licensed under the MIT License.

## License

This project is licensed under the MIT License. See `LICENSE` for details.

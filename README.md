# Share URL Cleaner

Share URL Cleaner is a small Android share-target app that removes tracking query parameters from
URLs before you pass them on.

It is meant for the normal Android share flow: share a link to Share URL Cleaner, the app cleans the
URL with local filter rules, then immediately opens the system share sheet again with the cleaned
link.

## Features

- Cleans every HTTP and HTTPS URL in shared text, including links embedded in a caption.
- Keeps an attached image when an app shares image + text together.
- Removes common tracking parameters such as campaign IDs and click IDs.
- Uses filter rules parsed from AdGuard `$removeparam` filter lists, including regex rules.
- Combines several filter lists; built-in lists can be turned off and custom lists added.
- Optional aggressive mode follows redirects (link shorteners, share wrappers) to the real
  destination before cleaning.
- Stores rules locally on device.
- Supports manual rule updates and optional daily background updates with WorkManager.
- English, Simplified Chinese, and Traditional Chinese UI.
- No account system, ads, or analytics.

## How It Works

The app has two activities:

- `MainActivity`: settings screen for filter lists, manual update, auto-update, aggressive mode,
  status, and third-party licenses.
- `ShareActivity`: headless share target for `ACTION_SEND` with `text/plain` or `image/*`.

Share flow:

```text
Share text or image + text from another app
  -> ShareActivity
  -> load local filter_rules.txt
  -> ShareTextCleaner.cleanUrls(...)   clean each URL, optionally follow redirects
  -> Android share chooser with the cleaned text (and the original image, if any)
```

The share target can appear for any plain text share because Android intent filters cannot restrict
`EXTRA_TEXT` to URLs only. Text without an `http://` or `https://` URL is passed through unchanged.

### Aggressive Mode

Some share links are only a redirect (`b23.tv`, `bit.ly`, `s.shopee.tw`, Facebook and Threads share
links, ...). Aggressive mode requests the link, follows the full redirect chain (HTTP redirects and
simple JavaScript `location` handoffs), cleans every hop, and shares the final URL.

Settings:

- **Off**: clean tracking parameters only (default).
- **Selected domains only**: follow redirects only for hosts in an editable domain list.
  Subdomains match automatically.
- **All links**: follow redirects for every shared link.

The share sheet also has a second entry, **Aggressive Mode**, which follows redirects for every
link in that one share, whatever the setting.

If the request fails, the link is shared without its redirect followed.

Warning: aggressive mode opens the link before you share it. One-time links, such as password
reset or magic sign-in links, can be used up.

## Filter Rules

Built-in filter lists, all enabled by default:

| Name | URL |
|------|-----|
| AdGuard URL Tracking | `https://raw.githubusercontent.com/AdguardTeam/FiltersRegistry/master/filters/filter_17_TrackParam/filter.txt` |
| Legitimate URL Shortener | `https://raw.githubusercontent.com/DandelionSprout/adfilt/master/LegitimateURLShortener.txt` |
| uBlock Origin Privacy | `https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/privacy.txt` |
| Kenichi's URL Cleaner List | `https://raw.githubusercontent.com/cytsai1008/ShareURLCleaner/main/Filters/filter.txt` |

Kenichi's URL Cleaner List is maintained in this repository at `Filters/filter.txt`. It holds
tracking parameters the public lists miss.

Only `$removeparam` rules are used. Comments, exceptions (`@@`), bare `$removeparam`, inverted
parameters (`~param`), and regexes that Java cannot compile are skipped.

`FilterRepository` downloads all enabled lists, parses and de-duplicates the rules, and stores
them at:

```text
filesDir/filter_rules.txt
```

If one list fails to download, the update stops and the previous rule file stays in place.

Internal rule file format, one rule per line:

```text
param
domain.com|other.com<TAB>param
```

Examples:

```text
utm_source
facebook.com	fbclid
shopee.*	sp_atk
/^[a-z]{4}=/
```

A parameter written as `/regex/` (optional `i` flag) is matched against `name=value`.

## Privacy

Share URL Cleaner processes shared URLs locally on device. The app does not use accounts, ads, or
analytics.

Network access is used to download the configured filter lists over HTTPS. Shared URLs are not sent
to the filter list hosts.

When aggressive mode applies to a link, the app requests that link from its own host (and from each
host in the redirect chain) to find the destination. This is the same request your browser makes
when you open the link. With aggressive mode off, shared links are never requested.

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
  core/ShareTextCleaner.kt     Finds and cleans every URL in shared text
  core/RedirectResolver.kt     Aggressive mode redirect following
  data/FilterRepository.kt     Filter download, parse, read/write
  data/SettingsDataStore.kt    DataStore settings, built-in lists, aggressive domains
  worker/FilterUpdateWorker.kt Daily background update worker
Filters/filter.txt             Kenichi's URL Cleaner List
```

## Third-Party Licenses

Third-party license information is available inside the app settings.

The app icon is based on Phosphor Icons, licensed under the MIT License.

## License

This project is licensed under the MIT License. See `LICENSE` for details.

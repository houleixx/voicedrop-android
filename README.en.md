# VoiceDrop Android

**An Android client that turns spoken recordings into articles.**

VoiceDrop Android is the Android version of VoiceDrop. It follows the iOS app as the reference implementation and shares the same backend APIs, article format, and sharing flow.

- iOS project: https://github.com/jianshuo/voicedrop
- 中文: [README.md](README.md)

---

## Features

- Recording: start recording from "My Recordings" and produce a `VoiceDrop-*.m4a` file.
- Automatic upload: upload finished recordings and trigger transcription plus article generation.
- Recording list: show pending, ASR, mining, article-ready, and empty states.
- Article detail: read articles, play source audio, delete recordings/articles, and share public links.
- Voice editing: hold to speak an edit request, then refresh the rewritten article.
- Photo insertion: pick photos or use the camera, upload them, and ask AI to insert them into the article.
- Writing style: save a personal writing style for future article generation.
- WeChat drafts: configure a WeChat Official Account and publish or update drafts.
- VD Community: browse community posts, share articles, report content, and block users.
- Export: package articles, audio, subtitles, and indexes.

---

## Relationship To The iOS App

The Android version uses the iOS app as the product and API reference:

- Backend APIs, article schema, photo markers, and WeChat publishing behavior stay aligned.
- Android interactions may follow platform conventions for tabs, permissions, dialogs, and keyboard handling.
- Internal implementation can differ as long as user-visible behavior and backend contracts match.

iOS project:

```text
https://github.com/jianshuo/voicedrop
```

---

## Flow

```text
Record
  -> upload audio
  -> backend transcription
  -> AI article generation
  -> article appears in the app
  -> share, edit, add photos, or publish to WeChat
```

Article generation status is reflected in the list in near real time. The article detail view supports voice-based edits and photo insertion.

---

## Backend APIs

Android and iOS share the same backend services:

| Service | Purpose |
|---|---|
| Files API | recordings, articles, photos, sharing, settings, WeChat config |
| Agent Worker | article generation, article editing, live status |
| WebSocket | generation status and voice editing |
| Public share page | public article preview and link cards |

Main capabilities:

- Upload recordings and photos
- Fetch recording lists and article details
- Create public share links
- Save writing style and app settings
- Publish or update WeChat Official Account drafts
- Read and submit VD Community content

---

## Recording Filename Format

Recordings follow the iOS naming convention:

```text
VoiceDrop-2026-06-18-143052-0m33s-Thu-Afternoon.m4a
```

The filename contains:

- recording start time
- duration
- weekday
- day period

The `VoiceDrop-` prefix and `.m4a` suffix are part of the backend recognition contract and should not be changed casually.

---

## Photo Markers

Photos inside articles use marker syntax:

```text
[[photo:photos/<sessionTs>/<offset>-<rand>.jpg]]
```

The app renders these markers as inline photos. WeChat publishing, public share pages, and community detail pages use the same rule.

---

## WeChat Publishing

After configuring a WeChat Official Account AppID and AppSecret in settings, the article detail page can publish to the WeChat draft box.

Publishing behavior:

- Create a new draft when the article has not been published before.
- Update the existing draft when one already exists.
- Show readable messages for WeChat API errors.
- Open the settings page when configuration is missing.

The WeChat Official Account backend must whitelist the server egress IP. The current IP is shown in the app settings page.

---

## Tech Stack

- Java
- Native Android Views
- Android Gradle Plugin
- ViewPager
- OkHttp
- Bouncy Castle
- DialogX
- JUnit

---

## Build

Requirements:

- JDK 17
- Android SDK
- Gradle Wrapper

Run unit tests:

```bash
./gradlew testDebugUnitTest
```

Build debug APK:

```bash
./gradlew assembleDebug
```

Build release APK:

```bash
./gradlew assembleRelease
```

The release APK is generated at:

```text
app/build/outputs/apk/release/app-release.apk
```

---

## Release Signing

Release builds can be signed through the Gradle signing config. The project supports:

- local signing config files
- environment variables

Available environment variables:

```text
ANDROID_KEYSTORE_PATH
ANDROID_KEYSTORE_PASSWORD
ANDROID_KEY_ALIAS
ANDROID_KEY_PASSWORD
```

When signing config is complete, `assembleRelease` produces a signed APK.

---

## Notes

- Real devices are best for testing the full recording flow.
- Emulators are useful for UI, list, dialog, and API-flow checks.
- WeChat publishing depends on WeChat configuration, the backend relay, and the Official Account IP whitelist.
- Backend APIs, article format, and cross-platform behavior should follow the iOS project first.

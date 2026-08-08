# Lattice

Lattice is a privacy-first Android Telegram client project with a familiar WhatsApp-style navigation model and Maximum Privacy defaults.

## Current milestone: 0.1.0-alpha.1 foundation

This branch introduces the first buildable Android application foundation:

- Native Android + Jetpack Compose + Material 3
- Chats / Updates / Groups / Calls navigation
- Dedicated Privacy, Media, Sexual content, Gore, Spam, Swearing and Do Not Disturb settings tabs
- Maximum Privacy defaults
- `t.me`, `telegram.me` and `tg://` intent handling
- Telegram transport abstraction ready for TDLib
- Tor / Orbot policy foundation
- Lattice Key Android Keystore / StrongBox foundation
- GitHub Actions debug APK build

## Privacy baseline

Lattice defaults to sending no optional data from the client, no contact syncing, no automatic link previews, no media auto-download, no Stars/paid-media teasers, no P2P calls when IP hiding is active, and screenshot/Recents protection.

These controls cannot prevent Telegram from receiving data technically required to provide Telegram service. Lattice will describe those limits rather than claim otherwise.

## Telegram status

TDLib is not wired into this first foundation commit yet. The UI talks to a `TelegramRepository` boundary so TDLib authentication, encrypted local storage, chat updates, groups, channels, media, proxies and deep-link resolution can be added without coupling the UI directly to native TDLib classes.

## Build

GitHub Actions builds a debug APK using JDK 17, Gradle 9.5 and Android API 36 (Android 16). Pushes to `main` run the APK build automatically. Locally, use a compatible Gradle 9.5 installation until the Gradle wrapper is committed.

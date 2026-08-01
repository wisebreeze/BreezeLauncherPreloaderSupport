# BreezeLauncherPreloaderSupport

> [!IMPORTANT]
> **This repository has been migrated.** The codebase has been moved to the
> [BreezeLauncherAndroid](https://github.com/wisebreeze/BreezeLauncherAndroid)
> repository (referred to as "BLA"). This repo is kept only as a redirect.

## Where to get the APK

Pre-built release APKs are published on the BLA repository's
[Releases](https://github.com/wisebreeze/BreezeLauncherAndroid/releases) page.

Look for the `preloader` variant:

| File | Package name |
|------|--------------|
| `BreezeLauncher-preloader-release.apk` | `org.levimc.launcher` |

Download and install that APK on your device.

## Why the migration?

The BLA repository hosts the unified CI pipeline that builds all BreezeLauncher
variants (default, preloader, antutu) in parallel. Consolidating the build
here avoids duplicated CI config and keeps all release artifacts in one place.

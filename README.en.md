<div align="center">

# Breeze Launcher Compatibility Framework

**Breeze Launcher Preloader Support — A LeviLaunchroid preloader compatibility layer for BreezeLauncher**

[![License: Apache 2.0](https://img.shields.io/github/license/wisebreeze/BreezeLauncherPreloaderSupport)](https://github.com/wisebreeze/BreezeLauncherPreloaderSupport/blob/main/LICENSE)
[![Android](https://img.shields.io/badge/Android-9.0%2B-green?style=flat-square&logo=android)](https://www.android.com/)
[![NDK](https://img.shields.io/badge/NDK-r29-orange?style=flat-square)](https://developer.android.com/ndk)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.0-purple?style=flat-square&logo=kotlin)](https://kotlinlang.org/)

</div>

---

## Introduction

The Breeze Launcher Compatibility Framework is a companion project for [BreezeLauncher](https://github.com/wisebreeze/BreezeLauncher), providing a full LeviLaunchroid preloader compatibility environment. It enables native C++ mods developed for LeviLaunchroid (such as LeviMap, BreezeMap, etc.) to run on BreezeLauncher without modification.

This framework is built on LeviLaunchroid's preloader-android subsystem, retaining all core interfaces including mod loading, game hooks, Mod Menu registration, and input event forwarding, while adapting to BreezeLauncher's `com.wisebreeze.launcher` package structure.

### Key Features

- **Preloader Compatibility Layer** — Full port of `libpreloader.so` and its JNI bindings, supporting `PL_REGISTER_MOD` lifecycle, `pl::memory::hook`, `pl::input` input callbacks, `pl::modmenu` Mod Menu registration, and all other APIs
- **Signature Rules System** — Built-in `PreloaderSignatureRulesManager` locates pause-menu / HUD-screen functions inside `libminecraftpe.so` via pattern scanning, enabling the in-game Mod Menu button to correctly sense menu state
- **Input Event Forwarding** — Touch, key, mouse, and text input events are all forwarded to the preloader; native mods can register callbacks to receive them
- **Mod Loading Chain** — `ModNativeLoader` → `System.load` → `initializeLoadedMod` → `enableLoadedMods`, with full `.levipack` package format support
- **Firebase Integration** — Crashlytics crash reporting + FCM push notifications

### Tech Stack

| Component | Version |
|-----------|---------|
| Android minSdk | 28 (Android 9.0) |
| targetSdk | 35 |
| compileSdk | 36 |
| AGP | 8.13.2 |
| Kotlin | 2.3.0 |
| NDK | 29.0.14206865 |
| CMake | 3.22 |
| Java | 21 |

## Build

### Prerequisites

- Android Studio Ladybug or later
- JDK 21
- Android SDK (compileSdk 36)
- Android NDK r29 (29.0.14206865)
- CMake 3.22+

### Local Build

```bash
git clone --recursive https://github.com/wisebreeze/BreezeLauncherPreloaderSupport.git
cd BreezeLauncherPreloaderSupport
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

### CI Build

The project includes a GitHub Actions workflow (`.github/workflows/android.yml`) that supports:

- **Manual trigger** (`workflow_dispatch`) — Builds a debug APK and uploads it to the `artifact` release
- **Tag push trigger** — Builds a release APK, signs it, and publishes a GitHub Release

Build artifacts: https://github.com/wisebreeze/BreezeLauncherPreloaderSupport/releases/tag/artifact

## Architecture

```
BreezeLauncherPreloaderSupport/
├── app/                              # Main application module
│   ├── src/main/cpp/
│   │   ├── preloader/                # preloader-android submodule (LiteLDev)
│   │   ├── libHttpClient/            # Microsoft libHttpClient submodule
│   │   └── CMakeLists.txt
│   ├── src/main/java/org/levimc/launcher/
│   │   ├── core/minecraft/           # MinecraftActivity, runtime prep, game package manager
│   │   ├── core/mods/                # Mod manager, native loader
│   │   ├── core/preloader/           # Signature rules manager
│   │   ├── preloader/                # PreloaderInput JNI compatibility shim
│   │   └── ui/                       # Activities, dialogs, overlays
│   └── google-services.json          # Firebase config (committed)
├── minecraft/                        # minecraft library module (org.levimc compat classes)
├── minecraft-msftauth/               # Microsoft auth library module
└── resources/preloader/              # Signature rules source JSON
```

### Preloader Compatibility Design

BreezeLauncher uses the `com.wisebreeze.launcher` package name, while `libpreloader.so`'s JNI symbols are bound to the `org.levimc.launcher` package path. The compatibility approach:

1. **minecraft module** provides shim classes under `org.levimc.launcher.*` packages (`ModManager`, `ExternalModBridge`, `PreloaderInput`, `MinecraftRuntimePreparer`) that declare native methods matching the preloader's JNI symbols
2. **app module**'s corresponding classes (`com.wisebreeze.launcher.*`) do not declare native methods; instead they delegate to the minecraft module's shims
3. **Application startup** registers a `ModManagerDelegate` that bridges Mod object conversion between the two package namespaces

## Mod Development

This framework is compatible with all of LeviLaunchroid's native mod APIs. Mods register their lifecycle via the `PL_REGISTER_MOD` macro, register Mod Menu controls via `pl::modmenu::ModuleBuilder` / `ButtonBuilder`, and install game hooks via `pl::memory::hook`.

Example mods:
- [full-cpp-mod](examples/full-cpp-mod/) — Complete C++ lifecycle mod example
- [LeviMap](https://github.com/wisebreeze/breezeMap) — Minimap mod

Mods are packaged as `.levipack` (ZIP) containing `manifest.json` + `lib*.so` + optional resources.

## License

Apache License 2.0 — see [LICENSE](LICENSE)

## Acknowledgements

- [LiteLDev/LeviLaunchroid](https://github.com/LiteLDev/LeviLaunchroid) — Original LeviLaunchroid project
- [LiteLDev/preloader-android](https://github.com/LiteLDev/preloader-android) — Preloader runtime
- [microsoft/libHttpClient](https://github.com/microsoft/libHttpClient) — HTTP client library

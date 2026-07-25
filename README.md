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

- **Preloader Compatibility Layer** — Full port of `libpreloader.so` and its JNI bindings, supporting `PL_REGISTER_MOD` lifecycle, `pl::memory::hook`, `pl::input` input callbacks, and `pl::modmenu` Mod Menu registration APIs
- **Signature Rules System** — Built-in `PreloaderSignatureRulesManager` locates pause-menu / HUD-screen functions inside `libminecraftpe.so` via pattern scanning, so the in-game Mod Menu button correctly senses menu state
- **Input Event Forwarding** — Touch, key, mouse, and text input events are all forwarded to the preloader; native mods can register callbacks to receive them
- **Mod Loading Chain** — `ModNativeLoader` → `System.load` → `initializeLoadedMod` → `enableLoadedMods`, with full `.levipack` package format support
- **Firebase Integration** — Crashlytics crash reporting and FCM push notifications

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

```bash
git clone --recursive https://github.com/wisebreeze/BreezeLauncherPreloaderSupport.git
cd BreezeLauncherPreloaderSupport
./gradlew assembleDebug
```

CI builds are triggered manually via GitHub Actions (workflow_dispatch) and publish APKs to the `artifact` release tag.

## Architecture

```
BreezeLauncherPreloaderSupport/
├── app/                               # Main application module
│   ├── src/main/cpp/                  # Native code
│   │   ├── preloader/                 # preloader-android submodule
│   │   └── libHttpClient/             # Microsoft libHttpClient submodule
│   └── src/main/java/
│       └── org/levimc/launcher/
│           ├── core/minecraft/        # MinecraftActivity, runtime preparer
│           ├── core/mods/             # ModManager, ModNativeLoader
│           ├── core/preloader/        # PreloaderSignatureRulesManager
│           └── ui/                    # Activities, dialogs, overlays
├── minecraft/                         # minecraft library module (org.levimc shim classes)
├── minecraft-msftauth/                # Microsoft auth library module
└── resources/preloader/               # Signature rules source JSON
```

### Preloader Compatibility Design

BreezeLauncher uses the `com.wisebreeze.launcher` package name, while `libpreloader.so`'s JNI symbols are bound to the `org.levimc.launcher` package path. The compatibility approach:

1. **minecraft module** provides shim classes under `org.levimc.launcher.*` packages (`ModManager`, `ExternalModBridge`, `PreloaderInput`, `MinecraftRuntimePreparer`) that declare native methods matching the preloader's JNI symbols
2. **app module**'s corresponding classes (`com.wisebreeze.launcher.*`) do not declare native methods; instead they delegate to the minecraft module's shims
3. **Application startup** registers a `ModManagerDelegate` that bridges Mod object conversion between the two package namespaces

## License

Apache License 2.0 — see [LICENSE](LICENSE)

## Acknowledgements

- [LiteLDev/LeviLaunchroid](https://github.com/LiteLDev/LeviLaunchroid) — Original LeviLaunchroid project
- [LiteLDev/preloader-android](https://github.com/LiteLDev/preloader-android) — Preloader runtime
- [microsoft/libHttpClient](https://github.com/microsoft/libHttpClient) — HTTP client library

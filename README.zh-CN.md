<div align="center">

# Breeze Launcher 兼容框架

**Breeze Launcher Preloader Support — 为 BreezeLauncher 提供 LeviLaunchroid preloader 兼容层**

[![License: Apache 2.0](https://img.shields.io/github/license/wisebreeze/BreezeLauncherPreloaderSupport)](https://github.com/wisebreeze/BreezeLauncherPreloaderSupport/blob/main/LICENSE)
[![Android](https://img.shields.io/badge/Android-9.0%2B-green?style=flat-square&logo=android)](https://www.android.com/)
[![NDK](https://img.shields.io/badge/NDK-r29-orange?style=flat-square)](https://developer.android.com/ndk)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.0-purple?style=flat-square&logo=kotlin)](https://kotlinlang.org/)

</div>

---

## 简介

Breeze Launcher 兼容框架是 [BreezeLauncher](https://github.com/wisebreeze/BreezeLauncher) 的配套项目，为其提供完整的 LeviLaunchroid preloader 兼容环境。它让为 LeviLaunchroid 开发的原生 C++ 模组（如 LeviMap、BreezeMap 等）能够无需修改直接在 BreezeLauncher 上运行。

本框架基于 LeviLaunchroid 的 preloader-android 子系统，保留了模组加载、游戏 Hook、Mod Menu 注册、输入事件转发等全部核心接口，同时适配了 BreezeLauncher 的 `com.wisebreeze.launcher` 包名结构。

### 核心能力

- **Preloader 兼容层** — 完整移植 `libpreloader.so` 及其 JNI 绑定，支持 `PL_REGISTER_MOD` 生命周期、`pl::memory::hook`、`pl::input` 输入回调、`pl::modmenu` Mod Menu 注册等全部 API
- **签名规则系统** — 内置 `PreloaderSignatureRulesManager`，通过模式扫描定位 `libminecraftpe.so` 中的暂停菜单 / HUD 界面函数，使游戏内 Mod Menu 按钮能正确感知菜单状态
- **输入事件转发** — 触摸、按键、鼠标、文本输入全部转发给 preloader，原生模组可注册回调接收
- **模组加载链** — `ModNativeLoader` → `System.load` → `initializeLoadedMod` → `enableLoadedMods`，完整支持 `.levipack` 包格式
- **Firebase 集成** — Crashlytics 崩溃上报与 FCM 推送通知

### 技术栈

| 组件 | 版本 |
|------|------|
| Android minSdk | 28 (Android 9.0) |
| targetSdk | 35 |
| compileSdk | 36 |
| AGP | 8.13.2 |
| Kotlin | 2.3.0 |
| NDK | 29.0.14206865 |
| CMake | 3.22 |
| Java | 21 |

## 构建

```bash
git clone --recursive https://github.com/wisebreeze/BreezeLauncherPreloaderSupport.git
cd BreezeLauncherPreloaderSupport
./gradlew assembleDebug
```

CI 构建通过 GitHub Actions 手动触发（workflow_dispatch），APK 产物发布到 `artifact` release tag。

## 架构

```
BreezeLauncherPreloaderSupport/
├── app/                               # 主应用模块
│   ├── src/main/cpp/                  # Native 代码
│   │   ├── preloader/                 # preloader-android 子模块
│   │   └── libHttpClient/             # Microsoft libHttpClient 子模块
│   └── src/main/java/
│       └── org/levimc/launcher/
│           ├── core/minecraft/        # MinecraftActivity、运行时准备器
│           ├── core/mods/             # ModManager、ModNativeLoader
│           ├── core/preloader/        # PreloaderSignatureRulesManager
│           └── ui/                    # Activity、对话框、悬浮窗
├── minecraft/                         # minecraft 库模块（org.levimc 兼容 shim 类）
├── minecraft-msftauth/                # 微软认证库模块
└── resources/preloader/               # 签名规则源 JSON
```

### Preloader 兼容设计

BreezeLauncher 使用 `com.wisebreeze.launcher` 包名，而 `libpreloader.so` 的 JNI 符号绑定到 `org.levimc.launcher` 包路径。兼容方案：

1. **minecraft 模块** 提供 `org.levimc.launcher.*` 包下的 shim 类（`ModManager`、`ExternalModBridge`、`PreloaderInput`、`MinecraftRuntimePreparer`），声明与 preloader JNI 符号匹配的 native 方法
2. **app 模块** 的对应类（`com.wisebreeze.launcher.*`）不声明 native 方法，而是委托给 minecraft 模块的 shim
3. **Application 启动时** 注册 `ModManagerDelegate`，桥接两个包名空间的 Mod 对象转换

## 许可证

Apache License 2.0 — 详见 [LICENSE](LICENSE)

## 致谢

- [LiteLDev/LeviLaunchroid](https://github.com/LiteLDev/LeviLaunchroid) — 原始 LeviLaunchroid 项目
- [LiteLDev/preloader-android](https://github.com/LiteLDev/preloader-android) — preloader 运行时
- [microsoft/libHttpClient](https://github.com/microsoft/libHttpClient) — HTTP 客户端库

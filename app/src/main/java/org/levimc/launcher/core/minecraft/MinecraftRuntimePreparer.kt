package org.levimc.launcher.core.minecraft

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.util.Log
import org.levimc.launcher.core.mods.Mod
import org.levimc.launcher.core.mods.ModManager
import org.levimc.launcher.core.mods.ModNativeLoader
import org.levimc.launcher.core.versions.GameVersion
import org.levimc.launcher.preloader.PreloaderInput
import org.levimc.launcher.preloader.PreloaderSignatureRulesManager
import org.levimc.launcher.util.LauncherStorage
import java.io.File
import java.io.FileOutputStream

object MinecraftRuntimePreparer {
    data class PreparedRuntime(
        val version: GameVersion?,
        val gameManager: GamePackageManager,
        val skippedIncompatibleMods: List<String> = emptyList()
    )

    interface ProgressListener {
        fun onProgress(progress: Int, status: String, detail: String? = null)
        fun onLog(message: String)
    }

    @JvmStatic
    @JvmName("nativeSetupRuntime")
    private external fun nativeSetupRuntime(modsPath: String)

    private val noopListener = object : ProgressListener {
        override fun onProgress(progress: Int, status: String, detail: String?) = Unit
        override fun onLog(message: String) = Unit
    }

    // Extras pushed by BreezeLauncher's CompatibilityLauncher when sharing version APKs
    // through its exported FileProvider. Kept in sync with CompatibilityLauncher.kt.
    private const val EXTRA_BASE_APK_URI = "MC_BASE_APK_URI"
    private const val EXTRA_SPLIT_URIS = "MC_SPLIT_URIS"
    private const val EXTRA_RESOURCE_URI = "MC_RESOURCE_URI"

    fun prepare(
        context: Context,
        launchIntent: Intent,
        listener: ProgressListener = noopListener
    ): PreparedRuntime {
        val trace = LaunchTrace.ensure(launchIntent)
        trace.milestone("Runtime preparation started")
        listener.onProgress(4, "Checking selected version")
        val version = resolveGameVersion(launchIntent)
            ?: throw IllegalArgumentException("No Minecraft version specified")
        listener.onLog("Using ${version.directoryName} (${version.versionCode})")
        trace.mark("Minecraft version resolved", "${version.directoryName} ${version.versionCode}")

        // When launched from BreezeLauncher's compatibility mode, the version APK lives in
        // BreezeLauncher's sandbox and is shared via content URIs. Stage those files into our
        // own cache so createFakeApplicationInfo / GamePackageManager can read them locally.
        stageSharedVersionFiles(context.applicationContext, launchIntent, version, listener, trace)

        listener.onProgress(12, "Preparing game files")
        val gameManager = GamePackageManager.getInstance(context.applicationContext, version, trace, null)
        trace.mark("GamePackageManager ready")

        listener.onProgress(26, "Preparing launch")
        prepareMinecraftIntent(context, launchIntent, gameManager, version)
        trace.mark("Launch intent prepared")

        listener.onProgress(34, "Checking mods")
        val modManager = ModManager.getInstance()
        modManager.setCurrentVersion(version)
        trace.mark("ModManager state prepared")

        listener.onProgress(40, "Preparing game loader")
        listener.onLog("Loading game loader")
        try {
            trace.mark("Game loader load started")
            System.loadLibrary("preloader")
            trace.mark("Game loader load finished")
        } catch (error: UnsatisfiedLinkError) {
            trace.mark("Game loader load skipped", error.message ?: error.javaClass.simpleName)
        }
        val signatureRulesFile = PreloaderSignatureRulesManager.getRulesFile(context.applicationContext)
        PreloaderInput.configureSignatureRules(signatureRulesFile, version.versionCode)
        trace.mark("Preloader signature rules configured", signatureRulesFile?.absolutePath ?: "<none>")

        listener.onLog("Loading native libraries")
        loadMinecraftLibraries(gameManager, version, listener, trace)

        listener.onProgress(78, "Loading enabled mods")
        listener.onLog("Loading native mods")

        //nativeSetupRuntime(modManager.currentVersion?.modsDir?.absolutePath.toString())
        val skippedIncompatibleMods = loadNativeMods(context, launchIntent, modManager, listener, trace)

        listener.onProgress(100, "Runtime ready", "Entering Minecraft")
        trace.milestone("Runtime preparation finished")
        return PreparedRuntime(version, gameManager, skippedIncompatibleMods)
    }

    @JvmStatic
    fun resolveGameVersion(intent: Intent): GameVersion? {
        val parcelableVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(MinecraftLauncher.EXTRA_GAME_VERSION, GameVersion::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<GameVersion>(MinecraftLauncher.EXTRA_GAME_VERSION)
        }
        if (parcelableVersion != null) {
            return parcelableVersion
        }

        val versionDir = intent.getStringExtra("MC_PATH")
        val versionCode = intent.getStringExtra("MINECRAFT_VERSION") ?: ""
        val versionDirName = intent.getStringExtra("MINECRAFT_VERSION_DIR") ?: ""
        val isInstalled = intent.getBooleanExtra("IS_INSTALLED", false)
        // 微风启动器传来的选中实例包名（已安装版本时为 com.mojang.minecraftpe / .beta /
        // .preview）。为空时回退到 MC_PACKAGE_NAME 常量，保持旧行为。
        val packageName = intent.getStringExtra("MC_PACKAGE_NAME")
            ?.takeIf { it.isNotEmpty() }
            ?: MinecraftLauncher.MC_PACKAGE_NAME

        return if (!versionDir.isNullOrEmpty()) {
            GameVersion(
                versionDirName,
                versionCode,
                versionCode,
                File(versionDir),
                isInstalled,
                packageName,
                ""
            )
        } else if (versionCode.isNotEmpty()) {
            GameVersion(
                versionDirName,
                versionCode,
                versionCode,
                null,
                isInstalled,
                packageName,
                ""
            )
        } else {
            null
        }
    }

    /**
     * 当由 BreezeLauncher 兼容模式启动时，版本 APK 位于微风启动器沙箱内，无法直接读取。
     * 微风启动器通过导出的 FileProvider 以 content:// URI 共享 base.apk 与分包，并授予读权限。
     * 这里把共享的文件拷到自身 cacheDir/compat_staging/<versionDir>/ 下，并把
     * [version.versionDir] 指向本地拷贝，使后续 createFakeApplicationInfo /
     * GamePackageManager 能用本地路径读取。
     *
     * 文件命名差异：微风启动器存的是 base.apk.bak / *.apk.bak，这里统一重命名为
     * base.apk.levi / *.apk.levi 以匹配 [MinecraftLauncher.createFakeApplicationInfo] 的约定。
     */
    private fun stageSharedVersionFiles(
        context: Context,
        intent: Intent,
        version: GameVersion,
        listener: ProgressListener,
        trace: LaunchTrace
    ) {
        val baseApkUriString = intent.getStringExtra(EXTRA_BASE_APK_URI)
        if (baseApkUriString.isNullOrEmpty()) {
            // Not launched via compatibility mode, or no URI provided. Keep the raw MC_PATH.
            return
        }

        val versionDirName = version.directoryName.ifEmpty { "shared" }
        val stagingDir = File(context.cacheDir, "compat_staging/$versionDirName")
        stagingDir.deleteRecursively()
        stagingDir.mkdirs()

        listener.onLog("Staging shared version files into ${stagingDir.absolutePath}")
        trace.mark("Staging shared version", stagingDir.absolutePath)

        // Base APK -> staging/base.apk.levi
        val baseApkDest = File(stagingDir, "base.apk.levi")
        copyUriToFile(context, baseApkUriString, baseApkDest, listener)

        // Splits -> staging/splits/<name>.apk.levi
        val splitUris = intent.getStringArrayListExtra(EXTRA_SPLIT_URIS)
        if (splitUris != null && splitUris.isNotEmpty()) {
            val splitsDir = File(stagingDir, "splits")
            splitsDir.mkdirs()
            splitUris.forEachIndexed { index, uriString ->
                val srcName = try {
                    Uri.parse(uriString).lastPathSegment ?: "split_$index"
                } catch (e: Exception) {
                    "split_$index"
                }
                val destName = srcName.removeSuffix(".apk.bak").removeSuffix(".apk") + ".apk.levi"
                copyUriToFile(context, uriString, File(splitsDir, destName), listener)
            }
        }

        // Redirect the version directory to the local staging copy so the rest of the
        // preparation pipeline (createFakeApplicationInfo, GamePackageManager) reads locally.
        version.versionDir = stagingDir
        trace.mark("Version dir redirected", stagingDir.absolutePath)
    }

    private fun copyUriToFile(
        context: Context,
        uriString: String,
        dest: File,
        listener: ProgressListener
    ) {
        val uri = Uri.parse(uriString)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(dest).use { output ->
                input.copyTo(output)
            }
        } ?: throw java.io.FileNotFoundException("Cannot open shared URI: $uriString")
        listener.onLog("Staged ${dest.name} (${dest.length()} bytes)")
    }

    private fun prepareMinecraftIntent(
        context: Context,
        launchIntent: Intent,
        gameManager: GamePackageManager,
        version: GameVersion
    ) {
        val profileId = MinecraftLauncher.getStorageProfileId(version)
        val versionIsolation = version.versionIsolation
        val filesDir = LauncherStorage.getStorageFilesRoot(context, profileId, versionIsolation, false)
        val externalFilesDir = LauncherStorage.getStorageFilesRoot(context, profileId, versionIsolation, true)
        val dataDir = LauncherStorage.getStorageDataRoot(context, profileId, versionIsolation)
        val cacheDir = LauncherStorage.getStorageCacheRoot(context, profileId, versionIsolation)

        version.versionDir?.let { launchIntent.putExtra("MC_PATH", it.absolutePath) }
        launchIntent.putExtra("IS_INSTALLED", version.isInstalled)
        launchIntent.putExtra("VERSION_ISOLATION", versionIsolation)
        launchIntent.putExtra(MinecraftLauncher.EXTRA_STORAGE_PROFILE_ID, profileId)
        launchIntent.putExtra(MinecraftLauncher.EXTRA_STORAGE_FILES_DIR, filesDir.absolutePath)
        launchIntent.putExtra(MinecraftLauncher.EXTRA_STORAGE_EXTERNAL_FILES_DIR, externalFilesDir.absolutePath)
        launchIntent.putExtra(MinecraftLauncher.EXTRA_STORAGE_DATA_DIR, dataDir.absolutePath)
        launchIntent.putExtra(MinecraftLauncher.EXTRA_STORAGE_CACHE_DIR, cacheDir.absolutePath)

        val mcInfo: ApplicationInfo = if (version.isInstalled) {
            gameManager.getPackageContext().applicationInfo
        } else {
            MinecraftLauncher(context).createFakeApplicationInfo(version, MinecraftLauncher.MC_PACKAGE_NAME)
        }
        launchIntent.putExtra("MC_SRC", mcInfo.sourceDir)
        val splitSourceDirs = mcInfo.splitSourceDirs
        if (splitSourceDirs != null) {
            launchIntent.putExtra("MC_SPLIT_SRC", arrayListOf(*splitSourceDirs))
        }
        launchIntent.putExtra("MINECRAFT_VERSION", version.versionCode)
        launchIntent.putExtra("MINECRAFT_VERSION_DIR", version.directoryName)
        launchIntent.putExtra("LAUNCH_VERTICALLY", version.launchVertically)
        launchIntent.putExtra("VERSION_ISOLATION", version.versionIsolation)

        // 资源导入（.mcpack/.mcworld）：微风启动器兼容模式下点"立即启动"导入资源包时，
        // 把 content/file URI 作为 MC_RESOURCE_URI extra + ClipData 传来（不作为
        // LAUNCH_MINECRAFT intent.data，避免 intent-filter 不匹配）。这里设为
        // gameIntent.data，让 MinecraftActivity（Mojang 代码）启动时导入资源包。
        // 读权限已由微风启动器通过 FLAG_GRANT_READ_URI_PERMISSION + ClipData 授予本进程。
        launchIntent.getStringExtra(EXTRA_RESOURCE_URI)
            ?.takeIf { it.isNotEmpty() }
            ?.let { uriString ->
                launchIntent.data = Uri.parse(uriString)
            }
    }

    private fun loadMinecraftLibraries(
        gameManager: GamePackageManager,
        version: GameVersion,
        listener: ProgressListener,
        trace: LaunchTrace
    ) {
        listener.onProgress(46, "Loading native libraries")
        trace.mark("Minecraft library loading started")

        if (shouldLoadHttpClient(version)) {
            loadLibrary(gameManager, "c++_shared", 48, true, listener, trace)
            loadLibrary(gameManager, "HttpClient.Android", 52, true, listener, trace)
        }

        if (shouldLoadMaesdk(version)) {
            val excludeLibs = HashSet<String>()
            val excludeReasons = HashMap<String, String>()
            if (shouldLoadHttpClient(version)) {
                excludeLibs.add("c++_shared")
                excludeLibs.add("HttpClient.Android")
                excludeReasons["c++_shared"] = "already loaded before the bundle"
                excludeReasons["HttpClient.Android"] = "already loaded before the bundle"
            }
            if (!shouldLoadPlayFab(version)) {
                excludeLibs.add("PlayFabMultiplayer")
                excludeReasons["PlayFabMultiplayer"] = "not required by this Minecraft version"
            }
            listener.onProgress(56, "Loading native libraries")
            trace.mark("Minecraft native library bundle loading started", "1.21.110+ layout")
            val failedLibraries = gameManager
                .loadAllLibraries(excludeLibs, trace, listener, 56, 74, excludeReasons)
                .filterNot { it.loaded }
            if (failedLibraries.isNotEmpty()) {
                val details = failedLibraries.joinToString(separator = "\n") { result ->
                    "${result.fileName}: ${result.detail ?: "unknown error"}"
                }
                trace.error("Native library bundle load failed", details)
                throw RuntimeException("Failed to load native libraries:\n$details")
            }
            trace.mark("Minecraft native library bundle loading finished")
        } else {
            if (!shouldLoadHttpClient(version)) {
                loadLibrary(gameManager, "c++_shared", 50, true, listener, trace)
            }
            loadLibrary(gameManager, "fmod", 56, true, listener, trace)
            loadLibrary(gameManager, "MediaDecoders_Android", 62, true, listener, trace)
            loadLibrary(gameManager, "minecraftpe", 70, true, listener, trace)
            loadLibrary(gameManager, "gxcore", 74, true, listener, trace)
        }
        trace.mark("Minecraft library loading finished")
    }

    private fun loadLibrary(
        gameManager: GamePackageManager,
        name: String,
        progress: Int,
        required: Boolean,
        listener: ProgressListener,
        trace: LaunchTrace
    ) {
        val fileName = toLibraryFileName(name)
        listener.onProgress(progress, "Loading native libraries", fileName)
        listener.onLog("Loading native library: $fileName")
        trace.mark("Native library load started", fileName)
        val result = gameManager.loadLibraryDetailed(name)
        if (!result.loaded && required) {
            listener.onLog("Failed to load native library: ${result.fileName}")
            trace.error(
                "Required library load failed",
                "${result.fileName} in ${result.durationMs}ms from ${result.source}" +
                    (result.detail?.let { " - $it" } ?: "")
            )
            throw RuntimeException("Failed to load ${result.fileName}: ${result.detail ?: "unknown error"}")
        }
        if (result.loaded) {
            listener.onLog("Loaded native library: ${result.fileName}")
            trace.mark(
                "Native library load finished",
                "${result.fileName} in ${result.durationMs}ms from ${result.source}" +
                    (result.detail?.let { " - $it" } ?: "")
            )
        } else {
            listener.onLog("Skipped native library: ${result.fileName}")
            trace.mark(
                "Native library load skipped",
                "${result.fileName} in ${result.durationMs}ms from ${result.source}" +
                    (result.detail?.let { " - $it" } ?: "")
            )
        }
    }

    private fun loadNativeMods(
        context: Context,
        launchIntent: Intent,
        modManager: ModManager,
        listener: ProgressListener,
        trace: LaunchTrace
    ): List<String> {
        val cacheDir = resolveNativeModCacheDir(context, launchIntent)
        trace.mark(
            "Native mod loading started",
            "mods=${modManager.currentVersion?.modsDir?.absolutePath ?: "<unknown>"}"
        )
        val modLoadLabels = java.util.IdentityHashMap<Mod, String>()
        val skippedIncompatibleMods = mutableListOf<String>()
        ModNativeLoader.loadEnabledSoMods(
            modManager,
            cacheDir,
            object : ModNativeLoader.LoadListener {
                override fun onScanStarted(totalEnabled: Int) {
                    if (totalEnabled > 0) {
                        listener.onLog("Loading $totalEnabled enabled mod(s)")
                    } else {
                        listener.onLog("No enabled native mods")
                    }
                }

                override fun onModLoadStarted(mod: Mod, index: Int, total: Int) {
                    val progress = 80 + ((index - 1) * 15 / total.coerceAtLeast(1))
                    val label = "$index/$total"
                    modLoadLabels[mod] = label
                    listener.onProgress(progress, "Loading native mods", "$label ${mod.displayName}")
                    trace.mark("Native mod load started", "$label ${mod.displayName}")
                }

                override fun onModLoadFinished(mod: Mod) {
                    val label = modLoadLabels.remove(mod)?.let { "$it " }.orEmpty()
                    listener.onLog("Loaded mod: $label${mod.displayName}")
                    trace.mark("Native mod load finished", mod.displayName)
                }

                override fun onModLoadSkipped(mod: Mod, minecraftVersion: String) {
                    val label = modLoadLabels.remove(mod)?.let { "$it " }.orEmpty()
                    skippedIncompatibleMods.add(mod.displayName)
                    listener.onLog("Skipped incompatible mod ${label}${mod.displayName} for Minecraft $minecraftVersion")
                    trace.warning("Native mod skipped as incompatible", "${mod.displayName}: $minecraftVersion")
                }

                override fun onModLoadFailed(mod: Mod, error: Throwable) {
                    trace.warning("Native mod load failed", "${mod.displayName}: ${error.message ?: error.javaClass.simpleName}")
                    listener.onLog("Failed to load mod ${mod.displayName}: ${error.message ?: error.javaClass.simpleName}")
                }

                override fun onMessage(message: String) {
                    listener.onLog(message)
                    trace.warning("Native mod loader message", message)
                }
            }
        )
        listener.onProgress(96, "Native mods ready")
        listener.onLog("Native mods ready")
        trace.mark("Native mod loading finished")
        return skippedIncompatibleMods
    }

    private fun resolveNativeModCacheDir(context: Context, launchIntent: Intent): File {
        val versionDirName = launchIntent.getStringExtra("MINECRAFT_VERSION_DIR")
            ?.takeIf { it.isNotBlank() }
            ?.replace(Regex("[^A-Za-z0-9._-]"), "_")
            ?: "default"
        return File(context.cacheDir, "native_mods/$versionDirName").also { it.mkdirs() }
    }

    private fun shouldLoadMaesdk(version: GameVersion): Boolean {
        val versionCode = version.versionCode
        val targetVersion = if (versionCode.contains("beta")) "1.21.110.22" else "1.21.110"
        return isVersionAtLeast(versionCode, targetVersion)
    }

    private fun shouldLoadHttpClient(version: GameVersion): Boolean {
        val versionCode = version.versionCode
        val targetVersion = if (versionCode.contains("beta")) "1.21.130.20" else "1.21.130"
        return isVersionAtLeast(versionCode, targetVersion)
    }

    private fun shouldLoadPlayFab(version: GameVersion): Boolean {
        val versionCode = version.versionCode
        val targetVersion = if (versionCode.contains("beta")) "1.21.130.20" else "1.21.130"
        return isVersionAtLeast(versionCode, targetVersion)
    }

    private fun toLibraryFileName(name: String): String {
        return if (name.startsWith("lib") && name.endsWith(".so")) name else "lib${name.removePrefix("lib").removeSuffix(".so")}.so"
    }

    private fun isVersionAtLeast(currentVersion: String, targetVersion: String): Boolean {
        return try {
            val current = currentVersion.replace(Regex("[^0-9.]"), "").split(".")
            val target = targetVersion.split(".")
            val maxLength = maxOf(current.size, target.size)

            for (i in 0 until maxLength) {
                val currentPart = current.getOrNull(i)?.toIntOrNull() ?: 0
                val targetPart = target.getOrNull(i)?.toIntOrNull() ?: 0

                if (currentPart > targetPart) return true
                if (currentPart < targetPart) return false
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}

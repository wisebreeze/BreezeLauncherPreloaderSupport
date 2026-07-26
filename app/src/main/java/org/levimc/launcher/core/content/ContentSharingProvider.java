package org.levimc.launcher.core.content;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.levimc.launcher.util.LauncherStorage;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Locale;

/**
 * 把兼容框架的游戏数据目录（worlds / resource_packs / behavior_packs 等）
 * 通过 ContentProvider 暴露给微风启动器，让微风启动器在兼容模式下能查询
 * 兼容框架的内容数量、列表，并读取/导出单个文件。
 *
 * URI:
 * - content://org.levimc.launcher.content/worlds
 * - content://org.levimc.launcher.content/resource_packs
 * - content://org.levimc.launcher.content/behavior_packs
 * - content://org.levimc.launcher.content/skin_packs
 * - content://org.levimc.launcher.content/screenshots
 * - content://org.levimc.launcher.content/file/relative/path → openFile
 *
 * 列：_id, _display_name, _size, last_modified, relative_path
 */
public class ContentSharingProvider extends ContentProvider {

    private static final String AUTHORITY = "org.levimc.launcher.content";

    private static final String[] COLUMNS = {
            "_id",
            OpenableColumns.DISPLAY_NAME,
            OpenableColumns.SIZE,
            "last_modified",
            "relative_path"
    };

    @Override
    public boolean onCreate() {
        return true;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection,
                        @Nullable String selection, @Nullable String[] selectionArgs,
                        @Nullable String sortOrder) {
        Context context = getContext();
        if (context == null) return null;
        File dir = resolveContentDir(context, uri);
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return new MatrixCursor(COLUMNS, 0);
        }
        MatrixCursor cursor = new MatrixCursor(COLUMNS);
        File[] files = dir.listFiles();
        if (files == null) return cursor;
        int id = 0;
        for (File f : files) {
            if (!f.exists()) continue;
            String rel = relativize(dir, f);
            cursor.newRow()
                    .add(id++)
                    .add(f.getName())
                    .add(f.isDirectory() ? 0L : f.length())
                    .add(f.lastModified())
                    .add(rel);
        }
        return cursor;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return "vnd.android.cursor.dir/vnd.org.levimc.launcher.content";
    }

    @Nullable
    @Override
    public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode)
            throws FileNotFoundException {
        Context context = getContext();
        if (context == null) throw new FileNotFoundException("no context");
        // 期望 path 段是相对路径（相对 game data dir）
        String rel = uri.getPath();
        if (rel == null || rel.isEmpty()) throw new FileNotFoundException("no path");
        if (rel.startsWith("/")) rel = rel.substring(1);
        // 优先外部存储，回退内部
        File externalBase = LauncherStorage.getSharedGameDataDir(context, true);
        File target = new File(externalBase, rel);
        if (!target.exists()) {
            File internalBase = LauncherStorage.getSharedGameDataDir(context, false);
            target = new File(internalBase, rel);
        }
        if (!target.exists()) throw new FileNotFoundException(target.getAbsolutePath());
        return ParcelFileDescriptor.open(target, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Nullable
    private File resolveContentDir(Context context, Uri uri) {
        String first = uri.getPathSegments().isEmpty() ? "" : uri.getPathSegments().get(0);
        String dirName;
        switch (first) {
            case "worlds": dirName = "minecraftWorlds"; break;
            case "resource_packs": dirName = "resource_packs"; break;
            case "behavior_packs": dirName = "behavior_packs"; break;
            case "skin_packs": dirName = "skin_packs"; break;
            case "screenshots": dirName = "Screenshots"; break;
            default: return null;
        }
        // Minecraft 把游戏数据存在外部存储（Android/data/.../files/games/com.mojang/），
        // 优先用外部；外部不存在时回退内部。
        File externalBase = LauncherStorage.getSharedGameDataDir(context, true);
        File externalDir = new File(externalBase, dirName);
        if (externalDir.exists() && externalDir.isDirectory()) {
            return externalDir;
        }
        File internalBase = LauncherStorage.getSharedGameDataDir(context, false);
        return new File(internalBase, dirName);
    }

    private String relativize(File base, File f) {
        String b = base.getAbsolutePath();
        String p = f.getAbsolutePath();
        if (p.startsWith(b)) {
            String rel = p.substring(b.length());
            if (rel.startsWith(File.separator)) rel = rel.substring(1);
            return rel;
        }
        return f.getName();
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection,
                      @Nullable String[] selectionArgs) {
        // 允许微风启动器删除单个文件（按 relative_path）
        Context context = getContext();
        if (context == null) return 0;
        String rel = uri.getPath();
        if (rel == null) return 0;
        if (rel.startsWith("/")) rel = rel.substring(1);
        File base = LauncherStorage.getSharedGameDataDir(context, false);
        File target = new File(base, rel);
        if (target.exists()) {
            return deleteRecursively(target) ? 1 : 0;
        }
        return 0;
    }

    private boolean deleteRecursively(File f) {
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) {
                for (File c : children) {
                    if (!deleteRecursively(c)) return false;
                }
            }
        }
        return f.delete();
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values,
                      @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }
}

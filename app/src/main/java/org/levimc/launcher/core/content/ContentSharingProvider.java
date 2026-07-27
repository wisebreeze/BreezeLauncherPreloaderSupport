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

import java.io.File;
import java.io.FileNotFoundException;

/**
 * 把兼容框架的游戏数据目录通过 ContentProvider 暴露给微风启动器。
 *
 * 关键：用 context.getExternalFilesDir(null) 作为基路径（即 Android/data/org.levimc.launcher/files），
 * 因为 Mojang 的 setStorageDirectory 把游戏数据存在这里（games/com.mojang/ 下），
 * 而不是 LauncherStorage.getSharedGameDataDir（会加 minecraft/_shared/ 前缀，路径不匹配）。
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
        String rel = uri.getPath();
        if (rel == null || rel.isEmpty()) throw new FileNotFoundException("no path");
        if (rel.startsWith("/")) rel = rel.substring(1);
        File base = getGameDataDir(context);
        File target = new File(base, rel);
        if (!target.exists()) throw new FileNotFoundException(target.getAbsolutePath());
        return ParcelFileDescriptor.open(target, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    /**
     * 游戏数据根目录：getExternalFilesDir(null) + games/com.mojang
     * = Android/data/org.levimc.launcher/files/games/com.mojang
     */
    private File getGameDataDir(Context context) {
        File external = context.getExternalFilesDir(null);
        if (external == null) external = context.getFilesDir();
        return new File(external, "games/com.mojang");
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
        return new File(getGameDataDir(context), dirName);
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
        Context context = getContext();
        if (context == null) return 0;
        String rel = uri.getPath();
        if (rel == null) return 0;
        if (rel.startsWith("/")) rel = rel.substring(1);
        File base = getGameDataDir(context);
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

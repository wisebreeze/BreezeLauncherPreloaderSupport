package org.levimc.launcher.core.content;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * 绑定服务，通过 AIDL 把兼容框架的游戏数据目录暴露给微风启动器。
 *
 * 服务跑在框架进程里，能直接访问自己的 Android/data/.../files/games/com.mojang/。
 * 微风启动器 bindService 时框架进程自动拉起（BIND_AUTO_CREATE），不需要框架在后台运行。
 */
public class ContentService extends Service {

    private static final String TAG = "ContentService";

    private final IContentService.Stub binder = new IContentService.Stub() {
        @Override
        public int getContentCount(String dirName) {
            File dir = resolveDir(dirName);
            if (dir == null || !dir.exists() || !dir.isDirectory()) return 0;
            File[] files = dir.listFiles();
            return files != null ? files.length : 0;
        }

        @Override
        public String[] listContentNames(String dirName) {
            File dir = resolveDir(dirName);
            if (dir == null || !dir.exists() || !dir.isDirectory()) return new String[0];
            File[] files = dir.listFiles();
            if (files == null) return new String[0];
            String[] names = new String[files.length];
            for (int i = 0; i < files.length; i++) {
                names[i] = files[i].getName();
            }
            return names;
        }

        @Override
        public long[] listContentSizes(String dirName) {
            File dir = resolveDir(dirName);
            if (dir == null || !dir.exists() || !dir.isDirectory()) return new long[0];
            File[] files = dir.listFiles();
            if (files == null) return new long[0];
            long[] sizes = new long[files.length];
            for (int i = 0; i < files.length; i++) {
                sizes[i] = files[i].isDirectory() ? 0 : files[i].length();
            }
            return sizes;
        }

        @Override
        public long[] listContentLastModified(String dirName) {
            File dir = resolveDir(dirName);
            if (dir == null || !dir.exists() || !dir.isDirectory()) return new long[0];
            File[] files = dir.listFiles();
            if (files == null) return new long[0];
            long[] times = new long[files.length];
            for (int i = 0; i < files.length; i++) {
                times[i] = files[i].lastModified();
            }
            return times;
        }

        @Override
        public ParcelFileDescriptor openFile(String relativePath) {
            File target = resolveFile(relativePath);
            if (target == null || !target.exists()) return null;
            try {
                return ParcelFileDescriptor.open(target, ParcelFileDescriptor.MODE_READ_ONLY);
            } catch (Exception e) {
                Log.w(TAG, "openFile failed: " + relativePath, e);
                return null;
            }
        }

        @Override
        public boolean deleteFile(String relativePath) {
            File target = resolveFile(relativePath);
            if (target == null || !target.exists()) return false;
            return deleteRecursively(target);
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
    };

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    /**
     * 解析内容目录：getExternalFilesDir(null)/games/com.mojang/<dirName>
     * 这是 Mojang 的 setStorageDirectory 实际使用的路径。
     */
    private File resolveDir(String dirName) {
        File base = getExternalFilesDir(null);
        if (base == null) {
            Log.w(TAG, "getExternalFilesDir(null) returned null");
            return null;
        }
        return new File(base, "games/com.mojang/" + dirName);
    }

    /**
     * 解析单个文件：getExternalFilesDir(null)/games/com.mojang/<relativePath>
     */
    private File resolveFile(String relativePath) {
        File base = getExternalFilesDir(null);
        if (base == null) return null;
        return new File(base, "games/com.mojang/" + relativePath);
    }
}

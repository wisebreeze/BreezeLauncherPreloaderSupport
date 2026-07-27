package org.levimc.launcher.core.content;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.documentfile.provider.DocumentFile;

import java.io.FileNotFoundException;

/**
 * 绑定服务，通过 AIDL 把兼容框架的游戏数据目录暴露给微风启动器。
 *
 * 使用 SAF（Storage Access Framework）：框架进程持有 games/com.mojang/ 目录的
 * 持久化 URI 权限，用 DocumentFile 读文件。微风启动器通过 AIDL 调用本服务，
 * 服务在框架进程里执行（能访问 SAF 授权的目录），不需要框架在后台运行。
 *
 * 授权流程：微风启动器调 requestSafAccess() → 本服务启动一个透明 Activity
 * 触发 ACTION_OPEN_DOCUMENT_TREE → 用户选择 games/com.mojang/ → 框架进程
 * takePersistableUriPermission → 之后 getContentCount/listContentNames 等用
 * DocumentFile 读取。
 */
public class ContentService extends Service {

    private static final String TAG = "ContentService";
    private static final String PREFS_NAME = "compat_content";
    private static final String KEY_SAF_TREE_URI = "saf_tree_uri";

    private final IContentService.Stub binder = new IContentService.Stub() {

        @Override
        public int getContentCount(String dirName) {
            DocumentFile dir = resolveSubDir(dirName);
            if (dir == null || !dir.exists() || !dir.isDirectory()) return 0;
            return dir.listFiles().length;
        }

        @Override
        public String[] listContentNames(String dirName) {
            DocumentFile dir = resolveSubDir(dirName);
            if (dir == null || !dir.exists() || !dir.isDirectory()) return new String[0];
            DocumentFile[] files = dir.listFiles();
            String[] names = new String[files.length];
            for (int i = 0; i < files.length; i++) {
                names[i] = files[i].getName() != null ? files[i].getName() : "unknown";
            }
            return names;
        }

        @Override
        public long[] listContentSizes(String dirName) {
            DocumentFile dir = resolveSubDir(dirName);
            if (dir == null || !dir.exists() || !dir.isDirectory()) return new long[0];
            DocumentFile[] files = dir.listFiles();
            long[] sizes = new long[files.length];
            for (int i = 0; i < files.length; i++) {
                sizes[i] = files[i].isFile() ? files[i].length() : 0L;
            }
            return sizes;
        }

        @Override
        public long[] listContentLastModified(String dirName) {
            DocumentFile dir = resolveSubDir(dirName);
            if (dir == null || !dir.exists() || !dir.isDirectory()) return new long[0];
            DocumentFile[] files = dir.listFiles();
            long[] times = new long[files.length];
            for (int i = 0; i < files.length; i++) {
                times[i] = files[i].lastModified();
            }
            return times;
        }

        @Override
        public ParcelFileDescriptor openFile(String relativePath) {
            DocumentFile file = resolveFile(relativePath);
            if (file == null || !file.isFile()) return null;
            try {
                return getContentResolver().openFileDescriptor(file.getUri(), "r");
            } catch (FileNotFoundException e) {
                Log.w(TAG, "openFile failed: " + relativePath, e);
                return null;
            }
        }

        @Override
        public boolean deleteFile(String relativePath) {
            DocumentFile file = resolveFile(relativePath);
            if (file == null || !file.exists()) return false;
            return file.delete();
        }

        @Override
        public boolean hasSafAccess() {
            return getSafTreeUri() != null;
        }

        @Override
        public void requestSafAccess() {
            // 启动透明 Activity 触发 ACTION_OPEN_DOCUMENT_TREE
            Intent intent = new Intent(ContentService.this, SafRequestActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }

        private DocumentFile resolveSubDir(String dirName) {
            Uri treeUri = getSafTreeUri();
            if (treeUri == null) return null;
            DocumentFile treeDoc = DocumentFile.fromTreeUri(ContentService.this, treeUri);
            if (treeDoc == null) return null;
            return treeDoc.findFile(dirName);
        }

        private DocumentFile resolveFile(String relativePath) {
            Uri treeUri = getSafTreeUri();
            if (treeUri == null) return null;
            DocumentFile treeDoc = DocumentFile.fromTreeUri(ContentService.this, treeUri);
            if (treeDoc == null) return null;
            // relativePath 可能含子目录，逐级查找
            String[] parts = relativePath.split("/");
            DocumentFile current = treeDoc;
            for (String part : parts) {
                if (part.isEmpty()) continue;
                if (current == null) return null;
                current = current.findFile(part);
            }
            return current;
        }

        private Uri getSafTreeUri() {
            String str = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getString(KEY_SAF_TREE_URI, null);
            if (str == null) return null;
            return Uri.parse(str);
        }
    };

    /** 保存 SAF 树 URI（由 SafRequestActivity 调用）。 */
    public static void saveSafTreeUri(Context context, Uri uri) {
        try {
            context.getContentResolver().takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );
        } catch (Exception e) {
            Log.w(TAG, "takePersistableUriPermission failed", e);
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_SAF_TREE_URI, uri.toString())
                .apply();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
}

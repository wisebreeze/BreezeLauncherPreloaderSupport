package org.levimc.launcher.core.content;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

/**
 * 透明 Activity，用于在框架进程里触发 ACTION_OPEN_DOCUMENT_TREE。
 * 用户选择 games/com.mojang/ 目录后，框架进程 takePersistableUriPermission
 * 持久化权限，之后 ContentService 用 DocumentFile 读文件。
 *
 * 继承 Activity（非 AppCompatActivity），因为用的是系统透明主题，
 * AppCompat 主题会要求 Theme.AppCompat 后代。
 */
public class SafRequestActivity extends Activity {

    private static final String TAG = "SafRequestActivity";

    private final ActivityResultLauncher<Uri> openTreeLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(), uri -> {
                if (uri != null) {
                    ContentService.saveSafTreeUri(this, uri);
                    Log.i(TAG, "SAF tree URI granted: " + uri);
                } else {
                    Log.w(TAG, "SAF tree URI not granted");
                }
                finish();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 直接启动 SAF 选择器
        openTreeLauncher.launch(null);
    }
}

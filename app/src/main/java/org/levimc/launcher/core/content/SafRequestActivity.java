package org.levimc.launcher.core.content;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

/**
 * 透明 Activity，用于在框架进程里触发 ACTION_OPEN_DOCUMENT_TREE。
 * 用户选择 games/com.mojang/ 目录后，框架进程 takePersistableUriPermission
 * 持久化权限，之后 ContentService 用 DocumentFile 读文件。
 *
 * 继承 Activity（非 AppCompatActivity），因为用的是系统透明主题。
 * 用传统 startActivityForResult（registerForActivityResult 需要 ComponentActivity）。
 */
public class SafRequestActivity extends Activity {

    private static final String TAG = "SafRequestActivity";
    private static final int REQUEST_CODE_SAF = 0x7a66;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_CODE_SAF);
        } catch (Exception e) {
            Log.w(TAG, "Cannot launch SAF picker", e);
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_SAF && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                ContentService.saveSafTreeUri(this, uri);
                Log.i(TAG, "SAF tree URI granted: " + uri);
            }
        } else {
            Log.w(TAG, "SAF tree URI not granted");
        }
        finish();
    }
}

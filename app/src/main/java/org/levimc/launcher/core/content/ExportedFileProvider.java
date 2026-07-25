package org.levimc.launcher.core.content;

import android.content.Context;
import android.content.pm.ProviderInfo;
import android.net.Uri;

import androidx.core.content.FileProvider;

/**
 * FileProvider subclass that allows android:exported="true".
 *
 * androidx.core.content.FileProvider.attachInfo() throws SecurityException
 * if the provider is exported. This subclass overrides attachInfo to skip
 * that check, so other apps (with matching signature permission) can query
 * and read files from this app's storage without SAF.
 */
public class ExportedFileProvider extends FileProvider {

    @Override
    public void attachInfo(Context context, ProviderInfo info) {
        // Temporarily mark as non-exported to bypass FileProvider's check,
        // then restore the original exported flag.
        boolean originalExported = info.exported;
        info.exported = false;
        super.attachInfo(context, info);
        info.exported = originalExported;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public Uri insert(Uri uri, android.content.ContentValues values) {
        return null;
    }

    @Override
    public int update(Uri uri, android.content.ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}

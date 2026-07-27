// AIDL interface for cross-process content access between BreezeLauncher
// and BreezeLauncherPreloaderSupport via SAF.
package org.levimc.launcher.core.content;

interface IContentService {
    // Returns the number of files in the given content directory
    // (e.g. "minecraftWorlds", "resource_packs", "behavior_packs").
    int getContentCount(String dirName);

    // Returns the names of files in the given content directory.
    String[] listContentNames(String dirName);

    // Returns the sizes (bytes) of files in the given content directory.
    long[] listContentSizes(String dirName);

    // Returns the last-modified timestamps (epoch ms) of files.
    long[] listContentLastModified(String dirName);

    // Opens a file by relative path for reading.
    ParcelFileDescriptor openFile(String relativePath);

    // Deletes a file/dir by relative path.
    boolean deleteFile(String relativePath);

    // Whether the framework has been granted SAF access to games/com.mojang/.
    boolean hasSafAccess();

    // Trigger SAF folder selection (launches a transparent Activity in the
    // framework process so the user can pick games/com.mojang/).
    void requestSafAccess();
}

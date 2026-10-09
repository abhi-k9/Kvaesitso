package de.mm20.launcher2.files.providers

import android.content.Context
import android.provider.MediaStore
import androidx.core.database.getStringOrNull
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.search.File
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class LocalFileProvider(
    private val context: Context,
    private val permissionsManager: PermissionsManager,
    private val skipNoMediaFolders: Boolean = false,
): FileProvider {
    override suspend fun search(query: String, allowNetwork: Boolean): List<File> = withContext(Dispatchers.IO) {
        if (!permissionsManager.checkPermissionOnce(PermissionGroup.ExternalStorage)) {
            return@withContext emptyList()
        }
        if (query.length < 2 || query.isBlank()) return@withContext emptyList()
        val results = mutableListOf<LocalFile>()
        val uri = MediaStore.Files.getContentUri("external").buildUpon()
            // More than are shown, some may be left out below
            .appendQueryParameter("limit", "30").build()
        val projection = arrayOf(
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.MIME_TYPE
        )
        // The title is the name without the extension (or the title tag of songs), so the file
        // name is searched too, to find files by their full name or extension, e.g. ".pdf"
        val selection =
            "(${MediaStore.Files.FileColumns.TITLE} LIKE ? OR ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?)"
        val pattern = if (query.length > 3 || query.startsWith('.')) "%$query%" else "$query%"
        val selArgs = arrayOf(pattern, pattern)
        val sort = "${MediaStore.Files.FileColumns.DISPLAY_NAME} COLLATE NOCASE ASC"


        val cursor = try {
            context.contentResolver.query(uri, projection, selection, selArgs, sort)
        } catch (e: IllegalArgumentException) {
            CrashReporter.logException(e)
            null
        } ?: return@withContext results
        val noMediaFolders = mutableMapOf<String, Boolean>()
        while (cursor.moveToNext()) {
            if (results.size >= 10) {
                break
            }
            val path = cursor.getStringOrNull(3) ?: continue
            if (!java.io.File(path).exists()) continue
            val directory = java.io.File(path).isDirectory
            if (skipNoMediaFolders) {
                val folder = if (directory) java.io.File(path) else java.io.File(path).parentFile
                if (folder != null && hasNoMedia(folder, noMediaFolders)) continue
            }
            val mimeType = (cursor.getStringOrNull(4).takeIf { it != "application/octet-stream" }
                ?: if (directory) "resource/folder" else LocalFile.getMimetypeByFileExtension(
                    path.substringAfterLast(
                        '.'
                    )
                ))
            val file = LocalFile(
                path = path,
                mimeType = mimeType,
                size = cursor.getLong(2),
                isDirectory = directory,
                id = cursor.getLong(1),
                metaData = persistentMapOf()
            )
            results.add(file)
        }
        cursor.close()
        return@withContext results
    }

    /**
     * Whether [folder] or a folder that contains it has a .nomedia file. The results are kept in
     * [cache], by path.
     */
    private fun hasNoMedia(folder: java.io.File, cache: MutableMap<String, Boolean>): Boolean {
        cache[folder.path]?.let { return it }
        val parent = folder.parentFile
        val hasNoMedia = java.io.File(folder, ".nomedia").exists() ||
                parent != null && hasNoMedia(parent, cache)
        cache[folder.path] = hasNoMedia
        return hasNoMedia
    }
}
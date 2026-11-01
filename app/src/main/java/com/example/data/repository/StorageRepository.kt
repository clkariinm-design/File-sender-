package com.example.data.repository

import android.content.ContentUris
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.example.data.model.FileCategory
import com.example.data.model.ShareableItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class StorageOverview(
    val totalBytes: Long,
    val freeBytes: Long,
    val usedBytes: Long,
    val usedPercentage: Float
) {
    val formattedTotal: String
        get() = formatBytes(totalBytes)
    val formattedFree: String
        get() = formatBytes(freeBytes)
    val formattedUsed: String
        get() = formatBytes(usedBytes)

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 GB"
        val gb = bytes / (1024.0 * 1024.0 * 1024.0)
        return String.format("%.1f GB", gb)
    }
}

class StorageRepository(private val context: Context) {

    suspend fun getStorageOverview(): StorageOverview = withContext(Dispatchers.IO) {
        try {
            val path = Environment.getDataDirectory()
            val stat = StatFs(path.path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong

            val total = totalBlocks * blockSize
            val free = availableBlocks * blockSize
            val used = total - free
            val percentage = if (total > 0) (used.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f

            StorageOverview(total, free, used, percentage)
        } catch (e: Exception) {
            StorageOverview(64L * 1024 * 1024 * 1024, 32L * 1024 * 1024 * 1024, 32L * 1024 * 1024 * 1024, 0.5f)
        }
    }

    suspend fun getInstalledApps(): List<ShareableItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<ShareableItem>()
        val pm = context.packageManager
        try {
            val installedPackages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledApplications(0)
            }

            for (appInfo in installedPackages) {
                // Focus on user-facing apps or updated system apps
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val isUpdatedSystem = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                val hasLauncher = pm.getLaunchIntentForPackage(appInfo.packageName) != null

                if ((!isSystem || isUpdatedSystem || hasLauncher) && appInfo.packageName != context.packageName) {
                    val apkFile = File(appInfo.sourceDir)
                    if (apkFile.exists()) {
                        val appName = pm.getApplicationLabel(appInfo).toString()
                        val size = apkFile.length()
                        val uri = Uri.fromFile(apkFile)
                        val packageInfo = try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                pm.getPackageInfo(appInfo.packageName, PackageManager.PackageInfoFlags.of(0))
                            } else {
                                @Suppress("DEPRECATION")
                                pm.getPackageInfo(appInfo.packageName, 0)
                            }
                        } catch (e: Exception) {
                            null
                        }

                        items.add(
                            ShareableItem(
                                id = "app_${appInfo.packageName}",
                                name = "$appName.apk",
                                size = size,
                                uri = uri,
                                mimeType = "application/vnd.android.package-archive",
                                category = FileCategory.APPS,
                                packageName = appInfo.packageName,
                                versionName = packageInfo?.versionName ?: "1.0",
                                dateModified = apkFile.lastModified()
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        items.sortedByDescending { it.dateModified }
    }

    suspend fun getPhotos(): List<ShareableItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<ShareableItem>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"

        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                val widthCol = cursor.getColumnIndex(MediaStore.Images.Media.WIDTH)
                val heightCol = cursor.getColumnIndex(MediaStore.Images.Media.HEIGHT)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Image_$id.jpg"
                    val size = cursor.getLong(sizeCol)
                    val mime = cursor.getString(mimeCol) ?: "image/jpeg"
                    val date = cursor.getLong(dateCol) * 1000
                    val width = if (widthCol != -1) cursor.getInt(widthCol) else 0
                    val height = if (heightCol != -1) cursor.getInt(heightCol) else 0
                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

                    val resolution = if (width > 0 && height > 0) "${width}x${height}" else null

                    items.add(
                        ShareableItem(
                            id = "photo_$id",
                            name = name,
                            size = size,
                            uri = uri,
                            mimeType = mime,
                            category = FileCategory.PHOTOS,
                            dateModified = date,
                            resolution = resolution
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        items
    }

    suspend fun getVideos(): List<ShareableItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<ShareableItem>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.DURATION
        )
        val sortOrder = "${MediaStore.Video.Media.DATE_MODIFIED} DESC"

        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                val durCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Video_$id.mp4"
                    val size = cursor.getLong(sizeCol)
                    val mime = cursor.getString(mimeCol) ?: "video/mp4"
                    val date = cursor.getLong(dateCol) * 1000
                    val duration = if (durCol != -1) cursor.getLong(durCol) else null
                    val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

                    items.add(
                        ShareableItem(
                            id = "video_$id",
                            name = name,
                            size = size,
                            uri = uri,
                            mimeType = mime,
                            category = FileCategory.VIDEOS,
                            dateModified = date,
                            durationMs = duration
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        items
    }

    suspend fun getMusic(): List<ShareableItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<ShareableItem>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ARTIST
        )
        val sortOrder = "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"

        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                val durCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
                val artistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Audio_$id.mp3"
                    val size = cursor.getLong(sizeCol)
                    val mime = cursor.getString(mimeCol) ?: "audio/mpeg"
                    val date = cursor.getLong(dateCol) * 1000
                    val duration = if (durCol != -1) cursor.getLong(durCol) else null
                    val artist = if (artistCol != -1) cursor.getString(artistCol) else null
                    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

                    items.add(
                        ShareableItem(
                            id = "audio_$id",
                            name = name,
                            size = size,
                            uri = uri,
                            mimeType = mime,
                            category = FileCategory.MUSIC,
                            dateModified = date,
                            durationMs = duration,
                            versionName = artist
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        items
    }

    suspend fun getDocuments(): List<ShareableItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<ShareableItem>()
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATE_MODIFIED
        )
        
        val docExtensions = listOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "epub")
        val selection = StringBuilder()
        for (i in docExtensions.indices) {
            if (i > 0) selection.append(" OR ")
            selection.append("${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.${docExtensions[i]}'")
        }

        try {
            context.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                projection,
                selection.toString(),
                null,
                "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val mimeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Document_$id"
                    val size = cursor.getLong(sizeCol)
                    val mime = if (mimeCol != -1) cursor.getString(mimeCol) else getMimeFromExtension(name)
                    val date = cursor.getLong(dateCol) * 1000
                    val uri = ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), id)

                    items.add(
                        ShareableItem(
                            id = "doc_$id",
                            name = name,
                            size = size,
                            uri = uri,
                            mimeType = mime ?: "application/octet-stream",
                            category = FileCategory.DOCUMENTS,
                            dateModified = date
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        items
    }

    suspend fun getArchives(): List<ShareableItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<ShareableItem>()
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATE_MODIFIED
        )

        val archiveExtensions = listOf("zip", "rar", "7z", "tar", "gz", "bz2")
        val selection = StringBuilder()
        for (i in archiveExtensions.indices) {
            if (i > 0) selection.append(" OR ")
            selection.append("${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.${archiveExtensions[i]}'")
        }

        try {
            context.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                projection,
                selection.toString(),
                null,
                "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val mimeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Archive_$id.zip"
                    val size = cursor.getLong(sizeCol)
                    val mime = if (mimeCol != -1) cursor.getString(mimeCol) else "application/zip"
                    val date = cursor.getLong(dateCol) * 1000
                    val uri = ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), id)

                    items.add(
                        ShareableItem(
                            id = "archive_$id",
                            name = name,
                            size = size,
                            uri = uri,
                            mimeType = mime ?: "application/zip",
                            category = FileCategory.ARCHIVES,
                            dateModified = date
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        items
    }

    private fun getMimeFromExtension(name: String): String? {
        val ext = name.substringAfterLast('.', "")
        return if (ext.isNotEmpty()) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase())
        } else null
    }
}

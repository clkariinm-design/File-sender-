package com.example.data.model

import android.net.Uri

enum class FileCategory(val title: String) {
    APPS("Apps"),
    PHOTOS("Photos"),
    VIDEOS("Videos"),
    MUSIC("Music"),
    DOCUMENTS("Documents"),
    ARCHIVES("Archives"),
    LARGE_FILES("Large Files")
}

data class ShareableItem(
    val id: String,
    val name: String,
    val size: Long,
    val uri: Uri,
    val mimeType: String,
    val category: FileCategory,
    val dateModified: Long = System.currentTimeMillis(),
    val packageName: String? = null,
    val versionName: String? = null,
    val durationMs: Long? = null,
    val resolution: String? = null,
    val isSelected: Boolean = false
) {
    val formattedSize: String
        get() {
            if (size <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
            val safeGroup = digitGroups.coerceIn(0, units.size - 1)
            val value = size / Math.pow(1024.0, safeGroup.toDouble())
            return String.format("%.1f %s", value, units[safeGroup])
        }
}

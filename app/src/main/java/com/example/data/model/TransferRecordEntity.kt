package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transfer_history")
data class TransferRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fileName: String,
    val filePath: String,
    val fileSize: Long,
    val mimeType: String,
    val isIncoming: Boolean,
    val peerName: String,
    val peerAddress: String,
    val bytesTransferred: Long,
    val speedBytesPerSec: Long,
    val status: String, // COMPLETED, FAILED, TRANSFERRING, CANCELLED
    val timestamp: Long = System.currentTimeMillis()
) {
    val formattedSize: String
        get() {
            if (fileSize <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB")
            val digitGroups = (Math.log10(fileSize.toDouble()) / Math.log10(1024.0)).toInt()
            val safeGroup = digitGroups.coerceIn(0, units.size - 1)
            val value = fileSize / Math.pow(1024.0, safeGroup.toDouble())
            return String.format("%.1f %s", value, units[safeGroup])
        }

    val formattedSpeed: String
        get() {
            val mbps = speedBytesPerSec / (1024.0 * 1024.0)
            return if (mbps >= 1.0) {
                String.format("%.1f MB/s", mbps)
            } else {
                val kbps = speedBytesPerSec / 1024.0
                String.format("%.0f KB/s", kbps)
            }
        }
}

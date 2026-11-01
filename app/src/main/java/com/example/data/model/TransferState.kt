package com.example.data.model

data class TransferProgressState(
    val isActive: Boolean = false,
    val isIncoming: Boolean = false,
    val currentFileName: String = "",
    val currentFileIndex: Int = 0,
    val totalFiles: Int = 0,
    val currentFileBytesTransferred: Long = 0L,
    val currentFileTotalBytes: Long = 0L,
    val totalBatchBytesTransferred: Long = 0L,
    val totalBatchBytes: Long = 0L,
    val currentSpeedBytesPerSec: Long = 0L,
    val peerName: String = "",
    val statusText: String = "Idle",
    val isCompleted: Boolean = false,
    val isError: Boolean = false,
    val errorMessage: String? = null
) {
    val progressFraction: Float
        get() = if (totalBatchBytes > 0) {
            (totalBatchBytesTransferred.toFloat() / totalBatchBytes.toFloat()).coerceIn(0f, 1f)
        } else if (currentFileTotalBytes > 0) {
            (currentFileBytesTransferred.toFloat() / currentFileTotalBytes.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

    val currentFileProgressFraction: Float
        get() = if (currentFileTotalBytes > 0) {
            (currentFileBytesTransferred.toFloat() / currentFileTotalBytes.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

    val formattedSpeed: String
        get() {
            val mbps = currentSpeedBytesPerSec / (1024.0 * 1024.0)
            return if (mbps >= 1.0) {
                String.format("%.1f MB/s", mbps)
            } else {
                val kbps = currentSpeedBytesPerSec / 1024.0
                String.format("%.0f KB/s", kbps)
            }
        }

    val estimatedTimeRemaining: String
        get() {
            if (currentSpeedBytesPerSec <= 0 || totalBatchBytes <= totalBatchBytesTransferred) {
                return "--"
            }
            val remainingBytes = totalBatchBytes - totalBatchBytesTransferred
            val secondsRemaining = remainingBytes / currentSpeedBytesPerSec
            return if (secondsRemaining < 60) {
                "${secondsRemaining}s"
            } else {
                val mins = secondsRemaining / 60
                val secs = secondsRemaining % 60
                "${mins}m ${secs}s"
            }
        }
}

data class DiscoveredPeer(
    val deviceAddress: String,
    val deviceName: String,
    val status: String = "Available",
    val isGroupOwner: Boolean = false,
    val isWifiDirect: Boolean = true,
    val ipAddress: String? = null,
    val port: Int = 8988
)

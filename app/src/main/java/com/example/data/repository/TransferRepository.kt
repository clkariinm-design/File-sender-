package com.example.data.repository

import com.example.data.db.TransferDao
import com.example.data.model.TransferRecordEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class OverallTransferStats(
    val totalBytesSent: Long = 0L,
    val totalBytesReceived: Long = 0L,
    val peakSpeedBytesPerSec: Long = 0L,
    val completedCount: Int = 0
) {
    val formattedSent: String
        get() = formatBytes(totalBytesSent)
    val formattedReceived: String
        get() = formatBytes(totalBytesReceived)
    val formattedPeakSpeed: String
        get() {
            val mbps = peakSpeedBytesPerSec / (1024.0 * 1024.0)
            return if (mbps >= 1.0) {
                String.format("%.1f MB/s", mbps)
            } else {
                val kbps = peakSpeedBytesPerSec / 1024.0
                String.format("%.0f KB/s", kbps)
            }
        }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 MB"
        val gb = bytes / (1024.0 * 1024.0 * 1024.0)
        return if (gb >= 1.0) {
            String.format("%.2f GB", gb)
        } else {
            val mb = bytes / (1024.0 * 1024.0)
            String.format("%.1f MB", mb)
        }
    }
}

class TransferRepository(private val dao: TransferDao) {

    val allTransfers: Flow<List<TransferRecordEntity>> = dao.getAllTransfers()
    val receivedTransfers: Flow<List<TransferRecordEntity>> = dao.getReceivedTransfers()
    val sentTransfers: Flow<List<TransferRecordEntity>> = dao.getSentTransfers()
    val recentTransfers: Flow<List<TransferRecordEntity>> = dao.getRecentTransfers(5)

    val overallStats: Flow<OverallTransferStats> = combine(
        dao.getTotalBytesSent(),
        dao.getTotalBytesReceived(),
        dao.getPeakSpeed(),
        dao.getCompletedCount()
    ) { sent, received, peak, count ->
        OverallTransferStats(
            totalBytesSent = sent ?: 0L,
            totalBytesReceived = received ?: 0L,
            peakSpeedBytesPerSec = peak ?: 0L,
            completedCount = count
        )
    }

    suspend fun recordTransfer(record: TransferRecordEntity): Long {
        return dao.insertTransfer(record)
    }

    suspend fun updateTransfer(record: TransferRecordEntity) {
        dao.updateTransfer(record)
    }

    suspend fun deleteTransfer(id: Long) {
        dao.deleteById(id)
    }

    suspend fun clearHistory() {
        dao.clearAll()
    }
}

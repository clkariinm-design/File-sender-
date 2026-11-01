package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.TransferRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransferDao {
    @Query("SELECT * FROM transfer_history ORDER BY timestamp DESC")
    fun getAllTransfers(): Flow<List<TransferRecordEntity>>

    @Query("SELECT * FROM transfer_history WHERE isIncoming = 1 ORDER BY timestamp DESC")
    fun getReceivedTransfers(): Flow<List<TransferRecordEntity>>

    @Query("SELECT * FROM transfer_history WHERE isIncoming = 0 ORDER BY timestamp DESC")
    fun getSentTransfers(): Flow<List<TransferRecordEntity>>

    @Query("SELECT * FROM transfer_history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentTransfers(limit: Int): Flow<List<TransferRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransfer(record: TransferRecordEntity): Long

    @Update
    suspend fun updateTransfer(record: TransferRecordEntity)

    @Query("DELETE FROM transfer_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM transfer_history")
    suspend fun clearAll()

    @Query("SELECT SUM(fileSize) FROM transfer_history WHERE isIncoming = 0 AND status = 'COMPLETED'")
    fun getTotalBytesSent(): Flow<Long?>

    @Query("SELECT SUM(fileSize) FROM transfer_history WHERE isIncoming = 1 AND status = 'COMPLETED'")
    fun getTotalBytesReceived(): Flow<Long?>

    @Query("SELECT MAX(speedBytesPerSec) FROM transfer_history WHERE status = 'COMPLETED'")
    fun getPeakSpeed(): Flow<Long?>

    @Query("SELECT COUNT(*) FROM transfer_history WHERE status = 'COMPLETED'")
    fun getCompletedCount(): Flow<Int>
}

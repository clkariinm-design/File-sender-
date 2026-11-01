package com.example.engine

import android.content.Context
import com.example.data.model.ShareableItem
import com.example.data.model.TransferProgressState
import com.example.data.model.TransferRecordEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket

class TcpTransferClient(
    private val context: Context,
    private val onTransferRecorded: suspend (TransferRecordEntity) -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var socket: Socket? = null
    private var isCancelled = false

    private val _progressState = MutableStateFlow(TransferProgressState())
    val progressState: StateFlow<TransferProgressState> = _progressState.asStateFlow()

    fun sendFiles(
        targetHost: String,
        targetPort: Int = TransferProtocol.PORT,
        items: List<ShareableItem>,
        onFinished: (Boolean) -> Unit = {}
    ) {
        if (items.isEmpty()) return
        isCancelled = false

        val totalBatchBytes = items.sumOf { it.size }
        val senderDeviceName = NetworkUtils.getDeviceName(context)

        _progressState.update {
            TransferProgressState(
                isActive = true,
                isIncoming = false,
                totalFiles = items.size,
                totalBatchBytes = totalBatchBytes,
                statusText = "Connecting to $targetHost...",
                peerName = targetHost
            )
        }

        scope.launch {
            var success = false
            try {
                val client = Socket()
                socket = client
                client.connect(InetSocketAddress(targetHost, targetPort), 15000)
                client.soTimeout = 30000

                val dos = DataOutputStream(client.getOutputStream())
                val dis = DataInputStream(client.getInputStream())

                // 1. Send Magic Header
                dos.write(TransferProtocol.MAGIC_HEADER)

                // 2. Send Handshake
                dos.writeByte(TransferProtocol.TYPE_HANDSHAKE.toInt())
                dos.writeUTF(senderDeviceName)
                dos.writeInt(items.size)
                dos.writeLong(totalBatchBytes)
                dos.flush()

                var totalBatchTransferred = 0L

                // 3. Stream each file
                for ((index, item) in items.withIndex()) {
                    if (isCancelled) {
                        dos.writeByte(TransferProtocol.TYPE_CANCEL.toInt())
                        dos.flush()
                        break
                    }

                    _progressState.update {
                        it.copy(
                            currentFileName = item.name,
                            currentFileIndex = index + 1,
                            currentFileTotalBytes = item.size,
                            currentFileBytesTransferred = 0L,
                            statusText = "Sending ${item.name} (${index + 1}/${items.size})"
                        )
                    }

                    // Send File Header
                    dos.writeByte(TransferProtocol.TYPE_FILE_HEADER.toInt())
                    dos.writeInt(index)
                    dos.writeUTF(item.name)
                    dos.writeLong(item.size)
                    dos.writeUTF(item.mimeType)
                    dos.flush()

                    var fileBytesWritten = 0L
                    val startTime = System.currentTimeMillis()
                    var lastSpeedCalcTime = startTime
                    var lastSpeedBytes = 0L
                    var currentSpeed = 0L

                    val inputStream: InputStream? = context.contentResolver.openInputStream(item.uri)
                    if (inputStream != null) {
                        inputStream.use { stream ->
                            val buffer = ByteArray(TransferProtocol.BUFFER_SIZE)
                            var read: Int
                            while (stream.read(buffer).also { read = it } != -1 && !isCancelled) {
                                dos.write(buffer, 0, read)
                                fileBytesWritten += read
                                totalBatchTransferred += read

                                val now = System.currentTimeMillis()
                                val elapsed = now - lastSpeedCalcTime
                                if (elapsed >= 400) {
                                    val bytesDelta = fileBytesWritten - lastSpeedBytes
                                    currentSpeed = (bytesDelta * 1000L) / elapsed
                                    lastSpeedBytes = fileBytesWritten
                                    lastSpeedCalcTime = now

                                    _progressState.update {
                                        it.copy(
                                            currentFileBytesTransferred = fileBytesWritten,
                                            totalBatchBytesTransferred = totalBatchTransferred,
                                            currentSpeedBytesPerSec = currentSpeed
                                        )
                                    }
                                }
                            }
                            dos.flush()
                        }
                    }

                    // Await ACK from receiver
                    val ackType = dis.readByte()
                    val ackIndex = dis.readInt()

                    val duration = Math.max(1, System.currentTimeMillis() - startTime)
                    val avgSpeed = (fileBytesWritten * 1000L) / duration

                    val record = TransferRecordEntity(
                        fileName = item.name,
                        filePath = item.uri.toString(),
                        fileSize = item.size,
                        mimeType = item.mimeType,
                        isIncoming = false,
                        peerName = targetHost,
                        peerAddress = targetHost,
                        bytesTransferred = fileBytesWritten,
                        speedBytesPerSec = avgSpeed,
                        status = if (fileBytesWritten == item.size && ackType == TransferProtocol.TYPE_FILE_ACK) "COMPLETED" else "FAILED"
                    )
                    onTransferRecorded(record)
                }

                // Send Batch Complete
                if (!isCancelled) {
                    dos.writeByte(TransferProtocol.TYPE_BATCH_COMPLETE.toInt())
                    dos.flush()
                    success = true
                    _progressState.update {
                        it.copy(
                            isCompleted = true,
                            statusText = "All ${items.size} files sent successfully!",
                            currentSpeedBytesPerSec = 0L
                        )
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                _progressState.update {
                    it.copy(
                        isError = true,
                        errorMessage = e.localizedMessage ?: "Connection error while sending"
                    )
                }
            } finally {
                try { socket?.close() } catch (ignored: Exception) {}
                withContext(Dispatchers.Main) {
                    onFinished(success)
                }
            }
        }
    }

    fun cancelTransfer() {
        isCancelled = true
        _progressState.update { it.copy(statusText = "Transfer cancelled", isActive = false) }
        try {
            socket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

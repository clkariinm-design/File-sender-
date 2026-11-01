package com.example.engine

import android.content.Context
import android.os.Environment
import com.example.data.model.TransferProgressState
import com.example.data.model.TransferRecordEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException

class TcpTransferServer(
    private val context: Context,
    private val onTransferRecorded: suspend (TransferRecordEntity) -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var serverSocket: ServerSocket? = null
    private var currentClientSocket: Socket? = null
    private var isRunning = false
    private var isCancelled = false

    private val _progressState = MutableStateFlow(TransferProgressState())
    val progressState: StateFlow<TransferProgressState> = _progressState.asStateFlow()

    fun startServer(port: Int = TransferProtocol.PORT) {
        if (isRunning) return
        isRunning = true
        isCancelled = false

        scope.launch {
            try {
                val server = ServerSocket(port)
                server.reuseAddress = true
                serverSocket = server
                _progressState.update {
                    it.copy(
                        isActive = true,
                        isIncoming = true,
                        statusText = "Waiting for sender...",
                        isCompleted = false,
                        isError = false
                    )
                }

                while (isActive && isRunning && !server.isClosed) {
                    try {
                        val client = server.accept()
                        currentClientSocket = client
                        handleClientConnection(client)
                    } catch (e: Exception) {
                        if (!isRunning) break
                        e.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _progressState.update {
                    it.copy(
                        isError = true,
                        errorMessage = "Server error: ${e.localizedMessage ?: "Failed to bind port"}"
                    )
                }
            } finally {
                stopServer()
            }
        }
    }

    private suspend fun handleClientConnection(socket: Socket) {
        var dis: DataInputStream? = null
        var dos: DataOutputStream? = null

        try {
            socket.soTimeout = 30000 // 30 second read timeout
            dis = DataInputStream(socket.getInputStream())
            dos = DataOutputStream(socket.getOutputStream())

            // 1. Verify Magic Header
            val magic = ByteArray(TransferProtocol.MAGIC_HEADER.size)
            dis.readFully(magic)
            if (!magic.contentEquals(TransferProtocol.MAGIC_HEADER)) {
                return
            }

            // 2. Read Handshake
            val packetType = dis.readByte()
            if (packetType != TransferProtocol.TYPE_HANDSHAKE) {
                return
            }

            val peerName = dis.readUTF()
            val totalFiles = dis.readInt()
            val totalBatchBytes = dis.readLong()

            _progressState.update {
                it.copy(
                    peerName = peerName,
                    totalFiles = totalFiles,
                    totalBatchBytes = totalBatchBytes,
                    statusText = "Receiving from $peerName..."
                )
            }

            var totalBatchTransferred = 0L

            // 3. Receive each file
            for (fileIdx in 0 until totalFiles) {
                if (!isRunning || isCancelled) break

                val headerType = dis.readByte()
                if (headerType == TransferProtocol.TYPE_CANCEL) {
                    _progressState.update { it.copy(statusText = "Transfer cancelled by sender", isError = true) }
                    break
                }
                if (headerType != TransferProtocol.TYPE_FILE_HEADER) break

                val fileIndex = dis.readInt()
                val fileName = dis.readUTF()
                val fileSize = dis.readLong()
                val mimeType = dis.readUTF()

                val targetFile = getUniqueDownloadFile(fileName)
                var fileBytesRead = 0L

                val startTime = System.currentTimeMillis()
                var lastSpeedCalcTime = startTime
                var lastSpeedBytes = 0L
                var currentSpeed = 0L

                _progressState.update {
                    it.copy(
                        currentFileName = fileName,
                        currentFileIndex = fileIndex + 1,
                        currentFileTotalBytes = fileSize,
                        currentFileBytesTransferred = 0L,
                        statusText = "Receiving $fileName (${fileIndex + 1}/$totalFiles)"
                    )
                }

                FileOutputStream(targetFile).use { fos ->
                    val buffer = ByteArray(TransferProtocol.BUFFER_SIZE)
                    while (fileBytesRead < fileSize && !isCancelled && isRunning) {
                        val toRead = Math.min(buffer.size.toLong(), fileSize - fileBytesRead).toInt()
                        val bytesRead = dis.read(buffer, 0, toRead)
                        if (bytesRead == -1) break

                        fos.write(buffer, 0, bytesRead)
                        fileBytesRead += bytesRead
                        totalBatchTransferred += bytesRead

                        val now = System.currentTimeMillis()
                        val elapsed = now - lastSpeedCalcTime
                        if (elapsed >= 400) {
                            val bytesDelta = fileBytesRead - lastSpeedBytes
                            currentSpeed = (bytesDelta * 1000L) / elapsed
                            lastSpeedBytes = fileBytesRead
                            lastSpeedCalcTime = now

                            _progressState.update {
                                it.copy(
                                    currentFileBytesTransferred = fileBytesRead,
                                    totalBatchBytesTransferred = totalBatchTransferred,
                                    currentSpeedBytesPerSec = currentSpeed
                                )
                            }
                        }
                    }
                    fos.flush()
                }

                val transferDuration = Math.max(1, System.currentTimeMillis() - startTime)
                val avgSpeed = (fileBytesRead * 1000L) / transferDuration

                // Send ACK back
                dos.writeByte(TransferProtocol.TYPE_FILE_ACK.toInt())
                dos.writeInt(fileIndex)
                dos.flush()

                // Record transfer in database
                val record = TransferRecordEntity(
                    fileName = fileName,
                    filePath = targetFile.absolutePath,
                    fileSize = fileSize,
                    mimeType = mimeType,
                    isIncoming = true,
                    peerName = peerName,
                    peerAddress = socket.inetAddress?.hostAddress ?: "Unknown",
                    bytesTransferred = fileBytesRead,
                    speedBytesPerSec = avgSpeed,
                    status = if (fileBytesRead == fileSize) "COMPLETED" else "FAILED"
                )
                onTransferRecorded(record)
            }

            _progressState.update {
                it.copy(
                    isCompleted = true,
                    statusText = "Transfer complete! Received $totalFiles files.",
                    currentSpeedBytesPerSec = 0L
                )
            }

        } catch (e: Exception) {
            e.printStackTrace()
            _progressState.update {
                it.copy(
                    isError = true,
                    errorMessage = e.localizedMessage ?: "Connection interrupted"
                )
            }
        } finally {
            try { dis?.close() } catch (ignored: Exception) {}
            try { dos?.close() } catch (ignored: Exception) {}
            try { socket.close() } catch (ignored: Exception) {}
        }
    }

    private fun getUniqueDownloadFile(originalName: String): File {
        val downloadDir = try {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "XShare"
            )
            if (!dir.exists()) dir.mkdirs()
            dir
        } catch (e: Exception) {
            val fallback = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "XShare")
            if (!fallback.exists()) fallback.mkdirs()
            fallback
        }

        var candidate = File(downloadDir, originalName)
        if (!candidate.exists()) return candidate

        val baseName = originalName.substringBeforeLast('.')
        val ext = if (originalName.contains('.')) "." + originalName.substringAfterLast('.') else ""

        var counter = 1
        while (candidate.exists()) {
            candidate = File(downloadDir, "$baseName ($counter)$ext")
            counter++
        }
        return candidate
    }

    fun cancelTransfer() {
        isCancelled = true
        _progressState.update { it.copy(statusText = "Cancelled", isActive = false) }
        try {
            currentClientSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopServer() {
        isRunning = false
        try {
            currentClientSocket?.close()
        } catch (ignored: Exception) {}
        try {
            serverSocket?.close()
        } catch (ignored: Exception) {}
        _progressState.update { it.copy(isActive = false) }
    }
}

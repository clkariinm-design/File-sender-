package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import com.example.data.db.XShareDatabase
import com.example.data.model.DiscoveredPeer
import com.example.data.model.ShareableItem
import com.example.data.model.TransferProgressState
import com.example.data.repository.StorageRepository
import com.example.data.repository.TransferRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TransferManager private constructor(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val database = XShareDatabase.getDatabase(context)
    val transferRepository = TransferRepository(database.transferDao())
    val storageRepository = StorageRepository(context)

    val wifiDirectController = WifiDirectController(context)

    private val tcpServer = TcpTransferServer(context) { record ->
        transferRepository.recordTransfer(record)
    }

    private val tcpClient = TcpTransferClient(context) { record ->
        transferRepository.recordTransfer(record)
    }

    private val embeddedHttpServer = EmbeddedHttpServer(context) { record ->
        transferRepository.recordTransfer(record)
    }

    // Unified transfer progress: merges server or client progress
    private val _activeProgress = MutableStateFlow(TransferProgressState())
    val activeProgress: StateFlow<TransferProgressState> = _activeProgress.asStateFlow()

    private val _isReceivingMode = MutableStateFlow(false)
    val isReceivingMode: StateFlow<Boolean> = _isReceivingMode.asStateFlow()

    private val _qrBitmap = MutableStateFlow<Bitmap?>(null)
    val qrBitmap: StateFlow<Bitmap?> = _qrBitmap.asStateFlow()

    private val _localIp = MutableStateFlow("192.168.49.1")
    val localIp: StateFlow<String> = _localIp.asStateFlow()

    init {
        // Collect server progress
        scope.launch {
            tcpServer.progressState.collect { state ->
                if (state.isActive || state.isCompleted || state.isError) {
                    _activeProgress.value = state
                }
            }
        }
        // Collect client progress
        scope.launch {
            tcpClient.progressState.collect { state ->
                if (state.isActive || state.isCompleted || state.isError) {
                    _activeProgress.value = state
                }
            }
        }
    }

    fun startReceiveMode() {
        _isReceivingMode.value = true
        val ip = NetworkUtils.getDeviceIpAddress()
        _localIp.value = ip

        tcpServer.startServer(TransferProtocol.PORT)
        embeddedHttpServer.startServer(TransferProtocol.WEB_PORT)
        wifiDirectController.createGroup()

        // Generate QR Code payload
        val devName = NetworkUtils.getDeviceName(context)
        val qrPayload = "xshare://$ip:${TransferProtocol.PORT}?name=$devName&web=${TransferProtocol.WEB_PORT}"
        _qrBitmap.value = QrCodeGenerator.generateQrBitmap(qrPayload, 512)
    }

    fun stopReceiveMode() {
        _isReceivingMode.value = false
        tcpServer.stopServer()
        embeddedHttpServer.stopServer()
        wifiDirectController.disconnect()
        _qrBitmap.value = null
    }

    fun sendFilesToPeer(targetHost: String, items: List<ShareableItem>, onFinished: (Boolean) -> Unit = {}) {
        // Also queue files in embedded HTTP server in case the peer is using a web browser
        embeddedHttpServer.updateSharedFiles(items)
        embeddedHttpServer.startServer(TransferProtocol.WEB_PORT)

        tcpClient.sendFiles(targetHost, TransferProtocol.PORT, items, onFinished)
    }

    fun cancelActiveTransfer() {
        tcpServer.cancelTransfer()
        tcpClient.cancelTransfer()
        _activeProgress.value = TransferProgressState(isActive = false, statusText = "Cancelled")
    }

    fun dismissProgressModal() {
        _activeProgress.value = TransferProgressState(isActive = false)
    }

    companion object {
        @Volatile
        private var INSTANCE: TransferManager? = null

        fun getInstance(context: Context): TransferManager {
            return INSTANCE ?: synchronized(this) {
                val instance = TransferManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}

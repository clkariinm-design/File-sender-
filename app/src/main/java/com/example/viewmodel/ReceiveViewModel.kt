package com.example.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import com.example.data.model.DiscoveredPeer
import com.example.data.model.TransferProgressState
import com.example.engine.TransferManager
import com.example.engine.TransferProtocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ReceiveViewMode {
    RADAR,
    QR_CODE,
    WEB_PORTAL
}

class ReceiveViewModel(application: Application) : AndroidViewModel(application) {

    private val transferManager = TransferManager.getInstance(application)

    private val _viewMode = MutableStateFlow(ReceiveViewMode.RADAR)
    val viewMode: StateFlow<ReceiveViewMode> = _viewMode.asStateFlow()

    val isReceiving: StateFlow<Boolean> = transferManager.isReceivingMode
    val qrBitmap: StateFlow<Bitmap?> = transferManager.qrBitmap
    val localIp: StateFlow<String> = transferManager.localIp
    val activeProgress: StateFlow<TransferProgressState> = transferManager.activeProgress
    val discoveredPeers: StateFlow<List<DiscoveredPeer>> = transferManager.wifiDirectController.discoveredPeers

    val webPortalUrl: String
        get() = "http://${localIp.value}:${TransferProtocol.WEB_PORT}"

    init {
        startReceiving()
    }

    fun setViewMode(mode: ReceiveViewMode) {
        _viewMode.value = mode
    }

    fun startReceiving() {
        transferManager.startReceiveMode()
    }

    fun stopReceiving() {
        transferManager.stopReceiveMode()
    }

    override fun onCleared() {
        super.onCleared()
        stopReceiving()
    }
}

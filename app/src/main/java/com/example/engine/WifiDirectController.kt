package com.example.engine

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import com.example.data.model.DiscoveredPeer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class WifiDirectController(private val context: Context) {

    private val p2pManager: WifiP2pManager? by lazy {
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    }

    private var channel: WifiP2pManager.Channel? = null
    private var isReceiverRegistered = false

    private val _isP2pEnabled = MutableStateFlow(false)
    val isP2pEnabled: StateFlow<Boolean> = _isP2pEnabled.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val _discoveredPeers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    val discoveredPeers: StateFlow<List<DiscoveredPeer>> = _discoveredPeers.asStateFlow()

    private val _connectionInfo = MutableStateFlow<WifiP2pInfo?>(null)
    val connectionInfo: StateFlow<WifiP2pInfo?> = _connectionInfo.asStateFlow()

    private val broadcastReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    _isP2pEnabled.value = (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED)
                }
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    p2pManager?.requestPeers(channel) { peerList: WifiP2pDeviceList? ->
                        val peers = peerList?.deviceList?.map { device ->
                            DiscoveredPeer(
                                deviceAddress = device.deviceAddress,
                                deviceName = if (device.deviceName.isNullOrBlank()) "Nearby Device" else device.deviceName,
                                status = when (device.status) {
                                    WifiP2pDevice.AVAILABLE -> "Available"
                                    WifiP2pDevice.INVITED -> "Invited"
                                    WifiP2pDevice.CONNECTED -> "Connected"
                                    WifiP2pDevice.FAILED -> "Failed"
                                    WifiP2pDevice.UNAVAILABLE -> "Unavailable"
                                    else -> "Unknown"
                                },
                                isGroupOwner = device.isGroupOwner,
                                isWifiDirect = true
                            )
                        } ?: emptyList()
                        _discoveredPeers.value = peers
                    }
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    @Suppress("DEPRECATION")
                    val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
                    if (networkInfo?.isConnected == true) {
                        p2pManager?.requestConnectionInfo(channel) { info ->
                            _connectionInfo.value = info
                        }
                    } else {
                        _connectionInfo.value = null
                    }
                }
                WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                    // Local device status changed
                }
            }
        }
    }

    fun initialize() {
        if (p2pManager != null && channel == null) {
            channel = p2pManager?.initialize(context, context.mainLooper, null)
        }
        registerReceiver()
    }

    private fun registerReceiver() {
        if (isReceiverRegistered) return
        val intentFilter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(broadcastReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(broadcastReceiver, intentFilter)
        }
        isReceiverRegistered = true
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery(onStarted: (Boolean) -> Unit = {}) {
        initialize()
        _isDiscovering.value = true
        p2pManager?.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                _isDiscovering.value = true
                onStarted(true)
            }

            override fun onFailure(reason: Int) {
                _isDiscovering.value = false
                onStarted(false)
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun connectToPeer(deviceAddress: String, onResult: (Boolean) -> Unit = {}) {
        val config = WifiP2pConfig().apply {
            this.deviceAddress = deviceAddress
        }
        p2pManager?.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                onResult(true)
            }

            override fun onFailure(reason: Int) {
                onResult(false)
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun createGroup(onResult: (Boolean) -> Unit = {}) {
        initialize()
        p2pManager?.createGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                onResult(true)
            }

            override fun onFailure(reason: Int) {
                onResult(false)
            }
        })
    }

    fun disconnect() {
        p2pManager?.removeGroup(channel, null)
        _connectionInfo.value = null
    }

    fun release() {
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(broadcastReceiver)
            } catch (ignored: Exception) {}
            isReceiverRegistered = false
        }
        channel = null
    }
}

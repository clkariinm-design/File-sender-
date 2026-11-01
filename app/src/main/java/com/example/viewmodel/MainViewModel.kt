package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.TransferProgressState
import com.example.data.model.TransferRecordEntity
import com.example.data.repository.OverallTransferStats
import com.example.data.repository.StorageOverview
import com.example.engine.NetworkUtils
import com.example.engine.TransferManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppDestination(val label: String) {
    HOME("Home"),
    SEND("Send"),
    RECEIVE("Receive"),
    HISTORY("History"),
    SETTINGS("Settings")
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val transferManager = TransferManager.getInstance(application)

    private val _currentDestination = MutableStateFlow(AppDestination.HOME)
    val currentDestination: StateFlow<AppDestination> = _currentDestination.asStateFlow()

    private val _storageOverview = MutableStateFlow(StorageOverview(0, 0, 0, 0f))
    val storageOverview: StateFlow<StorageOverview> = _storageOverview.asStateFlow()

    val overallStats: StateFlow<OverallTransferStats> = transferManager.transferRepository.overallStats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OverallTransferStats())

    val recentTransfers: StateFlow<List<TransferRecordEntity>> = transferManager.transferRepository.recentTransfers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeProgress: StateFlow<TransferProgressState> = transferManager.activeProgress

    val deviceName: String = NetworkUtils.getDeviceName(application)
    val localIp: StateFlow<String> = transferManager.localIp

    init {
        refreshStorage()
    }

    fun navigateTo(destination: AppDestination) {
        _currentDestination.value = destination
    }

    fun refreshStorage() {
        viewModelScope.launch {
            _storageOverview.value = transferManager.storageRepository.getStorageOverview()
        }
    }

    fun cancelActiveTransfer() {
        transferManager.cancelActiveTransfer()
    }

    fun dismissProgressModal() {
        transferManager.dismissProgressModal()
    }
}

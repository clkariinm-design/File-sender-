package com.example.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.TransferRecordEntity
import com.example.engine.TransferManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

enum class HistoryFilter {
    ALL,
    RECEIVED,
    SENT
}

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val transferManager = TransferManager.getInstance(application)
    private val repo = transferManager.transferRepository

    private val _currentFilter = MutableStateFlow(HistoryFilter.ALL)
    val currentFilter: StateFlow<HistoryFilter> = _currentFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val filteredTransfers: StateFlow<List<TransferRecordEntity>> = combine(
        repo.allTransfers,
        _currentFilter,
        _searchQuery
    ) { all, filter, query ->
        val list = when (filter) {
            HistoryFilter.ALL -> all
            HistoryFilter.RECEIVED -> all.filter { it.isIncoming }
            HistoryFilter.SENT -> all.filter { !it.isIncoming }
        }
        if (query.isBlank()) {
            list
        } else {
            list.filter { it.fileName.contains(query, ignoreCase = true) || it.peerName.contains(query, ignoreCase = true) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setFilter(filter: HistoryFilter) {
        _currentFilter.value = filter
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun deleteTransfer(id: Long) {
        viewModelScope.launch {
            repo.deleteTransfer(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repo.clearHistory()
        }
    }

    fun openFile(record: TransferRecordEntity) {
        try {
            val file = File(record.filePath)
            val uri: Uri = if (file.exists()) {
                FileProvider.getUriForFile(
                    getApplication(),
                    "${getApplication<Application>().packageName}.fileprovider",
                    file
                )
            } else {
                Uri.parse(record.filePath)
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, record.mimeType.ifEmpty { "*/*" })
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            getApplication<Application>().startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(getApplication(), "Could not open file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}

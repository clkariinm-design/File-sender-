package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.DiscoveredPeer
import com.example.data.model.FileCategory
import com.example.data.model.ShareableItem
import com.example.engine.TransferManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SendViewModel(application: Application) : AndroidViewModel(application) {

    private val transferManager = TransferManager.getInstance(application)
    private val storageRepo = transferManager.storageRepository

    private val _selectedCategory = MutableStateFlow(FileCategory.APPS)
    val selectedCategory: StateFlow<FileCategory> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Cached files by category
    private val _categoryItems = MutableStateFlow<Map<FileCategory, List<ShareableItem>>>(emptyMap())

    // Selected item IDs across all categories
    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    // Discovered nearby peers for sending
    val discoveredPeers: StateFlow<List<DiscoveredPeer>> = transferManager.wifiDirectController.discoveredPeers
    val isDiscovering: StateFlow<Boolean> = transferManager.wifiDirectController.isDiscovering

    // Filtered items for current category and search query
    val currentCategoryItems: StateFlow<List<ShareableItem>> = combine(
        _selectedCategory,
        _searchQuery,
        _categoryItems,
        _selectedIds
    ) { category, query, allMap, selected ->
        val list = allMap[category] ?: emptyList()
        val filtered = if (query.isBlank()) {
            list
        } else {
            list.filter { it.name.contains(query, ignoreCase = true) }
        }
        filtered.map { it.copy(isSelected = selected.contains(it.id)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All currently selected items across categories
    val selectedItemsList: StateFlow<List<ShareableItem>> = combine(
        _categoryItems,
        _selectedIds
    ) { allMap, selected ->
        allMap.values.flatten().filter { selected.contains(it.id) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalSelectedSize: StateFlow<Long> = selectedItemsList.combine(_selectedIds) { list, _ ->
        list.sumOf { it.size }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    init {
        loadCategory(FileCategory.APPS)
        loadCategory(FileCategory.PHOTOS)
        loadCategory(FileCategory.VIDEOS)
        loadCategory(FileCategory.MUSIC)
        loadCategory(FileCategory.DOCUMENTS)
        loadCategory(FileCategory.ARCHIVES)
        startPeerDiscovery()
    }

    fun selectCategory(category: FileCategory) {
        _selectedCategory.value = category
        if (!_categoryItems.value.containsKey(category)) {
            loadCategory(category)
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleSelection(item: ShareableItem) {
        _selectedIds.update { set ->
            if (set.contains(item.id)) {
                set - item.id
            } else {
                set + item.id
            }
        }
    }

    fun selectAllCurrentCategory() {
        val currentItems = currentCategoryItems.value
        val allIds = currentItems.map { it.id }.toSet()
        _selectedIds.update { current ->
            if (current.containsAll(allIds)) {
                current - allIds
            } else {
                current + allIds
            }
        }
    }

    fun clearSelections() {
        _selectedIds.value = emptySet()
    }

    fun loadCategory(category: FileCategory) {
        viewModelScope.launch {
            _isLoading.value = true
            val items = when (category) {
                FileCategory.APPS -> storageRepo.getInstalledApps()
                FileCategory.PHOTOS -> storageRepo.getPhotos()
                FileCategory.VIDEOS -> storageRepo.getVideos()
                FileCategory.MUSIC -> storageRepo.getMusic()
                FileCategory.DOCUMENTS -> storageRepo.getDocuments()
                FileCategory.ARCHIVES -> storageRepo.getArchives()
                FileCategory.LARGE_FILES -> storageRepo.getVideos().filter { it.size > 100 * 1024 * 1024 }
            }
            _categoryItems.update { map -> map + (category to items) }
            _isLoading.value = false
        }
    }

    fun startPeerDiscovery() {
        transferManager.wifiDirectController.startDiscovery()
    }

    fun sendToPeer(peer: DiscoveredPeer, onFinished: (Boolean) -> Unit = {}) {
        val itemsToSend = selectedItemsList.value
        if (itemsToSend.isEmpty()) return

        val targetIp = peer.ipAddress ?: "192.168.49.1"
        transferManager.sendFilesToPeer(targetIp, itemsToSend, onFinished)
    }

    fun sendToIp(ip: String, onFinished: (Boolean) -> Unit = {}) {
        val itemsToSend = selectedItemsList.value
        if (itemsToSend.isEmpty()) return
        transferManager.sendFilesToPeer(ip, itemsToSend, onFinished)
    }
}

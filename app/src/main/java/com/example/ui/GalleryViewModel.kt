package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.GalleryDatabase
import com.example.data.model.MediaItem
import com.example.data.repository.MediaRepository
import com.example.data.telegram.TelegramBackupManager
import com.example.data.telegram.TelegramSettingsManager
import com.example.data.telegram.UploadResult
import com.example.data.telegram.TelegramBackupService
import com.example.data.telegram.BackupServiceState
import com.example.data.telegram.BackupStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class GalleryViewModel(
    private val context: Context,
    private val mediaRepository: MediaRepository,
    private val settingsManager: TelegramSettingsManager,
    private val backupManager: TelegramBackupManager
) : ViewModel() {

    // UI state for Local search & filtering
    private val _localSearchQuery = MutableStateFlow("")
    val localSearchQuery = _localSearchQuery.asStateFlow()

    private val _localMediaTypeFilter = MutableStateFlow("all") // "all", "image", "video"
    val localMediaTypeFilter = _localMediaTypeFilter.asStateFlow()

    // UI state for Cloud search & filtering
    private val _cloudSearchQuery = MutableStateFlow("")
    val cloudSearchQuery = _cloudSearchQuery.asStateFlow()

    private val _cloudMediaTypeFilter = MutableStateFlow("all") // "all", "image", "video"
    val cloudMediaTypeFilter = _cloudMediaTypeFilter.asStateFlow()

    private val _selectedTab = MutableStateFlow(0) // 0: Local Gallery, 1: Telegram Cloud, 2: Settings
    val selectedTab = _selectedTab.asStateFlow()

    // Scanning status
    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    private val _isRestoring = MutableStateFlow(false)
    val isRestoring = _isRestoring.asStateFlow()

    // Backup stats & status
    private val _backupMessage = MutableStateFlow<String?>(null)
    val backupMessage = _backupMessage.asStateFlow()

    private val _activeUploadId = MutableStateFlow<String?>(null)
    val activeUploadId = _activeUploadId.asStateFlow()

    private val _backupProgress = MutableStateFlow<Float?>(null) // 0.0f to 1.0f
    val backupProgress = _backupProgress.asStateFlow()

    // Settings
    private val _botToken = MutableStateFlow(settingsManager.getBotToken())
    val botToken = _botToken.asStateFlow()

    private val _chatId = MutableStateFlow(settingsManager.getChatId())
    val chatId = _chatId.asStateFlow()

    private val _autoBackupPhotosEnabled = MutableStateFlow(settingsManager.isAutoBackupPhotosEnabled())
    val autoBackupPhotosEnabled = _autoBackupPhotosEnabled.asStateFlow()

    private val _autoBackupVideosEnabled = MutableStateFlow(settingsManager.isAutoBackupVideosEnabled())
    val autoBackupVideosEnabled = _autoBackupVideosEnabled.asStateFlow()

    private val _originalQualityEnabled = MutableStateFlow(settingsManager.isOriginalQualityEnabled())
    val originalQualityEnabled = _originalQualityEnabled.asStateFlow()

    private val _wifiOnlyEnabled = MutableStateFlow(settingsManager.isWifiOnlyEnabled())
    val wifiOnlyEnabled = _wifiOnlyEnabled.asStateFlow()

    // Expose filtered media items for Local UI
    val mediaItems: StateFlow<List<MediaItem>> = combine(
        mediaRepository.allMediaItems,
        _localSearchQuery,
        _localMediaTypeFilter
    ) { items, query, typeFilter ->
        var filtered = items.filter { it.localUri != null }

        // Filter by Media Type ("all", "image", "video")
        if (typeFilter != "all") {
            filtered = filtered.filter { it.mediaType.contains(typeFilter, ignoreCase = true) }
        }

        // Filter by Search Query (Filename or Date taken)
        if (query.isNotEmpty()) {
            filtered = filtered.filter { item ->
                val dateString = android.text.format.DateFormat.format("yyyy-MM-dd", item.dateTaken).toString()
                item.displayName.contains(query, ignoreCase = true) || dateString.contains(query)
            }
        }
        filtered
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Expose filtered media items for Cloud UI
    val cloudMediaItems: StateFlow<List<MediaItem>> = combine(
        mediaRepository.allMediaItems,
        _cloudSearchQuery,
        _cloudMediaTypeFilter
    ) { items, query, typeFilter ->
        var filtered = items.filter { it.isBackedUp }

        // Filter by Media Type ("all", "image", "video")
        if (typeFilter != "all") {
            filtered = filtered.filter { it.mediaType.contains(typeFilter, ignoreCase = true) }
        }

        // Filter by Search Query (Filename or Date taken)
        if (query.isNotEmpty()) {
            filtered = filtered.filter { item ->
                val dateString = android.text.format.DateFormat.format("yyyy-MM-dd", item.dateTaken).toString()
                item.displayName.contains(query, ignoreCase = true) || dateString.contains(query)
            }
        }
        filtered
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val backupStatus = BackupServiceState.status

    init {
        // Collect from BackupServiceState to update local UI states
        viewModelScope.launch {
            BackupServiceState.status.collect { status ->
                if (status == BackupStatus.IDLE || status == BackupStatus.COMPLETED || status == BackupStatus.CANCELLED || status == BackupStatus.ERROR) {
                    _activeUploadId.value = null
                    _backupProgress.value = null
                    scanLocalGallery() // Re-scan to ensure UI status is updated
                }
            }
        }
        viewModelScope.launch {
            BackupServiceState.progressFraction.collect { progress ->
                val currentStatus = BackupServiceState.status.value
                _backupProgress.value = if (currentStatus == BackupStatus.UPLOADING || currentStatus == BackupStatus.PAUSED) {
                    progress
                } else {
                    null
                }
            }
        }
        viewModelScope.launch {
            BackupServiceState.currentName.collect { name ->
                if (name.isNotEmpty()) {
                    val currentItems = mediaItems.value
                    val item = currentItems.find { it.displayName == name }
                    _activeUploadId.value = item?.id
                } else {
                    _activeUploadId.value = null
                }
            }
        }
        viewModelScope.launch {
            BackupServiceState.message.collect { msg ->
                if (msg != null) {
                    _backupMessage.value = msg
                    BackupServiceState.clearMessage()
                }
            }
        }
    }

    fun scanLocalGallery() {
        if (_isScanning.value) return
        viewModelScope.launch {
            _isScanning.value = true
            mediaRepository.scanLocalMedia()
            _isScanning.value = false

            // Auto-backup trigger if enabled and configured
            if (settingsManager.isAutoBackupEnabled() && settingsManager.hasValidCredentials()) {
                backupAllPending(isAutoTrigger = true)
            }
        }
    }

    fun setTab(tab: Int) {
        _selectedTab.value = tab
    }

    // Keep legacy signatures to avoid breaking other files, mapping them to local filters
    fun setSearchQuery(query: String) {
        _localSearchQuery.value = query
    }

    fun setMediaTypeFilter(filter: String) {
        _localMediaTypeFilter.value = filter
    }

    // New specific setters
    fun setLocalSearchQuery(query: String) {
        _localSearchQuery.value = query
    }

    fun setLocalMediaTypeFilter(filter: String) {
        _localMediaTypeFilter.value = filter
    }

    fun setCloudSearchQuery(query: String) {
        _cloudSearchQuery.value = query
    }

    fun setCloudMediaTypeFilter(filter: String) {
        _cloudMediaTypeFilter.value = filter
    }

    fun pauseBackup() {
        val intent = Intent(context, TelegramBackupService::class.java).apply {
            action = TelegramBackupService.ACTION_PAUSE_BACKUP
        }
        context.startService(intent)
    }

    fun resumeBackup() {
        val intent = Intent(context, TelegramBackupService::class.java).apply {
            action = TelegramBackupService.ACTION_RESUME_BACKUP
        }
        context.startService(intent)
    }

    fun cancelBackup() {
        val intent = Intent(context, TelegramBackupService::class.java).apply {
            action = TelegramBackupService.ACTION_CANCEL_BACKUP
        }
        context.startService(intent)
    }

    fun backupSelectedItems(itemIds: Set<String>, onComplete: () -> Unit = {}) {
        val token = settingsManager.getBotToken()
        val chat = settingsManager.getChatId()

        if (token.isEmpty() || chat.isEmpty()) {
            _backupMessage.value = "Configure credentials in Settings first!"
            onComplete()
            return
        }

        viewModelScope.launch {
            val allItems = mediaRepository.allMediaItems.first()
            val selected = allItems.filter { it.id in itemIds && it.localUri != null && it.backupStatus != "BACKED_UP" }

            if (selected.isEmpty()) {
                _backupMessage.value = "Selected items are already backed up!"
                onComplete()
                return@launch
            }

            val idsList = ArrayList(selected.map { it.id })
            val intent = Intent(context, TelegramBackupService::class.java).apply {
                action = TelegramBackupService.ACTION_START_BACKUP
                putStringArrayListExtra(TelegramBackupService.EXTRA_MEDIA_IDS, idsList)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            onComplete()
        }
    }

    fun updateSettings(token: String, chat: String, autoBackupPhotos: Boolean, autoBackupVideos: Boolean, originalQuality: Boolean, wifiOnly: Boolean) {
        settingsManager.saveSettings(token, chat, autoBackupPhotos, autoBackupVideos, originalQuality, wifiOnly)
        _botToken.value = token
        _chatId.value = chat
        _autoBackupPhotosEnabled.value = autoBackupPhotos
        _autoBackupVideosEnabled.value = autoBackupVideos
        _originalQualityEnabled.value = originalQuality
        _wifiOnlyEnabled.value = wifiOnly
        _backupMessage.value = "Settings saved successfully!"
        
        // Automatically fetch backed-up media when credentials are saved or updated
        restoreFromCloud()
    }

    fun restoreFromCloud() {
        val token = settingsManager.getBotToken()
        val chat = settingsManager.getChatId()

        if (token.isEmpty() || chat.isEmpty()) {
            return
        }

        viewModelScope.launch {
            if (_isRestoring.value) return@launch
            _isRestoring.value = true
            _backupMessage.value = "Connecting to Vault & fetching backed up media..."
            
            try {
                val restoredItems = backupManager.restoreBackupIndex(token, chat)
                if (restoredItems != null) {
                    if (restoredItems.isNotEmpty()) {
                        for (item in restoredItems) {
                            mediaRepository.insertOrUpdate(item)
                        }
                        _backupMessage.value = "Successfully synchronized ${restoredItems.size} items from Telegram Cloud!"
                    } else {
                        _backupMessage.value = "Connected! No backed-up items found in the vault yet."
                    }
                } else {
                    _backupMessage.value = "Vault connected! No existing cloud index found in this Telegram channel."
                }
            } catch (e: Exception) {
                _backupMessage.value = "Sync failed: ${e.localizedMessage}"
            } finally {
                _isRestoring.value = false
                scanLocalGallery() // Ensure we merge the status with current local gallery
            }
        }
    }

    private fun triggerIndexUpload() {
        viewModelScope.launch {
            val token = settingsManager.getBotToken()
            val chat = settingsManager.getChatId()
            if (token.isNotEmpty() && chat.isNotEmpty()) {
                val allItems = mediaRepository.allMediaItems.stateIn(viewModelScope).value
                backupManager.uploadBackupIndex(token, chat, allItems)
            }
        }
    }

    fun dismissMessage() {
        _backupMessage.value = null
    }

    fun clearAppCache() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Clear Coil memory & disk cache
                val coilLoader = coil.Coil.imageLoader(context)
                coilLoader.memoryCache?.clear()
                coilLoader.diskCache?.clear()

                // Clear the rest of the cache directory
                context.cacheDir.deleteRecursively()

                _backupMessage.value = "Cache cleared successfully! Storage footprint minimized."
            } catch (e: Exception) {
                _backupMessage.value = "Failed to clear cache: ${e.localizedMessage}"
            }
        }
    }

    // Backup a single item to Telegram
    fun backupItem(item: MediaItem) {
        val token = settingsManager.getBotToken()
        val chat = settingsManager.getChatId()

        if (token.isEmpty() || chat.isEmpty()) {
            _backupMessage.value = "Please configure Telegram Credentials first!"
            return
        }

        val idsList = arrayListOf(item.id)
        val intent = Intent(context, TelegramBackupService::class.java).apply {
            action = TelegramBackupService.ACTION_START_BACKUP
            putStringArrayListExtra(TelegramBackupService.EXTRA_MEDIA_IDS, idsList)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    // Backup all files that are not backed up yet
    fun backupAllPending(isAutoTrigger: Boolean = false) {
        val token = settingsManager.getBotToken()
        val chat = settingsManager.getChatId()

        if (token.isEmpty() || chat.isEmpty()) {
            if (!isAutoTrigger) {
                _backupMessage.value = "Please configure Telegram Credentials first!"
            }
            return
        }

        viewModelScope.launch {
            val allItems = mediaRepository.allMediaItems.first()
            
            val photosAllowed = if (isAutoTrigger) settingsManager.isAutoBackupPhotosEnabled() else true
            val videosAllowed = if (isAutoTrigger) settingsManager.isAutoBackupVideosEnabled() else true

            val pending = allItems.filter { item ->
                item.localUri != null && 
                item.backupStatus != "BACKED_UP" && 
                ((!item.isVideo && photosAllowed) || (item.isVideo && videosAllowed))
            }

            if (pending.isEmpty()) {
                if (!isAutoTrigger) {
                    _backupMessage.value = "All files are already backed up!"
                }
                return@launch
            }

            val idsList = ArrayList(pending.map { it.id })
            val intent = Intent(context, TelegramBackupService::class.java).apply {
                action = TelegramBackupService.ACTION_START_BACKUP
                putStringArrayListExtra(TelegramBackupService.EXTRA_MEDIA_IDS, idsList)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    // Delete a file locally to free up phone storage (Google Photos "Free Up Space")
    fun deleteLocalFile(item: MediaItem) {
        if (!item.isBackedUp) {
            _backupMessage.value = "Warning: File is not backed up! Back up before deleting."
            return
        }

        viewModelScope.launch {
            try {
                // Try physical content resolver deletion
                val uri = Uri.parse(item.id)
                context.contentResolver.delete(uri, null, null)
                mediaRepository.markAsDeletedLocally(item.id)
                _backupMessage.value = "Successfully cleaned up local storage for: ${item.displayName}"
            } catch (e: SecurityException) {
                // In modern Android, deleting requires special popups.
                // We provide a fallback: mark as deleted in database, which makes it "Cloud only" for the app context.
                mediaRepository.markAsDeletedLocally(item.id)
                _backupMessage.value = "Removed local reference. File is now safely stored in VishwaKosh Cloud!"
            } catch (e: Exception) {
                mediaRepository.markAsDeletedLocally(item.id)
                _backupMessage.value = "Removed local reference. File is now safely stored in VishwaKosh Cloud!"
            }
        }
    }

    // Delete backed up media from Telegram Cloud and local database/state
    fun deleteFromCloud(itemIds: Set<String>, onComplete: () -> Unit = {}) {
        val token = settingsManager.getBotToken()
        val chat = settingsManager.getChatId()

        if (token.isEmpty() || chat.isEmpty()) {
            _backupMessage.value = "Credentials not configured in Settings!"
            return
        }

        viewModelScope.launch {
            _backupMessage.value = "Deleting ${itemIds.size} items from cloud..."
            var count = 0
            for ((index, id) in itemIds.withIndex()) {
                val item = mediaRepository.getMediaItemById(id) ?: continue
                
                // If we have a Telegram message ID, try to delete it from the channel
                item.telegramMessageId?.let { msgId ->
                    if (index > 0) {
                        delay(1000) // Safe delay between consecutive deletions
                    }
                    backupManager.deleteMessage(token, chat, msgId)
                }

                // If localUri is still present, reset its state to NOT_BACKED_UP
                if (item.localUri != null) {
                    mediaRepository.updateBackupStatus(
                        id = item.id,
                        status = "NOT_BACKED_UP",
                        fileId = null,
                        messageId = null,
                        url = null
                    )
                } else {
                    // Otherwise, delete completely from DB because no local reference exists
                    mediaRepository.deleteMediaItemById(item.id)
                }
                count++
            }
            
            _backupMessage.value = "Successfully deleted $count items from Cloud."
            
            // Re-upload index file to reflect updated remote files
            triggerIndexUpload()
            
            scanLocalGallery()
            onComplete()
        }
    }
}

// Custom Factory for GalleryViewModel since it takes non-empty parameters
class GalleryViewModelFactory(
    private val context: Context,
    private val mediaRepository: MediaRepository,
    private val settingsManager: TelegramSettingsManager,
    private val backupManager: TelegramBackupManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GalleryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return GalleryViewModel(context, mediaRepository, settingsManager, backupManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

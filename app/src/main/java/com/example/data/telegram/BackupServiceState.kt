package com.example.data.telegram

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class BackupStatus {
    IDLE, UPLOADING, PAUSED, COMPLETED, CANCELLED, ERROR
}

object BackupServiceState {
    private val _status = MutableStateFlow(BackupStatus.IDLE)
    val status = _status.asStateFlow()

    private val _progressFraction = MutableStateFlow(0f)
    val progressFraction = _progressFraction.asStateFlow()

    private val _currentName = MutableStateFlow("")
    val currentName = _currentName.asStateFlow()

    private val _completedCount = MutableStateFlow(0)
    val completedCount = _completedCount.asStateFlow()

    private val _totalCount = MutableStateFlow(0)
    val totalCount = _totalCount.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun update(
        status: BackupStatus,
        progressFraction: Float = 0f,
        currentName: String = "",
        completedCount: Int = 0,
        totalCount: Int = 0,
        message: String? = null
    ) {
        _status.value = status
        _progressFraction.value = progressFraction
        _currentName.value = currentName
        _completedCount.value = completedCount
        _totalCount.value = totalCount
        if (message != null) {
            _message.value = message
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun reset() {
        _status.value = BackupStatus.IDLE
        _progressFraction.value = 0f
        _currentName.value = ""
        _completedCount.value = 0
        _totalCount.value = 0
        _message.value = null
    }
}

package com.example.data.telegram

import android.content.Context
import android.content.SharedPreferences

class TelegramSettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "telegram_backup_settings",
        Context.MODE_PRIVATE
    )

    fun getBotToken(): String {
        return prefs.getString(KEY_BOT_TOKEN, "") ?: ""
    }

    fun getChatId(): String {
        return prefs.getString(KEY_CHAT_ID, "") ?: ""
    }

    fun isAutoBackupEnabled(): Boolean {
        return isAutoBackupPhotosEnabled() || isAutoBackupVideosEnabled()
    }

    fun isAutoBackupPhotosEnabled(): Boolean {
        if (!prefs.contains(KEY_AUTO_BACKUP_PHOTOS)) {
            return prefs.getBoolean(KEY_AUTO_BACKUP, false)
        }
        return prefs.getBoolean(KEY_AUTO_BACKUP_PHOTOS, false)
    }

    fun isAutoBackupVideosEnabled(): Boolean {
        if (!prefs.contains(KEY_AUTO_BACKUP_VIDEOS)) {
            return false
        }
        return prefs.getBoolean(KEY_AUTO_BACKUP_VIDEOS, false)
    }

    fun isOriginalQualityEnabled(): Boolean {
        return prefs.getBoolean(KEY_ORIGINAL_QUALITY, true)
    }

    fun isWifiOnlyEnabled(): Boolean {
        return prefs.getBoolean(KEY_WIFI_ONLY, false)
    }

    fun saveSettings(
        botToken: String,
        chatId: String,
        autoBackupPhotos: Boolean,
        autoBackupVideos: Boolean,
        originalQuality: Boolean = true,
        wifiOnly: Boolean = false
    ) {
        prefs.edit()
            .putString(KEY_BOT_TOKEN, botToken.trim())
            .putString(KEY_CHAT_ID, chatId.trim())
            .putBoolean(KEY_AUTO_BACKUP_PHOTOS, autoBackupPhotos)
            .putBoolean(KEY_AUTO_BACKUP_VIDEOS, autoBackupVideos)
            .putBoolean(KEY_AUTO_BACKUP, autoBackupPhotos || autoBackupVideos)
            .putBoolean(KEY_ORIGINAL_QUALITY, originalQuality)
            .putBoolean(KEY_WIFI_ONLY, wifiOnly)
            .apply()
    }

    fun hasValidCredentials(): Boolean {
        return getBotToken().isNotEmpty() && getChatId().isNotEmpty()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_BOT_TOKEN = "bot_token"
        private const val KEY_CHAT_ID = "chat_id"
        private const val KEY_AUTO_BACKUP = "auto_backup"
        private const val KEY_AUTO_BACKUP_PHOTOS = "auto_backup_photos"
        private const val KEY_AUTO_BACKUP_VIDEOS = "auto_backup_videos"
        private const val KEY_ORIGINAL_QUALITY = "original_quality"
        private const val KEY_WIFI_ONLY = "wifi_only"
    }
}

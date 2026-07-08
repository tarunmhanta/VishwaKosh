package com.example.data.telegram

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.GalleryDatabase
import com.example.data.model.MediaItem
import com.example.data.repository.MediaRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

class TelegramBackupService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var backupJob: Job? = null

    private lateinit var repository: MediaRepository
    private lateinit var settingsManager: TelegramSettingsManager
    private lateinit var backupManager: TelegramBackupManager

    private var isPaused = false
    private var isCancelled = false

    private val notificationId = 1001
    private val channelId = "telegram_backup_channel"

    private var currentMediaList = listOf<MediaItem>()
    private var totalItems = 0
    private var completedCount = 0

    companion object {
        const val ACTION_START_BACKUP = "com.example.action.START_BACKUP"
        const val ACTION_PAUSE_BACKUP = "com.example.action.PAUSE_BACKUP"
        const val ACTION_RESUME_BACKUP = "com.example.action.RESUME_BACKUP"
        const val ACTION_CANCEL_BACKUP = "com.example.action.CANCEL_BACKUP"

        const val EXTRA_MEDIA_IDS = "com.example.extra.MEDIA_IDS"
    }

    override fun onCreate() {
        super.onCreate()
        val database = GalleryDatabase.getDatabase(applicationContext)
        repository = MediaRepository(applicationContext, database.mediaDao())
        settingsManager = TelegramSettingsManager(applicationContext)
        backupManager = TelegramBackupManager(applicationContext)

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_BACKUP -> {
                val mediaIds = intent.getStringArrayListExtra(EXTRA_MEDIA_IDS)
                if (mediaIds != null && mediaIds.isNotEmpty()) {
                    startBackupTask(mediaIds)
                } else {
                    stopSelf()
                }
            }
            ACTION_PAUSE_BACKUP -> {
                pauseBackup()
            }
            ACTION_RESUME_BACKUP -> {
                resumeBackup()
            }
            ACTION_CANCEL_BACKUP -> {
                cancelBackup()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startBackupTask(mediaIds: List<String>) {
        isPaused = false
        isCancelled = false
        completedCount = 0

        backupJob?.cancel()
        backupJob = serviceScope.launch {
            try {
                val allItems = repository.allMediaItems.first()
                currentMediaList = allItems.filter { it.id in mediaIds && it.localUri != null && it.backupStatus != "BACKED_UP" }
                totalItems = currentMediaList.size

                if (totalItems == 0) {
                    BackupServiceState.update(BackupStatus.COMPLETED, message = "All selected items are already backed up!")
                    stopForeground(true)
                    stopSelf()
                    return@launch
                }

                if (settingsManager.isWifiOnlyEnabled() && !isWifiConnected(applicationContext)) {
                    BackupServiceState.update(BackupStatus.ERROR, message = "Backup paused: Wi-Fi connection required.")
                    stopForeground(true)
                    stopSelf()
                    return@launch
                }

                // Move to foreground immediately
                val initialNotification = buildNotification("Preparing backup...", 0f)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(notificationId, initialNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                } else {
                    startForeground(notificationId, initialNotification)
                }

                BackupServiceState.update(BackupStatus.UPLOADING, 0f, "", 0, totalItems)

                val token = settingsManager.getBotToken()
                val chat = settingsManager.getChatId()

                for ((index, item) in currentMediaList.withIndex()) {
                    if (settingsManager.isWifiOnlyEnabled() && !isWifiConnected(applicationContext)) {
                        BackupServiceState.update(BackupStatus.ERROR, message = "Backup paused: Wi-Fi connection required.")
                        break
                    }
                    checkPause()
                    if (isCancelled) break

                    // Update UI and Notification progress
                    val progressFraction = completedCount.toFloat() / totalItems
                    BackupServiceState.update(
                        status = BackupStatus.UPLOADING,
                        progressFraction = progressFraction,
                        currentName = item.displayName,
                        completedCount = completedCount,
                        totalCount = totalItems
                    )
                    updateNotification("Uploading: ${item.displayName} (${completedCount + 1}/$totalItems)", progressFraction)

                    // Update database to backing up status
                    repository.updateBackupStatus(item.id, "BACKING_UP", null, null, null)

                    val result = backupManager.uploadFile(item, token, chat)

                    checkPause()
                    if (isCancelled) break

                    when (result) {
                        is UploadResult.Success -> {
                            repository.updateBackupStatus(
                                id = item.id,
                                status = "BACKED_UP",
                                fileId = result.fileId,
                                messageId = result.messageId,
                                url = result.telegramUrl
                            )
                            completedCount++
                        }
                        is UploadResult.Error -> {
                            repository.updateBackupStatus(item.id, "FAILED", null, null, null)
                        }
                    }

                    // Throttle between consecutive uploads to stay under Telegram limit
                    if (index < currentMediaList.lastIndex) {
                        var waitTime = 2000L
                        while (waitTime > 0) {
                            delay(200)
                            waitTime -= 200
                            checkPause()
                            if (isCancelled) break
                        }
                    }
                }

                if (isCancelled) {
                    BackupServiceState.update(BackupStatus.CANCELLED, message = "Backup cancelled by user.")
                } else {
                    BackupServiceState.update(
                        status = BackupStatus.COMPLETED,
                        progressFraction = 1f,
                        currentName = "",
                        completedCount = completedCount,
                        totalCount = totalItems,
                        message = "Backup completed! Successfully uploaded $completedCount of $totalItems items."
                    )
                }

            } catch (e: Exception) {
                e.printStackTrace()
                BackupServiceState.update(BackupStatus.ERROR, message = "Backup error: ${e.localizedMessage}")
            } finally {
                // Ensure the index JSON is ALWAYS re-uploaded to Telegram to keep metadata in sync
                try {
                    val finalItems = repository.allMediaItems.first()
                    val token = settingsManager.getBotToken()
                    val chat = settingsManager.getChatId()
                    if (token.isNotEmpty() && chat.isNotEmpty()) {
                        backupManager.uploadBackupIndex(token, chat, finalItems)
                    }
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }

                stopForeground(true)
                stopSelf()
            }
        }
    }

    private suspend fun checkPause() {
        while (isPaused && !isCancelled) {
            delay(500)
        }
    }

    private fun pauseBackup() {
        isPaused = true
        BackupServiceState.update(
            status = BackupStatus.PAUSED,
            progressFraction = completedCount.toFloat() / totalItems,
            currentName = if (completedCount < currentMediaList.size) currentMediaList[completedCount].displayName else "",
            completedCount = completedCount,
            totalCount = totalItems,
            message = "Backup paused."
        )
        updateNotification("Backup paused (${completedCount}/$totalItems)", completedCount.toFloat() / totalItems)
    }

    private fun resumeBackup() {
        isPaused = false
        BackupServiceState.update(
            status = BackupStatus.UPLOADING,
            progressFraction = completedCount.toFloat() / totalItems,
            currentName = if (completedCount < currentMediaList.size) currentMediaList[completedCount].displayName else "",
            completedCount = completedCount,
            totalCount = totalItems,
            message = "Resuming backup..."
        )
        updateNotification("Resuming backup (${completedCount}/$totalItems)", completedCount.toFloat() / totalItems)
    }

    private fun cancelBackup() {
        isCancelled = true
        isPaused = false
        BackupServiceState.update(BackupStatus.CANCELLED, message = "Cancelling backup...")
        backupJob?.cancel()
        // The finally block of startBackupTask will upload index and stop service
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "VishwaKosh Backup Service"
            val descriptionText = "Shows progress of active media backup to Telegram Cloud"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String, progress: Float): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setContentTitle("VishwaKosh Backup")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        // Progress bar
        val maxProgress = 100
        val currentProgress = (progress * 100).toInt()
        builder.setProgress(maxProgress, currentProgress, false)

        // Action buttons
        val pauseResumeAction = if (isPaused) {
            val resumeIntent = Intent(this, TelegramBackupService::class.java).apply { action = ACTION_RESUME_BACKUP }
            val resumePendingIntent = PendingIntent.getService(
                this, 1, resumeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_play, "Resume", resumePendingIntent
            ).build()
        } else {
            val pauseIntent = Intent(this, TelegramBackupService::class.java).apply { action = ACTION_PAUSE_BACKUP }
            val pausePendingIntent = PendingIntent.getService(
                this, 2, pauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_pause, "Pause", pausePendingIntent
            ).build()
        }

        val cancelIntent = Intent(this, TelegramBackupService::class.java).apply { action = ACTION_CANCEL_BACKUP }
        val cancelPendingIntent = PendingIntent.getService(
            this, 3, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val cancelAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent
        ).build()

        builder.addAction(pauseResumeAction)
        builder.addAction(cancelAction)

        return builder.build()
    }

    private fun updateNotification(contentText: String, progress: Float) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, buildNotification(contentText, progress))
    }

    private fun isWifiConnected(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } else {
            @Suppress("DEPRECATION")
            val activeNetworkInfo = connectivityManager.activeNetworkInfo
            @Suppress("DEPRECATION")
            return activeNetworkInfo != null && activeNetworkInfo.type == ConnectivityManager.TYPE_WIFI
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}

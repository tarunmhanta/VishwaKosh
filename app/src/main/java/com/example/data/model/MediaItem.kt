package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "media_items")
data class MediaItem(
    @PrimaryKey val id: String,                 // Content Uri or remote file ID
    val localUri: String?,                      // Local content URI (null if deleted locally)
    val mediaType: String,                      // "image" or "video"
    val size: Long,
    val duration: Long = 0,                     // For video in ms
    val dateTaken: Long,                        // Timestamp in ms
    val displayName: String,
    val backupStatus: String = "NOT_BACKED_UP", // "NOT_BACKED_UP", "BACKING_UP", "BACKED_UP", "FAILED"
    val telegramFileId: String? = null,
    val telegramMessageId: Int? = null,
    val telegramUrl: String? = null
) {
    val isVideo: Boolean get() = mediaType.contains("video", ignoreCase = true)
    val isBackedUp: Boolean get() = backupStatus == "BACKED_UP"
    val isDeletedLocally: Boolean get() = localUri == null
}

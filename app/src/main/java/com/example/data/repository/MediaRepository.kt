package com.example.data.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.example.data.local.MediaDao
import com.example.data.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class MediaRepository(
    private val context: Context,
    private val mediaDao: MediaDao
) {
    val allMediaItems: Flow<List<MediaItem>> = mediaDao.getAllMediaItems()
    val backedUpItems: Flow<List<MediaItem>> = mediaDao.getBackedUpMediaItems()

    suspend fun getMediaItemById(id: String): MediaItem? {
        return mediaDao.getMediaItemById(id)
    }

    suspend fun updateBackupStatus(
        id: String,
        status: String,
        fileId: String?,
        messageId: Int?,
        url: String?
    ) {
        mediaDao.updateBackupStatus(id, status, fileId, messageId, url)
    }

    suspend fun deleteMediaItem(item: MediaItem) {
        mediaDao.deleteMediaItem(item)
    }

    suspend fun deleteMediaItemById(id: String) {
        mediaDao.deleteMediaItemById(id)
    }

    suspend fun markAsDeletedLocally(id: String) {
        mediaDao.markAsDeletedLocally(id)
    }

    suspend fun insertOrUpdate(item: MediaItem) {
        mediaDao.insertOrUpdate(item)
    }

    suspend fun clearAll() {
        mediaDao.clearAll()
    }

    suspend fun scanLocalMedia() = withContext(Dispatchers.IO) {
        val localMedia = mutableListOf<MediaItem>()

        // 1. Scan Images
        val imageProjection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_MODIFIED
        )

        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                imageProjection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_TAKEN} DESC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val dateTakenColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn) ?: "IMG_$id.jpg"
                    val size = cursor.getLong(sizeColumn)
                    var dateTaken = cursor.getLong(dateTakenColumn)
                    if (dateTaken == 0L) {
                        dateTaken = cursor.getLong(dateModifiedColumn) * 1000L
                    }
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    ).toString()

                    localMedia.add(
                        MediaItem(
                            id = contentUri,
                            localUri = contentUri,
                            mediaType = "image",
                            size = size,
                            dateTaken = dateTaken,
                            displayName = name,
                            backupStatus = "NOT_BACKED_UP"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Scan Videos
        val videoProjection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_TAKEN,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.DURATION
        )

        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                videoProjection,
                null,
                null,
                "${MediaStore.Video.Media.DATE_TAKEN} DESC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val dateTakenColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_TAKEN)
                val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn) ?: "VID_$id.mp4"
                    val size = cursor.getLong(sizeColumn)
                    var dateTaken = cursor.getLong(dateTakenColumn)
                    if (dateTaken == 0L) {
                        dateTaken = cursor.getLong(dateModifiedColumn) * 1000L
                    }
                    val duration = cursor.getLong(durationColumn)
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id
                    ).toString()

                    localMedia.add(
                        MediaItem(
                            id = contentUri,
                            localUri = contentUri,
                            mediaType = "video",
                            size = size,
                            duration = duration,
                            dateTaken = dateTaken,
                            displayName = name,
                            backupStatus = "NOT_BACKED_UP"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. Insert into Room (IGNORE strategy means it will NOT overwrite backup status if already exists)
        if (localMedia.isNotEmpty()) {
            mediaDao.insertMediaItems(localMedia)
        }

        // 4. Synchronize: Find DB entries where localUri != null but are no longer in MediaStore
        val scannedIds = localMedia.map { it.id }.toSet()
        val dbItems = mediaDao.getAllMediaItemsList()

        for (dbItem in dbItems) {
            // Only care about items that are supposed to be local
            if (dbItem.localUri != null && !scannedIds.contains(dbItem.id)) {
                if (dbItem.isBackedUp) {
                    // Backed up, so mark as deleted locally. It is now "Cloud Only"
                    mediaDao.markAsDeletedLocally(dbItem.id)
                } else {
                    // Never backed up, and deleted locally. Remove from DB
                    mediaDao.deleteMediaItem(dbItem)
                }
            }
        }
    }
}

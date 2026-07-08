package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.model.MediaItem
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    @Query("SELECT * FROM media_items ORDER BY dateTaken DESC")
    fun getAllMediaItems(): Flow<List<MediaItem>>

    @Query("SELECT * FROM media_items ORDER BY dateTaken DESC")
    suspend fun getAllMediaItemsList(): List<MediaItem>

    @Query("SELECT * FROM media_items WHERE backupStatus = 'BACKED_UP' ORDER BY dateTaken DESC")
    fun getBackedUpMediaItems(): Flow<List<MediaItem>>

    @Query("SELECT * FROM media_items WHERE id = :id")
    suspend fun getMediaItemById(id: String): MediaItem?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMediaItems(items: List<MediaItem>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(item: MediaItem)

    @Update
    suspend fun updateMediaItem(item: MediaItem)

    @Query("UPDATE media_items SET backupStatus = :status, telegramFileId = :fileId, telegramMessageId = :messageId, telegramUrl = :url WHERE id = :id")
    suspend fun updateBackupStatus(id: String, status: String, fileId: String?, messageId: Int?, url: String?)

    @Query("UPDATE media_items SET localUri = NULL WHERE id = :id")
    suspend fun markAsDeletedLocally(id: String)

    @Delete
    suspend fun deleteMediaItem(item: MediaItem)

    @Query("DELETE FROM media_items WHERE id = :id")
    suspend fun deleteMediaItemById(id: String)

    @Query("DELETE FROM media_items")
    suspend fun clearAll()
}

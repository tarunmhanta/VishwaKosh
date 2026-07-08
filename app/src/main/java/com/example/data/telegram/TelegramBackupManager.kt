package com.example.data.telegram

import android.content.Context
import android.net.Uri
import com.example.data.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

sealed class UploadResult {
    data class Success(
        val fileId: String,
        val messageId: Int,
        val telegramUrl: String?
    ) : UploadResult()

    data class Error(val errorMsg: String) : UploadResult()
}

class TelegramBackupManager(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun uploadFile(
        mediaItem: MediaItem,
        botToken: String,
        chatId: String
    ): UploadResult = withContext(Dispatchers.IO) {
        val uriString = mediaItem.localUri ?: return@withContext UploadResult.Error("Local URI is missing.")
        val uri = Uri.parse(uriString)

        var tempFile: File? = null
        try {
            // 1. Copy Content URI to a temp file in cache directory
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext UploadResult.Error("Failed to open input stream.")

            val extension = if (mediaItem.isVideo) "mp4" else "jpg"
            tempFile = File(context.cacheDir, "tg_upload_${System.currentTimeMillis()}.$extension")
            FileOutputStream(tempFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }

            // 2. Prepare Multipart Request Body
            val isVideo = mediaItem.isVideo
            val settingsManager = TelegramSettingsManager(context)
            val originalQuality = settingsManager.isOriginalQualityEnabled()
            val isDocumentUpload = !isVideo && originalQuality

            val url = if (isVideo) {
                "https://api.telegram.org/bot$botToken/sendVideo"
            } else if (isDocumentUpload) {
                "https://api.telegram.org/bot$botToken/sendDocument"
            } else {
                "https://api.telegram.org/bot$botToken/sendPhoto"
            }

            val fileRequestBody = tempFile.asRequestBody(
                if (isVideo) "video/mp4".toMediaTypeOrNull() else "image/jpeg".toMediaTypeOrNull()
            )

            val partName = if (isVideo) {
                "video"
            } else if (isDocumentUpload) {
                "document"
            } else {
                "photo"
            }
            val requestBodyBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", chatId)
                .addFormDataPart("caption", "VishwaKosh Backup: ${mediaItem.displayName}")
                .addFormDataPart(partName, mediaItem.displayName, fileRequestBody)

            val request = Request.Builder()
                .url(url)
                .post(requestBodyBuilder.build())
                .build()

            // 3. Execute request
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorString = response.body?.string() ?: "Response code ${response.code}"
                    return@withContext UploadResult.Error("Telegram API error: $errorString")
                }

                val responseBodyString = response.body?.string()
                    ?: return@withContext UploadResult.Error("Empty response body.")

                val json = JSONObject(responseBodyString)
                if (!json.optBoolean("ok", false)) {
                    return@withContext UploadResult.Error("Telegram returned ok=false.")
                }

                val result = json.getJSONObject("result")
                val messageId = result.getInt("message_id")

                // Extract file_id
                val fileId = if (isVideo) {
                    val videoObj = result.getJSONObject("video")
                    videoObj.getString("file_id")
                } else if (isDocumentUpload) {
                    val docObj = result.getJSONObject("document")
                    docObj.getString("file_id")
                } else {
                    val photoArray = result.getJSONArray("photo")
                    // The last item in photo array has the largest size/best resolution
                    val largestPhoto = photoArray.getJSONObject(photoArray.length() - 1)
                    largestPhoto.getString("file_id")
                }

                // 4. Fetch the file path from Telegram to construct a permanent load/download URL
                val filePath = fetchFilePath(botToken, fileId)
                val telegramUrl = if (filePath != null) {
                    "https://api.telegram.org/file/bot$botToken/$filePath"
                } else {
                    null
                }

                return@withContext UploadResult.Success(
                    fileId = fileId,
                    messageId = messageId,
                    telegramUrl = telegramUrl
                )
            }

        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext UploadResult.Error("Backup failed: ${e.localizedMessage}")
        } finally {
            // Delete temp file from cache
            tempFile?.let {
                if (it.exists()) {
                    it.delete()
                }
            }
        }
    }

    private fun fetchFilePath(botToken: String, fileId: String): String? {
        val url = "https://api.telegram.org/bot$botToken/getFile?file_id=$fileId"
        val request = Request.Builder().url(url).build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return null
                    val json = JSONObject(body)
                    if (json.optBoolean("ok", false)) {
                        val result = json.getJSONObject("result")
                        return result.optString("file_path", null)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    suspend fun uploadBackupIndex(
        botToken: String,
        chatId: String,
        items: List<MediaItem>
    ): Boolean = withContext(Dispatchers.IO) {
        if (botToken.isEmpty() || chatId.isEmpty() || items.isEmpty()) return@withContext false
        
        var tempFile: File? = null
        try {
            // 1. Serialize backed up items to JSON
            val jsonArray = org.json.JSONArray()
            for (item in items) {
                if (item.isBackedUp) {
                    val jsonObj = org.json.JSONObject().apply {
                        put("id", item.id)
                        put("localUri", item.localUri ?: org.json.JSONObject.NULL)
                        put("mediaType", item.mediaType)
                        put("size", item.size)
                        put("duration", item.duration)
                        put("dateTaken", item.dateTaken)
                        put("displayName", item.displayName)
                        put("backupStatus", item.backupStatus)
                        put("telegramFileId", item.telegramFileId ?: org.json.JSONObject.NULL)
                        put("telegramMessageId", item.telegramMessageId ?: org.json.JSONObject.NULL)
                        put("telegramUrl", item.telegramUrl ?: org.json.JSONObject.NULL)
                    }
                    jsonArray.put(jsonObj)
                }
            }

            if (jsonArray.length() == 0) return@withContext false

            // 2. Write JSON to a temp file
            tempFile = File(context.cacheDir, "vishwakosh_backup_index.json")
            FileOutputStream(tempFile).use { fos ->
                fos.write(jsonArray.toString(2).toByteArray(Charsets.UTF_8))
            }

            // 3. Upload to Telegram channel
            val url = "https://api.telegram.org/bot$botToken/sendDocument"
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", chatId)
                .addFormDataPart("disable_notification", "true")
                .addFormDataPart(
                    "document",
                    "vishwakosh_backup_index.json",
                    tempFile.asRequestBody("application/json".toMediaTypeOrNull())
                )
                .build()

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext false
                val responseBodyString = response.body?.string() ?: return@withContext false
                val json = JSONObject(responseBodyString)
                if (!json.optBoolean("ok", false)) return@withContext false

                val result = json.getJSONObject("result")
                val messageId = result.getInt("message_id")

                // 4. Pin the new index file message so it can be fetched via getChat
                pinMessage(botToken, chatId, messageId)
                return@withContext true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        } finally {
            tempFile?.let {
                if (it.exists()) {
                    it.delete()
                }
            }
        }
    }

    private fun pinMessage(botToken: String, chatId: String, messageId: Int) {
        val url = "https://api.telegram.org/bot$botToken/pinChatMessage"
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", chatId)
            .addFormDataPart("message_id", messageId.toString())
            .addFormDataPart("disable_notification", "true")
            .build()
        val request = Request.Builder().url(url).post(requestBody).build()
        try {
            client.newCall(request).execute().use { _ -> }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun restoreBackupIndex(
        botToken: String,
        chatId: String
    ): List<MediaItem>? = withContext(Dispatchers.IO) {
        if (botToken.isEmpty() || chatId.isEmpty()) return@withContext null

        try {
            // 1. Get chat details to find pinned_message
            val url = "https://api.telegram.org/bot$botToken/getChat"
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", chatId)
                .build()
            val request = Request.Builder().url(url).post(requestBody).build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val bodyStr = response.body?.string() ?: return@withContext null
                val json = JSONObject(bodyStr)
                if (!json.optBoolean("ok", false)) return@withContext null

                val result = json.getJSONObject("result")
                if (!result.has("pinned_message")) return@withContext null

                val pinnedMessage = result.getJSONObject("pinned_message")
                if (!pinnedMessage.has("document")) return@withContext null

                val document = pinnedMessage.getJSONObject("document")
                val fileName = document.optString("file_name", "")
                if (!fileName.contains("vishwakosh_backup_index") && !fileName.contains("telebox_backup_index")) return@withContext null

                val fileId = document.getString("file_id")

                // 2. Fetch file path from Telegram
                val filePath = fetchFilePath(botToken, fileId) ?: return@withContext null
                val fileUrl = "https://api.telegram.org/file/bot$botToken/$filePath"

                // 3. Download the JSON index content
                val downloadRequest = Request.Builder().url(fileUrl).build()
                client.newCall(downloadRequest).execute().use { downloadResponse ->
                    if (!downloadResponse.isSuccessful) return@withContext null
                    val jsonContent = downloadResponse.body?.string() ?: return@withContext null

                    val jsonArray = org.json.JSONArray(jsonContent)
                    val restoredItems = mutableListOf<MediaItem>()

                    for (i in 0 until jsonArray.length()) {
                        val jsonObj = jsonArray.getJSONObject(i)
                        val id = jsonObj.getString("id")
                        val localUri = if (jsonObj.isNull("localUri")) null else jsonObj.getString("localUri")
                        val mediaType = jsonObj.getString("mediaType")
                        val size = jsonObj.getLong("size")
                        val duration = jsonObj.optLong("duration", 0L)
                        val dateTaken = jsonObj.getLong("dateTaken")
                        val displayName = jsonObj.getString("displayName")
                        val backupStatus = jsonObj.optString("backupStatus", "BACKED_UP")
                        val telegramFileId = if (jsonObj.isNull("telegramFileId")) null else jsonObj.getString("telegramFileId")
                        val telegramMessageId = if (jsonObj.isNull("telegramMessageId")) null else jsonObj.getInt("telegramMessageId")
                        val telegramUrl = if (jsonObj.isNull("telegramUrl")) null else jsonObj.getString("telegramUrl")

                        restoredItems.add(
                            MediaItem(
                                id = id,
                                localUri = localUri,
                                mediaType = mediaType,
                                size = size,
                                duration = duration,
                                dateTaken = dateTaken,
                                displayName = displayName,
                                backupStatus = backupStatus,
                                telegramFileId = telegramFileId,
                                telegramMessageId = telegramMessageId,
                                telegramUrl = telegramUrl
                            )
                        )
                    }
                    return@withContext restoredItems
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    suspend fun deleteMessage(
        botToken: String,
        chatId: String,
        messageId: Int
    ): Boolean = withContext(Dispatchers.IO) {
        val url = "https://api.telegram.org/bot$botToken/deleteMessage"
        val formBody = okhttp3.FormBody.Builder()
            .add("chat_id", chatId)
            .add("message_id", messageId.toString())
            .build()

        val request = Request.Builder()
            .url(url)
            .post(formBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@withContext false
                    val json = JSONObject(body)
                    return@withContext json.optBoolean("ok", false)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext false
    }
}

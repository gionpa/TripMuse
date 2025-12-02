package com.tripmuse.data.repository

import android.content.Context
import android.net.Uri
import com.tripmuse.data.api.TripMuseApi
import com.tripmuse.data.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepository @Inject constructor(
    private val api: TripMuseApi,
    @ApplicationContext private val context: Context,
    private val serverBaseUrl: String
) {
    private val currentUserId: Long = 1L

    suspend fun getMediaByAlbum(albumId: Long, type: MediaType? = null): Result<List<Media>> {
        return try {
            val response = api.getMediaByAlbum(currentUserId, albumId, type?.name)
            if (response.isSuccessful) {
                val mediaList = response.body()?.media?.map { it.withFullUrls(serverBaseUrl) } ?: emptyList()
                Result.success(mediaList)
            } else {
                Result.failure(Exception("Failed to fetch media: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMediaDetail(mediaId: Long): Result<MediaDetail> {
        return try {
            val response = api.getMediaDetail(currentUserId, mediaId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.withFullUrls(serverBaseUrl))
            } else {
                Result.failure(Exception("Failed to fetch media detail: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun Media.withFullUrls(baseUrl: String): Media {
        return copy(
            fileUrl = if (fileUrl.startsWith("/")) "$baseUrl$fileUrl" else fileUrl,
            thumbnailUrl = thumbnailUrl?.let { if (it.startsWith("/")) "$baseUrl$it" else it }
        )
    }

    private fun MediaDetail.withFullUrls(baseUrl: String): MediaDetail {
        return copy(
            fileUrl = if (fileUrl.startsWith("/")) "$baseUrl$fileUrl" else fileUrl,
            thumbnailUrl = thumbnailUrl?.let { if (it.startsWith("/")) "$baseUrl$it" else it }
        )
    }

    suspend fun uploadMedia(albumId: Long, uri: Uri): Result<Media> {
        return try {
            val file = getFileFromUri(uri)
            val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val requestBody = file.asRequestBody(mimeType.toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("file", file.name, requestBody)

            val response = api.uploadMedia(currentUserId, albumId, part)
            file.delete() // Clean up temp file

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to upload media: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteMedia(mediaId: Long): Result<Unit> {
        return try {
            val response = api.deleteMedia(currentUserId, mediaId)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to delete media: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setCoverImage(mediaId: Long): Result<Media> {
        return try {
            val response = api.setCoverImage(currentUserId, mediaId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.withFullUrls(serverBaseUrl))
            } else {
                Result.failure(Exception("Failed to set cover image: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun getFileFromUri(uri: Uri): File {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw Exception("Cannot open input stream")

        val fileName = "upload_${System.currentTimeMillis()}"
        val file = File(context.cacheDir, fileName)

        FileOutputStream(file).use { output ->
            inputStream.copyTo(output)
        }
        inputStream.close()

        return file
    }
}

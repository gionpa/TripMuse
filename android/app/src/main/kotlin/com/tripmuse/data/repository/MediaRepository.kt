package com.tripmuse.data.repository

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.tripmuse.data.api.TripMuseApi
import com.tripmuse.data.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
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
            // Extract EXIF metadata before copying file
            val metadata = extractExifMetadata(uri)
            Log.d("MediaRepository", "Extracted metadata: lat=${metadata.latitude}, lon=${metadata.longitude}, takenAt=${metadata.takenAt}")

            val file = getFileFromUri(uri)
            val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val requestBody = file.asRequestBody(mimeType.toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("file", file.name, requestBody)

            // Create metadata parts
            val latitudePart = metadata.latitude?.let {
                MultipartBody.Part.createFormData("latitude", it.toString())
            }
            val longitudePart = metadata.longitude?.let {
                MultipartBody.Part.createFormData("longitude", it.toString())
            }
            val takenAtPart = metadata.takenAt?.let {
                MultipartBody.Part.createFormData("takenAt", it)
            }

            val response = api.uploadMediaWithMetadata(
                currentUserId,
                albumId,
                part,
                latitudePart,
                longitudePart,
                takenAtPart
            )
            file.delete() // Clean up temp file

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to upload media: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e("MediaRepository", "Upload failed", e)
            Result.failure(e)
        }
    }

    private data class ExifMetadata(
        val latitude: Double? = null,
        val longitude: Double? = null,
        val takenAt: String? = null
    )

    private fun extractExifMetadata(uri: Uri): ExifMetadata {
        return try {
            // On Android 10+, we need to use setAccessUri to get location data
            val inputStream = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // For Android 10+, we need MediaStore.setRequireOriginal to get location
                val originalUri = android.provider.MediaStore.setRequireOriginal(uri)
                context.contentResolver.openInputStream(originalUri)
            } else {
                context.contentResolver.openInputStream(uri)
            }

            inputStream?.use { stream ->
                val exifInterface = ExifInterface(stream)

                // Extract GPS coordinates
                val latLong = exifInterface.latLong
                val latitude = latLong?.get(0)
                val longitude = latLong?.get(1)

                Log.d("MediaRepository", "EXIF GPS: lat=$latitude, lon=$longitude")

                // Extract date taken
                val dateTimeOriginal = exifInterface.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?: exifInterface.getAttribute(ExifInterface.TAG_DATETIME)

                val takenAt = dateTimeOriginal?.let { parseExifDateTime(it) }
                Log.d("MediaRepository", "EXIF DateTime: $dateTimeOriginal -> $takenAt")

                ExifMetadata(latitude, longitude, takenAt)
            } ?: ExifMetadata()
        } catch (e: Exception) {
            Log.e("MediaRepository", "Failed to extract EXIF metadata", e)
            ExifMetadata()
        }
    }

    private fun parseExifDateTime(exifDateTime: String): String? {
        return try {
            // EXIF format: "yyyy:MM:dd HH:mm:ss"
            val exifFormat = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
            val date = exifFormat.parse(exifDateTime)
            // Convert to ISO format for backend
            val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            date?.let { isoFormat.format(it) }
        } catch (e: Exception) {
            Log.e("MediaRepository", "Failed to parse EXIF date: $exifDateTime", e)
            null
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

        // Get file extension from mime type
        val mimeType = context.contentResolver.getType(uri)
        val extension = when (mimeType) {
            "image/jpeg" -> ".jpg"
            "image/png" -> ".png"
            "image/gif" -> ".gif"
            "image/webp" -> ".webp"
            "video/mp4" -> ".mp4"
            "video/3gpp" -> ".3gp"
            "video/quicktime" -> ".mov"
            else -> ".jpg" // Default to jpg for unknown image types
        }

        val fileName = "upload_${System.currentTimeMillis()}$extension"
        val file = File(context.cacheDir, fileName)

        FileOutputStream(file).use { output ->
            inputStream.copyTo(output)
        }
        inputStream.close()

        return file
    }
}

package com.tripmuse.data.repository

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.tripmuse.data.api.TripMuseApi
import com.tripmuse.data.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import okio.source
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class MediaRepository @Inject constructor(
    private val api: TripMuseApi,
    @ApplicationContext private val context: Context,
    private val serverBaseUrl: String
) {
    private val uploadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val uploadMutex = Mutex()

    fun uploadMediaInBackground(
        albumId: Long,
        uri: Uri,
        onResult: (Result<Media>) -> Unit = {}
    ) {
        uploadScope.launch {
            val result = uploadMedia(albumId, uri)
            onResult(result)
        }
    }

    /**
     * 여러 파일을 순차적으로 업로드합니다.
     * Mutex를 사용하여 한 번에 하나의 파일만 업로드되도록 보장합니다.
     * 서버 과부하를 방지하고 안정적인 업로드를 보장합니다.
     */
    fun uploadMediaSequentially(
        albumId: Long,
        uris: List<Uri>,
        onEachResult: (index: Int, uri: Uri, Result<Media>) -> Unit = { _, _, _ -> },
        onAllComplete: (successCount: Int, failCount: Int) -> Unit = { _, _ -> }
    ) {
        if (uris.isEmpty()) {
            onAllComplete(0, 0)
            return
        }

        uploadScope.launch {
            var successCount = 0
            var failCount = 0

            uris.forEachIndexed { index, uri ->
                uploadMutex.withLock {
                    Log.d("MediaRepository", "Sequential upload ${index + 1}/${uris.size}: $uri")
                    val result = uploadMedia(albumId, uri)

                    result.onSuccess {
                        successCount++
                        Log.d("MediaRepository", "Upload ${index + 1}/${uris.size} succeeded")
                    }
                    result.onFailure { e ->
                        failCount++
                        Log.e("MediaRepository", "Upload ${index + 1}/${uris.size} failed: ${e.message}")
                    }

                    onEachResult(index, uri, result)
                }
            }

            Log.d("MediaRepository", "All uploads complete: success=$successCount, fail=$failCount")
            onAllComplete(successCount, failCount)
        }
    }

    suspend fun getMediaByAlbum(
        albumId: Long,
        type: MediaType? = null,
        page: Int? = null,
        size: Int? = null
    ): Result<List<Media>> {
        return try {
            val response = api.getMediaByAlbum(albumId, type?.name, page, size)
            if (response.isSuccessful) {
                val mediaList = response.body()?.media?.map { it.withFullUrls(serverBaseUrl) } ?: emptyList()
                Result.success(mediaList)
            } else {
                Result.failure(Exception("Failed to fetch media: ${response.code()}"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMediaDetail(mediaId: Long): Result<MediaDetail> {
        return try {
            val response = api.getMediaDetail(mediaId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.withFullUrls(serverBaseUrl))
            } else {
                Result.failure(Exception("Failed to fetch media detail: ${response.code()}"))
            }
        } catch (e: CancellationException) {
            throw e
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
            Log.d("MediaRepository", "Starting upload for URI: $uri")
            Log.d("MediaRepository", "URI scheme: ${uri.scheme}, authority: ${uri.authority}, path: ${uri.path}")

            val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val isVideo = mimeType.startsWith("video")

            // Method 1: Try to extract EXIF directly from URI using ParcelFileDescriptor
            // This preserves GPS data when using ACTION_OPEN_DOCUMENT
            var metadata = extractExifMetadataFromUri(uri)
            Log.d("MediaRepository", "EXIF from URI (ParcelFileDescriptor): lat=${metadata.latitude}, lon=${metadata.longitude}, takenAt=${metadata.takenAt}")

            // Copy file to temp location for upload (이미지에만 사용)
            val file = if (isVideo) null else getFileFromUri(uri)

            // Method 2: If GPS not found and 이미지인 경우만 파일 EXIF 확인
            if (!isVideo && file != null && (metadata.latitude == null || metadata.longitude == null)) {
                val fileMetadata = extractExifMetadataFromFile(file)
                Log.d("MediaRepository", "EXIF from copied file: lat=${fileMetadata.latitude}, lon=${fileMetadata.longitude}")
                if (fileMetadata.latitude != null && fileMetadata.longitude != null) {
                    metadata = metadata.copy(
                        latitude = fileMetadata.latitude,
                        longitude = fileMetadata.longitude
                    )
                }
            }

            // Method 3: If still not found, try MediaStore (requires ACCESS_MEDIA_LOCATION)
            if (!isVideo && (metadata.latitude == null || metadata.longitude == null)) {
                val mediaStoreLocation = getLocationFromMediaStore(uri)
                if (mediaStoreLocation != null) {
                    Log.d("MediaRepository", "MediaStore location found: lat=${mediaStoreLocation.first}, lon=${mediaStoreLocation.second}")
                    metadata = metadata.copy(
                        latitude = mediaStoreLocation.first,
                        longitude = mediaStoreLocation.second
                    )
                } else {
                    Log.d("MediaRepository", "No location found in MediaStore either")
                }
            }

            Log.d("MediaRepository", "Final metadata: lat=${metadata.latitude}, lon=${metadata.longitude}, takenAt=${metadata.takenAt}")

            val inferredExt = file?.extension?.takeIf { it.isNotBlank() }?.let { ".$it" } ?: ""
            val fileName = queryDisplayName(uri)
                ?: "upload_${System.currentTimeMillis()}$inferredExt"

            val requestBody = if (isVideo) {
                createRequestBodyFromUri(uri, mimeType)
            } else {
                (file ?: getFileFromUri(uri)).asRequestBody(mimeType.toMediaTypeOrNull())
            }
            Log.d("MediaRepository", "Uploading uri=$uri mimeType=$mimeType fileName=$fileName")

            val part = MultipartBody.Part.createFormData("file", fileName, requestBody)

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
                albumId,
                part,
                latitudePart,
                longitudePart,
                takenAtPart
            )
            file?.delete() // Clean up temp file

            if (response.isSuccessful && response.body() != null) {
                Log.d("MediaRepository", "Upload success code=${response.code()} id=${response.body()!!.id}")
                Result.success(response.body()!!)
            } else {
                Log.e("MediaRepository", "Upload failed code=${response.code()} message=${response.errorBody()?.string()}")
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

    /**
     * Extract EXIF metadata directly from URI using ParcelFileDescriptor.
     * This method preserves GPS data when using ACTION_OPEN_DOCUMENT
     * because it reads directly from the file descriptor without going through
     * ContentResolver's metadata stripping.
     */
    private fun extractExifMetadataFromUri(uri: Uri): ExifMetadata {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val exifInterface = ExifInterface(pfd.fileDescriptor)

                // Extract GPS coordinates
                val latLong = exifInterface.latLong
                val latitude = latLong?.get(0)
                val longitude = latLong?.get(1)

                Log.d("MediaRepository", "EXIF GPS from ParcelFileDescriptor: lat=$latitude, lon=$longitude")

                // Log all GPS-related tags for debugging
                val gpsLatRef = exifInterface.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF)
                val gpsLat = exifInterface.getAttribute(ExifInterface.TAG_GPS_LATITUDE)
                val gpsLonRef = exifInterface.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF)
                val gpsLon = exifInterface.getAttribute(ExifInterface.TAG_GPS_LONGITUDE)
                Log.d("MediaRepository", "EXIF GPS raw: latRef=$gpsLatRef, lat=$gpsLat, lonRef=$gpsLonRef, lon=$gpsLon")

                // Extract date taken
                val dateTimeOriginal = exifInterface.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?: exifInterface.getAttribute(ExifInterface.TAG_DATETIME)

                val takenAt = dateTimeOriginal?.let { parseExifDateTime(it) }
                Log.d("MediaRepository", "EXIF DateTime: $dateTimeOriginal -> $takenAt")

                ExifMetadata(latitude, longitude, takenAt)
            } ?: ExifMetadata()
        } catch (e: Exception) {
            Log.e("MediaRepository", "Failed to extract EXIF metadata from URI using ParcelFileDescriptor", e)
            ExifMetadata()
        }
    }

    private fun extractExifMetadataFromFile(file: File): ExifMetadata {
        return try {
            val exifInterface = ExifInterface(file.absolutePath)

            // Extract GPS coordinates
            val latLong = exifInterface.latLong
            val latitude = latLong?.get(0)
            val longitude = latLong?.get(1)

            Log.d("MediaRepository", "EXIF GPS from file: lat=$latitude, lon=$longitude")

            // Log all GPS-related tags for debugging
            val gpsLatRef = exifInterface.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF)
            val gpsLat = exifInterface.getAttribute(ExifInterface.TAG_GPS_LATITUDE)
            val gpsLonRef = exifInterface.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF)
            val gpsLon = exifInterface.getAttribute(ExifInterface.TAG_GPS_LONGITUDE)
            Log.d("MediaRepository", "EXIF GPS raw: latRef=$gpsLatRef, lat=$gpsLat, lonRef=$gpsLonRef, lon=$gpsLon")

            // Extract date taken
            val dateTimeOriginal = exifInterface.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: exifInterface.getAttribute(ExifInterface.TAG_DATETIME)

            val takenAt = dateTimeOriginal?.let { parseExifDateTime(it) }
            Log.d("MediaRepository", "EXIF DateTime: $dateTimeOriginal -> $takenAt")

            ExifMetadata(latitude, longitude, takenAt)
        } catch (e: Exception) {
            Log.e("MediaRepository", "Failed to extract EXIF metadata from file", e)
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

    private fun queryDisplayName(uri: Uri): String? {
        val projection = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            if (cursor.moveToFirst()) {
                return cursor.getString(idx)
            }
        }
        return null
    }

    private fun createRequestBodyFromUri(uri: Uri, mimeType: String): okhttp3.RequestBody {
        val resolver = context.contentResolver
        val length = resolver.openAssetFileDescriptor(uri, "r")?.length ?: -1
        return object : okhttp3.RequestBody() {
            override fun contentType() = mimeType.toMediaTypeOrNull()
            override fun contentLength(): Long = length
            override fun writeTo(sink: okio.BufferedSink) {
                resolver.openInputStream(uri)?.use { input ->
                    sink.writeAll(input.source())
                } ?: throw IOException("Cannot open input stream for $uri")
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun getLocationFromMediaStore(uri: Uri): Pair<Double, Double>? {
        return try {
            // For Android Q+ (API 29+), we need ACCESS_MEDIA_LOCATION permission
            // and must use setRequireOriginal() to access location data
            val actualUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.setRequireOriginal(uri)
            } else {
                uri
            }

            val projection = arrayOf(
                MediaStore.Images.Media.LATITUDE,
                MediaStore.Images.Media.LONGITUDE
            )

            context.contentResolver.query(
                actualUri,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val latIndex = cursor.getColumnIndex(MediaStore.Images.Media.LATITUDE)
                    val lonIndex = cursor.getColumnIndex(MediaStore.Images.Media.LONGITUDE)

                    if (latIndex >= 0 && lonIndex >= 0) {
                        val latitude = cursor.getDouble(latIndex)
                        val longitude = cursor.getDouble(lonIndex)

                        Log.d("MediaRepository", "MediaStore query result: lat=$latitude, lon=$longitude")

                        // Check if values are valid (not 0.0)
                        if (latitude != 0.0 || longitude != 0.0) {
                            return Pair(latitude, longitude)
                        }
                    }
                }
            }

            // Try with PhotoPicker's media URI pattern
            // Photo Picker URIs are in format: content://media/picker/...
            // We need to find the original media file
            Log.d("MediaRepository", "URI scheme: ${uri.scheme}, authority: ${uri.authority}, path: ${uri.path}")

            null
        } catch (e: SecurityException) {
            Log.e("MediaRepository", "SecurityException accessing location - ACCESS_MEDIA_LOCATION may not be granted", e)
            null
        } catch (e: Exception) {
            Log.e("MediaRepository", "Failed to get location from MediaStore", e)
            null
        }
    }

    suspend fun deleteMedia(mediaId: Long): Result<Unit> {
        return try {
            val response = api.deleteMedia(mediaId)
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
            val response = api.setCoverImage(mediaId)
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
            else -> {
                // Try to infer from URI path
                val pathExt = uri.lastPathSegment?.substringAfterLast(".", "") ?: ""
                when (pathExt.lowercase()) {
                    "mp4", "mov", "avi", "webm", "3gp", "3gpp", "mpeg", "mpg" -> ".$pathExt"
                    "jpg", "jpeg", "png", "gif", "webp", "heic", "heif" -> ".$pathExt"
                    else -> ".mp4" // default to mp4 for safety
                }
            }
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

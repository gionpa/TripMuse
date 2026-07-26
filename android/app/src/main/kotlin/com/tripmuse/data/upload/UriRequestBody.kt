package com.tripmuse.data.upload

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source

/**
 * Uri의 내용을 메모리에 모두 올리지 않고 그대로 흘려보내는 RequestBody.
 *
 * 동영상처럼 큰 파일을 ByteArray로 읽으면 힙 한도를 넘겨 앱이 죽는다.
 */
class UriRequestBody(
    private val contentResolver: ContentResolver,
    private val uri: Uri,
    private val contentType: MediaType?
) : RequestBody() {

    override fun contentType(): MediaType? = contentType

    override fun contentLength(): Long =
        runCatching {
            contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
        }.getOrNull()?.takeIf { it >= 0 } ?: -1L

    override fun writeTo(sink: BufferedSink) {
        val stream = contentResolver.openInputStream(uri)
            ?: throw java.io.IOException("파일을 열 수 없습니다: $uri")
        stream.use { input ->
            input.source().use { source -> sink.writeAll(source) }
        }
    }
}

/** 업로드용 multipart 파트를 스트리밍 방식으로 만든다 */
fun Context.multipartFromUri(
    uri: Uri,
    fieldName: String = "file",
    fileName: String,
    mimeType: String
): MultipartBody.Part = MultipartBody.Part.createFormData(
    fieldName,
    fileName,
    UriRequestBody(contentResolver, uri, mimeType.toMediaTypeOrNull())
)

/** Uri가 가리키는 파일 크기 (알 수 없으면 -1) */
fun Context.fileSizeOf(uri: Uri): Long =
    runCatching {
        contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
    }.getOrNull() ?: -1L

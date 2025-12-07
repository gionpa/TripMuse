package com.tripmuse.ui.gallery

import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

// 선택된 파일 정보를 담는 데이터 클래스
data class SelectedMediaInfo(
    val uri: Uri,
    val filename: String,
    val isVideo: Boolean
)

@Composable
fun GalleryScreen(
    isPickerMode: Boolean = false,
    albumId: Long? = null,
    onMediaSelected: (() -> Unit)? = null,
    onUploadSuccess: (() -> Unit)? = null,
    onPendingMediaAdded: ((List<SelectedMediaInfo>) -> Unit)? = null,
    viewModel: GalleryViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var pickerLaunched by remember { mutableStateOf(false) }
    var pickerResultReceived by remember { mutableStateOf(false) }
    var hasNavigatedBackAfterUpload by remember { mutableStateOf(false) }

    // Handle back press - return to album detail
    BackHandler(enabled = isPickerMode) {
        Log.d("GalleryScreen", "BackHandler triggered, calling onMediaSelected")
        onMediaSelected?.invoke()
    }

    // Use ACTION_OPEN_DOCUMENT instead of Photo Picker to preserve EXIF GPS data
    // Photo Picker strips GPS metadata for privacy, but ACTION_OPEN_DOCUMENT preserves it
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        Log.d("GalleryScreen", "Document picker result received: ${uris.size} items")
        pickerResultReceived = true

        if (uris.isNotEmpty() && albumId != null) {
            Log.d("GalleryScreen", "Starting background upload for ${uris.size} items to album $albumId")

            // 선택된 파일 정보 추출하여 pending 미디어로 즉시 표시
            val selectedMediaInfoList = uris.mapNotNull { uri ->
                try {
                    val mimeType = context.contentResolver.getType(uri)
                    val isVideo = mimeType?.startsWith("video") == true
                    val filename = context.contentResolver.query(
                        uri,
                        arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
                        null, null, null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            cursor.getString(0)
                        } else null
                    } ?: "media_${System.currentTimeMillis()}"

                    SelectedMediaInfo(uri, filename, isVideo)
                } catch (e: Exception) {
                    Log.e("GalleryScreen", "Failed to extract media info for $uri", e)
                    null
                }
            }

            // pending 미디어 추가 콜백 호출 (UI에 즉시 표시)
            if (selectedMediaInfoList.isNotEmpty()) {
                onPendingMediaAdded?.invoke(selectedMediaInfoList)
            }

            // 업로드 시작 후 즉시 앨범 화면으로 복귀
            // 업로드는 백그라운드에서 계속 진행됨
            viewModel.uploadMediaFromUris(
                albumId = albumId,
                uris = uris,
                onComplete = {
                    // 즉시 앨범 화면으로 복귀
                    Log.d("GalleryScreen", "Upload started, navigating back to album immediately")
                    if (!hasNavigatedBackAfterUpload) {
                        hasNavigatedBackAfterUpload = true
                        onMediaSelected?.invoke()
                    }
                },
                onUploadSuccess = {
                    // 각 업로드 성공 시 앨범 리스트 갱신 트리거
                    onUploadSuccess?.invoke()
                }
            )
        } else {
            // User cancelled without selection or empty result
            Log.d("GalleryScreen", "No items selected, calling onMediaSelected to go back")
            onMediaSelected?.invoke()
        }
    }

    // Launch document picker immediately when screen appears
    LaunchedEffect(isPickerMode) {
        if (isPickerMode && !pickerLaunched) {
            Log.d("GalleryScreen", "Launching document picker for album $albumId")
            pickerLaunched = true
            // Allow both images and videos
            documentPickerLauncher.launch(arrayOf("image/*", "video/*"))
        }
    }

    // Show upload progress overlay
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (uiState.isUploading) {
            Card {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("업로드 중...")
                }
            }
        } else if (isPickerMode && !pickerResultReceived) {
            // Show loading while picker is open
            CircularProgressIndicator()
        }
    }
}

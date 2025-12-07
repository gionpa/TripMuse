package com.tripmuse.ui.gallery

import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun GalleryScreen(
    isPickerMode: Boolean = false,
    albumId: Long? = null,
    onMediaSelected: (() -> Unit)? = null,
    viewModel: GalleryViewModel = hiltViewModel()
) {
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

            // 업로드 시작 - 서버가 PROCESSING 상태로 바로 미디어를 생성함
            // 앨범 화면으로 복귀하면 refreshAlbumKey로 자동 갱신되고
            // PROCESSING 상태인 미디어는 scheduleRefresh로 자동 폴링됨
            viewModel.uploadMediaFromUris(
                albumId = albumId,
                uris = uris,
                onComplete = {
                    Log.d("GalleryScreen", "Upload request sent, navigating back to album")
                    if (!hasNavigatedBackAfterUpload) {
                        hasNavigatedBackAfterUpload = true
                        onMediaSelected?.invoke()
                    }
                },
                onUploadSuccess = {
                    // 개별 업로드 성공 - 서버에서 PROCESSING→COMPLETED 처리됨
                    Log.d("GalleryScreen", "Individual upload completed on server")
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

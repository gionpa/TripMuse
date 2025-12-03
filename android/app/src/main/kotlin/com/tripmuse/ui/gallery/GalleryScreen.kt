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
            Log.d("GalleryScreen", "Uploading ${uris.size} items to album $albumId")
            viewModel.uploadMediaFromUris(albumId, uris) {
                Log.d("GalleryScreen", "Upload complete, calling onMediaSelected")
                onMediaSelected?.invoke()
            }
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

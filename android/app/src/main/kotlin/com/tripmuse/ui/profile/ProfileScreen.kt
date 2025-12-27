package com.tripmuse.ui.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.tripmuse.data.api.ApiModule

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onLogout: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var showImageOptionsDialog by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.uploadProfileImage(context, it) }
    }

    val nickname = uiState.user?.nickname ?: "사용자"
    val email = uiState.user?.email ?: ""
    val albumCount = uiState.user?.stats?.albumCount ?: 0
    val imageCount = uiState.user?.stats?.imageCount ?: 0
    val videoCount = uiState.user?.stats?.videoCount ?: 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("프로필") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Profile header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val profileImageUrl = uiState.user?.profileImageUrl

                    if (profileImageUrl != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(ApiModule.BASE_URL.trimEnd('/') + profileImageUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = "프로필 이미지",
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                                .clickable { showImageOptionsDialog = true },
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Surface(
                            modifier = Modifier
                                .size(100.dp)
                                .clickable { imagePickerLauncher.launch("image/*") },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = nickname.take(1),
                                    style = MaterialTheme.typography.headlineLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    // Camera icon overlay
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable {
                                if (profileImageUrl != null) {
                                    showImageOptionsDialog = true
                                } else {
                                    imagePickerLauncher.launch("image/*")
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "프로필 이미지 변경",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }

                    // Loading indicator
                    if (uiState.isUploading) {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = nickname,
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider()

            // Statistics
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(count = albumCount.toString(), label = "앨범")
                StatItem(count = imageCount.toString(), label = "사진")
                StatItem(count = videoCount.toString(), label = "동영상")
            }

            HorizontalDivider()

            // Menu items (card style)
            ProfileMenuCard(
                icon = Icons.Default.Settings,
                title = "설정",
                onClick = onNavigateToSettings
            )
            ProfileMenuCard(
                icon = Icons.Default.ExitToApp,
                title = "로그아웃",
                onClick = { viewModel.logout(onLogout) }
            )
        }
    }

    // Profile image options dialog
    if (showImageOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showImageOptionsDialog = false },
            title = { Text("프로필 이미지") },
            text = { Text("프로필 이미지를 변경하거나 삭제할 수 있습니다.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showImageOptionsDialog = false
                        imagePickerLauncher.launch("image/*")
                    }
                ) {
                    Text("변경")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            showImageOptionsDialog = false
                            viewModel.deleteProfileImage()
                        }
                    ) {
                        Text("삭제", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { showImageOptionsDialog = false }) {
                        Text("취소")
                    }
                }
            }
        )
    }

    // Error Snackbar
    uiState.error?.let { error ->
        LaunchedEffect(error) {
            // Auto clear error after showing
        }
        Snackbar(
            modifier = Modifier.padding(16.dp),
            action = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text("닫기")
                }
            }
        ) {
            Text(error)
        }
    }
}

@Composable
fun StatItem(
    count: String,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = count,
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ProfileMenuCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .shadow(4.dp, shape),
        shape = shape,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                        )
                    ),
                    shape = shape
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

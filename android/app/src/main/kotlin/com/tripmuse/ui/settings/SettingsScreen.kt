package com.tripmuse.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadStorageUsage()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("설정") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // 저장공간 섹션
            StorageSection(
                imageBytes = uiState.storageUsage?.imageBytes ?: 0,
                videoBytes = uiState.storageUsage?.videoBytes ?: 0,
                totalBytes = uiState.storageUsage?.totalBytes ?: 0,
                maxBytes = uiState.storageUsage?.maxBytes ?: (500 * 1024 * 1024),
                isLoading = uiState.isLoading
            )
        }
    }
}

@Composable
fun StorageSection(
    imageBytes: Long,
    videoBytes: Long,
    totalBytes: Long,
    maxBytes: Long,
    isLoading: Boolean
) {
    val imageColor = Color(0xFF4CAF50)  // 초록
    val videoColor = Color(0xFF2196F3)  // 파랑
    val emptyColor = Color(0xFFE0E0E0)  // 회색

    val imagePercent = if (maxBytes > 0) imageBytes.toFloat() / maxBytes else 0f
    val videoPercent = if (maxBytes > 0) videoBytes.toFloat() / maxBytes else 0f
    val totalPercent = if (maxBytes > 0) totalBytes.toFloat() / maxBytes else 0f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "저장공간",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                // 바 그래프
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(emptyColor)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // 사진 영역
                        if (imagePercent > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight(imagePercent.coerceAtLeast(0.001f))
                                    .background(imageColor)
                            )
                        }
                        // 동영상 영역
                        if (videoPercent > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight(videoPercent.coerceAtLeast(0.001f))
                                    .background(videoColor)
                            )
                        }
                        // 빈 공간
                        val emptyPercent = (1f - totalPercent).coerceAtLeast(0f)
                        if (emptyPercent > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight(emptyPercent.coerceAtLeast(0.001f))
                                    .background(emptyColor)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 사용량 텍스트
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatBytes(totalBytes),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "/ ${formatBytes(maxBytes)}",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${String.format("%.1f", totalPercent * 100)}% 사용 중",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 범례
                HorizontalDivider()

                Spacer(modifier = Modifier.height(16.dp))

                // 사진 항목
                StorageLegendItem(
                    color = imageColor,
                    label = "사진",
                    bytes = imageBytes
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 동영상 항목
                StorageLegendItem(
                    color = videoColor,
                    label = "동영상",
                    bytes = videoBytes
                )
            }
        }
    }
}

@Composable
fun StorageLegendItem(
    color: Color,
    label: String,
    bytes: Long
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = formatBytes(bytes),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 * 1024 -> String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> String.format("%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}

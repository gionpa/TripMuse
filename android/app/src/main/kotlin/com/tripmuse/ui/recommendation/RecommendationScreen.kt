package com.tripmuse.ui.recommendation

import android.Manifest
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.tripmuse.data.model.DetectedTrip
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun RecommendationScreen(
    onNavigateToAlbum: (Long) -> Unit,
    viewModel: RecommendationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // 권한 요청
    val permissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.ACCESS_MEDIA_LOCATION
            )
        } else {
            listOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }

    val permissionsState = rememberMultiplePermissionsState(permissions) { permissionsResult ->
        val allGranted = permissionsResult.values.all { it }
        if (allGranted) {
            viewModel.setPermissionRequired(false)
            viewModel.scanForTrips(forceRefresh = true)
        } else {
            viewModel.setPermissionRequired(true)
        }
    }

    LaunchedEffect(Unit) {
        if (!permissionsState.allPermissionsGranted) {
            viewModel.setPermissionRequired(true)
        }
    }

    // 앨범 생성 완료 후 네비게이션
    LaunchedEffect(uiState.createdAlbumId) {
        uiState.createdAlbumId?.let { albumId ->
            viewModel.onNavigatedToAlbum()
            onNavigateToAlbum(albumId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("스마트 추천") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ),
                actions = {
                    if (!uiState.permissionRequired && !uiState.isScanning) {
                        IconButton(onClick = { viewModel.scanForTrips(forceRefresh = true) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "새로고침")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                // 권한 요청 필요
                uiState.permissionRequired -> {
                    PermissionRequestContent(
                        onRequestPermission = { permissionsState.launchMultiplePermissionRequest() }
                    )
                }

                // 스캔 중
                uiState.isScanning -> {
                    ScanningContent()
                }

                // 에러 발생
                uiState.error != null -> {
                    ErrorContent(
                        error = uiState.error!!,
                        onRetry = { viewModel.scanForTrips(forceRefresh = true) }
                    )
                }

                // 빈 상태
                uiState.detectedTrips.isEmpty() -> {
                    EmptyContent()
                }

                // 추천 목록
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(uiState.detectedTrips, key = { it.id }) { trip ->
                            DetectedTripCard(
                                trip = trip,
                                onDismiss = { viewModel.dismissTrip(trip.id) },
                                onCreateAlbum = { viewModel.selectTripForAlbum(trip) }
                            )
                        }
                    }
                }
            }

            // 앨범 생성 바텀시트
            if (uiState.showCreateSheet && uiState.selectedTrip != null) {
                QuickAlbumCreateSheet(
                    trip = uiState.selectedTrip!!,
                    title = uiState.albumTitle,
                    onTitleChange = viewModel::updateAlbumTitle,
                    isCreating = uiState.isCreating,
                    uploadProgress = uiState.uploadProgress,
                    uploadTotal = uiState.uploadTotal,
                    onDismiss = viewModel::dismissCreateSheet,
                    onConfirm = viewModel::createAlbum
                )
            }
        }
    }
}

@Composable
private fun PermissionRequestContent(
    onRequestPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row {
            Icon(
                Icons.Default.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                Icons.Default.Image,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "여행 사진을 자동으로 찾아드려요",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "이 기능을 사용하려면 다음 권한이 필요해요:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            horizontalAlignment = Alignment.Start
        ) {
            PermissionItem(text = "사진 및 동영상 접근")
            PermissionItem(text = "사진 위치 정보 접근")
        }

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(onClick = onRequestPermission) {
                Text("권한 허용하기")
            }
        }
    }
}

@Composable
private fun PermissionItem(text: String) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Check,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ScanningContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "여행을 찾고 있어요...",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "갤러리에서 여행 사진을 분석하고 있습니다",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ErrorContent(
    error: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = error,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text("다시 시도")
        }
    }
}

@Composable
private fun EmptyContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Lightbulb,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "새로운 여행이 감지되지 않았어요",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "평소와 다른 곳에서 사진을 3장 이상 찍으면\n자동으로 여행을 감지해드려요",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun DetectedTripCard(
    trip: DetectedTrip,
    onDismiss: () -> Unit,
    onCreateAlbum: () -> Unit
) {
    val dateFormatter = DateTimeFormatter.ofPattern("M월 d일", Locale.KOREAN)
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // 헤더
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = Color(0xFFFFC107),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "새로운 여행을 발견했어요!",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 위치
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = trip.location,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 날짜
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.CalendarMonth,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (trip.startDate == trip.endDate) {
                        trip.startDate.format(dateFormatter)
                    } else {
                        "${trip.startDate.format(dateFormatter)} ~ ${trip.endDate.format(dateFormatter)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 미디어 수
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.PhotoLibrary,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = buildString {
                        append("사진 ${trip.photoCount}장")
                        if (trip.videoCount > 0) {
                            append(", 동영상 ${trip.videoCount}개")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 미리보기 썸네일
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(trip.previewUris.take(3)) { uri ->
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(uri)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }

                // 추가 미디어 수 배지
                if (trip.mediaCount > 3) {
                    item {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "+${trip.mediaCount - 3}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 액션 버튼
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("나중에")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onCreateAlbum) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("앨범 만들기")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAlbumCreateSheet(
    trip: DetectedTrip,
    title: String,
    onTitleChange: (String) -> Unit,
    isCreating: Boolean,
    uploadProgress: Int,
    uploadTotal: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val dateFormatter = DateTimeFormatter.ofPattern("M월 d일", Locale.KOREAN)

    ModalBottomSheet(
        onDismissRequest = { if (!isCreating) onDismiss() },
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            // 헤더
            Text(
                text = "${trip.location} 앨범을 만들어보세요",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 앨범 제목 입력
            OutlinedTextField(
                value = title,
                onValueChange = onTitleChange,
                label = { Text("앨범 제목") },
                singleLine = true,
                enabled = !isCreating,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 자동 감지 정보
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    InfoRow(
                        icon = Icons.Default.LocationOn,
                        label = "위치",
                        value = "${trip.location} (자동 감지)"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    InfoRow(
                        icon = Icons.Default.CalendarMonth,
                        label = "기간",
                        value = if (trip.startDate == trip.endDate) {
                            "${trip.startDate.format(dateFormatter)} (자동 감지)"
                        } else {
                            "${trip.startDate.format(dateFormatter)} ~ ${trip.endDate.format(dateFormatter)} (자동 감지)"
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    InfoRow(
                        icon = Icons.Default.PhotoLibrary,
                        label = "미디어",
                        value = "${trip.mediaCount}개 자동 추가됨"
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 업로드 진행률
            if (isCreating) {
                Column {
                    Text(
                        text = "업로드 중... ($uploadProgress/$uploadTotal)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { if (uploadTotal > 0) uploadProgress.toFloat() / uploadTotal else 0f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // 버튼
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onDismiss,
                    enabled = !isCreating
                ) {
                    Text("취소")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onConfirm,
                    enabled = !isCreating && title.isNotBlank()
                ) {
                    if (isCreating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("앨범 생성")
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

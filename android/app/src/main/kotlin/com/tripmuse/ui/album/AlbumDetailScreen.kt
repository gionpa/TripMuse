package com.tripmuse.ui.album

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.tripmuse.R
import com.tripmuse.data.model.Media
import com.tripmuse.data.model.MediaType
import com.tripmuse.data.model.UploadStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    albumId: Long,
    onBackClick: () -> Unit,
    onMediaClick: (Long, List<Long>) -> Unit,
    onAddMediaClick: (Long) -> Unit,
    onEditAlbumClick: (Long) -> Unit = {},
    viewModel: AlbumViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // 화면 진입 및 복귀 시 앨범/미디어를 최신 상태로 로딩
    // repeatOnLifecycle(STARTED)를 사용하여 첫 진입 시에도 호출되고, 백그라운드에서 복귀 시에도 호출됨
    LaunchedEffect(lifecycleOwner, albumId) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.loadAlbum(albumId)
        }
    }

    // 삭제 확인 다이얼로그
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("앨범 삭제") },
            text = { Text("'${uiState.album?.title ?: ""}' 앨범을 삭제하시겠습니까?\n삭제된 앨범은 복구할 수 없습니다.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAlbum(albumId)
                        showDeleteDialog = false
                        onBackClick()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("삭제")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("취소")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.album?.title ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    val isOwner = uiState.album?.isOwner == true
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "더보기")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("앨범 수정") },
                                onClick = {
                                    showMenu = false
                                    onEditAlbumClick(albumId)
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Edit, contentDescription = null)
                                },
                                enabled = isOwner
                            )
                            DropdownMenuItem(
                                text = { Text("앨범 삭제") },
                                onClick = {
                                    showMenu = false
                                    showDeleteDialog = true
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Delete, contentDescription = null)
                                },
                                enabled = isOwner
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            val isOwner = uiState.album?.isOwner == true
            FloatingActionButton(
                onClick = { if (isOwner) onAddMediaClick(albumId) },
                containerColor = if (isOwner)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (isOwner)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            ) {
                Icon(Icons.Default.Add, contentDescription = "미디어 추가")
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .paint(
                    painter = painterResource(id = R.drawable.bg),
                    contentScale = ContentScale.Crop,
                    alpha = 0.3f
                )
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                uiState.error != null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = uiState.error ?: "오류가 발생했습니다",
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { viewModel.loadAlbum(albumId) }) {
                            Text("다시 시도")
                        }
                    }
                }
                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Album info header
                        uiState.album?.let { album ->
                            AlbumInfoHeader(album = album)
                        }

                        // Filter tabs
                        FilterTabs(
                            selectedFilter = uiState.selectedFilter,
                            onFilterSelected = { viewModel.setFilter(it) }
                        )

                        // Media grid
                        val displayList = uiState.mediaList
                        if (displayList.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "아직 미디어가 없습니다",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(4),
                                contentPadding = PaddingValues(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                itemsIndexed(displayList) { index, media ->
                                    MediaThumbnail(
                                        media = media,
                                        isOwner = uiState.album?.isOwner == true,
                                        onClick = { onMediaClick(media.id, displayList.map { it.id }) },
                                        onSetCover = { viewModel.setCoverImage(media.id) },
                                        onDelete = { viewModel.deleteMedia(media.id) }
                                    )
                                    if (index >= displayList.size - 4 && uiState.hasMore && !uiState.isLoadingMore) {
                                        viewModel.loadNextPage(albumId)
                                    }
                                }
                                if (uiState.isLoadingMore) {
                                    item(span = { GridItemSpan(4) }) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AlbumInfoHeader(album: com.tripmuse.data.model.AlbumDetail) {
    Column(
        modifier = Modifier.padding(16.dp)
    ) {
        if (album.location != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = album.location,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (album.startDate != null || album.endDate != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Icon(
                    Icons.Default.DateRange,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = buildString {
                        album.startDate?.let { append(it) }
                        if (album.startDate != null && album.endDate != null) append(" ~ ")
                        album.endDate?.let { append(it) }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 4.dp)
        ) {
            Icon(
                Icons.Default.Photo,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "${album.mediaCount}장의 추억",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun FilterTabs(
    selectedFilter: MediaFilter,
    onFilterSelected: (MediaFilter) -> Unit
) {
    TabRow(
        selectedTabIndex = selectedFilter.ordinal
    ) {
        Tab(
            selected = selectedFilter == MediaFilter.ALL,
            onClick = { onFilterSelected(MediaFilter.ALL) },
            text = { Text("전체") }
        )
        Tab(
            selected = selectedFilter == MediaFilter.IMAGE,
            onClick = { onFilterSelected(MediaFilter.IMAGE) },
            text = { Text("사진") }
        )
        Tab(
            selected = selectedFilter == MediaFilter.VIDEO,
            onClick = { onFilterSelected(MediaFilter.VIDEO) },
            text = { Text("동영상") }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaThumbnail(
    media: Media,
    isOwner: Boolean = true,
    onClick: () -> Unit,
    onSetCover: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    val context = LocalContext.current
    var showContextMenu by remember { mutableStateOf(false) }
    val isProcessing = media.uploadStatus == UploadStatus.PROCESSING
    val isFailed = media.uploadStatus == UploadStatus.FAILED
    val imageRequest = remember(media.thumbnailUrl, media.fileUrl) {
        val target = media.thumbnailUrl ?: media.fileUrl
        ImageRequest.Builder(context)
            .data(target)
            .crossfade(true)
            .size(512, 512) // 작은 썸네일로 요청해 네트워크/디코드 비용 감소
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .build()
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .combinedClickable(
                enabled = !isProcessing && !isFailed,
                onClick = onClick,
                onLongClick = { showContextMenu = true }
            )
    ) {
        // For videos without thumbnail, show a placeholder instead of trying to load video URL as image
        if (media.type == MediaType.VIDEO && media.thumbnailUrl == null) {
            // Video placeholder when no thumbnail is available
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.small),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Videocam,
                        contentDescription = media.originalFilename,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            AsyncImage(
                model = imageRequest,
                contentDescription = media.originalFilename,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.small),
                contentScale = ContentScale.Crop
            )
        }

        if (isProcessing || isFailed) {
            Surface(
                modifier = Modifier
                    .matchParentSize()
                    .clip(MaterialTheme.shapes.small),
                color = Color.Black.copy(alpha = 0.45f)
            ) {}
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "업로드 중",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } else {
                    Text(
                        "업로드 실패",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // Cover indicator
        if (media.isCover) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp),
                shape = MaterialTheme.shapes.extraSmall,
                color = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = "대표 이미지",
                    modifier = Modifier
                        .size(16.dp)
                        .padding(2.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        // Video indicator
        if (media.type == MediaType.VIDEO) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(32.dp),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.padding(4.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Unread comments badge
        if (media.hasUnreadComments) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
                shape = MaterialTheme.shapes.extraSmall,
                color = MaterialTheme.colorScheme.error
            ) {
                Icon(
                    Icons.Default.ChatBubble,
                    contentDescription = "새 댓글",
                    modifier = Modifier
                        .size(16.dp)
                        .padding(2.dp),
                    tint = MaterialTheme.colorScheme.onError
                )
            }
        }

        // Context menu - 항상 표시, isOwner가 아니면 메뉴 항목 비활성화
        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false },
            offset = DpOffset(0.dp, 0.dp)
        ) {
            DropdownMenuItem(
                text = { Text("대표 이미지로 설정") },
                onClick = {
                    showContextMenu = false
                    onSetCover()
                },
                leadingIcon = {
                    Icon(Icons.Default.Star, contentDescription = null)
                },
                enabled = isOwner
            )
            DropdownMenuItem(
                text = { Text("삭제") },
                onClick = {
                    showContextMenu = false
                    onDelete()
                },
                leadingIcon = {
                    Icon(Icons.Default.Delete, contentDescription = null)
                },
                enabled = isOwner
            )
        }
    }
}

package com.tripmuse.ui.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.paint
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.tripmuse.R
import com.tripmuse.data.model.Album
import com.tripmuse.data.model.AlbumVisibility
import coil.request.CachePolicy
import coil.request.ImageRequest
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAlbumClick: (Long) -> Unit,
    onCreateAlbumClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadAlbums()
    }

    Scaffold(
        topBar = {
            if (uiState.isSearching) {
                SearchBar(
                    query = uiState.searchQuery,
                    onQueryChange = { viewModel.onSearchQueryChange(it) },
                    onClose = { viewModel.toggleSearch() }
                )
            } else {
                TopAppBar(
                    title = {
                        Image(
                            painter = painterResource(id = R.drawable.tripmuse_title),
                            contentDescription = "TripMuse",
                            modifier = Modifier.height(80.dp),
                            contentScale = ContentScale.FillHeight
                        )
                    },
                    actions = {
                        IconButton(onClick = { viewModel.toggleSearch() }) {
                            Icon(Icons.Default.Search, contentDescription = "검색")
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateAlbumClick) {
                Icon(Icons.Default.Add, contentDescription = "새 앨범")
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
                        Button(onClick = { viewModel.loadAlbums() }) {
                            Text("다시 시도")
                        }
                    }
                }
                uiState.filteredAlbums.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "아직 앨범이 없습니다",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = onCreateAlbumClick) {
                            Text("첫 번째 앨범 만들기")
                        }
                    }
                }
                else -> {
                    val albumsToShow = uiState.filteredAlbums
                    val selectedTab = uiState.selectedTab
                    val allowModify = selectedTab == AlbumTab.MINE

                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        TabRow(
                            selectedTabIndex = if (selectedTab == AlbumTab.MINE) 0 else 1
                        ) {
                            Tab(
                                selected = selectedTab == AlbumTab.MINE,
                                onClick = { viewModel.selectTab(AlbumTab.MINE) },
                                text = { Text("나의 앨범") }
                            )
                            Tab(
                                selected = selectedTab == AlbumTab.SHARED,
                                onClick = { viewModel.selectTab(AlbumTab.SHARED) },
                                text = { Text("공유 받은 앨범") }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (albumsToShow.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = if (selectedTab == AlbumTab.MINE) "아직 앨범이 없습니다" else "공유 받은 앨범이 없습니다",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            DraggableAlbumGrid(
                                albums = albumsToShow,
                                gridState = viewModel.gridState,
                                onAlbumClick = onAlbumClick,
                                onDeleteClick = { if (allowModify) viewModel.deleteAlbum(it) },
                                onMove = { from, to -> if (allowModify) viewModel.moveAlbum(from, to) },
                                enableReorder = allowModify,
                                showDelete = allowModify
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DraggableAlbumGrid(
    albums: List<Album>,
    gridState: LazyGridState,
    onAlbumClick: (Long) -> Unit,
    onDeleteClick: (Long) -> Unit,
    onMove: (Int, Int) -> Unit,
    enableReorder: Boolean,
    showDelete: Boolean
) {
    var draggedItemIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(Pair(0f, 0f)) }

    val itemWidthPx = 500f
    val itemHeightPx = 600f

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(
            items = albums,
            key = { _, album -> album.id }
        ) { index, album ->
            val isDragging = draggedItemIndex == index
            val dragModifier = if (enableReorder) {
                Modifier
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer {
                        if (isDragging) {
                            translationX = dragOffset.first
                            translationY = dragOffset.second
                            scaleX = 1.05f
                            scaleY = 1.05f
                        }
                    }
                    .shadow(if (isDragging) 8.dp else 0.dp)
                    .pointerInput(album.id, index) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggedItemIndex = index
                                dragOffset = Pair(0f, 0f)
                            },
                            onDragEnd = {
                                draggedItemIndex = null
                                dragOffset = Pair(0f, 0f)
                            },
                            onDragCancel = {
                                draggedItemIndex = null
                                dragOffset = Pair(0f, 0f)
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffset = Pair(
                                    dragOffset.first + dragAmount.x,
                                    dragOffset.second + dragAmount.y
                                )

                                val currentRow = index / 2
                                val currentCol = index % 2

                                val totalOffsetX = dragOffset.first
                                val totalOffsetY = dragOffset.second

                                val colOffset = when {
                                    totalOffsetX > itemWidthPx / 2 && currentCol == 0 -> 1
                                    totalOffsetX < -itemWidthPx / 2 && currentCol == 1 -> -1
                                    else -> 0
                                }

                                val rowOffset = when {
                                    totalOffsetY > itemHeightPx / 2 -> 1
                                    totalOffsetY < -itemHeightPx / 2 -> -1
                                    else -> 0
                                }

                                val newCol = (currentCol + colOffset).coerceIn(0, 1)
                                val newRow = (currentRow + rowOffset).coerceIn(0, (albums.size - 1) / 2)
                                val targetIndex = (newRow * 2 + newCol).coerceIn(0, albums.size - 1)

                                if (targetIndex != index && targetIndex in albums.indices) {
                                    onMove(index, targetIndex)
                                    draggedItemIndex = targetIndex
                                    dragOffset = Pair(0f, 0f)
                                }
                            }
                        )
                    }
            } else {
                Modifier
            }

            Box(
                modifier = dragModifier
            ) {
                AlbumCard(
                    album = album,
                    onClick = { if (!isDragging) onAlbumClick(album.id) },
                    onDeleteClick = { onDeleteClick(album.id) },
                    isDragging = isDragging && enableReorder,
                    showMenu = showDelete
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    TopAppBar(
        title = {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("앨범 검색...") },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "닫기")
            }
        }
    )
}

@Composable
fun AlbumCard(
    album: Album,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    isDragging: Boolean = false,
    showMenu: Boolean = true
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val elevation by animateDpAsState(
        targetValue = if (isDragging) 8.dp else 2.dp,
        label = "card elevation"
    )

    // Light blue tint for card background
    val cardBackgroundColor = Color(0xFFE8F4FC)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (!isDragging) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        colors = CardDefaults.cardColors(containerColor = cardBackgroundColor)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            ) {
                if (album.coverImageUrl != null) {
                    val coverRequest = remember(album.coverImageUrl) {
                        ImageRequest.Builder(context)
                            .data(album.coverImageUrl)
                            .crossfade(true)
                            .size(720, 720) // 요청 크기를 제한해 썸네일 우선 로딩
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .build()
                    }
                    AsyncImage(
                        model = coverRequest,
                        contentDescription = album.title,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(MaterialTheme.shapes.medium),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color(0xFFD0E8F2)  // Soft blue for placeholder
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = album.title.take(1),
                                style = MaterialTheme.typography.displaySmall,
                                color = Color(0xFF5B7FFF)  // Modern blue text
                            )
                        }
                    }
                }

                // 내 앨범이면서 공개 설정된 경우 좌측 상단에 공개 범위 표시
                if (album.isOwner && album.visibility != AlbumVisibility.PRIVATE) {
                    val (icon, label, bgColor) = when (album.visibility) {
                        AlbumVisibility.FRIENDS_ONLY -> Triple("👥", "친구 공개", Color(0xFF4A90D9))
                        AlbumVisibility.PUBLIC -> Triple("🌐", "전체 공개", Color(0xFF4CAF50))
                        else -> Triple("", "", Color.Transparent)
                    }
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp),
                        color = bgColor.copy(alpha = 0.85f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = icon,
                                style = MaterialTheme.typography.labelSmall
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }
                }

                // 공유받은 앨범인 경우 소유자 정보 표시
                if (!album.isOwner && album.owner != null) {
                    val owner = album.owner
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 프로필 이미지
                            if (owner.profileImageUrl != null) {
                                AsyncImage(
                                    model = owner.profileImageUrl,
                                    contentDescription = "프로필",
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(MaterialTheme.shapes.extraSmall),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Surface(
                                    modifier = Modifier.size(20.dp),
                                    color = Color(0xFF5B7FFF),
                                    shape = MaterialTheme.shapes.extraSmall
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = owner.nickname.take(1),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = owner.nickname,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // More options
                if (showMenu) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                    ) {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "더보기",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("삭제") },
                                onClick = {
                                    menuExpanded = false
                                    onDeleteClick()
                                },
                                enabled = album.isOwner
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color(0xFF1A4A7A),  // Deep blue tone
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (album.location != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = album.location,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (album.startDate != null) {
                        Text(
                            text = album.startDate,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "${album.mediaCount}장",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1A4A7A)  // Deep blue, matching title
                    )
                }
            }
        }
    }
}

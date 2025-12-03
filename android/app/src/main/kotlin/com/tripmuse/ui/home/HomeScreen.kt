package com.tripmuse.ui.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.tripmuse.R
import com.tripmuse.data.model.Album
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
                uiState.albums.isEmpty() -> {
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
                    val albumsToShow = if (uiState.isSearching) uiState.filteredAlbums else uiState.albums
                    if (albumsToShow.isEmpty() && uiState.isSearching) {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "검색 결과가 없습니다",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        DraggableAlbumGrid(
                            albums = albumsToShow,
                            onAlbumClick = onAlbumClick,
                            onDeleteClick = { viewModel.deleteAlbum(it) },
                            onMove = { from, to -> viewModel.moveAlbum(from, to) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DraggableAlbumGrid(
    albums: List<Album>,
    onAlbumClick: (Long) -> Unit,
    onDeleteClick: (Long) -> Unit,
    onMove: (Int, Int) -> Unit
) {
    var draggedItemIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(Pair(0f, 0f)) }
    val gridState = rememberLazyGridState()

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

            Box(
                modifier = Modifier
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
                    .pointerInput(Unit) {
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
            ) {
                AlbumCard(
                    album = album,
                    onClick = { if (!isDragging) onAlbumClick(album.id) },
                    onDeleteClick = { onDeleteClick(album.id) },
                    isDragging = isDragging
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
    isDragging: Boolean = false
) {
    var showMenu by remember { mutableStateOf(false) }

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
                    AsyncImage(
                        model = album.coverImageUrl,
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

                // More options
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                ) {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "더보기",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("삭제") },
                            onClick = {
                                showMenu = false
                                onDeleteClick()
                            }
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.titleMedium,
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
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = album.location,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "${album.mediaCount}장",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

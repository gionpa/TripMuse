package com.tripmuse.ui.media

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.tripmuse.data.api.ApiModule
import com.tripmuse.data.model.Comment
import com.tripmuse.data.model.MediaType
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MediaDetailScreen(
    initialMediaId: Long,
    mediaIds: List<Long>,
    onBackClick: () -> Unit,
    viewModel: MediaViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Find initial page index
    val initialPage = remember(initialMediaId, mediaIds) {
        mediaIds.indexOf(initialMediaId).coerceAtLeast(0)
    }

    // HorizontalPager state
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { mediaIds.size }
    )

    // Current media ID based on pager position
    val currentMediaId = remember(pagerState.currentPage, mediaIds) {
        if (mediaIds.isNotEmpty() && pagerState.currentPage < mediaIds.size) {
            mediaIds[pagerState.currentPage]
        } else {
            initialMediaId
        }
    }

    // Load media when page changes
    LaunchedEffect(currentMediaId) {
        viewModel.loadMedia(currentMediaId)
    }

    // 미디어 삭제 성공 시 뒤로가기
    LaunchedEffect(uiState.isDeleted) {
        if (uiState.isDeleted) {
            onBackClick()
        }
    }

    // 삭제 확인 다이얼로그
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("사진 삭제") },
            text = { Text("이 사진을 삭제하시겠습니까?\n삭제된 사진은 복구할 수 없습니다.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteMedia(currentMediaId)
                        showDeleteDialog = false
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
                title = {
                    if (mediaIds.size > 1) {
                        Text("${pagerState.currentPage + 1} / ${mediaIds.size}")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "더보기")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("사진 삭제") },
                                onClick = {
                                    showMenu = false
                                    showDeleteDialog = true
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Delete, contentDescription = null)
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            key = { page -> mediaIds.getOrElse(page) { page.toLong() } },
            pageSpacing = 8.dp,
            beyondBoundsPageCount = 1,
            flingBehavior = PagerDefaults.flingBehavior(
                state = pagerState,
                snapAnimationSpec = spring(stiffness = Spring.StiffnessLow)
            )
        ) { page ->
            val pageMediaId = mediaIds.getOrElse(page) { initialMediaId }
            MediaDetailPageContent(
                mediaId = pageMediaId,
                isCurrentPage = page == pagerState.currentPage,
                isSettling = pagerState.currentPageOffsetFraction != 0f,
                uiState = uiState,
                viewModel = viewModel
            )
        }
    }
}

@Composable
private fun MediaDetailPageContent(
    mediaId: Long,
    isCurrentPage: Boolean,
    isSettling: Boolean,
    uiState: MediaDetailUiState,
    viewModel: MediaViewModel
) {
    Box(modifier = Modifier.fillMaxSize()) {
        val showContent = uiState.media != null && uiState.media.id == mediaId && isCurrentPage

        if (showContent) {
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
                        Button(onClick = { viewModel.loadMedia(mediaId) }) {
                            Text("다시 시도")
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Media viewer
                        item {
                            MediaViewer(
                                fileUrl = uiState.media!!.fileUrl,
                                mediaType = uiState.media!!.type
                            )
                        }

                        // Metadata
                        item {
                            MediaMetadataSection(media = uiState.media!!)
                        }

                        // Memo section
                        item {
                            MemoSection(
                                memo = uiState.media!!.memo?.content,
                                isEditing = uiState.isEditingMemo,
                                isSaving = uiState.isSavingMemo,
                                editContent = uiState.memoContent,
                                onEditClick = { viewModel.setEditingMemo(true) },
                                onCancelClick = { viewModel.setEditingMemo(false) },
                                onContentChange = { viewModel.updateMemoContent(it) },
                                onSaveClick = { viewModel.saveMemo() }
                            )
                        }

                        // Comments header
                        item {
                            Text(
                                text = "댓글 (${uiState.comments.size})",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(16.dp)
                            )
                        }

                        // Comments list
                        items(uiState.comments) { comment ->
                            CommentItem(
                                comment = comment,
                                onDeleteClick = { viewModel.deleteComment(comment.id) }
                            )
                        }

                        // Comment input
                        item {
                            CommentInput(
                                value = uiState.commentInput,
                                onValueChange = { viewModel.updateCommentInput(it) },
                                onSendClick = { viewModel.postComment() }
                            )
                        }

                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        } else {
            // Placeholder during swipe for non-current pages to keep transition smooth
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (isSettling) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
fun MediaViewer(
    fileUrl: String,
    mediaType: MediaType
) {
    var showFullscreen by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable { showFullscreen = true }
    ) {
        if (mediaType == MediaType.IMAGE) {
            AsyncImage(
                model = fileUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            // PlayerView가 터치를 소비하므로 투명 클릭 레이어를 추가해 전체 화면 전환을 보장
            Box(modifier = Modifier.fillMaxSize()) {
                VideoPlayer(videoUrl = fileUrl)

                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { showFullscreen = true }
                )

                Surface(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(56.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                ) {
                    Icon(
                        Icons.Default.Fullscreen,
                        contentDescription = "전체 화면",
                        modifier = Modifier.padding(12.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }

    // Fullscreen viewer
    if (showFullscreen) {
        if (mediaType == MediaType.IMAGE) {
            FullscreenImageViewer(
                imageUrl = fileUrl,
                onDismiss = { showFullscreen = false }
            )
        } else {
            FullscreenVideoPlayer(
                videoUrl = fileUrl,
                onDismiss = { showFullscreen = false }
            )
        }
    }
}

@Composable
fun FullscreenImageViewer(
    imageUrl: String,
    onDismiss: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    BackHandler { onDismiss() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        if (scale > 1f) {
                            offsetX += pan.x
                            offsetY += pan.y
                        } else {
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > 1f) {
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                            } else {
                                scale = 2.5f
                            }
                        },
                        onTap = { onDismiss() }
                    )
                }
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    ),
                contentScale = ContentScale.Fit
            )

            // Close button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .statusBarsPadding()
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "닫기",
                    tint = Color.White
                )
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun FullscreenVideoPlayer(
    videoUrl: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    val originalOrientation = remember {
        activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    var desiredOrientation by remember { mutableIntStateOf(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(videoUrl)
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                // 가로/세로 비율에 따라 화면 회전 방향을 결정
                desiredOrientation = if (videoSize.width >= videoSize.height) {
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                }
                activity?.requestedOrientation = desiredOrientation
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
            activity?.requestedOrientation = originalOrientation
        }
    }

    BackHandler { onDismiss() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = true
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Close button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .statusBarsPadding()
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "닫기",
                    tint = Color.White
                )
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoPlayer(videoUrl: String) {
    val context = LocalContext.current

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(videoUrl)
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false  // 미리보기에서는 컨트롤러 비활성화
                    // 터치 이벤트를 소비하지 않고 부모로 전달
                    setOnTouchListener { _, _ -> false }
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun MediaMetadataSection(media: com.tripmuse.data.model.MediaDetail) {
    Column(
        modifier = Modifier.padding(16.dp)
    ) {
        val formattedTakenAt = remember(media.takenAt) {
            media.takenAt?.let { raw ->
                // Try OffsetDateTime -> LocalDateTime fallbacks, default to replacing 'T'
                val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                runCatching {
                    OffsetDateTime.parse(raw)
                        .withOffsetSameInstant(ZoneOffset.ofHours(9))
                        .toLocalDateTime()
                        .format(formatter)
                }.recoverCatching {
                    LocalDateTime.parse(raw)
                        .atZone(ZoneId.systemDefault())
                        .withZoneSameInstant(ZoneId.of("Asia/Seoul"))
                        .toLocalDateTime()
                        .format(formatter)
                }.getOrElse {
                    raw.replace("T", " ")
                }
            }
        }

        if (formattedTakenAt != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.DateRange,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formattedTakenAt,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Display location if locationName or coordinates available
        val locationText = media.locationName
            ?: if (media.latitude != null && media.longitude != null) {
                "%.4f, %.4f".format(media.latitude, media.longitude)
            } else null

        if (locationText != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = locationText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun MemoSection(
    memo: String?,
    isEditing: Boolean,
    isSaving: Boolean,
    editContent: String,
    onEditClick: () -> Unit,
    onCancelClick: () -> Unit,
    onContentChange: (String) -> Unit,
    onSaveClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "메모",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.weight(1f))
                if (!isEditing && !isSaving) {
                    TextButton(onClick = onEditClick) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (memo.isNullOrEmpty()) "추가" else "편집")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isEditing) {
                OutlinedTextField(
                    value = editContent,
                    onValueChange = onContentChange,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6,
                    enabled = !isSaving,
                    placeholder = { Text("이 사진에 대한 메모를 남겨보세요...") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    ),
                    shape = MaterialTheme.shapes.medium
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onCancelClick,
                        enabled = !isSaving
                    ) {
                        Text("취소")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onSaveClick,
                        enabled = !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("저장 중...")
                        } else {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("저장")
                        }
                    }
                }
            } else {
                if (memo.isNullOrEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onEditClick)
                            .padding(vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "메모를 추가하세요",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        text = memo,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onEditClick)
                            .padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CommentItem(
    comment: Comment,
    onDeleteClick: () -> Unit
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        // User avatar with profile image support
        if (comment.user.profileImageUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(ApiModule.BASE_URL.trimEnd('/') + comment.user.profileImageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "프로필 이미지",
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Surface(
                modifier = Modifier.size(32.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = comment.user.nickname.take(1),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.user.nickname,
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = comment.createdAt.take(10),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = comment.content,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "더보기",
                    modifier = Modifier.size(20.dp)
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
}

@Composable
fun CommentInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSendClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("댓글을 입력하세요...") },
            singleLine = true
        )
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(
            onClick = onSendClick,
            enabled = value.isNotBlank()
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = "전송",
                tint = if (value.isNotBlank())
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

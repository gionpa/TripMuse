package com.tripmuse.ui.chat

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.tripmuse.data.api.ApiModule
import com.tripmuse.data.model.ChatMessage
import com.tripmuse.data.model.ChatRoom
import com.tripmuse.data.presence.ChatUnreadMonitor
import com.tripmuse.data.repository.ChatRepository
import com.tripmuse.ui.theme.TripMuseAccents
import androidx.compose.ui.text.font.FontWeight
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class ChatRoomUiState(
    val isLoading: Boolean = true,
    val room: ChatRoom? = null,
    val messages: List<ChatMessage> = emptyList(), // 오래된 → 최신 순
    val hasMore: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    /** 상대가 읽은 마지막 메시지 ID — 이보다 큰 내 메시지에 안읽음 숫자를 표시한다 */
    val otherLastReadMessageId: Long = 0,
    val otherTyping: Boolean = false
)

@HiltViewModel
class ChatRoomViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val chatUnreadMonitor: ChatUnreadMonitor
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatRoomUiState())
    val uiState: StateFlow<ChatRoomUiState> = _uiState.asStateFlow()

    private var lastTypingSentAt = 0L

    fun enterRoom(roomId: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            chatRepository.getRoom(roomId)
                .onSuccess { room ->
                    _uiState.value = _uiState.value.copy(room = room)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(error = e.message)
                }

            chatRepository.getMessages(roomId)
                .onSuccess { response ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        messages = response.messages,
                        hasMore = response.hasMore,
                        otherLastReadMessageId = response.otherLastReadMessageId,
                        otherTyping = response.otherTyping
                    )
                    markRead(roomId)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                }
        }
    }

    /** 3초 주기 폴링: 새 메시지 + 상대 읽음 위치 + 입력중 상태를 한 번에 받는다 */
    suspend fun pollNewMessages(roomId: Long) {
        if (_uiState.value.isLoading) return
        val lastId = _uiState.value.messages.lastOrNull()?.id ?: 0L
        chatRepository.getMessages(roomId, afterId = lastId)
            .onSuccess { response ->
                _uiState.value = _uiState.value.copy(
                    otherLastReadMessageId = maxOf(
                        response.otherLastReadMessageId,
                        _uiState.value.otherLastReadMessageId
                    ),
                    otherTyping = response.otherTyping
                )
                if (response.messages.isNotEmpty()) {
                    appendMessages(response.messages)
                    markRead(roomId)
                }
            }
        // 폴링 실패는 조용히 넘어가고 다음 주기에 재시도
    }

    /**
     * 입력 중 신호. 타이핑마다 보내지 않고 2초에 한 번으로 제한한다.
     */
    fun onInputChanged(roomId: Long, text: String) {
        if (text.isBlank()) return
        val now = System.currentTimeMillis()
        if (now - lastTypingSentAt < 2_000L) return
        lastTypingSentAt = now
        viewModelScope.launch { chatRepository.markTyping(roomId) }
    }

    fun loadOlderMessages(roomId: Long) {
        val state = _uiState.value
        val firstId = state.messages.firstOrNull()?.id ?: return
        if (state.isLoadingMore || !state.hasMore) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMore = true)
            chatRepository.getMessages(roomId, beforeId = firstId)
                .onSuccess { response ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingMore = false,
                        messages = (response.messages + _uiState.value.messages).distinctBy { it.id },
                        hasMore = response.hasMore
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(isLoadingMore = false)
                }
        }
    }

    fun sendMessage(roomId: Long, content: String) {
        viewModelScope.launch {
            chatRepository.sendMessage(roomId, content)
                .onSuccess { message -> appendMessages(listOf(message)) }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(error = e.message ?: "메시지 전송에 실패했습니다")
                }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun appendMessages(newMessages: List<ChatMessage>) {
        _uiState.value = _uiState.value.copy(
            messages = (_uiState.value.messages + newMessages)
                .distinctBy { it.id }
                .sortedBy { it.id }
        )
    }

    private fun markRead(roomId: Long) {
        viewModelScope.launch {
            chatRepository.markAsRead(roomId)
            // 탭 레드닷을 폴링 주기까지 기다리지 않고 즉시 갱신
            chatUnreadMonitor.refreshNow()
        }
    }
}

private sealed class ChatListEntry {
    data class MessageEntry(val message: ChatMessage) : ChatListEntry()
    data class DateEntry(val label: String) : ChatListEntry()
    object TypingEntry : ChatListEntry()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatRoomScreen(
    roomId: Long,
    onBackClick: () -> Unit,
    viewModel: ChatRoomViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    var inputText by remember { mutableStateOf("") }

    LaunchedEffect(roomId) {
        viewModel.enterRoom(roomId)
    }

    // 화면이 보이는 동안 3초 주기로 새 메시지 폴링
    LaunchedEffect(lifecycleOwner, roomId) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(3000)
                viewModel.pollNewMessages(roomId)
            }
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // 새 메시지 도착/전송, 입력중 표시 등장 시 맨 아래로 스크롤 (reverseLayout이라 index 0이 최신)
    LaunchedEffect(uiState.messages.size, uiState.otherTyping) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    // 위로 스크롤해 목록 끝에 가까워지면 이전 메시지 페이지 로드
    LaunchedEffect(listState, roomId) {
        snapshotFlow {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) to info.totalItemsCount
        }.collect { (lastVisible, total) ->
            if (total > 0 && lastVisible >= total - 3) {
                viewModel.loadOlderMessages(roomId)
            }
        }
    }

    // 날짜 구분선을 끼워 넣은 표시용 목록 (reverseLayout에 맞춰 최신이 앞으로 오게 뒤집는다)
    val entries = remember(uiState.messages, uiState.otherTyping) {
        buildList {
            var lastDate: LocalDate? = null
            uiState.messages.forEach { message ->
                val date = parseServerTime(message.createdAt)?.toLocalDate()
                if (date != null && date != lastDate) {
                    add(ChatListEntry.DateEntry(formatDateSeparator(date)))
                    lastDate = date
                }
                add(ChatListEntry.MessageEntry(message))
            }
            // 뒤집기 전 맨 끝에 두면 화면상 가장 아래(최신 아래)에 보인다
            if (uiState.otherTyping) add(ChatListEntry.TypingEntry)
        }.asReversed()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.room?.otherUser?.nickname ?: "채팅", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = TripMuseAccents.Chat.container
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFFBF5E9)) // 채팅방 배경: 노을빛 종이 톤
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues)
                .imePadding()
        ) {
            Box(modifier = Modifier.weight(1f)) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else {
                    LazyColumn(
                        state = listState,
                        reverseLayout = true,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(
                            items = entries,
                            key = { entry ->
                                when (entry) {
                                    is ChatListEntry.MessageEntry -> "m-${entry.message.id}"
                                    is ChatListEntry.DateEntry -> "d-${entry.label}"
                                    ChatListEntry.TypingEntry -> "typing"
                                }
                            }
                        ) { entry ->
                            when (entry) {
                                is ChatListEntry.MessageEntry -> MessageBubble(
                                    message = entry.message,
                                    otherUserProfileImageUrl = uiState.room?.otherUser?.profileImageUrl,
                                    // 상대가 아직 읽지 않은 내 메시지에 숫자를 붙인다
                                    unreadCount = if (entry.message.isMine &&
                                        entry.message.id > uiState.otherLastReadMessageId
                                    ) 1 else 0
                                )
                                is ChatListEntry.DateEntry -> DateSeparator(entry.label)
                                ChatListEntry.TypingEntry -> TypingIndicator(
                                    nickname = uiState.room?.otherUser?.nickname ?: "상대방",
                                    profileImageUrl = uiState.room?.otherUser?.profileImageUrl
                                )
                            }
                        }

                        if (uiState.isLoadingMore) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                }
            }

            ChatInputBar(
                value = inputText,
                onValueChange = {
                    inputText = it
                    viewModel.onInputChanged(roomId, it)
                },
                onSend = {
                    val text = inputText.trim()
                    if (text.isNotEmpty()) {
                        inputText = ""
                        viewModel.sendMessage(roomId, text)
                    }
                }
            )
        }
    }
}

@Composable
private fun DateSeparator(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.White.copy(alpha = 0.75f)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF8A8377),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    otherUserProfileImageUrl: String? = null,
    unreadCount: Int = 0
) {
    val context = LocalContext.current
    val timeText = formatMessageTime(message.createdAt)

    if (message.isMine) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.Bottom
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(end = 6.dp, bottom = 2.dp)
            ) {
                // 아직 읽지 않은 사람 수 (읽으면 사라진다)
                if (unreadCount > 0) {
                    Text(
                        text = unreadCount.toString(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TripMuseAccents.Chat.accent
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                }
                Text(
                    text = timeText,
                    fontSize = 10.sp,
                    color = Color(0xFFA89F8F)
                )
            }
            Surface(
                shape = RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
                color = TripMuseAccents.Album.accent
            ) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                )
            }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 3.dp)
        ) {
            // 상대방 아바타
            if (otherUserProfileImageUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(ApiModule.BASE_URL.trimEnd('/') + otherUserProfileImageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape,
                    color = TripMuseAccents.Chat.container
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = message.senderNickname.take(1),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = TripMuseAccents.Chat.deep
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Surface(
                    shape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
                    color = Color.White,
                    shadowElevation = 1.dp
                ) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF2E2A24),
                        modifier = Modifier
                            .widthIn(max = 260.dp)
                            .padding(horizontal = 14.dp, vertical = 9.dp)
                    )
                }
                Text(
                    text = timeText,
                    fontSize = 10.sp,
                    color = Color(0xFFA89F8F),
                    modifier = Modifier.padding(start = 6.dp, bottom = 2.dp)
                )
            }
        }
    }
}

/**
 * 상대가 입력 중일 때 말풍선 자리에 표시되는 점 세 개 인디케이터.
 */
@Composable
private fun TypingIndicator(
    nickname: String,
    profileImageUrl: String?
) {
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        if (profileImageUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(ApiModule.BASE_URL.trimEnd('/') + profileImageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = TripMuseAccents.Chat.container
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = nickname.take(1),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = TripMuseAccents.Chat.deep
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Surface(
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
            color = Color.White,
            shadowElevation = 1.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) { index ->
                    val alpha = rememberTypingDotAlpha(index)
                    Surface(
                        modifier = Modifier.size(7.dp),
                        shape = CircleShape,
                        color = TripMuseAccents.Chat.deep.copy(alpha = alpha)
                    ) {}
                    if (index < 2) Spacer(modifier = Modifier.width(4.dp))
                }
            }
        }

        Text(
            text = "입력 중",
            fontSize = 10.sp,
            color = Color(0xFFA89F8F),
            modifier = Modifier.padding(start = 6.dp, bottom = 2.dp)
        )
    }
}

/** 점 세 개가 차례로 밝아지는 애니메이션 */
@Composable
private fun rememberTypingDotAlpha(index: Int): Float {
    val transition = rememberInfiniteTransition(label = "typing")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, delayMillis = index * 200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot$index"
    )
    return alpha
}

@Composable
private fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Surface(color = Color.White, shadowElevation = 8.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.Bottom
        ) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(max = 120.dp),
                placeholder = { Text("메시지 입력", color = Color(0xFFB4AC9E)) },
                maxLines = 4,
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFFF6F1E7),
                    unfocusedContainerColor = Color(0xFFF6F1E7),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            FilledIconButton(
                onClick = onSend,
                enabled = value.isNotBlank(),
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = TripMuseAccents.Chat.accent,
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFFEDE7DB),
                    disabledContentColor = Color(0xFFB4AC9E)
                )
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "전송")
            }
        }
    }
}

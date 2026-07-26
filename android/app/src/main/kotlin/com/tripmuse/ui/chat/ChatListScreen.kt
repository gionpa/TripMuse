package com.tripmuse.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.tripmuse.data.model.ChatRoom
import com.tripmuse.data.presence.ChatUnreadMonitor
import com.tripmuse.data.repository.ChatRepository
import com.tripmuse.ui.theme.TripMuseAccents
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatListUiState(
    val isLoading: Boolean = false,
    val rooms: List<ChatRoom> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val chatUnreadMonitor: ChatUnreadMonitor
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatListUiState())
    val uiState: StateFlow<ChatListUiState> = _uiState.asStateFlow()

    fun loadRooms(showLoading: Boolean = false) {
        viewModelScope.launch {
            if (showLoading) {
                _uiState.value = _uiState.value.copy(isLoading = true)
            }
            chatRepository.getRooms()
                .onSuccess { response ->
                    _uiState.value = _uiState.value.copy(isLoading = false, rooms = response.rooms, error = null)
                    // 목록에 보이는 개수와 탭 배지가 어긋나지 않도록 같은 값을 공유한다
                    chatUnreadMonitor.updateCount(response.rooms.sumOf { it.unreadCount })
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                }
        }
    }

    /** 목록 화면을 보고 있는 동안 새 메시지가 도착하면 바로 반영한다 */
    suspend fun pollRooms() {
        chatRepository.getRooms().onSuccess { response ->
            _uiState.value = _uiState.value.copy(rooms = response.rooms, error = null)
            chatUnreadMonitor.updateCount(response.rooms.sumOf { it.unreadCount })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    onRoomClick: (Long) -> Unit,
    viewModel: ChatListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    // 탭 진입/복귀 시 목록 갱신 + 화면을 보고 있는 동안 주기적으로 새 메시지 반영
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.loadRooms(showLoading = uiState.rooms.isEmpty())
            while (true) {
                delay(10_000)
                viewModel.pollRooms()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("채팅", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = TripMuseAccents.Chat.container
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(TripMuseAccents.Chat.container.copy(alpha = 0.45f), Color.White)
                    )
                )
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading && uiState.rooms.isEmpty() -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                uiState.rooms.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Outlined.ChatBubbleOutline,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "채팅이 없습니다",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "친구 탭에서 채팅을 시작해보세요",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(uiState.rooms, key = { it.roomId }) { room ->
                            ChatRoomItem(room = room, onClick = { onRoomClick(room.roomId) })
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 84.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatRoomItem(
    room: ChatRoom,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar (그룹방은 인원수를 겹쳐 보여준다)
        val profileImageUrl = room.otherUser?.profileImageUrl
        if (profileImageUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(ApiModule.BASE_URL.trimEnd('/') + profileImageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "프로필 이미지",
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = CircleShape,
                color = TripMuseAccents.Chat.container
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (room.isGroup) {
                        Icon(
                            Icons.Default.Groups,
                            contentDescription = null,
                            tint = TripMuseAccents.Chat.deep,
                            modifier = Modifier.size(26.dp)
                        )
                    } else {
                        Text(
                            text = room.displayTitle.take(1),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TripMuseAccents.Chat.deep
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = room.displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (room.isGroup) {
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = room.memberCount.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = formatRoomListTime(room.lastMessageAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = room.lastMessage ?: "대화를 시작해보세요",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (room.unreadCount > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFFF3B30)
                    ) {
                        Text(
                            text = if (room.unreadCount > 99) "99+" else room.unreadCount.toString(),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

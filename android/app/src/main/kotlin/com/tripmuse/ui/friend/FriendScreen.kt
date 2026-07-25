package com.tripmuse.ui.friend

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.tripmuse.data.api.ApiModule
import com.tripmuse.data.model.Friend
import com.tripmuse.data.model.Invitation
import com.tripmuse.data.model.LocationShareStatus
import com.tripmuse.data.model.UserSearchResult
import com.tripmuse.ui.location.FriendLocationDialog
import com.tripmuse.ui.theme.TripMuseAccents

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendScreen(
    onNavigateToChatRoom: (Long) -> Unit = {},
    viewModel: FriendViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    // 위치 권한: 없으면 탭 진입 시 한 번 요청하고, 허용되면 내 위치를 올린다
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.any { it }) viewModel.uploadMyLocation()
    }

    // 탭 복귀 시 위치 공유 상태 등 최신화 (상대방의 요청/승인 반영)
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.loadFriends()
            viewModel.loadInvitations()
            if (viewModel.hasLocationPermission()) {
                viewModel.uploadMyLocation()
            } else {
                locationPermissionLauncher.launch(
                    arrayOf(
                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSuccessMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("친구", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = TripMuseAccents.Friend.container
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(TripMuseAccents.Friend.container.copy(alpha = 0.55f), Color.White)
                    )
                )
                .padding(paddingValues)
        ) {
            // Search bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("이메일 또는 닉네임으로 검색") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearSearch() }) {
                            Icon(Icons.Default.Clear, contentDescription = "검색 초기화")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { focusManager.clearFocus() }
                )
            )

            // Search results or friend list
            if (uiState.searchQuery.length >= 2) {
                // Search results
                if (uiState.isSearching) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (uiState.searchResults.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "검색 결과가 없습니다",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        if (uiState.invitations.isNotEmpty()) {
                            item {
                                InvitationSection(
                                    invitations = uiState.invitations,
                                    onAccept = { id -> viewModel.acceptInvitation(id) },
                                    onReject = { id -> viewModel.rejectInvitation(id) }
                                )
                            }
                        }
                        items(uiState.searchResults) { user ->
                            SearchResultItem(
                                user = user,
                                onInvite = { viewModel.sendInvitation(user.id) }
                            )
                        }
                    }
                }
            } else {
                // Friend list
                if (uiState.isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        // Always show invitations section if there are any
                        if (uiState.invitations.isNotEmpty()) {
                            item {
                                InvitationSection(
                                    invitations = uiState.invitations,
                                    onAccept = { id -> viewModel.acceptInvitation(id) },
                                    onReject = { id -> viewModel.rejectInvitation(id) }
                                )
                            }
                        }

                        if (uiState.friends.isEmpty()) {
                            // Empty state for no friends
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 48.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            Icons.Default.PersonAdd,
                                            contentDescription = null,
                                            modifier = Modifier.size(64.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = "등록된 친구가 없습니다",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "이메일이나 닉네임으로 친구를 검색해보세요",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        } else {
                            // Friend list header and items
                            item {
                                Text(
                                    text = "내 친구 ${uiState.friends.size}명",
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            items(uiState.friends) { friend ->
                                FriendItem(
                                    friend = friend,
                                    onRemoveFriend = { viewModel.removeFriend(friend.id) },
                                    onRequestLocationShare = { viewModel.requestLocationShare(friend.id) },
                                    onApproveLocationShare = { viewModel.approveLocationShare(friend.id) },
                                    onChatClick = { viewModel.openChat(friend.id) { roomId -> onNavigateToChatRoom(roomId) } }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchResultItem(
    user: UserSearchResult,
    onInvite: () -> Unit
) {
    val context = LocalContext.current

    Surface(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            if (user.profileImageUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(ApiModule.BASE_URL.trimEnd('/') + user.profileImageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "프로필 이미지",
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = user.nickname.take(1),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.nickname,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = user.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Add button
            if (user.isFriend) {
                FilledTonalButton(
                    onClick = { },
                    enabled = false
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("친구")
                }
            } else if (user.invitedByMe) {
                FilledTonalButton(
                    onClick = { },
                    enabled = false
                ) {
                    Icon(Icons.Default.Schedule, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("초대 보냄")
                }
            } else if (user.invitedMe && user.invitationId != null) {
                FilledTonalButton(
                    onClick = { },
                    enabled = false
                ) {
                    Icon(Icons.Default.MarkEmailUnread, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("초대 도착")
                }
            } else {
                Button(onClick = onInvite) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("초대")
                }
            }
        }
    }
}

@Composable
fun InvitationSection(
    invitations: List<Invitation>,
    onAccept: (Long) -> Unit,
    onReject: (Long) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = "초대 요청",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        invitations.forEach { invite ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val context = LocalContext.current
                    if (invite.fromProfileImageUrl != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(ApiModule.BASE_URL.trimEnd('/') + invite.fromProfileImageUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Surface(
                            modifier = Modifier.size(44.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = invite.fromNickname.take(1),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(invite.fromNickname, style = MaterialTheme.typography.titleMedium)
                        Text(
                            invite.fromEmail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row {
                        TextButton(onClick = { onReject(invite.invitationId) }) {
                            Text("거절")
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Button(onClick = { onAccept(invite.invitationId) }) {
                            Text("수락")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FriendItem(
    friend: Friend,
    onRemoveFriend: () -> Unit,
    onRequestLocationShare: () -> Unit = {},
    onApproveLocationShare: () -> Unit = {},
    onChatClick: () -> Unit = {}
) {
    val context = LocalContext.current
    var showRemoveDialog by remember { mutableStateOf(false) }
    var showLocationDialog by remember { mutableStateOf(false) }

    // 현재 위치보기: 국내는 네이버 지도, 해외는 구글 지도
    if (showLocationDialog) {
        FriendLocationDialog(
            friendId = friend.id,
            friendNickname = friend.nickname,
            onDismiss = { showLocationDialog = false }
        )
    }

    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title = { Text("친구 삭제") },
            text = { Text("${friend.nickname}님을 친구 목록에서 삭제하시겠습니까?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemoveFriend()
                        showRemoveDialog = false
                    }
                ) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDialog = false }) {
                    Text("취소")
                }
            }
        )
    }

    val cardShape = RoundedCornerShape(20.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 7.dp)
            .shadow(elevation = 3.dp, shape = cardShape, ambientColor = TripMuseAccents.Friend.accent.copy(alpha = 0.25f)),
        shape = cardShape,
        color = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 10.dp, top = 14.dp, bottom = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar
                if (friend.profileImageUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(ApiModule.BASE_URL.trimEnd('/') + friend.profileImageUrl)
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
                        color = TripMuseAccents.Friend.container
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = friend.nickname.take(1),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TripMuseAccents.Friend.deep
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                // Info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = friend.nickname,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = friend.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 삭제: 파괴적 액션은 조용한 아이콘으로 (확인 다이얼로그에서 명확히)
                IconButton(onClick = { showRemoveDialog = true }) {
                    Icon(
                        Icons.Default.PersonRemove,
                        contentDescription = "친구 삭제",
                        modifier = Modifier.size(20.dp),
                        tint = Color(0xFFB6BEC9)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 액션 버튼: 동일한 형태(pill), 색으로만 의미 구분
            // 위치 계열 = 친구 탭 청록, 채팅 = 채팅 탭 앰버 (하단 탭 팔레트와 연결)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (friend.locationShareStatus ?: LocationShareStatus.NONE) {
                    LocationShareStatus.REQUESTED_BY_ME -> FriendActionButton(
                        text = "승인 대기중",
                        icon = Icons.Default.Schedule,
                        container = Color(0xFFF1F3F6),
                        content = Color(0xFF9AA3AF),
                        enabled = false,
                        modifier = Modifier.weight(1f),
                        onClick = { }
                    )
                    LocationShareStatus.PENDING_MY_APPROVAL -> FriendActionButton(
                        text = "위치 공유 승인",
                        icon = Icons.Default.Check,
                        container = TripMuseAccents.Friend.accent,
                        content = Color.White,
                        modifier = Modifier.weight(1f),
                        onClick = onApproveLocationShare
                    )
                    LocationShareStatus.APPROVED -> FriendActionButton(
                        text = "현재 위치보기",
                        icon = Icons.Default.Map,
                        container = TripMuseAccents.Friend.accent,
                        content = Color.White,
                        modifier = Modifier.weight(1f),
                        onClick = { showLocationDialog = true }
                    )
                    else -> FriendActionButton(
                        text = "위치 공유 요청",
                        icon = Icons.Default.LocationOn,
                        container = TripMuseAccents.Friend.container,
                        content = TripMuseAccents.Friend.deep,
                        modifier = Modifier.weight(1f),
                        onClick = onRequestLocationShare
                    )
                }

                FriendActionButton(
                    text = "채팅",
                    icon = Icons.Default.ChatBubble,
                    container = TripMuseAccents.Chat.container,
                    content = TripMuseAccents.Chat.deep,
                    modifier = Modifier.weight(1f),
                    onClick = onChatClick
                )
            }
        }
    }
}

/**
 * 친구 카드 공용 액션 버튼 — 형태/높이/타이포는 고정, 색만 의미에 따라 달라진다.
 */
@Composable
private fun FriendActionButton(
    text: String,
    icon: ImageVector,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(42.dp),
        shape = RoundedCornerShape(21.dp),
        color = container,
        contentColor = content
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

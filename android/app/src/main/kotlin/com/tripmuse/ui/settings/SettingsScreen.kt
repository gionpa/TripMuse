package com.tripmuse.ui.settings

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.tripmuse.data.sound.ChatAlertMode
import com.tripmuse.data.sound.ChatSound
import com.tripmuse.ui.theme.TripMuseAccents

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

    // 벨소리/진동 전환은 앱 밖에서 일어나므로 화면에 돌아올 때마다 다시 읽는다
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refreshAlertMode()
        }
    }

    // 이 화면을 보는 중에 볼륨 키로 모드를 바꿀 수도 있다. 그때도 안내가 따라가야 한다.
    val context = LocalContext.current
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) = viewModel.refreshAlertMode()
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose { context.unregisterReceiver(receiver) }
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
                maxBytes = uiState.storageUsage?.maxBytes ?: (1024 * 1024 * 1024),
                isLoading = uiState.isLoading
            )

            NotificationSection(
                friendOnlineAlertEnabled = uiState.friendOnlineAlertEnabled,
                onFriendOnlineAlertChange = { viewModel.setFriendOnlineAlertEnabled(it) },
                chatSoundEnabled = uiState.chatSoundEnabled,
                onChatSoundEnabledChange = { viewModel.setChatSoundEnabled(it) },
                selectedSound = uiState.chatSound,
                onSelectSound = { viewModel.selectChatSound(it) },
                alertMode = uiState.alertMode
            )

            AboutSection(
                versionName = uiState.versionName,
                versionCode = uiState.versionCode
            )
        }
    }
}

@Composable
private fun NotificationSection(
    friendOnlineAlertEnabled: Boolean,
    onFriendOnlineAlertChange: (Boolean) -> Unit,
    chatSoundEnabled: Boolean,
    onChatSoundEnabledChange: (Boolean) -> Unit,
    selectedSound: ChatSound,
    onSelectSound: (ChatSound) -> Unit,
    alertMode: ChatAlertMode
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "알림",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "친구 접속 알림",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "친구가 TripMuse에 접속하면 알려줍니다",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = friendOnlineAlertEnabled,
                    onCheckedChange = onFriendOnlineAlertChange
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "채팅 수신음",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "새 메시지가 오면 소리나 진동으로 알려줍니다",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = chatSoundEnabled,
                    onCheckedChange = onChatSoundEnabledChange
                )
            }

            // 지금 수신 모드에서 실제로 어떻게 울리는지 미리 알려준다
            val modeNotice = when (alertMode) {
                ChatAlertMode.SOUND -> null
                ChatAlertMode.VIBRATE -> "휴대폰이 진동 모드예요. 소리 대신 짧게 진동으로 알려드려요."
                ChatAlertMode.NONE -> "휴대폰이 무음 모드예요. 소리도 진동도 울리지 않습니다."
            }
            modeNotice?.let { notice ->
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = notice,
                    style = MaterialTheme.typography.bodySmall,
                    color = TripMuseAccents.Chat.deep
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                // 꺼져 있어도 미리듣기는 되지만, 지금은 울리지 않는다는 걸 흐리게 표시한다
                modifier = Modifier.alpha(if (chatSoundEnabled) 1f else 0.45f)
            ) {
                ChatSound.entries.forEach { sound ->
                    ChatSoundRow(
                        sound = sound,
                        selected = sound == selectedSound,
                        onClick = { onSelectSound(sound) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutSection(
    versionName: String,
    versionCode: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "앱 정보",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "버전",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    // 문의를 받을 때 빌드까지 특정할 수 있게 versionCode도 같이 보여준다
                    text = "$versionName ($versionCode)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ChatSoundRow(
    sound: ChatSound,
    selected: Boolean,
    onClick: () -> Unit
) {
    val accent = TripMuseAccents.Chat
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) accent.container else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = if (selected) accent.deep else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sound.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) accent.deep else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = sound.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "선택됨",
                    tint = accent.deep,
                    modifier = Modifier.size(20.dp)
                )
            }
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

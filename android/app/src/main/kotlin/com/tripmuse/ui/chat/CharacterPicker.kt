package com.tripmuse.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.tripmuse.ui.theme.TripMuseAccents

/** 캐릭터 터치 시 뜨는 컨텍스트 메뉴 내용 (캐릭터 변경 / 감정 표현) */
@Composable
fun CharacterContextMenu(
    onCharacterChange: () -> Unit,
    onEmotionPick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.width(180.dp).padding(vertical = 6.dp)) {
            MenuRow(Icons.Default.Face, "캐릭터 변경", onCharacterChange)
            MenuRow(Icons.Default.Mood, "감정 표현", onEmotionPick)
        }
    }
}

@Composable
private fun MenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = TripMuseAccents.Chat.deep, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** 감정 선택 메뉴 (컨텍스트 메뉴에서 "감정 표현"을 고르면) */
@Composable
fun EmotionMenu(onPick: (Emotion) -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.width(160.dp).padding(vertical = 6.dp)) {
            Emotion.entries.forEach { emo ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onPick(emo) }.padding(horizontal = 16.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(emo.emoji, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(12.dp))
                    Text(emo.label, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

/** 8종 캐릭터 선택 모달 */
@Composable
fun CharacterPickerDialog(
    currentKey: String?,
    onPick: (CharacterStyle) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("캐릭터 선택", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "마음에 드는 캐릭터를 골라보세요",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(2.dp)
                ) {
                    items(CHARACTER_STYLES, key = { it.key }) { style ->
                        val selected = style.key == currentKey
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) TripMuseAccents.Chat.container else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                .then(
                                    if (selected) Modifier.border(2.dp, TripMuseAccents.Chat.accent, RoundedCornerShape(12.dp)) else Modifier
                                )
                                .clickable { onPick(style) }
                                .padding(vertical = 8.dp, horizontal = 4.dp)
                        ) {
                            CharacterPreview(style, Modifier.size(52.dp))
                            Spacer(Modifier.height(4.dp))
                            Text(
                                style.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) TripMuseAccents.Chat.deep else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("닫기")
                }
            }
        }
    }
}

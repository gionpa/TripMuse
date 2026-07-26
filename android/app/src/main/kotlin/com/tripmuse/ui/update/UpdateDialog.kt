package com.tripmuse.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.tripmuse.data.model.AppVersionInfo

/**
 * 새 버전 안내.
 *
 * 강제 업데이트일 때는 닫을 방법을 주지 않는다 — 뒤로가기나 바깥 탭으로 빠져나가면
 * 서버와 맞지 않는 앱을 계속 쓰게 된다.
 */
@Composable
fun UpdateDialog(
    info: AppVersionInfo,
    currentVersionName: String,
    onUpdate: () -> Unit,
    onLater: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!info.updateRequired) onLater() },
        properties = DialogProperties(
            dismissOnBackPress = !info.updateRequired,
            dismissOnClickOutside = !info.updateRequired
        ),
        icon = {
            Icon(
                imageVector = Icons.Default.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(if (info.updateRequired) "업데이트가 필요해요" else "새 버전이 나왔어요")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (info.updateRequired) {
                        "지금 쓰고 있는 ${currentVersionName} 버전은 더 이상 지원되지 않습니다. " +
                            "${info.latestVersionName} 버전으로 업데이트해야 계속 사용할 수 있습니다."
                    } else {
                        "${currentVersionName} → ${info.latestVersionName}"
                    }
                )
                info.releaseNotes?.takeIf { it.isNotBlank() }?.let { notes ->
                    Text(
                        text = notes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onUpdate) { Text("업데이트") }
        },
        dismissButton = if (info.updateRequired) null else {
            { TextButton(onClick = onLater) { Text("나중에") } }
        }
    )
}

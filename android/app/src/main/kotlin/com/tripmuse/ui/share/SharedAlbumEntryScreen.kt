package com.tripmuse.ui.share

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripmuse.data.deeplink.DeepLinkManager
import com.tripmuse.data.repository.AlbumRepository
import com.tripmuse.data.repository.ShareLinkException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SharedAlbumViewModel @Inject constructor(
    private val albumRepository: AlbumRepository,
    private val deepLinkManager: DeepLinkManager
) : ViewModel() {

    fun resolve(
        token: String,
        onResolved: (Long) -> Unit,
        onInvalidLink: () -> Unit
    ) {
        // 미로그인(401) 시 전역 AuthEvent로 로그인 화면으로 이동하므로,
        // 토큰을 보관해 두었다가 로그인 성공 후 이어서 처리한다
        deepLinkManager.pendingShareToken = token

        viewModelScope.launch {
            albumRepository.resolveShareLink(token)
                .onSuccess {
                    deepLinkManager.pendingShareToken = null
                    onResolved(it.albumId)
                }
                .onFailure { e ->
                    val isUnauthorized = e is ShareLinkException && e.statusCode == 401
                    if (!isUnauthorized) {
                        deepLinkManager.pendingShareToken = null
                        onInvalidLink()
                    }
                    // 401이면 전역 Unauthorized 이벤트가 로그인 화면으로 보내준다
                }
        }
    }
}

/**
 * 공유 딥링크 진입 화면: 토큰을 리졸브해 앨범 상세로 이동한다.
 */
@Composable
fun SharedAlbumEntryScreen(
    token: String,
    onResolved: (Long) -> Unit,
    onInvalidLink: () -> Unit,
    viewModel: SharedAlbumViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    LaunchedEffect(token) {
        viewModel.resolve(
            token = token,
            onResolved = onResolved,
            onInvalidLink = {
                Toast.makeText(context, "유효하지 않거나 만료된 공유 링크입니다", Toast.LENGTH_LONG).show()
                onInvalidLink()
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
    }
}

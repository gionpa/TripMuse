package com.tripmuse.ui.location

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.tripmuse.BuildConfig
import com.tripmuse.data.model.FriendLocation
import com.tripmuse.data.model.MapRegion
import com.tripmuse.data.repository.LocationRepository
import com.tripmuse.ui.chat.formatRoomListTime
import com.tripmuse.ui.theme.TripMuseAccents
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FriendLocationUiState(
    val isLoading: Boolean = true,
    val location: FriendLocation? = null,
    val error: String? = null
)

@HiltViewModel
class FriendLocationViewModel @Inject constructor(
    private val locationRepository: LocationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FriendLocationUiState())
    val uiState: StateFlow<FriendLocationUiState> = _uiState.asStateFlow()

    fun load(friendId: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            locationRepository.getFriendLocation(friendId)
                .onSuccess { _uiState.value = FriendLocationUiState(isLoading = false, location = it) }
                .onFailure { _uiState.value = FriendLocationUiState(isLoading = false, error = it.message) }
        }
    }
}

/**
 * 친구의 현재 위치를 지도에 표시한다.
 * 국내 좌표는 네이버 지도, 해외 좌표는 구글 지도를 사용한다.
 */
@Composable
fun FriendLocationDialog(
    friendId: Long,
    friendNickname: String,
    onDismiss: () -> Unit,
    viewModel: FriendLocationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(friendId) {
        viewModel.load(friendId)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.78f),
            shape = RoundedCornerShape(24.dp),
            color = Color.White
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TripMuseAccents.Friend.container)
                        .padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${friendNickname}님의 현재 위치",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TripMuseAccents.Friend.deep
                        )
                        val subtitle = uiState.location
                            ?.takeIf { it.hasLocation }
                            ?.let { loc ->
                                val provider = if (MapRegion.isDomestic(loc.latitude!!, loc.longitude!!)) {
                                    "네이버 지도"
                                } else {
                                    "구글 지도"
                                }
                                val time = formatRoomListTime(loc.recordedAt)
                                if (time.isNotEmpty()) "$provider · $time 기준" else provider
                            }
                        if (subtitle != null) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = TripMuseAccents.Friend.deep.copy(alpha = 0.7f)
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "닫기",
                            tint = TripMuseAccents.Friend.deep
                        )
                    }
                }

                // Map / states
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    val location = uiState.location
                    when {
                        uiState.isLoading -> {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.Center),
                                color = TripMuseAccents.Friend.accent
                            )
                        }
                        uiState.error != null -> {
                            EmptyLocationState(
                                title = uiState.error ?: "위치를 불러올 수 없습니다",
                                description = "잠시 후 다시 시도해주세요.",
                                onRetry = { viewModel.load(friendId) }
                            )
                        }
                        location == null || !location.hasLocation -> {
                            EmptyLocationState(
                                title = "아직 공유된 위치가 없습니다",
                                description = "${friendNickname}님이 앱을 열면 위치가 표시됩니다.",
                                onRetry = { viewModel.load(friendId) }
                            )
                        }
                        else -> {
                            val lat = location.latitude!!
                            val lon = location.longitude!!
                            if (MapRegion.isDomestic(lat, lon)) {
                                if (BuildConfig.NAVER_MAPS_CLIENT_ID.isBlank()) {
                                    MissingKeyState(
                                        provider = "네이버 지도",
                                        key = "naver.maps.clientId",
                                        latitude = lat,
                                        longitude = lon
                                    )
                                } else {
                                    NaverLocationMap(
                                        latitude = lat,
                                        longitude = lon,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            } else {
                                if (BuildConfig.GOOGLE_MAPS_API_KEY.isBlank()) {
                                    MissingKeyState(
                                        provider = "구글 지도",
                                        key = "google.maps.apiKey",
                                        latitude = lat,
                                        longitude = lon
                                    )
                                } else {
                                    GoogleLocationMap(
                                        latitude = lat,
                                        longitude = lon,
                                        title = friendNickname,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }
                }

                // Footer: coordinates + refresh
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val coords = uiState.location?.takeIf { it.hasLocation }?.let {
                        String.format("%.5f, %.5f", it.latitude, it.longitude)
                    } ?: "위치 정보 없음"
                    Text(
                        text = coords,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { viewModel.load(friendId) }) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("새로고침")
                    }
                }
            }
        }
    }
}

@Composable
private fun GoogleLocationMap(
    latitude: Double,
    longitude: Double,
    title: String,
    modifier: Modifier = Modifier
) {
    val target = LatLng(latitude, longitude)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(target, 14f)
    }
    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        properties = MapProperties(isMyLocationEnabled = false),
        uiSettings = MapUiSettings(zoomControlsEnabled = true, myLocationButtonEnabled = false)
    ) {
        Marker(state = MarkerState(position = target), title = title)
    }
}

@Composable
private fun BoxScope.EmptyLocationState(
    title: String,
    description: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .align(Alignment.Center)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.LocationOff,
            contentDescription = null,
            modifier = Modifier.size(44.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        FilledTonalButton(onClick = onRetry) {
            Text("다시 시도")
        }
    }
}

@Composable
private fun BoxScope.MissingKeyState(
    provider: String,
    key: String,
    latitude: Double,
    longitude: Double
) {
    Column(
        modifier = Modifier
            .align(Alignment.Center)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "$provider 키가 설정되지 않았습니다",
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "local.properties에 $key 를 추가하면 지도가 표시됩니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                text = String.format("위도 %.5f\n경도 %.5f", latitude, longitude),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
        }
    }
}

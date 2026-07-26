package com.tripmuse.ui.location

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.CameraPosition
import com.naver.maps.map.MapView
import com.naver.maps.map.NaverMap
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.overlay.OverlayImage
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 국내 좌표용 네이버 지도. MapView는 Compose 밖의 View라 생명주기를 직접 연결한다.
 *
 * 주의: 이 컴포저블이 화면에서 빠지면 MapView가 파괴된다. 새로고침 같은 상태 변화 때
 * 지도를 언마운트하지 않도록 호출부에서 지도를 계속 붙여 두어야 한다.
 */
@Composable
fun NaverLocationMap(
    latitude: Double,
    longitude: Double,
    modifier: Modifier = Modifier,
    zoom: Double = 15.0
) {
    val context = LocalContext.current

    // getMapAsync보다 onCreate가 먼저 끝나야 하므로 생성 시점에 함께 호출한다
    val mapView = remember { MapView(context).apply { onCreate(Bundle()) } }
    // 마커는 하나만 두고 위치만 옮긴다 (recomposition마다 새로 만들면 마커가 쌓인다)
    val marker = remember { Marker() }

    DisposableEffect(mapView) {
        mapView.onStart()
        mapView.onResume()
        onDispose {
            marker.map = null
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    // 좌표가 실제로 바뀔 때만 카메라와 마커를 갱신한다
    LaunchedEffect(mapView, latitude, longitude, zoom) {
        val naverMap = mapView.awaitMap()
        val target = LatLng(latitude, longitude)
        naverMap.cameraPosition = CameraPosition(target, zoom)
        marker.position = target
        marker.icon = OverlayImage.fromResource(com.naver.maps.map.R.drawable.navermap_default_marker_icon_red)
        marker.map = naverMap
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

private suspend fun MapView.awaitMap(): NaverMap = suspendCancellableCoroutine { continuation ->
    getMapAsync { naverMap ->
        if (continuation.isActive) continuation.resume(naverMap)
    }
}

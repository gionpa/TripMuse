package com.tripmuse.ui.location

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.CameraPosition
import com.naver.maps.map.MapView
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.overlay.OverlayImage

/**
 * 국내 좌표용 네이버 지도. MapView는 Compose 밖의 View라 생명주기를 직접 연결한다.
 */
@Composable
fun NaverLocationMap(
    latitude: Double,
    longitude: Double,
    modifier: Modifier = Modifier,
    zoom: Double = 15.0
) {
    val context = LocalContext.current
    val mapView = remember { MapView(context) }

    DisposableEffect(mapView) {
        mapView.onCreate(Bundle())
        mapView.onStart()
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { view ->
            view.getMapAsync { naverMap ->
                val target = LatLng(latitude, longitude)
                naverMap.cameraPosition = CameraPosition(target, zoom)
                Marker().apply {
                    position = target
                    icon = OverlayImage.fromResource(com.naver.maps.map.R.drawable.navermap_default_marker_icon_red)
                    map = naverMap
                }
            }
        }
    )
}

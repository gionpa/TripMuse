package com.tripmuse.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import android.location.Location as AndroidLocation
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.tripmuse.data.api.TripMuseApi
import com.tripmuse.data.model.FriendLocation
import com.tripmuse.data.model.UpdateLocationRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class LocationRepository @Inject constructor(
    private val api: TripMuseApi,
    @ApplicationContext private val context: Context
) {

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

    /**
     * 기기의 현재 위치를 읽어 서버에 올린다. 권한이 없거나 위치를 얻지 못하면 조용히 실패한다.
     */
    @SuppressLint("MissingPermission")
    suspend fun uploadMyLocation(): Result<Unit> {
        if (!hasLocationPermission()) {
            return Result.failure(Exception("위치 권한이 없습니다"))
        }

        val location = currentLocation()
            ?: return Result.failure(Exception("현재 위치를 확인할 수 없습니다"))

        return try {
            val response = api.updateMyLocation(
                UpdateLocationRequest(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracy = location.accuracy.takeIf { it > 0f }
                )
            )
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("위치 업로드에 실패했습니다 (${response.code()})"))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(Exception("네트워크 오류: ${e.message}"))
        }
    }

    /**
     * 위치 획득: 새 fix 요청 → 캐시된 fix → 시스템이 마지막으로 알던 위치 순으로 시도한다.
     * 실내/기기 상태에 따라 앞 단계가 비는 경우가 흔해 폴백이 필요하다.
     */
    @SuppressLint("MissingPermission")
    private suspend fun currentLocation(): AndroidLocation? {
        val fused = LocationServices.getFusedLocationProviderClient(context)

        suspendCancellableCoroutine<AndroidLocation?> { continuation ->
            val request = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setDurationMillis(8_000)
                .setMaxUpdateAgeMillis(5 * 60_000)
                .build()
            fused.getCurrentLocation(request, null)
                .addOnSuccessListener { result -> continuation.resume(result) }
                .addOnFailureListener { continuation.resume(null) }
        }?.let { return it }

        suspendCancellableCoroutine<AndroidLocation?> { continuation ->
            fused.lastLocation
                .addOnSuccessListener { result -> continuation.resume(result) }
                .addOnFailureListener { continuation.resume(null) }
        }?.let { return it }

        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null
        return listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        ).asSequence()
            .mapNotNull { provider ->
                runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            }
            .maxByOrNull { it.time }
    }

    suspend fun getFriendLocation(friendId: Long): Result<FriendLocation> {
        return try {
            val response = api.getFriendLocation(friendId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val message = when (response.code()) {
                    403 -> "위치 공유가 승인되지 않은 친구입니다"
                    404 -> "친구를 찾을 수 없습니다"
                    else -> "위치를 불러올 수 없습니다"
                }
                Result.failure(Exception(message))
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(Exception("네트워크 오류: ${e.message}"))
        }
    }
}

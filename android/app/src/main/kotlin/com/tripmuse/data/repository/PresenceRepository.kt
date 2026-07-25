package com.tripmuse.data.repository

import com.tripmuse.data.api.TripMuseApi
import com.tripmuse.data.model.FriendPresence
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PresenceRepository @Inject constructor(
    private val api: TripMuseApi
) {

    suspend fun sendHeartbeat(): Result<Unit> {
        return try {
            val response = api.sendHeartbeat()
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("heartbeat 실패 (${response.code()})"))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getFriendPresences(): Result<List<FriendPresence>> {
        return try {
            val response = api.getFriendPresences()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.presences)
            } else {
                Result.failure(Exception("접속 상태를 불러올 수 없습니다"))
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

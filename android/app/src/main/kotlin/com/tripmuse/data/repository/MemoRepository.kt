package com.tripmuse.data.repository

import com.tripmuse.data.api.TripMuseApi
import com.tripmuse.data.model.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoRepository @Inject constructor(
    private val api: TripMuseApi
) {
    private val currentUserId: Long = 1L

    suspend fun getMemo(mediaId: Long): Result<Memo?> {
        return try {
            val response = api.getMemo(currentUserId, mediaId)
            if (response.isSuccessful) {
                Result.success(response.body())
            } else {
                Result.failure(Exception("Failed to fetch memo: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateMemo(mediaId: Long, content: String): Result<Memo> {
        return try {
            val response = api.updateMemo(currentUserId, mediaId, UpdateMemoRequest(content))
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to update memo: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteMemo(mediaId: Long): Result<Unit> {
        return try {
            val response = api.deleteMemo(currentUserId, mediaId)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to delete memo: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

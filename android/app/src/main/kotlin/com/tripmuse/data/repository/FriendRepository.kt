package com.tripmuse.data.repository

import com.tripmuse.data.api.TripMuseApi
import com.tripmuse.data.model.AddFriendRequest
import com.tripmuse.data.model.Friend
import com.tripmuse.data.model.FriendListResponse
import com.tripmuse.data.model.UserSearchListResponse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FriendRepository @Inject constructor(
    private val api: TripMuseApi
) {

    suspend fun getFriends(): Result<FriendListResponse> {
        return try {
            val response = api.getFriends()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("친구 목록을 불러올 수 없습니다"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("네트워크 오류: ${e.message}"))
        }
    }

    suspend fun searchUsers(query: String): Result<UserSearchListResponse> {
        return try {
            val response = api.searchUsers(query)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("사용자 검색에 실패했습니다"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("네트워크 오류: ${e.message}"))
        }
    }

    suspend fun addFriend(friendId: Long): Result<Friend> {
        return try {
            val response = api.addFriend(AddFriendRequest(friendId))
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorMessage = when (response.code()) {
                    400 -> "자기 자신을 친구로 추가할 수 없습니다"
                    404 -> "사용자를 찾을 수 없습니다"
                    409 -> "이미 친구로 등록된 사용자입니다"
                    else -> "친구 추가에 실패했습니다"
                }
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Result.failure(Exception("네트워크 오류: ${e.message}"))
        }
    }

    suspend fun removeFriend(friendId: Long): Result<Unit> {
        return try {
            val response = api.removeFriend(friendId)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("친구 삭제에 실패했습니다"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("네트워크 오류: ${e.message}"))
        }
    }
}

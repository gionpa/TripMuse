package com.tripmuse.data.repository

import com.tripmuse.data.api.TripMuseApi
import com.tripmuse.data.model.AddFriendRequest
import com.tripmuse.data.model.Friend
import com.tripmuse.data.model.FriendListResponse
import com.tripmuse.data.model.InvitationListResponse
import com.tripmuse.data.model.LocationShareStatusResponse
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

    suspend fun requestLocationShare(friendId: Long): Result<LocationShareStatusResponse> {
        return try {
            val response = api.requestLocationShare(friendId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val message = when (response.code()) {
                    403 -> "친구 관계가 아닌 사용자입니다"
                    409 -> "이미 위치 공유가 요청되었거나 승인된 상태입니다"
                    else -> "위치 공유 요청에 실패했습니다"
                }
                Result.failure(Exception(message))
            }
        } catch (e: Exception) {
            Result.failure(Exception("네트워크 오류: ${e.message}"))
        }
    }

    suspend fun approveLocationShare(friendId: Long): Result<LocationShareStatusResponse> {
        return try {
            val response = api.approveLocationShare(friendId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val message = when (response.code()) {
                    404 -> "승인할 위치 공유 요청이 없습니다"
                    else -> "위치 공유 승인에 실패했습니다"
                }
                Result.failure(Exception(message))
            }
        } catch (e: Exception) {
            Result.failure(Exception("네트워크 오류: ${e.message}"))
        }
    }

    suspend fun getInvitations(): Result<InvitationListResponse> {
        return try {
            val response = api.getInvitations()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("초대 목록을 불러올 수 없습니다"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("네트워크 오류: ${e.message}"))
        }
    }

    suspend fun acceptInvitation(invitationId: Long): Result<Unit> {
        return try {
            val response = api.acceptInvitation(invitationId)
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("초대를 수락할 수 없습니다"))
        } catch (e: Exception) {
            Result.failure(Exception("네트워크 오류: ${e.message}"))
        }
    }

    suspend fun rejectInvitation(invitationId: Long): Result<Unit> {
        return try {
            val response = api.rejectInvitation(invitationId)
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("초대를 거절할 수 없습니다"))
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

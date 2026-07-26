package com.tripmuse.data.repository

import com.tripmuse.data.api.TripMuseApi
import com.tripmuse.data.model.ChatMessage
import com.tripmuse.data.model.ChatMessageListResponse
import com.tripmuse.data.model.ChatRoom
import com.tripmuse.data.model.ChatRoomListResponse
import com.tripmuse.data.model.CreateChatRoomRequest
import com.tripmuse.data.model.SendMessageRequest
import okhttp3.MultipartBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val api: TripMuseApi
) {

    suspend fun getOrCreateRoom(friendId: Long): Result<ChatRoom> {
        return try {
            val response = api.getOrCreateChatRoom(CreateChatRoomRequest(friendId))
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("채팅방을 열 수 없습니다 (${response.code()})"))
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(Exception("네트워크 오류: ${e.message}"))
        }
    }

    suspend fun getRooms(): Result<ChatRoomListResponse> {
        return try {
            val response = api.getChatRooms()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("채팅 목록을 불러올 수 없습니다"))
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(Exception("네트워크 오류: ${e.message}"))
        }
    }

    suspend fun getRoom(roomId: Long): Result<ChatRoom> {
        return try {
            val response = api.getChatRoom(roomId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("채팅방 정보를 불러올 수 없습니다"))
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(Exception("네트워크 오류: ${e.message}"))
        }
    }

    suspend fun getMessages(roomId: Long, beforeId: Long? = null, afterId: Long? = null): Result<ChatMessageListResponse> {
        return try {
            val response = api.getChatMessages(roomId, beforeId, afterId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("메시지를 불러올 수 없습니다"))
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(Exception("네트워크 오류: ${e.message}"))
        }
    }

    suspend fun sendMessage(roomId: Long, content: String): Result<ChatMessage> {
        return try {
            val response = api.sendChatMessage(roomId, SendMessageRequest(content))
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("메시지 전송에 실패했습니다"))
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(Exception("네트워크 오류: ${e.message}"))
        }
    }

    suspend fun sendImage(roomId: Long, part: MultipartBody.Part): Result<ChatMessage> {
        return try {
            val response = api.sendChatImage(roomId, part)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val message = when (response.code()) {
                    400 -> "이미지 파일만 전송할 수 있습니다"
                    413 -> "사진 용량이 너무 큽니다"
                    else -> "사진 전송에 실패했습니다"
                }
                Result.failure(Exception(message))
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(Exception("네트워크 오류: ${e.message}"))
        }
    }

    suspend fun markTyping(roomId: Long): Result<Unit> {
        return try {
            val response = api.markChatTyping(roomId)
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("입력 상태 전송 실패"))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTotalUnreadCount(): Result<Long> {
        return try {
            val response = api.getChatUnreadCount()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.totalUnread)
            } else {
                Result.failure(Exception("안읽음 수를 불러올 수 없습니다"))
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markAsRead(roomId: Long): Result<Unit> {
        return try {
            val response = api.markChatAsRead(roomId)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("읽음 처리에 실패했습니다"))
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(Exception("네트워크 오류: ${e.message}"))
        }
    }
}

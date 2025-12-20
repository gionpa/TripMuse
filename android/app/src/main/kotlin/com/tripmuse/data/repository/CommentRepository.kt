package com.tripmuse.data.repository

import com.tripmuse.data.api.TripMuseApi
import com.tripmuse.data.model.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommentRepository @Inject constructor(
    private val api: TripMuseApi
) {

    suspend fun getComments(mediaId: Long): Result<List<Comment>> {
        return try {
            val response = api.getComments(mediaId)
            if (response.isSuccessful) {
                Result.success(response.body()?.comments ?: emptyList())
            } else {
                Result.failure(Exception("Failed to fetch comments: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createComment(mediaId: Long, content: String): Result<Comment> {
        return try {
            val response = api.createComment(mediaId, CreateCommentRequest(content))
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to create comment: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateComment(commentId: Long, content: String): Result<Comment> {
        return try {
            val response = api.updateComment(commentId, UpdateCommentRequest(content))
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to update comment: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteComment(commentId: Long): Result<Unit> {
        return try {
            val response = api.deleteComment(commentId)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to delete comment: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markCommentsAsRead(mediaId: Long): Result<Unit> {
        return try {
            val response = api.markCommentsAsRead(mediaId)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to mark comments as read: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

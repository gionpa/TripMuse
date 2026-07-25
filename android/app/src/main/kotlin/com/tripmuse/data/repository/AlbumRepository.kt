package com.tripmuse.data.repository

import com.tripmuse.data.api.TripMuseApi
import com.tripmuse.data.model.*
import javax.inject.Inject
import javax.inject.Singleton

/** 공유 링크 리졸브 실패 (statusCode로 401 등을 구분) */
class ShareLinkException(val statusCode: Int) : Exception("Failed to resolve share link: $statusCode")

@Singleton
class AlbumRepository @Inject constructor(
    private val api: TripMuseApi,
    private val serverBaseUrl: String
) {

    suspend fun getAlbums(): Result<List<Album>> {
        return try {
            val response = api.getAlbums()
            if (response.isSuccessful) {
                val albums = response.body()?.albums?.map { it.withFullUrls(serverBaseUrl) } ?: emptyList()
                Result.success(albums)
            } else {
                Result.failure(Exception("Failed to fetch albums: ${response.code()}"))
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAlbumDetail(albumId: Long): Result<AlbumDetail> {
        return try {
            val response = api.getAlbumDetail(albumId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.withFullUrls(serverBaseUrl))
            } else {
                Result.failure(Exception("Failed to fetch album: ${response.code()}"))
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun Album.withFullUrls(baseUrl: String): Album {
        return copy(
            coverImageUrl = coverImageUrl?.let { if (it.startsWith("/")) "$baseUrl$it" else it }
        )
    }

    private fun AlbumDetail.withFullUrls(baseUrl: String): AlbumDetail {
        return copy(
            coverImageUrl = coverImageUrl?.let { if (it.startsWith("/")) "$baseUrl$it" else it }
        )
    }

    suspend fun createAlbum(request: CreateAlbumRequest): Result<Album> {
        return try {
            val response = api.createAlbum(request)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to create album: ${response.code()}"))
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateAlbum(albumId: Long, request: UpdateAlbumRequest): Result<Album> {
        return try {
            val response = api.updateAlbum(albumId, request)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to update album: ${response.code()}"))
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteAlbum(albumId: Long): Result<Unit> {
        return try {
            val response = api.deleteAlbum(albumId)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to delete album: ${response.code()}"))
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createShareLink(albumId: Long): Result<ShareLinkResponse> {
        return try {
            val response = api.createShareLink(albumId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to create share link: ${response.code()}"))
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun revokeShareLink(albumId: Long): Result<Unit> {
        return try {
            val response = api.revokeShareLink(albumId)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to revoke share link: ${response.code()}"))
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun resolveShareLink(token: String): Result<ShareResolveResponse> {
        return try {
            val response = api.resolveShareLink(token)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(ShareLinkException(response.code()))
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun reorderAlbums(albumIds: List<Long>): Result<Unit> {
        return try {
            val response = api.reorderAlbums(ReorderAlbumsRequest(albumIds))
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to reorder albums: ${response.code()}"))
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

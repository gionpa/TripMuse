package com.tripmuse.data.api

import com.tripmuse.data.model.*
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

interface TripMuseApi {

    // User
    @GET("users/me")
    suspend fun getCurrentUser(
        @Header("X-User-Id") userId: Long
    ): Response<User>

    // Albums
    @GET("albums")
    suspend fun getAlbums(
        @Header("X-User-Id") userId: Long
    ): Response<AlbumListResponse>

    @POST("albums")
    suspend fun createAlbum(
        @Header("X-User-Id") userId: Long,
        @Body request: CreateAlbumRequest
    ): Response<Album>

    @GET("albums/{albumId}")
    suspend fun getAlbumDetail(
        @Header("X-User-Id") userId: Long,
        @Path("albumId") albumId: Long
    ): Response<AlbumDetail>

    @PUT("albums/{albumId}")
    suspend fun updateAlbum(
        @Header("X-User-Id") userId: Long,
        @Path("albumId") albumId: Long,
        @Body request: UpdateAlbumRequest
    ): Response<Album>

    @DELETE("albums/{albumId}")
    suspend fun deleteAlbum(
        @Header("X-User-Id") userId: Long,
        @Path("albumId") albumId: Long
    ): Response<Unit>

    @PUT("albums/reorder")
    suspend fun reorderAlbums(
        @Header("X-User-Id") userId: Long,
        @Body request: ReorderAlbumsRequest
    ): Response<Unit>

    // Media
    @GET("albums/{albumId}/media")
    suspend fun getMediaByAlbum(
        @Header("X-User-Id") userId: Long,
        @Path("albumId") albumId: Long,
        @Query("type") type: String? = null
    ): Response<MediaListResponse>

    @Multipart
    @POST("albums/{albumId}/media")
    suspend fun uploadMedia(
        @Header("X-User-Id") userId: Long,
        @Path("albumId") albumId: Long,
        @Part file: MultipartBody.Part
    ): Response<Media>

    @GET("media/{mediaId}")
    suspend fun getMediaDetail(
        @Header("X-User-Id") userId: Long,
        @Path("mediaId") mediaId: Long
    ): Response<MediaDetail>

    @DELETE("media/{mediaId}")
    suspend fun deleteMedia(
        @Header("X-User-Id") userId: Long,
        @Path("mediaId") mediaId: Long
    ): Response<Unit>

    @POST("media/{mediaId}/cover")
    suspend fun setCoverImage(
        @Header("X-User-Id") userId: Long,
        @Path("mediaId") mediaId: Long
    ): Response<Media>

    // Memo
    @GET("media/{mediaId}/memo")
    suspend fun getMemo(
        @Header("X-User-Id") userId: Long,
        @Path("mediaId") mediaId: Long
    ): Response<Memo?>

    @PUT("media/{mediaId}/memo")
    suspend fun updateMemo(
        @Header("X-User-Id") userId: Long,
        @Path("mediaId") mediaId: Long,
        @Body request: UpdateMemoRequest
    ): Response<Memo>

    @DELETE("media/{mediaId}/memo")
    suspend fun deleteMemo(
        @Header("X-User-Id") userId: Long,
        @Path("mediaId") mediaId: Long
    ): Response<Unit>

    // Comments
    @GET("media/{mediaId}/comments")
    suspend fun getComments(
        @Header("X-User-Id") userId: Long,
        @Path("mediaId") mediaId: Long
    ): Response<CommentListResponse>

    @POST("media/{mediaId}/comments")
    suspend fun createComment(
        @Header("X-User-Id") userId: Long,
        @Path("mediaId") mediaId: Long,
        @Body request: CreateCommentRequest
    ): Response<Comment>

    @PUT("comments/{commentId}")
    suspend fun updateComment(
        @Header("X-User-Id") userId: Long,
        @Path("commentId") commentId: Long,
        @Body request: UpdateCommentRequest
    ): Response<Comment>

    @DELETE("comments/{commentId}")
    suspend fun deleteComment(
        @Header("X-User-Id") userId: Long,
        @Path("commentId") commentId: Long
    ): Response<Unit>

    // Recommendations
    @POST("recommendations/analyze")
    suspend fun analyzeMedia(
        @Header("X-User-Id") userId: Long,
        @Body request: AnalyzeMediaRequest
    ): Response<RecommendationResponse>
}

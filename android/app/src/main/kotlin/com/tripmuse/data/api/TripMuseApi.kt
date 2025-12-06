package com.tripmuse.data.api

import com.tripmuse.data.model.*
import com.tripmuse.data.model.auth.AuthResponse
import com.tripmuse.data.model.auth.LoginRequest
import com.tripmuse.data.model.auth.NaverLoginRequest
import com.tripmuse.data.model.auth.RefreshRequest
import com.tripmuse.data.model.auth.SignupRequest
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface TripMuseApi {

    // Auth
    @POST("auth/signup")
    suspend fun signup(@Body request: SignupRequest): Response<AuthResponse>

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST("auth/naver")
    suspend fun loginWithNaver(@Body request: NaverLoginRequest): Response<AuthResponse>

    @POST("auth/refresh")
    suspend fun refresh(@Body request: RefreshRequest): Response<AuthResponse>

    // User
    @GET("users/me")
    suspend fun getCurrentUser(): Response<User>

    // Albums
    @GET("albums")
    suspend fun getAlbums(): Response<AlbumListResponse>

    @POST("albums")
    suspend fun createAlbum(
        @Body request: CreateAlbumRequest
    ): Response<Album>

    @GET("albums/{albumId}")
    suspend fun getAlbumDetail(
        @Path("albumId") albumId: Long
    ): Response<AlbumDetail>

    @PUT("albums/{albumId}")
    suspend fun updateAlbum(
        @Path("albumId") albumId: Long,
        @Body request: UpdateAlbumRequest
    ): Response<Album>

    @DELETE("albums/{albumId}")
    suspend fun deleteAlbum(
        @Path("albumId") albumId: Long
    ): Response<Unit>

    @PUT("albums/reorder")
    suspend fun reorderAlbums(
        @Body request: ReorderAlbumsRequest
    ): Response<Unit>

    // Media
    @GET("albums/{albumId}/media")
    suspend fun getMediaByAlbum(
        @Path("albumId") albumId: Long,
        @Query("type") type: String? = null
    ): Response<MediaListResponse>

    @Multipart
    @POST("albums/{albumId}/media")
    suspend fun uploadMedia(
        @Path("albumId") albumId: Long,
        @Part file: MultipartBody.Part
    ): Response<Media>

    @Multipart
    @POST("albums/{albumId}/media")
    suspend fun uploadMediaWithMetadata(
        @Path("albumId") albumId: Long,
        @Part file: MultipartBody.Part,
        @Part latitude: MultipartBody.Part?,
        @Part longitude: MultipartBody.Part?,
        @Part takenAt: MultipartBody.Part?
    ): Response<Media>

    @GET("media/{mediaId}")
    suspend fun getMediaDetail(
        @Path("mediaId") mediaId: Long
    ): Response<MediaDetail>

    @DELETE("media/{mediaId}")
    suspend fun deleteMedia(
        @Path("mediaId") mediaId: Long
    ): Response<Unit>

    @POST("media/{mediaId}/cover")
    suspend fun setCoverImage(
        @Path("mediaId") mediaId: Long
    ): Response<Media>

    // Memo
    @GET("media/{mediaId}/memo")
    suspend fun getMemo(
        @Path("mediaId") mediaId: Long
    ): Response<Memo?>

    @PUT("media/{mediaId}/memo")
    suspend fun updateMemo(
        @Path("mediaId") mediaId: Long,
        @Body request: UpdateMemoRequest
    ): Response<Memo>

    @DELETE("media/{mediaId}/memo")
    suspend fun deleteMemo(
        @Path("mediaId") mediaId: Long
    ): Response<Unit>

    // Comments
    @GET("media/{mediaId}/comments")
    suspend fun getComments(
        @Path("mediaId") mediaId: Long
    ): Response<CommentListResponse>

    @POST("media/{mediaId}/comments")
    suspend fun createComment(
        @Path("mediaId") mediaId: Long,
        @Body request: CreateCommentRequest
    ): Response<Comment>

    @PUT("comments/{commentId}")
    suspend fun updateComment(
        @Path("commentId") commentId: Long,
        @Body request: UpdateCommentRequest
    ): Response<Comment>

    @DELETE("comments/{commentId}")
    suspend fun deleteComment(
        @Path("commentId") commentId: Long
    ): Response<Unit>

    // Recommendations
    @POST("recommendations/analyze")
    suspend fun analyzeMedia(
        @Body request: AnalyzeMediaRequest
    ): Response<RecommendationResponse>
}

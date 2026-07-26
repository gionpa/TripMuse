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

    @Multipart
    @POST("users/me/profile-image")
    suspend fun uploadProfileImage(
        @Part file: MultipartBody.Part
    ): Response<User>

    @DELETE("users/me/profile-image")
    suspend fun deleteProfileImage(): Response<User>

    @GET("users/me/storage")
    suspend fun getStorageUsage(): Response<StorageUsage>

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

    // Album share link
    @POST("albums/{albumId}/share")
    suspend fun createShareLink(
        @Path("albumId") albumId: Long
    ): Response<ShareLinkResponse>

    @DELETE("albums/{albumId}/share")
    suspend fun revokeShareLink(
        @Path("albumId") albumId: Long
    ): Response<Unit>

    @GET("share/{token}")
    suspend fun resolveShareLink(
        @Path("token") token: String
    ): Response<ShareResolveResponse>

    // Media
    @GET("albums/{albumId}/media")
    suspend fun getMediaByAlbum(
        @Path("albumId") albumId: Long,
        @Query("type") type: String? = null,
        @Query("page") page: Int? = null,
        @Query("size") size: Int? = null
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

    @POST("media/{mediaId}/comments/read")
    suspend fun markCommentsAsRead(
        @Path("mediaId") mediaId: Long
    ): Response<Unit>

    // Recommendations
    @POST("recommendations/analyze")
    suspend fun analyzeMedia(
        @Body request: AnalyzeMediaRequest
    ): Response<RecommendationResponse>

    // Friends
    @GET("friends")
    suspend fun getFriends(): Response<FriendListResponse>

    @GET("friends/search")
    suspend fun searchUsers(
        @Query("query") query: String
    ): Response<UserSearchListResponse>

    @POST("friends")
    suspend fun addFriend(
        @Body request: AddFriendRequest
    ): Response<Friend>

    @DELETE("friends/{friendId}")
    suspend fun removeFriend(
        @Path("friendId") friendId: Long
    ): Response<Unit>

    @GET("friends/invitations")
    suspend fun getInvitations(): Response<InvitationListResponse>

    // Location share
    @POST("friends/{friendId}/location-share/request")
    suspend fun requestLocationShare(
        @Path("friendId") friendId: Long
    ): Response<LocationShareStatusResponse>

    @POST("friends/{friendId}/location-share/approve")
    suspend fun approveLocationShare(
        @Path("friendId") friendId: Long
    ): Response<LocationShareStatusResponse>

    @PUT("users/me/location")
    suspend fun updateMyLocation(
        @Body request: UpdateLocationRequest
    ): Response<Unit>

    // Presence
    @POST("users/me/heartbeat")
    suspend fun sendHeartbeat(): Response<Unit>

    @GET("friends/presence")
    suspend fun getFriendPresences(): Response<FriendPresenceListResponse>

    @GET("friends/{friendId}/location")
    suspend fun getFriendLocation(
        @Path("friendId") friendId: Long
    ): Response<FriendLocation>

    // Chat
    @POST("chats")
    suspend fun getOrCreateChatRoom(
        @Body request: CreateChatRoomRequest
    ): Response<ChatRoom>

    @GET("chats")
    suspend fun getChatRooms(): Response<ChatRoomListResponse>

    @GET("chats/{roomId}")
    suspend fun getChatRoom(
        @Path("roomId") roomId: Long
    ): Response<ChatRoom>

    @GET("chats/{roomId}/messages")
    suspend fun getChatMessages(
        @Path("roomId") roomId: Long,
        @Query("beforeId") beforeId: Long? = null,
        @Query("afterId") afterId: Long? = null
    ): Response<ChatMessageListResponse>

    @POST("chats/{roomId}/messages")
    suspend fun sendChatMessage(
        @Path("roomId") roomId: Long,
        @Body request: SendMessageRequest
    ): Response<ChatMessage>

    @POST("chats/{roomId}/read")
    suspend fun markChatAsRead(
        @Path("roomId") roomId: Long
    ): Response<Unit>

    @Multipart
    @POST("chats/{roomId}/messages/image")
    suspend fun sendChatImage(
        @Path("roomId") roomId: Long,
        @Part file: MultipartBody.Part
    ): Response<ChatMessage>

    @POST("chats/{roomId}/typing")
    suspend fun markChatTyping(
        @Path("roomId") roomId: Long
    ): Response<Unit>

    @GET("chats/unread-count")
    suspend fun getChatUnreadCount(): Response<ChatUnreadCountResponse>

    @POST("chats/{roomId}/members")
    suspend fun inviteChatMembers(
        @Path("roomId") roomId: Long,
        @Body request: InviteMembersRequest
    ): Response<ChatRoom>

    @DELETE("chats/{roomId}/members/me")
    suspend fun leaveChatRoom(
        @Path("roomId") roomId: Long
    ): Response<Unit>

    @POST("friends/invitations/{invitationId}/accept")
    suspend fun acceptInvitation(
        @Path("invitationId") invitationId: Long
    ): Response<Unit>

    @POST("friends/invitations/{invitationId}/reject")
    suspend fun rejectInvitation(
        @Path("invitationId") invitationId: Long
    ): Response<Unit>
}

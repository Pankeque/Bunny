package com.bunny.data.remote.api

import com.bunny.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

interface BunnyApi {
    @POST("api/auth/register")
    suspend fun register(@Body request: AuthRequestDto): Response<AuthResponseDto>

    @POST("api/auth/login")
    suspend fun login(@Body request: AuthRequestDto): Response<AuthResponseDto>

    @POST("api/auth/refresh")
    suspend fun refreshToken(@Body request: RefreshTokenRequestDto): Response<AuthResponseDto>

    @GET("api/servers")
    suspend fun getServers(): Response<List<ServerDto>>

    @POST("api/servers")
    suspend fun createServer(@Body request: CreateServerRequestDto): Response<ServerDto>

    @DELETE("api/servers/{id}")
    suspend fun deleteServer(@Path("id") serverId: Int): Response<Unit>

    @PUT("api/servers/{id}")
    suspend fun updateServer(@Path("id") serverId: Int, @Body request: UpdateServerRequestDto): Response<ServerDto>

    @POST("api/servers/{id}/icon")
    suspend fun uploadServerIcon(@Path("id") serverId: Int, @Body request: UpdateServerRequestDto): Response<ServerDto>

    @POST("api/servers/{id}/regenerate-invite")
    suspend fun regenerateInviteCode(@Path("id") serverId: Int): Response<Map<String, String>>

    @POST("api/servers/join")
    suspend fun joinServer(@Body request: JoinServerRequestDto): Response<ServerDto>

    @POST("api/servers/{id}/leave")
    suspend fun leaveServer(@Path("id") serverId: Int): Response<Unit>

    @GET("api/servers/{serverId}/channels")
    suspend fun getChannels(@Path("serverId") serverId: Int): Response<List<ChannelDto>>

    @POST("api/servers/{serverId}/channels")
    suspend fun createChannel(@Path("serverId") serverId: Int, @Body request: CreateChannelRequestDto): Response<ChannelDto>

    @PUT("api/channels/{id}")
    suspend fun updateChannel(@Path("id") channelId: Int, @Body request: UpdateChannelRequestDto): Response<ChannelDto>

    @DELETE("api/channels/{id}")
    suspend fun deleteChannel(@Path("id") channelId: Int): Response<Unit>

    @GET("api/channels/{channelId}/messages")
    suspend fun getMessages(
        @Path("channelId") channelId: Int,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50
    ): Response<List<MessageDto>>

    @POST("api/channels/{channelId}/messages")
    suspend fun sendMessage(@Path("channelId") channelId: Int, @Body request: SendMessageRequestDto): Response<MessageDto>

    @PUT("api/users/me")
    suspend fun updateProfile(@Body request: UpdateUserRequestDto): Response<UserDto>

    @GET("api/servers/{serverId}/roles")
    suspend fun getRoles(@Path("serverId") serverId: Int): Response<List<RoleDto>>

    @POST("api/servers/{serverId}/roles")
    suspend fun createRole(@Path("serverId") serverId: Int, @Body request: CreateRoleRequestDto): Response<RoleDto>

    @DELETE("api/roles/{roleId}")
    suspend fun deleteRole(@Path("roleId") roleId: Int): Response<Unit>
}

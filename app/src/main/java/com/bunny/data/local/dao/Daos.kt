package com.bunny.data.local.dao

import androidx.room.*
import com.bunny.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun getUserById(id: Int): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Query("DELETE FROM users")
    suspend fun clearUsers()
}

@Dao
interface RefreshTokenDao {
    @Query("SELECT * FROM refresh_tokens WHERE token = :token")
    suspend fun getToken(token: String): RefreshTokenEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertToken(token: RefreshTokenEntity)

    @Query("DELETE FROM refresh_tokens WHERE token = :token")
    suspend fun deleteToken(token: String)

    @Query("DELETE FROM refresh_tokens")
    suspend fun clearTokens()
}

@Dao
interface ServerDao {
    @Query("SELECT * FROM servers")
    fun getAllServers(): Flow<List<ServerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServer(server: ServerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServers(servers: List<ServerEntity>)

    @Query("DELETE FROM servers WHERE id = :id")
    suspend fun deleteServer(id: Int)

    @Query("DELETE FROM servers")
    suspend fun clearServers()
}

@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels WHERE serverId = :serverId")
    fun getChannelsForServer(serverId: Int): Flow<List<ChannelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannel(channel: ChannelEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<ChannelEntity>)

    @Query("DELETE FROM channels WHERE id = :id")
    suspend fun deleteChannel(id: Int)

    @Query("DELETE FROM channels")
    suspend fun clearChannels()
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE channelId = :channelId ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getMessagesForChannel(channelId: Int, limit: Int, offset: Int): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Query("DELETE FROM messages WHERE channelId = :channelId")
    suspend fun clearMessages(channelId: Int)
}

package com.bunny.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.bunny.data.local.dao.*
import com.bunny.data.local.entity.*

@Database(
    entities = [
        UserEntity::class, 
        ServerEntity::class, 
        ChannelEntity::class, 
        MessageEntity::class, 
        RefreshTokenEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class BunnyDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun refreshTokenDao(): RefreshTokenDao
    abstract fun serverDao(): ServerDao
    abstract fun channelDao(): ChannelDao
    abstract fun messageDao(): MessageDao
}

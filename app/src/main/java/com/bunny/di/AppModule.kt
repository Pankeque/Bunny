package com.bunny.di

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import com.bunny.data.local.BunnyDatabase
import com.bunny.data.local.dao.*
import com.bunny.data.remote.api.BunnyApi
import com.bunny.data.remote.socket.SocketService
import com.bunny.data.repository.*
import com.bunny.domain.repository.*
import com.bunny.util.Constants
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder().setLenient().create()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .build()
    }

    @Provides
    @Singleton
    fun provideBunnyApi(client: OkHttpClient, gson: Gson): BunnyApi {
        return Retrofit.Builder()
            .baseUrl(Constants.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(BunnyApi::class.java)
    }

    @Provides
    @Singleton
    fun provideBunnyDatabase(@ApplicationContext context: Context): BunnyDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            BunnyDatabase::class.java,
            "bunny_database"
        ).build()
    }

    // --- FORNECIMENTO EXPLÍCITO DOS DAOS ---
    @Provides
    fun provideUserDao(database: BunnyDatabase): UserDao = database.userDao()

    @Provides
    fun provideRefreshTokenDao(database: BunnyDatabase): RefreshTokenDao = database.refreshTokenDao()

    @Provides
    fun provideServerDao(database: BunnyDatabase): ServerDao = database.serverDao()

    @Provides
    fun provideChannelDao(database: BunnyDatabase): ChannelDao = database.channelDao()

    @Provides
    fun provideMessageDao(database: BunnyDatabase): MessageDao = database.messageDao()

    @Provides
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

    @Provides
    @Singleton
    fun provideAuthRepository(api: BunnyApi, tokenDao: RefreshTokenDao, userDao: UserDao, prefs: SharedPreferences): AuthRepository {
        return AuthRepositoryImpl(api, tokenDao, userDao, prefs)
    }

    @Provides
    @Singleton
    fun provideServerRepository(api: BunnyApi, serverDao: ServerDao): ServerRepository {
        return ServerRepositoryImpl(api, serverDao)
    }

    @Provides
    @Singleton
    fun provideChannelRepository(api: BunnyApi, channelDao: ChannelDao): ChannelRepository {
        return ChannelRepositoryImpl(api, channelDao)
    }

    @Provides
    @Singleton
    fun provideMessageRepository(api: BunnyApi, messageDao: MessageDao): MessageRepository {
        return MessageRepositoryImpl(api, messageDao)
    }

    @Provides
    @Singleton
    fun provideUserRepository(api: BunnyApi): UserRepository {
        return UserRepositoryImpl(api)
    }

    @Provides
    @Singleton
    fun provideRoleRepository(api: BunnyApi): RoleRepository {
        return RoleRepositoryImpl(api)
    }

    @Provides
    @Singleton
    fun provideSocketService(): SocketService = SocketService()
}

package com.zai.chat.di

import com.zai.chat.config.ZaiConfig
import com.zai.chat.network.auth.AuthInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @OptIn(ExperimentalSerializationApi::class)
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true      // server adds fields → we never break
        isLenient = true              // tolerate loose JSON in SSE lines
        coerceInputValues = true      // nulls into defaults instead of crashes
        encodeDefaults = true         // stream:true etc. always on the wire
        explicitNulls = false         // never send "file_ids":null
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: AuthInterceptor): OkHttpClient {
        // BASIC level: logs request line + status only. It does NOT buffer
        // bodies, so SSE streaming is unaffected and chunks stay un-logged.
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(ZaiConfig.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(ZaiConfig.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)   // gap between bytes, fits SSE
            .writeTimeout(ZaiConfig.WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)   // connect-phase retries; mid-stream failures surface as Error events
            .build()
    }
}

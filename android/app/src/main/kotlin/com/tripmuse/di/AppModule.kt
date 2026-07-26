package com.tripmuse.di

import com.tripmuse.BuildConfig
import com.tripmuse.data.api.TripMuseApi
import com.tripmuse.data.auth.AuthInterceptor
import com.tripmuse.data.auth.TokenRefreshAuthenticator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/** 이보다 큰 본문은 로그에 남기지 않는다 (메모리에 통째로 올라가는 것을 막기 위해) */
private const val MAX_LOGGED_BODY_BYTES = 256L * 1024

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideBaseUrl(): String {
        // Remove /api/v1/ suffix to get the server base URL
        return BuildConfig.BASE_URL.removeSuffix("api/v1/").removeSuffix("/")
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        tokenRefreshAuthenticator: TokenRefreshAuthenticator
    ): OkHttpClient {
        // BODY 로깅은 요청 본문 전체를 메모리에 문자열로 올린다.
        // 사진·동영상 업로드에서는 그 자체로 OOM이 나므로 헤더까지만 남긴다.
        val bodyLogger = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
            else HttpLoggingInterceptor.Level.NONE
        }
        val headersOnlyLogger = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.HEADERS
            else HttpLoggingInterceptor.Level.NONE
        }
        val loggingInterceptor = Interceptor { chain ->
            val body = chain.request().body
            val isUpload = body != null &&
                (body.contentType()?.type == "multipart" || body.contentLength() > MAX_LOGGED_BODY_BYTES)
            if (isUpload) {
                headersOnlyLogger.intercept(chain)
            } else {
                bodyLogger.intercept(chain)
            }
        }

        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .authenticator(tokenRefreshAuthenticator)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideTripMuseApi(retrofit: Retrofit): TripMuseApi {
        return retrofit.create(TripMuseApi::class.java)
    }
}

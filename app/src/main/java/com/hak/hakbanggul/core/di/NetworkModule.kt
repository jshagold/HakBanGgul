package com.hak.hakbanggul.core.di

import com.hak.hakbanggul.core.model.DataSource
import com.hak.hakbanggul.core.network.DataSourceEndpoints
import com.hak.hakbanggul.core.network.SourceResponseClassifier
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    // 연결·읽기 제한 15초. 키 확인의 10초 제한은 설정 기능이 따로 건다.
    private const val TIMEOUT_SECONDS = 15L

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true }

    @Provides
    @Singleton
    fun provideDataSourceEndpoints(): DataSourceEndpoints = DataSourceEndpoints()

    @Provides
    @Singleton
    fun provideSourceResponseClassifier(): SourceResponseClassifier = SourceResponseClassifier()

    @Provides
    @Singleton
    @TwelveDataRetrofit
    fun provideTwelveDataRetrofit(
        client: OkHttpClient,
        json: Json,
        endpoints: DataSourceEndpoints,
    ): Retrofit = buildRetrofit(endpoints.baseUrl(DataSource.TWELVE_DATA), client, json)

    @Provides
    @Singleton
    @FredRetrofit
    fun provideFredRetrofit(
        client: OkHttpClient,
        json: Json,
        endpoints: DataSourceEndpoints,
    ): Retrofit = buildRetrofit(endpoints.baseUrl(DataSource.FRED), client, json)

    private fun buildRetrofit(baseUrl: String, client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
}

package com.hak.hakbanggul.core.di

import com.hak.hakbanggul.core.model.DataSource
import com.hak.hakbanggul.core.network.ApiKeyInterceptor
import com.hak.hakbanggul.core.network.ApiKeyProvider
import com.hak.hakbanggul.core.network.DataSourceEndpoints
import com.hak.hakbanggul.core.network.SourceCallExecutor
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

    @Provides
    @Singleton
    fun provideSourceCallExecutor(classifier: SourceResponseClassifier): SourceCallExecutor =
        SourceCallExecutor(classifier)

    // 키를 붙이는 Retrofit은 있는 클라이언트에 그 출처의 ApiKeyInterceptor를 더한 클라이언트를 쓴다.
    // 설정의 키 확인 요청은 위의 키를 붙이지 않는 Retrofit을 쓴다.
    @Provides
    @Singleton
    @TwelveDataAuthorizedRetrofit
    fun provideTwelveDataAuthorizedRetrofit(
        client: OkHttpClient,
        json: Json,
        endpoints: DataSourceEndpoints,
        apiKeyProvider: ApiKeyProvider,
        classifier: SourceResponseClassifier,
    ): Retrofit = buildRetrofit(
        endpoints.baseUrl(DataSource.TWELVE_DATA),
        client.withKey(DataSource.TWELVE_DATA, apiKeyProvider, classifier),
        json,
    )

    @Provides
    @Singleton
    @FredAuthorizedRetrofit
    fun provideFredAuthorizedRetrofit(
        client: OkHttpClient,
        json: Json,
        endpoints: DataSourceEndpoints,
        apiKeyProvider: ApiKeyProvider,
        classifier: SourceResponseClassifier,
    ): Retrofit = buildRetrofit(
        endpoints.baseUrl(DataSource.FRED),
        client.withKey(DataSource.FRED, apiKeyProvider, classifier),
        json,
    )

    private fun OkHttpClient.withKey(
        source: DataSource,
        apiKeyProvider: ApiKeyProvider,
        classifier: SourceResponseClassifier,
    ): OkHttpClient = newBuilder()
        .addInterceptor(ApiKeyInterceptor(source, apiKeyProvider, classifier))
        .build()

    private fun buildRetrofit(baseUrl: String, client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
}

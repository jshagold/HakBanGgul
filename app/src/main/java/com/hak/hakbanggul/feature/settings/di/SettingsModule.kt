package com.hak.hakbanggul.feature.settings.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.hak.hakbanggul.core.di.FredRetrofit
import com.hak.hakbanggul.core.di.TwelveDataRetrofit
import com.hak.hakbanggul.core.network.ApiKeyProvider
import com.hak.hakbanggul.feature.settings.data.ApiKeyCheckRepositoryImpl
import com.hak.hakbanggul.feature.settings.data.ApiKeyCipher
import com.hak.hakbanggul.feature.settings.data.ApiKeyProviderImpl
import com.hak.hakbanggul.feature.settings.data.ApiKeyRepositoryImpl
import com.hak.hakbanggul.feature.settings.data.FredKeyCheckApi
import com.hak.hakbanggul.feature.settings.data.TinkApiKeyCipher
import com.hak.hakbanggul.feature.settings.data.TwelveDataKeyCheckApi
import com.hak.hakbanggul.feature.settings.domain.ApiKeyCheckRepository
import com.hak.hakbanggul.feature.settings.domain.ApiKeyRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Qualifier
import javax.inject.Singleton

// API 키를 두는 DataStore(파일 이름 api_keys)
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApiKeysDataStore

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsModule {

    @Binds
    @Singleton
    abstract fun bindApiKeyRepository(impl: ApiKeyRepositoryImpl): ApiKeyRepository

    @Binds
    @Singleton
    abstract fun bindApiKeyCheckRepository(impl: ApiKeyCheckRepositoryImpl): ApiKeyCheckRepository

    @Binds
    @Singleton
    abstract fun bindApiKeyCipher(impl: TinkApiKeyCipher): ApiKeyCipher

    @Binds
    @Singleton
    abstract fun bindApiKeyProvider(impl: ApiKeyProviderImpl): ApiKeyProvider

    companion object {
        private const val API_KEYS_FILE = "api_keys"

        @Provides
        @Singleton
        @ApiKeysDataStore
        fun provideApiKeysDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                // 파일이 손상되었으면 빈 것으로 바꾼다.
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                produceFile = { context.preferencesDataStoreFile(API_KEYS_FILE) },
            )

        @Provides
        @Singleton
        fun provideTwelveDataKeyCheckApi(@TwelveDataRetrofit retrofit: Retrofit): TwelveDataKeyCheckApi =
            retrofit.create(TwelveDataKeyCheckApi::class.java)

        @Provides
        @Singleton
        fun provideFredKeyCheckApi(@FredRetrofit retrofit: Retrofit): FredKeyCheckApi =
            retrofit.create(FredKeyCheckApi::class.java)
    }
}

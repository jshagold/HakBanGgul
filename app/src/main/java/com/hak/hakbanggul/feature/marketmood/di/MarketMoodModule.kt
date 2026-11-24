package com.hak.hakbanggul.feature.marketmood.di

import com.hak.hakbanggul.core.di.FredAuthorizedRetrofit
import com.hak.hakbanggul.core.di.TwelveDataAuthorizedRetrofit
import com.hak.hakbanggul.feature.marketmood.data.FredObservationsApi
import com.hak.hakbanggul.feature.marketmood.data.MarketDataRepositoryImpl
import com.hak.hakbanggul.feature.marketmood.data.MoodRulesRepositoryImpl
import com.hak.hakbanggul.feature.marketmood.data.TwelveDataTimeSeriesApi
import com.hak.hakbanggul.feature.marketmood.domain.MarketDataRepository
import com.hak.hakbanggul.feature.marketmood.domain.MoodRulesRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MarketMoodModule {

    @Binds
    @Singleton
    abstract fun bindMarketDataRepository(impl: MarketDataRepositoryImpl): MarketDataRepository

    @Binds
    @Singleton
    abstract fun bindMoodRulesRepository(impl: MoodRulesRepositoryImpl): MoodRulesRepository

    companion object {
        // 두 Api는 요청에 키를 붙이는 출처별 Retrofit으로 만든다.
        @Provides
        @Singleton
        fun provideTwelveDataTimeSeriesApi(@TwelveDataAuthorizedRetrofit retrofit: Retrofit): TwelveDataTimeSeriesApi =
            retrofit.create(TwelveDataTimeSeriesApi::class.java)

        @Provides
        @Singleton
        fun provideFredObservationsApi(@FredAuthorizedRetrofit retrofit: Retrofit): FredObservationsApi =
            retrofit.create(FredObservationsApi::class.java)
    }
}

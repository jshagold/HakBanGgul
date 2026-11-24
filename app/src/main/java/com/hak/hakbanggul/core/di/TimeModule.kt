package com.hak.hakbanggul.core.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TimeModule {

    // 지금 시각. UTC 기준이다.
    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemUTC()
}

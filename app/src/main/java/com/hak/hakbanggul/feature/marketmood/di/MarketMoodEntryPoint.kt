package com.hak.hakbanggul.feature.marketmood.di

import android.content.Context
import com.hak.hakbanggul.feature.marketmood.domain.MarketDataRefresher
import com.hak.hakbanggul.feature.marketmood.presentation.CloseRefreshScheduler
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

// 시스템이 만드는 것(뒤에서 미리 받는 Worker)이 의존성을 받는 통로. 그 뒤의 클래스는 모두 생성자로 받는다.
@EntryPoint
@InstallIn(SingletonComponent::class)
interface MarketMoodEntryPoint {
    fun marketDataRefresher(): MarketDataRefresher
    fun closeRefreshScheduler(): CloseRefreshScheduler
}

fun marketMoodEntryPoint(context: Context): MarketMoodEntryPoint =
    EntryPointAccessors.fromApplication(context.applicationContext, MarketMoodEntryPoint::class.java)

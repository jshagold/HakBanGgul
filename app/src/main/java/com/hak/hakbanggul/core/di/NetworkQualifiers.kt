package com.hak.hakbanggul.core.di

import javax.inject.Qualifier

// Twelve Data 접속용 Retrofit
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TwelveDataRetrofit

// FRED 접속용 Retrofit
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class FredRetrofit

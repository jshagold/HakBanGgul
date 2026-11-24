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

// 요청에 키를 붙이는 Twelve Data 접속용 Retrofit
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TwelveDataAuthorizedRetrofit

// 요청에 키를 붙이는 FRED 접속용 Retrofit
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class FredAuthorizedRetrofit

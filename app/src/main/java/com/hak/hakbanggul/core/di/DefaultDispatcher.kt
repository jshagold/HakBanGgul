package com.hak.hakbanggul.core.di

import javax.inject.Qualifier

// 계산용 디스패처(Dispatchers.Default)를 가리키는 한정자
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

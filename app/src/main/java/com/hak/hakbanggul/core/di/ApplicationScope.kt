package com.hak.hakbanggul.core.di

import javax.inject.Qualifier

// 앱이 살아 있는 동안의 CoroutineScope를 가리키는 한정자
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

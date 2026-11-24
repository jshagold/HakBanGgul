package com.hak.hakbanggul.core.di

import android.content.Context
import androidx.room.Room
import com.hak.hakbanggul.core.database.AppDatabase
import com.hak.hakbanggul.core.database.DailyValueDao
import com.hak.hakbanggul.core.database.RoomTransactionRunner
import com.hak.hakbanggul.core.database.SyncStampDao
import com.hak.hakbanggul.core.database.TransactionRunner
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.FILE_NAME)
            // 스키마가 바뀌면 옮기지 않고 모든 표를 지우고 새로 만든다.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    @Singleton
    fun provideDailyValueDao(database: AppDatabase): DailyValueDao = database.dailyValueDao()

    @Provides
    @Singleton
    fun provideSyncStampDao(database: AppDatabase): SyncStampDao = database.syncStampDao()

    @Provides
    @Singleton
    fun provideTransactionRunner(database: AppDatabase): TransactionRunner = RoomTransactionRunner(database)
}

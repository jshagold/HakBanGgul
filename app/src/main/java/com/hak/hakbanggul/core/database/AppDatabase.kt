package com.hak.hakbanggul.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

// 앱의 Room DB 하나. 파일 이름은 hakbanggul.db다.
// 스키마가 바뀌면 옮기지 않고 표를 지우고 새로 만든다(DatabaseModule). 다시 받으면 되는 값이다.
@Database(
    entities = [DailyValueEntity::class, SyncStampEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dailyValueDao(): DailyValueDao
    abstract fun syncStampDao(): SyncStampDao

    companion object {
        const val FILE_NAME = "hakbanggul.db"
    }
}

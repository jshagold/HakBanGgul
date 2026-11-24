package com.hak.hakbanggul.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

// 표 sync_stamps의 행. 받기에 마지막으로 성공한 때를 값과 같은 DB에 두어 한 트랜잭션으로 적는다.
@Entity(tableName = "sync_stamps")
data class SyncStampEntity(
    @PrimaryKey @ColumnInfo(name = "name") val name: String,
    // UTC 밀리초
    @ColumnInfo(name = "timestamp_millis") val timestampMillis: Long,
)

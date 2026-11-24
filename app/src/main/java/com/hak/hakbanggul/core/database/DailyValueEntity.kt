package com.hak.hakbanggul.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity

// 표 daily_values의 행. 시리즈 하루의 값 하나다.
// 기능 안이 아니라 공용 표에 두어, 다른 기능도 같은 값을 쓸 수 있게 한다.
@Entity(tableName = "daily_values", primaryKeys = ["series_id", "date"])
data class DailyValueEntity(
    // 티커나 출처의 ID
    @ColumnInfo(name = "series_id") val seriesId: String,
    // YYYY-MM-DD
    @ColumnInfo(name = "date") val date: String,
    @ColumnInfo(name = "value") val value: Double,
)

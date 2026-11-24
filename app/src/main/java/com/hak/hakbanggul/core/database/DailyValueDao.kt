package com.hak.hakbanggul.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyValueDao {

    @Query("SELECT * FROM daily_values ORDER BY series_id, date")
    fun observeAll(): Flow<List<DailyValueEntity>>

    @Query("DELETE FROM daily_values WHERE series_id = :seriesId")
    suspend fun deleteBySeries(seriesId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<DailyValueEntity>)

    @Query("DELETE FROM daily_values")
    suspend fun deleteAll()
}

package com.hak.hakbanggul.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncStampDao {

    @Query("SELECT timestamp_millis FROM sync_stamps WHERE name = :name")
    fun observe(name: String): Flow<Long?>

    @Upsert
    suspend fun upsert(stamp: SyncStampEntity)

    @Query("DELETE FROM sync_stamps")
    suspend fun deleteAll()
}

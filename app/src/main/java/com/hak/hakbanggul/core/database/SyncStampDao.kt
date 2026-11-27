package com.hak.hakbanggul.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncStampDao {

    @Query("SELECT timestamp_millis FROM sync_stamps WHERE name = :name")
    fun observe(name: String): Flow<Long?>

    // 이름이 prefix로 시작하는 행 모두. LIKE는 _와 %를 아무 글자로 보므로 앞부분을 잘라 그대로 견준다.
    @Query("SELECT * FROM sync_stamps WHERE substr(name, 1, length(:prefix)) = :prefix")
    suspend fun getByPrefix(prefix: String): List<SyncStampEntity>

    @Upsert
    suspend fun upsert(stamp: SyncStampEntity)

    @Query("DELETE FROM sync_stamps")
    suspend fun deleteAll()
}

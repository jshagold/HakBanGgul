package com.hak.hakbanggul.feature.settings.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.hak.hakbanggul.core.model.DataSource
import com.hak.hakbanggul.feature.settings.di.ApiKeysDataStore
import com.hak.hakbanggul.feature.settings.domain.ApiKeyRepository
import com.hak.hakbanggul.feature.settings.domain.ApiKeyStatus
import com.hak.hakbanggul.feature.settings.domain.StoredApiKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

// 출처마다 암호화한 키와 상태를 DataStore에 둔다.
@Singleton
class ApiKeyRepositoryImpl @Inject constructor(
    @param:ApiKeysDataStore private val dataStore: DataStore<Preferences>,
    private val cipher: ApiKeyCipher,
) : ApiKeyRepository {

    override fun observeKeys(): Flow<Map<DataSource, StoredApiKey>> =
        dataStore.data
            // 읽다가 IOException이 나면 빈 것으로 본다.
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { prefs ->
                val keys = mutableMapOf<DataSource, StoredApiKey>()
                val broken = mutableListOf<DataSource>()
                for (source in DataSource.entries) {
                    val cipherText = prefs[keyOf(source)] ?: continue
                    val stored = readEntry(source, cipherText, prefs[statusOf(source)])
                    if (stored == null) broken += source else keys[source] = stored
                }
                if (broken.isNotEmpty()) removeBroken(broken)
                keys
            }

    override suspend fun save(source: DataSource, key: String) {
        // 암호화 실패도 저장 실패로 다룬다. 부르는 쪽은 IOException만 받는다.
        val cipherText = try {
            cipher.encrypt(key)
        } catch (e: Exception) {
            throw IOException("키를 암호화하지 못했습니다", e)
        }
        dataStore.edit { prefs ->
            prefs[keyOf(source)] = cipherText
            prefs[statusOf(source)] = ApiKeyStatus.SAVED.name
        }
    }

    override suspend fun delete(source: DataSource) {
        dataStore.edit { prefs ->
            prefs.remove(keyOf(source))
            prefs.remove(statusOf(source))
        }
    }

    override suspend fun updateStatus(source: DataSource, status: ApiKeyStatus) {
        dataStore.edit { prefs ->
            if (prefs.contains(keyOf(source))) prefs[statusOf(source)] = status.name
        }
    }

    // 복호화하지 못하거나 상태를 알 수 없는 항목은 null. 앱을 멈추지 않는다.
    private fun readEntry(source: DataSource, cipherText: String, statusName: String?): StoredApiKey? {
        val status = ApiKeyStatus.entries.firstOrNull { it.name == statusName } ?: return null
        val key = try {
            cipher.decrypt(cipherText)
        } catch (e: Exception) {
            return null
        }
        return StoredApiKey(source, key, status)
    }

    private suspend fun removeBroken(sources: List<DataSource>) {
        try {
            dataStore.edit { prefs ->
                sources.forEach {
                    prefs.remove(keyOf(it))
                    prefs.remove(statusOf(it))
                }
            }
        } catch (e: IOException) {
            // 지우지 못해도 그 출처는 키가 없는 것으로 보이므로 그대로 둔다.
        }
    }

    private fun keyOf(source: DataSource) = stringPreferencesKey("key_${source.name}")

    private fun statusOf(source: DataSource) = stringPreferencesKey("status_${source.name}")
}

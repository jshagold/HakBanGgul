package com.hak.hakbanggul.feature.settings.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.hak.hakbanggul.core.model.DataSource.FRED
import com.hak.hakbanggul.core.model.DataSource.TWELVE_DATA
import com.hak.hakbanggul.feature.settings.domain.ApiKeyStatus
import com.hak.hakbanggul.feature.settings.domain.StoredApiKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ApiKeyRepositoryImplTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // 정해 둔 키는 복호화할 때 예외를 던지는 가짜
    private class FakeCipher : ApiKeyCipher {
        val failingPlainKeys = mutableSetOf<String>()

        override fun encrypt(plain: String): String = "enc:$plain"

        override fun decrypt(cipherText: String): String {
            val plain = cipherText.removePrefix("enc:")
            if (plain in failingPlainKeys) throw IllegalStateException("복호화 실패")
            return plain
        }
    }

    private val dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var dataStore: DataStore<Preferences>
    private val cipher = FakeCipher()
    private lateinit var repository: ApiKeyRepositoryImpl

    @Before
    fun setUp() {
        dataStore = PreferenceDataStoreFactory.create(scope = dataStoreScope) {
            tempFolder.root.resolve("api_keys.preferences_pb")
        }
        repository = ApiKeyRepositoryImpl(dataStore, cipher)
    }

    @After
    fun tearDown() {
        dataStoreScope.cancel()
    }

    @Test
    fun `R-42 복호화하지 못한 키는 없는 것으로 보고 DataStore에서 지운다`() = runTest {
        repository.save(TWELVE_DATA, "tdkey")
        cipher.failingPlainKeys += "tdkey"

        val keys = repository.observeKeys().first()

        assertFalse(keys.containsKey(TWELVE_DATA))
        val raw = dataStore.data.first().asMap().keys.map { it.name }
        assertTrue("남은 항목: $raw", raw.none { it.endsWith(TWELVE_DATA.name) })
    }

    @Test
    fun `R-42 다른 출처의 키는 정상으로 복호화되면 그대로 있다`() = runTest {
        repository.save(TWELVE_DATA, "tdkey")
        repository.save(FRED, "fredkey")
        cipher.failingPlainKeys += "tdkey"

        val keys = repository.observeKeys().first()

        assertEquals(mapOf(FRED to StoredApiKey(FRED, "fredkey", ApiKeyStatus.SAVED)), keys)
    }
}

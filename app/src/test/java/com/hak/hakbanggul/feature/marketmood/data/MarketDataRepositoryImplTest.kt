package com.hak.hakbanggul.feature.marketmood.data

import com.hak.hakbanggul.core.database.DailyValueDao
import com.hak.hakbanggul.core.database.DailyValueEntity
import com.hak.hakbanggul.core.database.SyncStampDao
import com.hak.hakbanggul.core.database.SyncStampEntity
import com.hak.hakbanggul.core.database.TransactionRunner
import com.hak.hakbanggul.core.model.DailyValue
import com.hak.hakbanggul.core.network.SourceCallExecutor
import com.hak.hakbanggul.core.network.SourceResponseClassifier
import com.hak.hakbanggul.feature.marketmood.domain.StoredMarketData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.time.Instant
import java.time.LocalDate

class MarketDataRepositoryImplTest {

    // 호출마다 트랜잭션 안이었는지 함께 적는 가짜 DB

    private var inTransaction = false
    private val calls = mutableListOf<Pair<String, Boolean>>()

    private inner class FakeDailyValueDao : DailyValueDao {
        val rows = MutableStateFlow<List<DailyValueEntity>>(emptyList())
        var failOnObserve = false
        var failOnDeleteAll = false
        var deleteAllCalls = 0

        override fun observeAll(): Flow<List<DailyValueEntity>> =
            if (failOnObserve) flow { throw IllegalStateException("읽기 실패") } else rows

        override suspend fun deleteBySeries(seriesId: String) {
            calls += "deleteBySeries:$seriesId" to inTransaction
            rows.update { list -> list.filterNot { it.seriesId == seriesId } }
        }

        override suspend fun insertAll(rows: List<DailyValueEntity>) {
            calls += "insertAll" to inTransaction
            this.rows.update { it + rows }
        }

        override suspend fun deleteAll() {
            deleteAllCalls++
            if (failOnDeleteAll) throw IllegalStateException("지우기 실패")
            rows.value = emptyList()
        }
    }

    private inner class FakeSyncStampDao : SyncStampDao {
        val stamps = MutableStateFlow<Map<String, Long>>(emptyMap())
        var failOnDeleteAll = false
        var deleteAllCalls = 0

        override fun observe(name: String): Flow<Long?> = stamps.map { it[name] }

        override suspend fun upsert(stamp: SyncStampEntity) {
            calls += "upsert" to inTransaction
            stamps.update { it + (stamp.name to stamp.timestampMillis) }
        }

        override suspend fun deleteAll() {
            deleteAllCalls++
            if (failOnDeleteAll) throw IllegalStateException("지우기 실패")
            stamps.value = emptyMap()
        }
    }

    private inner class FakeTransactionRunner : TransactionRunner {
        var runCalls = 0
        var failure: Exception? = null

        override suspend fun <T> run(block: suspend () -> T): T {
            runCalls++
            failure?.let { throw it }
            inTransaction = true
            try {
                return block()
            } finally {
                inTransaction = false
            }
        }
    }

    // 저장과 읽기 테스트에서는 요청을 보내지 않는다.
    private val unusedTwelveData = object : TwelveDataTimeSeriesApi {
        override suspend fun timeSeries(symbol: String, startDate: String, interval: String, outputSize: Int): Response<ResponseBody> =
            error("쓰지 않음")
    }
    private val unusedFred = object : FredObservationsApi {
        override suspend fun observations(seriesId: String, observationStart: String, fileType: String): Response<ResponseBody> =
            error("쓰지 않음")
    }

    private val dailyDao = FakeDailyValueDao()
    private val stampDao = FakeSyncStampDao()
    private val runner = FakeTransactionRunner()
    private val repository = MarketDataRepositoryImpl(
        unusedTwelveData,
        unusedFred,
        SourceCallExecutor(SourceResponseClassifier()),
        MarketResponseMapper(Json { ignoreUnknownKeys = true }),
        dailyDao,
        stampDao,
        runner,
    )

    private fun row(series: String, date: String, value: Double) = DailyValueEntity(series, date, value)

    private fun value(date: String, value: Double) = DailyValue(LocalDate.parse(date), value)

    // R-54

    @Test
    fun `R-54 넘긴 시리즈는 통째로 바뀌고 다른 시리즈는 그대로다`() = runTest {
        dailyDao.rows.value = listOf(
            row("SPY", "2026-11-19", 100.0),
            row("SPY", "2026-11-20", 100.0),
            row("QQQ", "2026-11-20", 100.0),
        )

        repository.save(mapOf("SPY" to listOf(value("2026-11-20", 101.0), value("2026-11-23", 102.0))), null)

        assertEquals(
            listOf(row("SPY", "2026-11-20", 101.0), row("SPY", "2026-11-23", 102.0)),
            dailyDao.rows.value.filter { it.seriesId == "SPY" }.sortedBy { it.date },
        )
        assertEquals(listOf(row("QQQ", "2026-11-20", 100.0)), dailyDao.rows.value.filter { it.seriesId == "QQQ" })
    }

    // R-55 한 번에 저장

    @Test
    fun `R-55 지우기, 넣기, 성공한 때 적기가 모두 한 트랜잭션 안에서 불린다`() = runTest {
        repository.save(
            mapOf(
                "SPY" to listOf(value("2026-11-23", 102.0)),
                "T10Y2Y" to listOf(value("2026-11-23", 0.29)),
            ),
            Instant.parse("2026-11-24T01:00:00Z"),
        )

        assertEquals(1, runner.runCalls)
        assertEquals(
            listOf("deleteBySeries:SPY", "insertAll", "deleteBySeries:T10Y2Y", "insertAll", "upsert"),
            calls.map { it.first },
        )
        assertTrue(calls.all { it.second })
        assertEquals(Instant.parse("2026-11-24T01:00:00Z").toEpochMilli(), stampDao.stamps.value["market_mood_refresh"])
    }

    @Test
    fun `R-55 트랜잭션이 예외를 던지면 IOException이 난다`() = runTest {
        runner.failure = IllegalStateException("트랜잭션 실패")
        try {
            repository.save(mapOf("SPY" to listOf(value("2026-11-23", 102.0))), null)
            fail("IOException이 나야 한다")
        } catch (e: IOException) {
            // 기대한 대로
        }
    }

    // R-56

    @Test
    fun `R-56 읽다가 예외가 나면 두 표를 비우고 받아 둔 값이 없는 것으로 내준다`() = runTest {
        dailyDao.failOnObserve = true

        val stored = repository.observeStored().first()

        assertEquals(StoredMarketData(emptyMap(), null), stored)
        assertTrue(dailyDao.deleteAllCalls > 0)
        assertTrue(stampDao.deleteAllCalls > 0)
    }

    @Test
    fun `R-56 비우다가 예외가 나도 같은 값을 내준다`() = runTest {
        dailyDao.failOnObserve = true
        dailyDao.failOnDeleteAll = true
        stampDao.failOnDeleteAll = true

        val stored = repository.observeStored().first()

        assertEquals(StoredMarketData(emptyMap(), null), stored)
    }
}

package com.hak.hakbanggul.feature.homewidget.domain

import com.hak.hakbanggul.feature.marketmood.domain.RefreshStamp
import com.hak.hakbanggul.feature.marketmood.domain.DEFAULT_PACING
import com.hak.hakbanggul.core.model.DataSource
import com.hak.hakbanggul.core.network.ApiKeyAvailability
import com.hak.hakbanggul.core.network.ApiKeyProvider
import com.hak.hakbanggul.core.network.FakeApiKeyProvider
import com.hak.hakbanggul.feature.marketmood.domain.FakeClock
import com.hak.hakbanggul.feature.marketmood.domain.FakeMarketDataRepository
import com.hak.hakbanggul.feature.marketmood.domain.FakeMoodRulesRepository
import com.hak.hakbanggul.feature.marketmood.domain.FetchResult
import com.hak.hakbanggul.feature.marketmood.domain.MarketDataRefresher
import com.hak.hakbanggul.feature.marketmood.domain.MarketDataRepository
import com.hak.hakbanggul.feature.marketmood.domain.MoodCalculator
import com.hak.hakbanggul.feature.marketmood.domain.MoodRules
import com.hak.hakbanggul.feature.marketmood.domain.MoodRulesRepository
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData.withMainWindow
import com.hak.hakbanggul.feature.marketmood.domain.ObserveMoodUseCase
import com.hak.hakbanggul.feature.marketmood.domain.ObserveRefreshStateUseCase
import com.hak.hakbanggul.feature.marketmood.domain.ObserveSourceKeysUseCase
import com.hak.hakbanggul.feature.marketmood.domain.RefreshMarketDataUseCase
import com.hak.hakbanggul.feature.marketmood.domain.RequestRefreshUseCase
import com.hak.hakbanggul.feature.marketmood.domain.RegimeCalculator
import com.hak.hakbanggul.feature.marketmood.domain.ValuationCalculator
import com.hak.hakbanggul.feature.marketmood.domain.ValuationStage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

// 홈 화면 위젯 테스트의 기본 상황: 진짜 시장 분위기 UseCase를 시장 분위기 테스트의 가짜로 만든다.
// 시계 2026-11-24T01:00:00Z, 두 출처의 키 AVAILABLE, 받아 둔 값 데이터 A(성공한 때 1시간 전), 시작한 때 없음, 출처도 데이터 A.
// Given을 바꾼 뒤 start()로 받기 관리자를 만든다.
class WidgetTestFixture(private val testScope: TestScope) {

    val now: Instant = Instant.parse("2026-11-24T01:00:00Z")
    val repository = FakeMarketDataRepository().apply { setStored(MoodTestData.dataA(), now.minus(Duration.ofHours(1))) }
    val rulesRepository = FakeMoodRulesRepository()
    val clock = FakeClock(now)
    val keys = FakeApiKeyProvider()
    private val calculator = MoodCalculator(RegimeCalculator(), ValuationCalculator())

    // 받기 관리자가 쓰는 저장소. 시작한 때를 읽다 예외가 나게 할 때 바꾼다.
    var refresherRepository: MarketDataRepository = repository

    // 위젯이 쓰는 판정. 예외를 던지게 할 때 바꾼다.
    var widgetMoodRules: MoodRulesRepository = rulesRepository

    // 위젯이 쓰는 키 제공. 모은 횟수를 셀 때 바꾼다.
    var widgetKeyProvider: ApiKeyProvider = keys

    lateinit var refresher: MarketDataRefresher
        private set
    lateinit var requestRefresh: RequestRefreshUseCase
        private set
    lateinit var observeRefreshState: ObserveRefreshStateUseCase
        private set
    lateinit var coordinator: WidgetRefreshCoordinator
        private set

    val fetchCount get() = repository.fetchCalls.size

    fun start() {
        val observeMood = ObserveMoodUseCase(rulesRepository, refresherRepository, calculator, UnconfinedTestDispatcher(testScope.testScheduler))
        val refresh = RefreshMarketDataUseCase(refresherRepository, rulesRepository, calculator, DEFAULT_PACING, clock)
        // 앱 범위를 흉내 낸다. backgroundScope의 일은 advanceUntilIdle이 돌리지 않아 따로 둔다.
        val appScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScope.testScheduler))
        refresher = MarketDataRefresher(
            refresh, observeMood, ObserveSourceKeysUseCase(rulesRepository, keys), refresherRepository, rulesRepository, clock, appScope,
        )
        requestRefresh = RequestRefreshUseCase(refresher)
        observeRefreshState = ObserveRefreshStateUseCase(refresher)
        coordinator = WidgetRefreshCoordinator(requestRefresh)
        testScope.runCurrent()
    }

    fun observeWidgetState(): ObserveWidgetStateUseCase = ObserveWidgetStateUseCase(
        ObserveMoodUseCase(widgetMoodRules, repository, calculator, UnconfinedTestDispatcher(testScope.testScheduler)),
        observeRefreshState,
        widgetKeyProvider,
        coordinator,
    )

    fun setKeys(twelveData: ApiKeyAvailability, fred: ApiKeyAvailability) {
        keys.availability.value = mapOf(DataSource.TWELVE_DATA to twelveData, DataSource.FRED to fred)
    }

    fun setStoredNone() = repository.setStored(null, null)

    // 테스트가 풀어 줄 때까지 fetch가 돌아오지 않게 한다. answer가 null이면 데이터 A를 돌려준다.
    fun holdFetch(answer: FetchResult? = null): CompletableDeferred<Unit> {
        val wait = CompletableDeferred<Unit>()
        val remote = repository.remote
        repository.fetchAnswer = { _, series ->
            wait.await()
            answer ?: FetchResult.Success(remote.build(series.map { it.series.seriesId }))
        }
        return wait
    }

    fun answerAlways(answer: FetchResult) {
        repository.fetchAnswer = { _, _ -> answer }
    }

    companion object {
        // 데이터 A의 매매 판단: 주가 위치와 금리 부담 모두 점수 50, 표시 단계 MIDDLE
        val MIDDLE_50 = WidgetValuation(50, ValuationStage.MIDDLE)

        val ASSESSMENT_A = WidgetContent.Assessment(
            com.hak.hakbanggul.feature.marketmood.domain.MarketStage.BULL,
            LocalDate.parse("2026-11-23"),
            MIDDLE_50,
            MIDDLE_50,
        )

        // 완충 규칙: 시험용 규칙에서 매매 판단의 메인 기간(2y)의 위치 기간만 101, 평균 일수만 1로 바꾼 것
        val BUFFER_RULES: MoodRules = MoodTestData.RULES.withMainWindow(101).let {
            it.copy(valuation = it.valuation.copy(smoothingDays = 1))
        }

        // 완충 데이터: 데이터 A에서 실질금리의 마지막 102개 평일(2026-11-23으로 끝남) 값만 점수열 30, 35가 나오는 값으로 덮은 것.
        // 그 앞의 날은 2.00 그대로 둔다(1년 전 값이 있어야 금리 부담이 나온다).
        fun bufferData(): MoodTestData.Builder {
            val values = MoodTestData.valuesForScores(listOf(30, 35))
            val builder = MoodTestData.dataA()
            MoodTestData.weekdaysEndingAt(MoodTestData.END, values.size).zip(values).forEach { (day, value) ->
                builder.set(MoodTestData.REAL_YIELD, day, value)
            }
            return builder
        }
    }
}

// 시작한 때를 읽다 예외가 나는 저장소
class ThrowingStartedAtRepository(private val delegate: MarketDataRepository) : MarketDataRepository by delegate {
    override suspend fun getStamp(stamp: RefreshStamp): Instant? = throw IllegalStateException("때 읽기 실패")
}

// 규칙의 Flow가 예외를 던지는 규칙 저장소
class ThrowingRulesRepository : MoodRulesRepository {
    override fun observe(): Flow<MoodRules> = flow { throw IllegalStateException("규칙 읽기 실패") }
}

// 키의 상태를 모은 횟수를 세는 키 제공
class CountingApiKeyProvider(private val delegate: ApiKeyProvider) : ApiKeyProvider by delegate {
    var observeCalls = 0
        private set

    override fun observeAvailability(source: DataSource): Flow<ApiKeyAvailability> {
        observeCalls++
        return delegate.observeAvailability(source)
    }
}

package com.hak.hakbanggul.feature.marketmood.presentation

import com.hak.hakbanggul.R
import com.hak.hakbanggul.core.model.DataFetchError
import com.hak.hakbanggul.core.model.DataSource.FRED
import com.hak.hakbanggul.core.model.DataSource.TWELVE_DATA
import com.hak.hakbanggul.core.network.ApiKeyAvailability.AVAILABLE
import com.hak.hakbanggul.core.network.ApiKeyAvailability.INVALID
import com.hak.hakbanggul.core.network.ApiKeyAvailability.MISSING
import com.hak.hakbanggul.core.network.FakeApiKeyProvider
import com.hak.hakbanggul.core.ui.KeyBadgeKind
import com.hak.hakbanggul.core.ui.UiText
import com.hak.hakbanggul.feature.marketmood.domain.FakeClock
import com.hak.hakbanggul.feature.marketmood.domain.FakeMarketDataRepository
import com.hak.hakbanggul.feature.marketmood.domain.FakeMoodRulesRepository
import com.hak.hakbanggul.feature.marketmood.domain.FetchResult
import com.hak.hakbanggul.feature.marketmood.domain.MarketDataRefresher
import com.hak.hakbanggul.feature.marketmood.domain.MarketStage
import com.hak.hakbanggul.feature.marketmood.domain.MoodCalculator
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData.valuesForScores
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData.closesForScores
import com.hak.hakbanggul.feature.marketmood.domain.ValuationStage
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData.HIGH_YIELD
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData.YIELD_CURVE
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData.dataA
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData.dataB
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData.withRegimeWeights
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData.withSingleWindow
import com.hak.hakbanggul.feature.marketmood.domain.ObserveMoodUseCase
import com.hak.hakbanggul.feature.marketmood.domain.ObserveSourceKeysUseCase
import com.hak.hakbanggul.feature.marketmood.domain.RefreshMarketDataUseCase
import com.hak.hakbanggul.feature.marketmood.domain.RegimeCalculator
import com.hak.hakbanggul.feature.marketmood.domain.ValuationCalculator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class MarketMoodViewModelTest {

    private val now = Instant.parse("2026-11-24T01:00:00Z")
    private val repository = FakeMarketDataRepository()
    private val rulesRepository = FakeMoodRulesRepository()
    private val keys = FakeApiKeyProvider()

    // 테스트가 풀어 줄 때까지 받기를 붙잡아 둔다.
    private val pendingFetch = CompletableDeferred<Unit>()

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.newViewModel(): MarketMoodViewModel {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val clock = FakeClock(now)
        val calculator = MoodCalculator(RegimeCalculator(), ValuationCalculator())
        val observeMood = ObserveMoodUseCase(rulesRepository, repository, calculator, UnconfinedTestDispatcher(testScheduler))
        val observeKeys = ObserveSourceKeysUseCase(rulesRepository, keys)
        val appScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val refresher = MarketDataRefresher(
            RefreshMarketDataUseCase(repository, rulesRepository, calculator, clock),
            observeMood,
            observeKeys,
            repository,
            clock,
            appScope,
        )
        return MarketMoodViewModel(observeMood, observeKeys, refresher).also { advanceUntilIdle() }
    }

    private fun stored(builder: MoodTestData.Builder?) = repository.setStored(builder, null)

    private fun holdFetch() {
        val remote = repository.remote
        repository.fetchAnswer = { _, series ->
            pendingFetch.await()
            FetchResult.Success(remote.build(series.map { it.series.seriesId }))
        }
    }

    private fun failFetch(error: DataFetchError) {
        repository.fetchAnswer = { _, _ -> FetchResult.Failure(error) }
    }

    private fun succeedFetch() {
        val remote = repository.remote
        repository.fetchAnswer = { _, series -> FetchResult.Success(remote.build(series.map { it.series.seriesId })) }
    }

    // 받기를 한 번 실패로 끝낸 뒤의 ViewModel
    private fun TestScope.afterFailure(error: DataFetchError): MarketMoodViewModel {
        failFetch(error)
        val viewModel = newViewModel()
        viewModel.onRefreshClick()
        advanceUntilIdle()
        return viewModel
    }

    private val MarketMoodViewModel.body get() = uiState.value.body

    private val MarketMoodViewModel.mood: MoodUiState
        get() = (body as MoodBody.Assessment).mood

    private fun MarketMoodViewModel.indicator(name: Int): IndicatorUiState =
        mood.groups.flatMap { it.indicators }.single { it.name == UiText(name) }

    // R-04 본문 고르기

    @Test
    fun `R-04 FRED의 키가 없으면 KeyGuide`() = runTest {
        stored(dataA())
        keys.set(FRED, MISSING)
        assertTrue(newViewModel().body is MoodBody.KeyGuide)
    }

    @Test
    fun `R-04 Twelve Data의 키가 틀림이면 KeyGuide`() = runTest {
        stored(dataA())
        keys.set(TWELVE_DATA, INVALID)
        assertTrue(newViewModel().body is MoodBody.KeyGuide)
    }

    @Test
    fun `R-04 판정이 있으면 Assessment`() = runTest {
        stored(dataA())
        assertTrue(newViewModel().body is MoodBody.Assessment)
    }

    @Test
    fun `R-04 받아 둔 값 없이 받는 중이면 Loading`() = runTest {
        holdFetch()
        val viewModel = newViewModel()

        viewModel.onScreenShown()
        runCurrent()

        assertEquals(MoodBody.Loading, viewModel.body)
        pendingFetch.complete(Unit)
    }

    @Test
    fun `R-04 받아 둔 값 없이 받기가 연결 안 됨으로 끝나면 Failed`() = runTest {
        assertTrue(afterFailure(DataFetchError.NO_CONNECTION).body is MoodBody.Failed)
    }

    // R-05

    @Test
    fun `R-05 묶음과 항목의 순서`() = runTest {
        stored(dataA())
        val mood = newViewModel().mood

        assertEquals(listOf(UiText(R.string.group_stocks), UiText(R.string.group_bond_signals)), mood.groups.map { it.name })
        assertEquals(
            listOf(
                R.string.indicator_above_average,
                R.string.indicator_from_high,
                R.string.indicator_average_slope,
            ).map { UiText(it) },
            mood.groups[0].indicators.map { it.name },
        )
        assertEquals(
            listOf(UiText(R.string.indicator_yield_curve), UiText(R.string.indicator_high_yield_spread)),
            mood.groups[1].indicators.map { it.name },
        )
    }

    // R-06

    @Test
    fun `R-06 장세, 기준 날짜, 종합 점수의 글`() = runTest {
        stored(dataB())
        val mood = newViewModel().mood

        assertEquals(MarketStage.BULL, mood.stage)
        assertEquals(UiText(R.string.stage_bull), mood.stageName)
        assertEquals(UiText(R.string.mood_as_of, listOf(11, 23)), mood.asOfText)
        assertEquals("+0.15", mood.compositeScoreText)
        assertEquals(ScoreTone.POSITIVE, mood.compositeTone)
        assertEquals(UiText(R.string.stage_neutral), mood.computedStageName)
    }

    @Test
    fun `R-06 데이터 B의 눈금 위치`() = runTest {
        stored(dataB())
        assertEquals(0.575f, newViewModel().mood.gaugePosition, 1e-6f)
    }

    @Test
    fun `R-06 데이터 A의 눈금 위치`() = runTest {
        stored(dataA())
        assertEquals(0.8833335f, newViewModel().mood.gaugePosition, 1e-6f)
    }

    @Test
    fun `R-06 위 경계가 0_6이면 눈금 위치도 따라 바뀐다`() = runTest {
        stored(dataB())
        rulesRepository.rules.value = MoodTestData.RULES.copy(regime = MoodTestData.RULES.regime.copy(upperBoundary = 0.6))
        assertEquals(0.5625f, newViewModel().mood.gaugePosition, 1e-6f)
    }

    @Test
    fun `R-06 현재 상태 -0_5의 눈금 위치`() = runTest {
        rulesRepository.rules.value = MoodTestData.RULES.withRegimeWeights(0.75, 0.25)
        stored(
            dataA().set("SPY", "2026-11-23", 93.0).set(YIELD_CURVE, "2026-11-23", 0.29).set(HIGH_YIELD, "2026-11-23", 5.00),
        )
        assertEquals(0.25f, newViewModel().mood.gaugePosition, 1e-6f)
    }

    // R-08

    @Test
    fun `R-08 계산 단계와 표시 단계가 다르면 다른 날 안내`() = runTest {
        stored(dataB())
        assertEquals(
            UiText(R.string.mood_pending_notice, listOf(UiText(R.string.stage_neutral), UiText(R.string.stage_bull), 3, 1)),
            newViewModel().mood.pendingNotice,
        )
    }

    @Test
    fun `R-08 같으면 다른 날 안내가 없다`() = runTest {
        stored(dataA())
        assertNull(newViewModel().mood.pendingNotice)
    }

    // R-09

    @Test
    fun `R-09 묶음 이름, 비중, 묶음 점수의 글`() = runTest {
        stored(dataB())
        val groups = newViewModel().mood.groups

        assertEquals(UiText(R.string.group_stocks), groups[0].name)
        assertEquals(UiText(R.string.group_weight, listOf(70)), groups[0].weightText)
        assertEquals("0.00", groups[0].scoreText)
        assertEquals(ScoreTone.ZERO, groups[0].scoreTone)
        assertEquals(UiText(R.string.group_bond_signals), groups[1].name)
        assertEquals(UiText(R.string.group_weight, listOf(30)), groups[1].weightText)
        assertEquals("+0.50", groups[1].scoreText)
        assertEquals(ScoreTone.POSITIVE, groups[1].scoreTone)
    }

    // R-10

    @Test
    fun `R-10 항목 이름, 덧붙이는 글, 근거 숫자`() = runTest {
        stored(dataB())
        val viewModel = newViewModel()

        // 주식 세 항목의 근거는 임시로 부호 붙인 % 하나다(장세 화면의 글은 단위 5).
        val above = viewModel.indicator(R.string.indicator_above_average)
        assertNull(above.suffix)
        assertEquals(UiText(R.string.evidence_value, listOf("-1.0%")), above.evidence)
        assertEquals("0.0", above.scoreText)

        val fromHigh = viewModel.indicator(R.string.indicator_from_high)
        assertNull(fromHigh.suffix)
        assertEquals(UiText(R.string.evidence_value, listOf("-1.0%")), fromHigh.evidence)
        assertEquals("+1.0", fromHigh.scoreText)

        val slope = viewModel.indicator(R.string.indicator_average_slope)
        assertNull(slope.suffix)
        assertEquals(UiText(R.string.evidence_value, listOf("0.0%")), slope.evidence)
        assertEquals("-1.0", slope.scoreText)
        assertEquals(ScoreTone.NEGATIVE, slope.scoreTone)

        val curve = viewModel.indicator(R.string.indicator_yield_curve)
        assertEquals(UiText(R.string.indicator_yield_curve_suffix), curve.suffix)
        assertEquals(UiText(R.string.evidence_value, listOf("+0.29%p")), curve.evidence)
        assertEquals("0.0", curve.scoreText)
        assertEquals(ScoreTone.ZERO, curve.scoreTone)

        val spread = viewModel.indicator(R.string.indicator_high_yield_spread)
        assertNull(spread.suffix)
        assertEquals("3.76%", spread.evidence.args.first())
    }

    // R-12

    @Test
    fun `R-12 값의 날짜가 기준 날짜와 다를 때만 날짜를 붙인다`() = runTest {
        stored(dataB())
        val viewModel = newViewModel()

        assertEquals(
            UiText(R.string.evidence_value_with_date, listOf("3.76%", 11, 20)),
            viewModel.indicator(R.string.indicator_high_yield_spread).evidence,
        )
        assertEquals(R.string.evidence_value, viewModel.indicator(R.string.indicator_yield_curve).evidence.resId)
    }

    // R-15, R-52

    @Test
    fun `R-15 출처마다의 줄과 딱지 종류`() = runTest {
        keys.set(TWELVE_DATA, MISSING)
        val viewModel = newViewModel()
        val rows = (viewModel.body as MoodBody.KeyGuide).rows
        assertEquals(listOf(TWELVE_DATA to KeyBadgeKind.NONE, FRED to KeyBadgeKind.SAVED), rows.map { it.source to it.badge })

        keys.set(TWELVE_DATA, INVALID)
        runCurrent()

        val invalidRows = (viewModel.body as MoodBody.KeyGuide).rows
        assertEquals(KeyBadgeKind.INVALID, invalidRows.single { it.source == TWELVE_DATA }.badge)
    }

    @Test
    fun `R-52 출처 문구는 SourceLabels의 것`() = runTest {
        keys.set(TWELVE_DATA, MISSING)
        val rows = (newViewModel().body as MoodBody.KeyGuide).rows

        assertEquals(R.string.source_twelve_data_name, rows[0].nameRes)
        assertEquals(R.string.source_twelve_data_description, rows[0].descriptionRes)
        assertEquals(R.string.source_fred_name, rows[1].nameRes)
        assertEquals(R.string.source_fred_description, rows[1].descriptionRes)
    }

    // R-18

    @Test
    fun `R-18 판정이 있고 받기에 실패하면 띠가 보인다`() = runTest {
        stored(dataA())
        val viewModel = afterFailure(DataFetchError.NO_CONNECTION)
        assertTrue(viewModel.body is MoodBody.Assessment)
        assertNotNull(viewModel.uiState.value.banner)
    }

    @Test
    fun `R-18 받은 적이 없으면 띠가 없다`() = runTest {
        stored(dataA())
        assertNull(newViewModel().uiState.value.banner)
    }

    @Test
    fun `R-18 판정이 없으면 Failed이고 띠가 없다`() = runTest {
        val viewModel = afterFailure(DataFetchError.NO_CONNECTION)
        assertTrue(viewModel.body is MoodBody.Failed)
        assertNull(viewModel.uiState.value.banner)
    }

    // R-45, R-46

    @Test
    fun `R-45 실패 원인에 따른 띠의 글`() = runTest {
        val cases = listOf(
            DataFetchError.RATE_LIMITED to BannerUiState(
                UiText(R.string.banner_rate_limited_title),
                UiText(R.string.banner_rate_limited_message),
            ),
            DataFetchError.NO_CONNECTION to BannerUiState(
                UiText(R.string.banner_stale_title),
                UiText(R.string.banner_no_connection_message),
            ),
            DataFetchError.OTHER to BannerUiState(UiText(R.string.banner_stale_title), UiText(R.string.banner_other_message)),
        )
        cases.forEach { (error, expected) ->
            stored(dataA())
            assertEquals(error.name, expected, afterFailure(error).uiState.value.banner)
        }
    }

    @Test
    fun `R-46 실패 원인에 따른 받지 못함의 글`() = runTest {
        val cases = listOf(
            DataFetchError.RATE_LIMITED to UiText(R.string.mood_failed_rate_limited),
            DataFetchError.NO_CONNECTION to UiText(R.string.mood_failed_no_connection),
            DataFetchError.OTHER to UiText(R.string.mood_failed_other),
        )
        cases.forEach { (error, expected) ->
            stored(null)
            assertEquals(error.name, MoodBody.Failed(expected), afterFailure(error).body)
        }
    }

    // R-47

    @Test
    fun `R-47 다시 시도가 성공하면 띠가 사라진다`() = runTest {
        stored(dataA())
        val viewModel = afterFailure(DataFetchError.NO_CONNECTION)
        assertNotNull(viewModel.uiState.value.banner)

        succeedFetch()
        viewModel.onRetryClick()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.banner)
    }

    // R-21, R-28

    @Test
    fun `R-21 새로 고침을 누르면 받기가 시작된다`() = runTest {
        stored(dataA())
        val viewModel = newViewModel()

        viewModel.onRefreshClick()
        advanceUntilIdle()

        assertTrue(repository.fetchCalls.isNotEmpty())
    }

    @Test
    fun `R-28 받지 못함에서 다시 시도를 누르면 받기가 시작되고 Loading이 된다`() = runTest {
        val viewModel = afterFailure(DataFetchError.NO_CONNECTION)
        assertTrue(viewModel.body is MoodBody.Failed)
        repository.fetchCalls.clear()
        holdFetch()

        viewModel.onRetryClick()
        runCurrent()

        assertTrue(repository.fetchCalls.isNotEmpty())
        assertEquals(MoodBody.Loading, viewModel.body)
        pendingFetch.complete(Unit)
    }

    // R-22, R-23, R-24

    @Test
    fun `R-22 받는 중에는 REFRESHING이고 판정은 그대로, 끝나면 ENABLED`() = runTest {
        stored(dataA())
        holdFetch()
        val viewModel = newViewModel()

        viewModel.onRefreshClick()
        runCurrent()

        assertEquals(RefreshButtonState.REFRESHING, viewModel.uiState.value.refreshButton)
        assertTrue(viewModel.body is MoodBody.Assessment)

        pendingFetch.complete(Unit)
        advanceUntilIdle()

        assertEquals(RefreshButtonState.ENABLED, viewModel.uiState.value.refreshButton)
    }

    @Test
    fun `R-23 새로 받아 저장하면 종합 점수가 바뀐다`() = runTest {
        stored(dataA())
        val viewModel = newViewModel()
        assertEquals("+0.77", viewModel.mood.compositeScoreText)
        repository.remote = dataB()

        viewModel.onRefreshClick()
        advanceUntilIdle()

        assertEquals("+0.15", viewModel.mood.compositeScoreText)
    }

    @Test
    fun `R-24 키가 없으면 새로 고침 버튼이 DISABLED`() = runTest {
        keys.set(FRED, MISSING)
        assertEquals(RefreshButtonState.DISABLED, newViewModel().uiState.value.refreshButton)
    }

    // R-44

    @Test
    fun `R-44 키가 틀림이 되면 키 안내 카드로 바뀐다`() = runTest {
        stored(dataA())
        val viewModel = newViewModel()
        assertTrue(viewModel.body is MoodBody.Assessment)

        keys.set(TWELVE_DATA, INVALID)
        runCurrent()

        val rows = (viewModel.body as MoodBody.KeyGuide).rows
        assertEquals(KeyBadgeKind.INVALID, rows.single { it.source == TWELVE_DATA }.badge)
    }

    // R-49

    @Test
    fun `R-49 키를 지우면 키 안내 카드가 되고 받아 둔 값은 그대로다`() = runTest {
        stored(dataA())
        val before = repository.stored.value
        val viewModel = newViewModel()

        keys.set(FRED, MISSING)
        advanceUntilIdle()

        assertTrue(viewModel.body is MoodBody.KeyGuide)
        assertTrue(repository.saveCalls.isEmpty())
        assertEquals(before, repository.stored.value)
    }

    @Test
    fun `R-49 키를 다시 저장하면 받아 둔 값으로 판정이 보이고 받기가 시작된다`() = runTest {
        stored(dataA())
        holdFetch()
        val viewModel = newViewModel()
        keys.set(FRED, MISSING)
        advanceUntilIdle()

        keys.set(FRED, AVAILABLE)
        runCurrent()

        assertTrue(viewModel.body is MoodBody.Assessment)
        assertTrue(repository.fetchCalls.isNotEmpty())
        pendingFetch.complete(Unit)
    }

    // R-53

    @Test
    fun `R-53 받아 둔 값 없이 키가 모두 저장됨이 되면 키 안내 카드가 아니다`() = runTest {
        holdFetch()
        keys.set(FRED, MISSING)
        val viewModel = newViewModel()
        assertTrue(viewModel.body is MoodBody.KeyGuide)

        keys.set(FRED, AVAILABLE)
        runCurrent()

        assertNotEquals(true, viewModel.body is MoodBody.KeyGuide)
        assertEquals(MoodBody.Loading, viewModel.body)
        pendingFetch.complete(Unit)
    }

    // 매매 판단 두 카드 (단위 4)

    // 주가 위치는 전체 시장의 종가로 낸다.
    private val testPrice = "SPY"

    // 점수를 만들기 쉬운 규칙: 위치 기간 101(메인 기간 하나), 평균 일수 1이다.
    private fun stageRules(low: Int = 30, high: Int = 70, buffer: Int = 5) = MoodTestData.RULES.copy(
        valuation = MoodTestData.RULES.valuation.withSingleWindow(101).copy(
            shortAverageDays = 2,
            longAverageDays = 2,
            smoothingDays = 1,
            threeMonthDays = 2,
            yearAgoDays = 1,
            lowBoundary = low,
            highBoundary = high,
            buffer = buffer,
        ),
    )

    // 주가 위치와 금리 부담의 점수열로 받아 둔 값과 규칙을 정하고 ViewModel을 만든다.
    private fun TestScope.valuationViewModel(
        priceScores: List<Int> = listOf(50),
        rateScores: List<Int>? = null,
        low: Int = 30,
        high: Int = 70,
        buffer: Int = 5,
    ): MarketMoodViewModel {
        rulesRepository.rules.value = stageRules(low, high, buffer)
        val builder = dataA().replaceSeries(testPrice, closesForScores(priceScores))
        if (rateScores != null) builder.replaceSeries(MoodTestData.REAL_YIELD, valuesForScores(rateScores))
        stored(builder)
        return newViewModel()
    }

    private fun scoreText(score: Int, nameRes: Int) = UiText(R.string.valuation_score, listOf(score, UiText(nameRes)))

    private fun withDate(value: String, month: Int, day: Int) = UiText(R.string.evidence_value_with_date, listOf(value, month, day))

    // R-05 판정의 구성

    @Test
    fun `R-05 데이터 A면 Assessment에 두 카드가 있다`() = runTest {
        stored(dataA())
        val mood = newViewModel().mood
        assertNotNull(mood.pricePosition)
        assertNotNull(mood.rateBurden)
    }

    @Test
    fun `R-05 안내 띠가 있어도 두 카드가 함께 있다`() = runTest {
        stored(dataA())
        val viewModel = afterFailure(DataFetchError.NO_CONNECTION)
        assertNotNull(viewModel.uiState.value.banner)
        assertEquals(UiText(R.string.price_position_subtitle), viewModel.mood.pricePosition.subtitle)
        assertEquals(UiText(R.string.rate_burden_subtitle, listOf("2.00%")), viewModel.mood.rateBurden.subtitle)
    }

    @Test
    fun `R-05 받는 중에도 두 카드가 그대로 있다`() = runTest {
        stored(dataA())
        holdFetch()
        val viewModel = newViewModel()
        val before = viewModel.mood

        viewModel.onRefreshClick()
        runCurrent()

        assertEquals(RefreshButtonState.REFRESHING, viewModel.uiState.value.refreshButton)
        assertEquals(before.pricePosition, viewModel.mood.pricePosition)
        assertEquals(before.rateBurden, viewModel.mood.rateBurden)
        pendingFetch.complete(Unit)
        advanceUntilIdle()
    }

    // R-63, R-64 이름 아래 글, 점수와 단계의 글

    @Test
    fun `R-63 R-64 데이터 A의 이름 아래 글과 점수와 단계`() = runTest {
        stored(dataA())
        val mood = newViewModel().mood
        assertEquals(UiText(R.string.price_position_subtitle), mood.pricePosition.subtitle)
        assertEquals(scoreText(50, R.string.valuation_middle), mood.pricePosition.scoreText)
        assertEquals(UiText(R.string.rate_burden_subtitle, listOf("2.00%")), mood.rateBurden.subtitle)
        assertEquals(scoreText(50, R.string.valuation_middle), mood.rateBurden.scoreText)
    }

    // R-66 표시 단계를 상태에 실음

    @Test
    fun `R-66 주가 위치 LOW 6, 금리 부담 HIGH 98`() = runTest {
        val mood = valuationViewModel(priceScores = listOf(6), rateScores = listOf(98)).mood
        assertEquals(ValuationStage.LOW, mood.pricePosition.stage)
        assertEquals(scoreText(6, R.string.valuation_price_low), mood.pricePosition.scoreText)
        assertEquals(ValuationStage.HIGH, mood.rateBurden.stage)
        assertEquals(scoreText(98, R.string.valuation_rate_high), mood.rateBurden.scoreText)
    }

    @Test
    fun `R-66 주가 위치 HIGH 74, 금리 부담 LOW 22`() = runTest {
        val mood = valuationViewModel(priceScores = listOf(74), rateScores = listOf(22)).mood
        assertEquals(ValuationStage.HIGH, mood.pricePosition.stage)
        assertEquals(scoreText(74, R.string.valuation_price_high), mood.pricePosition.scoreText)
        assertEquals(ValuationStage.LOW, mood.rateBurden.stage)
        assertEquals(scoreText(22, R.string.valuation_rate_low), mood.rateBurden.scoreText)
    }

    // R-65 칸 폭, 칸 이름, 표시 위치

    private fun assertSegments(expected: List<Float>, actual: List<Float>) {
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (e, a) -> assertEquals(e, a, 1e-6f) }
    }

    @Test
    fun `R-65 데이터 A면 칸 폭 31, 38, 31과 칸 이름, 표시 0_5`() = runTest {
        stored(dataA())
        val mood = newViewModel().mood
        listOf(mood.pricePosition, mood.rateBurden).forEach {
            assertSegments(listOf(0.31f, 0.38f, 0.31f), it.gauge.segments)
            assertEquals(0.5f, it.gauge.position, 1e-6f)
        }
        assertEquals(
            listOf(UiText(R.string.valuation_price_low), UiText(R.string.valuation_middle), UiText(R.string.valuation_price_high)),
            mood.pricePosition.gauge.labels,
        )
        assertEquals(
            listOf(UiText(R.string.valuation_rate_low), UiText(R.string.valuation_middle), UiText(R.string.valuation_rate_high)),
            mood.rateBurden.gauge.labels,
        )
    }

    @Test
    fun `R-65 경계 25 80이면 칸 폭 26, 53, 21`() = runTest {
        rulesRepository.rules.value = MoodTestData.RULES.copy(
            valuation = MoodTestData.RULES.valuation.copy(lowBoundary = 25, highBoundary = 80),
        )
        stored(dataA())
        assertSegments(listOf(0.26f, 0.53f, 0.21f), newViewModel().mood.pricePosition.gauge.segments)
    }

    @Test
    fun `R-65 점수 0이면 표시 0_0, 100이면 1_0`() = runTest {
        assertEquals(0f, valuationViewModel(priceScores = listOf(0)).mood.pricePosition.gauge.position, 1e-6f)
        assertEquals(1f, valuationViewModel(priceScores = listOf(100)).mood.pricePosition.gauge.position, 1e-6f)
    }

    // R-67 숫자와 단계가 다른 날 안내

    @Test
    fun `R-67 주가 위치 33, 점수로 정한 단계 MIDDLE, 표시 LOW`() = runTest {
        val mood = valuationViewModel(priceScores = listOf(30, 33)).mood
        assertEquals(
            UiText(R.string.valuation_notice_low, listOf(33, UiText(R.string.valuation_price_low), 36)),
            mood.pricePosition.notice,
        )
    }

    @Test
    fun `R-67 금리 부담 67, 점수로 정한 단계 MIDDLE, 표시 HIGH`() = runTest {
        val mood = valuationViewModel(rateScores = listOf(70, 67)).mood
        assertEquals(
            UiText(R.string.valuation_notice_high, listOf(67, UiText(R.string.valuation_rate_high), 64)),
            mood.rateBurden.notice,
        )
    }

    @Test
    fun `R-67 금리 부담 35, 표시 LOW`() = runTest {
        val mood = valuationViewModel(rateScores = listOf(30, 35)).mood
        assertEquals(
            UiText(R.string.valuation_notice_low, listOf(35, UiText(R.string.valuation_rate_low), 36)),
            mood.rateBurden.notice,
        )
    }

    @Test
    fun `R-67 경계 25 75, 완충 3이면 숫자를 판정에 실린 값에서 낸다`() = runTest {
        val low = valuationViewModel(priceScores = listOf(25, 28), low = 25, high = 75, buffer = 3).mood
        assertEquals(
            UiText(R.string.valuation_notice_low, listOf(28, UiText(R.string.valuation_price_low), 29)),
            low.pricePosition.notice,
        )
        val high = valuationViewModel(priceScores = listOf(75, 73), low = 25, high = 75, buffer = 3).mood
        assertEquals(
            UiText(R.string.valuation_notice_high, listOf(73, UiText(R.string.valuation_price_high), 71)),
            high.pricePosition.notice,
        )
    }

    @Test
    fun `R-67 점수로 정한 단계와 표시 단계가 같으면 안내가 없다`() = runTest {
        stored(dataA())
        val mood = newViewModel().mood
        assertNull(mood.pricePosition.notice)
        assertNull(mood.rateBurden.notice)
    }

    // R-68 주가 위치의 근거 글

    @Test
    fun `R-68 두 괴리, 낙폭, 3개월 수익률의 글`() = runTest {
        // 짧은 평균 2, 긴 평균 3, 위치 기간 3, 3개월 3인 규칙에서 마지막 종가 100의 괴리가 +0.041, +0.214, 낙폭 -0.032, 3개월 +0.058이 되게 한다.
        val p = 200 / 1.041 - 100
        val q = 300 / 1.214 - 100 - p
        // 앞에 100을 70개 두어 전체 시장의 장세도 낼 수 있게 한다(근거 넷은 마지막 다섯 종가로 정해진다).
        val closes = List(70) { 100.0 } + listOf(100 / 0.968, 100 / 1.058, q, p, 100.0)
        rulesRepository.rules.value = MoodTestData.RULES.copy(
            valuation = MoodTestData.RULES.valuation.withSingleWindow(3).copy(
                shortAverageDays = 2,
                longAverageDays = 3,
                smoothingDays = 1,
                threeMonthDays = 3,
            ),
        )
        stored(dataA().replaceSeries(testPrice, closes))

        assertEquals(
            listOf(
                UiText(R.string.price_evidence_average, listOf("+4.1%", "+21.4%")),
                UiText(R.string.price_evidence_drawdown, listOf("-3.2%", "+5.8%")),
            ),
            newViewModel().mood.pricePosition.evidence,
        )
    }

    @Test
    fun `R-68 데이터 A면 넷 모두 0_0%`() = runTest {
        stored(dataA())
        assertEquals(
            listOf(
                UiText(R.string.price_evidence_average, listOf("0.0%", "0.0%")),
                UiText(R.string.price_evidence_drawdown, listOf("0.0%", "0.0%")),
            ),
            newViewModel().mood.pricePosition.evidence,
        )
    }

    // R-69 금리 부담의 네 자리 글

    private fun rateData(): MoodTestData.Builder = dataA()
        .set(MoodTestData.REAL_YIELD, "2026-11-23", 2.79)
        .set(MoodTestData.REAL_YIELD, "2025-11-21", 1.82)
        .set(MoodTestData.TREASURY_10Y, "2026-11-23", 4.92)
        .set(MoodTestData.POLICY_RATE, "2026-11-23", 3.88)
        .set(MoodTestData.CONSUMER_PRICE, "2026-10-01", 312.3)

    private fun rising(change: String) = UiText(R.string.rate_evidence_year_change, listOf(change, UiText(R.string.rate_evidence_rising)))

    private fun TestScope.rateCard(builder: MoodTestData.Builder): ValuationCardUiState {
        stored(builder)
        return newViewModel().mood.rateBurden
    }

    @Test
    fun `R-69 실질금리, 1년 전과의 차이, 금리, 물가의 글`() = runTest {
        val card = rateCard(rateData())
        assertEquals(UiText(R.string.rate_burden_subtitle, listOf("2.79%")), card.subtitle)
        assertEquals(
            listOf(
                rising("+0.97%p"),
                UiText(R.string.rate_evidence_rates, listOf("4.92%", "3.88%")),
                UiText(R.string.rate_evidence_inflation, listOf("4.1%", "2", "+2.1%p", 10)),
            ),
            card.evidence,
        )
    }

    @Test
    fun `R-69 차이가 음수면 내리는 중`() = runTest {
        val card = rateCard(rateData().set(MoodTestData.REAL_YIELD, "2025-11-21", 2.97))
        assertEquals(
            UiText(R.string.rate_evidence_year_change, listOf("-0.18%p", UiText(R.string.rate_evidence_falling))),
            card.evidence[0],
        )
    }

    @Test
    fun `R-69 적는 값이 0이면 1년 전과 같음`() = runTest {
        val same = UiText(R.string.rate_evidence_year_same)
        assertEquals(same, rateCard(rateData().set(MoodTestData.REAL_YIELD, "2025-11-21", 2.79)).evidence[0])
        assertEquals(same, rateCard(rateData().set(MoodTestData.REAL_YIELD, "2025-11-21", 2.786)).evidence[0])
    }

    @Test
    fun `R-69 차이 +0_005는 +0_01%p로 오르는 중`() = runTest {
        val builder = rateData()
            .set(MoodTestData.REAL_YIELD, "2026-11-23", 1.0)
            .set(MoodTestData.REAL_YIELD, "2025-11-21", 0.995)
        assertEquals(rising("+0.01%p"), rateCard(builder).evidence[0])
    }

    @Test
    fun `R-69 물가 1_8%, 9월 값`() = runTest {
        val builder = rateData()
            .remove(MoodTestData.CONSUMER_PRICE, "2026-10-01")
            .set(MoodTestData.CONSUMER_PRICE, "2026-09-01", 305.4)
        assertEquals(
            UiText(R.string.rate_evidence_inflation, listOf("1.8%", "2", "-0.2%p", 9)),
            rateCard(builder).evidence[2],
        )
    }

    @Test
    fun `R-69 데이터 A의 글`() = runTest {
        assertEquals(
            listOf(
                UiText(R.string.rate_evidence_year_same),
                UiText(R.string.rate_evidence_rates, listOf("4.00%", "3.50%")),
                UiText(R.string.rate_evidence_inflation, listOf("3.0%", "2", "+1.0%p", 10)),
            ),
            rateCard(dataA()).evidence,
        )
    }

    // R-70 날짜를 붙일지와 그 글

    @Test
    fun `R-70 실질금리, 10년물, 기준금리의 날짜가 기준 날짜와 다르면 값 뒤에 날짜를 붙인다`() = runTest {
        val builder = rateData()
            .remove(MoodTestData.REAL_YIELD, "2026-11-23")
            .set(MoodTestData.REAL_YIELD, "2026-11-21", 2.79)
            .remove(MoodTestData.TREASURY_10Y, "2026-11-23")
            .set(MoodTestData.TREASURY_10Y, "2026-11-21", 4.92)
            .remove(MoodTestData.POLICY_RATE, "2026-11-23")
            .set(MoodTestData.POLICY_RATE, "2026-11-21", 3.88)
        stored(builder)
        val card = newViewModel().mood.rateBurden
        assertEquals(UiText(R.string.rate_burden_subtitle, listOf(withDate("2.79%", 11, 21))), card.subtitle)
        assertEquals(
            UiText(R.string.rate_evidence_rates, listOf(withDate("4.92%", 11, 21), withDate("3.88%", 11, 21))),
            card.evidence[1],
        )
        // 첫 줄과 점수에는 날짜가 없다.
        assertEquals(rising("+0.97%p"), card.evidence[0])
        // 점수: 실질금리의 날(11월 21일)까지 세 날의 위치 0.5, 0.5, 1.0의 평균
        assertEquals(scoreText(67, R.string.valuation_middle), card.scoreText)
    }

    @Test
    fun `R-70 기준금리만 날짜가 다르면 기준금리에만 붙인다`() = runTest {
        val builder = rateData()
            .remove(MoodTestData.POLICY_RATE, "2026-11-23")
            .set(MoodTestData.POLICY_RATE, "2026-11-20", 3.88)
        val card = rateCard(builder)
        assertEquals(UiText(R.string.rate_burden_subtitle, listOf("2.79%")), card.subtitle)
        assertEquals(
            UiText(R.string.rate_evidence_rates, listOf("4.92%", withDate("3.88%", 11, 20))),
            card.evidence[1],
        )
    }
}

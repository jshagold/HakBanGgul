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
import com.hak.hakbanggul.feature.marketmood.domain.DefaultMoodRules
import com.hak.hakbanggul.feature.marketmood.domain.FakeClock
import com.hak.hakbanggul.feature.marketmood.domain.FakeMarketDataRepository
import com.hak.hakbanggul.feature.marketmood.domain.FakeMoodRulesRepository
import com.hak.hakbanggul.feature.marketmood.domain.FetchResult
import com.hak.hakbanggul.feature.marketmood.domain.MarketDataRefresher
import com.hak.hakbanggul.feature.marketmood.domain.MarketStage
import com.hak.hakbanggul.feature.marketmood.domain.MoodCalculator
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData.HIGH_YIELD
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData.YIELD_CURVE
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData.dataA
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData.dataB
import com.hak.hakbanggul.feature.marketmood.domain.ObserveMoodUseCase
import com.hak.hakbanggul.feature.marketmood.domain.ObserveSourceKeysUseCase
import com.hak.hakbanggul.feature.marketmood.domain.RefreshMarketDataUseCase
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
        val calculator = MoodCalculator()
        val observeMood = ObserveMoodUseCase(rulesRepository, repository, calculator)
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
        repository.fetchAnswer = { _, series, _ ->
            pendingFetch.await()
            FetchResult.Success(remote.build(series.map { it.seriesId }))
        }
    }

    private fun failFetch(error: DataFetchError) {
        repository.fetchAnswer = { _, _, _ -> FetchResult.Failure(error) }
    }

    private fun succeedFetch() {
        val remote = repository.remote
        repository.fetchAnswer = { _, series, _ -> FetchResult.Success(remote.build(series.map { it.seriesId })) }
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
                R.string.indicator_broad_market,
                R.string.indicator_nasdaq_100,
                R.string.indicator_technology,
                R.string.indicator_semiconductors,
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
        assertEquals("+0.41", mood.compositeScoreText)
        assertEquals(ScoreTone.POSITIVE, mood.compositeTone)
        assertEquals(UiText(R.string.stage_neutral), mood.computedStageName)
    }

    @Test
    fun `R-06 데이터 B의 눈금 위치`() = runTest {
        stored(dataB())
        assertEquals(0.70625f, newViewModel().mood.gaugePosition, 1e-6f)
    }

    @Test
    fun `R-06 데이터 A의 눈금 위치`() = runTest {
        stored(dataA())
        assertEquals(1.0f, newViewModel().mood.gaugePosition, 1e-6f)
    }

    @Test
    fun `R-06 위 경계가 0_6이면 눈금 위치도 따라 바뀐다`() = runTest {
        stored(dataB())
        rulesRepository.rules.value = DefaultMoodRules.rules.copy(upperBoundary = 0.6)
        assertEquals(0.671875f, newViewModel().mood.gaugePosition, 1e-6f)
    }

    @Test
    fun `R-06 현재 상태 -0_5의 눈금 위치`() = runTest {
        stored(
            dataA().setStocks("2026-11-23", 89.9).set(YIELD_CURVE, "2026-11-23", -0.01).set(HIGH_YIELD, "2026-11-23", 5.00),
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
        assertEquals("+0.38", groups[0].scoreText)
        assertEquals(ScoreTone.POSITIVE, groups[0].scoreTone)
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

        val broad = viewModel.indicator(R.string.indicator_broad_market)
        assertEquals(UiText(R.string.indicator_broad_market_suffix), broad.suffix)
        assertEquals(UiText(R.string.evidence_price, listOf("0.0%", "0.0%")), broad.evidence)
        assertEquals("+1.0", broad.scoreText)

        val technology = viewModel.indicator(R.string.indicator_technology)
        assertEquals(UiText(R.string.indicator_technology_suffix), technology.suffix)
        assertEquals(UiText(R.string.evidence_price, listOf("0.0%", "-16.7%")), technology.evidence)
        assertEquals("+0.5", technology.scoreText)

        val semis = viewModel.indicator(R.string.indicator_semiconductors)
        assertEquals(UiText(R.string.indicator_semiconductors_suffix), semis.suffix)
        assertEquals(UiText(R.string.evidence_price, listOf("-19.9%", "-20.0%")), semis.evidence)
        assertEquals("-1.0", semis.scoreText)
        assertEquals(ScoreTone.NEGATIVE, semis.scoreTone)

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
        assertEquals("+1.00", viewModel.mood.compositeScoreText)
        repository.remote = dataB()

        viewModel.onRefreshClick()
        advanceUntilIdle()

        assertEquals("+0.41", viewModel.mood.compositeScoreText)
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
}

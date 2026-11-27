package com.hak.hakbanggul.feature.marketmood.presentation

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import org.junit.Assert.assertFalse
import com.hak.hakbanggul.feature.marketmood.domain.ObserveMarketOverviewUseCase
import com.hak.hakbanggul.feature.marketmood.domain.MarketOverviewCalculator
import com.hak.hakbanggul.feature.marketmood.domain.DEFAULT_PACING
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
import com.hak.hakbanggul.feature.marketmood.domain.FetchFreshness
import com.hak.hakbanggul.feature.marketmood.domain.FetchPacing
import com.hak.hakbanggul.feature.marketmood.domain.RefreshStamp
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
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds
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

    // overviewDispatcher: 한 벌을 계산하는 디스패처. 돌지 않는 디스패처를 넘기면 한 벌이 오지 않는다.
    // pacing: 받기의 나눠 받기. 기다림 띠를 볼 때는 묶음 둘 규칙으로 만든다.
    private fun TestScope.newViewModel(
        overviewDispatcher: CoroutineDispatcher = UnconfinedTestDispatcher(testScheduler),
        pacing: FetchPacing = DEFAULT_PACING,
    ): MarketMoodViewModel {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val clock = FakeClock(now)
        val calculator = MoodCalculator(RegimeCalculator(), ValuationCalculator())
        val observeMood = ObserveMoodUseCase(rulesRepository, repository, calculator, UnconfinedTestDispatcher(testScheduler))
        val observeKeys = ObserveSourceKeysUseCase(rulesRepository, keys)
        val appScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val overviewScope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))
        val observeOverview = ObserveMarketOverviewUseCase(
            rulesRepository,
            repository,
            MarketOverviewCalculator(calculator, RegimeCalculator(), ValuationCalculator()),
            overviewDispatcher,
            overviewScope,
        )
        val refresher = MarketDataRefresher(
            RefreshMarketDataUseCase(repository, rulesRepository, calculator, FetchFreshness(), pacing, clock),
            observeMood,
            observeKeys,
            repository,
            rulesRepository,
            FetchFreshness(),
            clock,
            appScope,
        )
        return MarketMoodViewModel(observeOverview, observeKeys, refresher, MoodUiMapper()).also { advanceUntilIdle() }
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
        assertEquals("+0.77", viewModel.mood.regime.compositeScoreText)
        repository.remote = dataB()

        viewModel.onRefreshClick()
        advanceUntilIdle()

        assertEquals("+0.15", viewModel.mood.regime.compositeScoreText)
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

    // R-05 첫 화면의 구성

    private fun assertMainComposition(mood: MoodUiState) {
        assertEquals(UiText(R.string.regime_broad_market), mood.regime.title.args[0])
        assertEquals(UiText(R.string.valuation_title, listOf(UiText(R.string.period_years, listOf(2)))), mood.valuationTitle)
        assertEquals(UiText(R.string.valuation_description, listOf(UiText(R.string.period_years, listOf(2)))), mood.valuationDescription)
        listOf(mood.pricePosition, mood.rateBurden).forEach {
            assertTrue(it.evidence.isEmpty())
            assertTrue(it.showLink)
        }
        assertEquals(UiText(R.string.sectors_header_name), mood.sectorHeader.nameText)
        assertEquals(listOf("nasdaq_100", "semiconductors", "technology"), mood.sectors.map { it.targetId })
    }

    @Test
    fun `R-05 데이터 A면 전체 장세 카드, 매매 판단 제목과 두 카드, 섹터 머리와 줄 셋`() = runTest {
        stored(dataA())
        assertMainComposition(newViewModel().mood)
    }

    @Test
    fun `R-05 안내 띠가 있을 때도 같다`() = runTest {
        stored(dataA())
        val viewModel = afterFailure(DataFetchError.NO_CONNECTION)
        assertNotNull(viewModel.uiState.value.banner)
        assertMainComposition(viewModel.mood)
    }

    @Test
    fun `R-05 받는 중에도 같다`() = runTest {
        stored(dataA())
        holdFetch()
        val viewModel = newViewModel()

        viewModel.onRefreshClick()
        runCurrent()

        assertEquals(RefreshButtonState.REFRESHING, viewModel.uiState.value.refreshButton)
        assertMainComposition(viewModel.mood)
        pendingFetch.complete(Unit)
        advanceUntilIdle()
    }

    // R-84, R-106 섹터를 아직 받지 못함

    private fun onlyBroadMarket() = dataA().removeSeries("QQQ").removeSeries("SMH").removeSeries("XLK")

    @Test
    fun `R-106 SPY와 FRED만 받아 둔 채 받는 중이면 판정이 보이고 섹터 줄은 모두 받는 중`() = runTest {
        stored(onlyBroadMarket())
        holdFetch()
        val viewModel = newViewModel()

        viewModel.onRefreshClick()
        runCurrent()

        assertTrue(viewModel.body is MoodBody.Assessment)
        viewModel.mood.sectors.forEach {
            assertEquals(SectorRowContent.Status(UiText(R.string.sector_loading)), it.content)
            assertFalse(it.clickable)
        }
        pendingFetch.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `R-106 받기가 실패로 끝나면 섹터 줄은 모두 받지 못함이고 안내 띠가 있다`() = runTest {
        stored(onlyBroadMarket())
        val viewModel = afterFailure(DataFetchError.NO_CONNECTION)

        assertNotNull(viewModel.uiState.value.banner)
        viewModel.mood.sectors.forEach {
            assertEquals(SectorRowContent.Status(UiText(R.string.sector_failed)), it.content)
            assertFalse(it.clickable)
        }
    }

    // R-04, R-112, R-22, R-24 계산 중

    @Test
    fun `R-04 계산 중이고 받는 중이 아니면 Calculating, ENABLED, 띠 없음이고 디스패처를 돌리면 Assessment`() = runTest {
        stored(dataA())
        val dispatcher = HeldDispatcher(StandardTestDispatcher(testScheduler))
        val viewModel = newViewModel(dispatcher)

        assertEquals(MoodBody.Calculating, viewModel.body)
        assertEquals(RefreshButtonState.ENABLED, viewModel.uiState.value.refreshButton)
        assertNull(viewModel.uiState.value.banner)
        assertNull(viewModel.uiState.value.waitingBanner)

        dispatcher.release()
        runCurrent()

        assertTrue(viewModel.body is MoodBody.Assessment)
    }

    @Test
    fun `R-22 계산 중에 받는 중이면 Calculating이고 새로 고침 자리가 REFRESHING이다`() = runTest {
        stored(dataA())
        holdFetch()
        val viewModel = newViewModel(HeldDispatcher(StandardTestDispatcher(testScheduler)))

        viewModel.onRefreshClick()
        runCurrent()

        assertEquals(MoodBody.Calculating, viewModel.body)
        assertEquals(RefreshButtonState.REFRESHING, viewModel.uiState.value.refreshButton)
        assertNull(viewModel.uiState.value.banner)
        pendingFetch.complete(Unit)
        advanceUntilIdle()
        assertEquals(RefreshButtonState.ENABLED, viewModel.uiState.value.refreshButton)
    }

    @Test
    fun `R-112 판정을 본 뒤 값이 바뀌어 다시 계산하는 동안 판정은 그대로이고 돌리면 새 판정`() = runTest {
        stored(dataA())
        val dispatcher = HeldDispatcher(StandardTestDispatcher(testScheduler))
        val viewModel = newViewModel(dispatcher)
        dispatcher.release()
        runCurrent()
        assertEquals("+0.77", viewModel.mood.regime.compositeScoreText)
        dispatcher.hold()

        stored(dataB())
        runCurrent()

        assertEquals("+0.77", viewModel.mood.regime.compositeScoreText)

        dispatcher.release()
        runCurrent()

        assertEquals("+0.15", viewModel.mood.regime.compositeScoreText)
    }

    @Test
    fun `R-04 받아 둔 값 없이 받는 중이면 계산 중에는 Calculating, 돌리면 Loading`() = runTest {
        holdFetch()
        val dispatcher = HeldDispatcher(StandardTestDispatcher(testScheduler))
        val viewModel = newViewModel(dispatcher)
        viewModel.onRefreshClick()
        runCurrent()

        assertEquals(MoodBody.Calculating, viewModel.body)

        dispatcher.release()
        runCurrent()

        assertEquals(MoodBody.Loading, viewModel.body)
        pendingFetch.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `R-22 한 벌이 오기 전이어도 키가 없으면 키 안내 카드다`() = runTest {
        keys.set(FRED, MISSING)
        val viewModel = newViewModel(NeverDispatcher)
        assertTrue(viewModel.body is MoodBody.KeyGuide)
        assertEquals(RefreshButtonState.DISABLED, viewModel.uiState.value.refreshButton)
    }

    // R-114, R-02, R-18 띠 고르기 (묶음 둘 규칙, 데이터 A, 넷 모두 최신 아님)

    private val twoBatches = FetchPacing(2, 65.seconds, 10.seconds)

    private val waitingTitle2 = UiText(R.string.waiting_banner_title, listOf(2))
    private val waitingMessage = UiText(R.string.waiting_banner_message)

    // 1초씩 가상 시간을 보내며 그 조건이 될 때까지 기다린다.
    private fun TestScope.advanceUntil(limitSeconds: Int = 300, condition: () -> Boolean) {
        repeat(limitSeconds) {
            if (condition()) return
            advanceTimeBy(1_000)
            runCurrent()
        }
        assertTrue("조건이 되지 않음", condition())
    }

    @Test
    fun `R-114 첫 묶음을 보낸 뒤에만 기다림 띠가 있고 둘째 묶음을 보내면 사라진다`() = runTest {
        stored(dataA())
        repository.stamps[RefreshStamp.TWELVE_DATA_REQUESTED] = Instant.parse("2026-11-24T00:59:30Z")
        val viewModel = newViewModel(pacing = twoBatches)
        val states = mutableListOf<MarketMoodUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.toList(states) }

        viewModel.onRefreshClick()
        runCurrent()
        advanceTimeBy(34_000)
        runCurrent()
        assertNull(viewModel.uiState.value.waitingBanner)

        advanceTimeBy(1_001)
        runCurrent()
        val waiting = viewModel.uiState.value
        assertEquals(WaitingBannerUiState(waitingTitle2, waitingMessage), waiting.waitingBanner)
        assertNull(waiting.banner)

        advanceTimeBy(64_000)
        runCurrent()
        assertEquals(WaitingBannerUiState(waitingTitle2, waitingMessage), viewModel.uiState.value.waitingBanner)

        advanceTimeBy(1_000)
        runCurrent()
        assertNull(viewModel.uiState.value.waitingBanner)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.waitingBanner)
        assertNull(viewModel.uiState.value.banner)
        assertTrue(states.none { it.waitingBanner != null && it.banner != null })
    }

    @Test
    fun `R-114 R-18 안내 띠가 있어도 첫 묶음을 보낸 뒤에는 기다림 띠만, 둘째 묶음이 실패하면 안내 띠만`() = runTest {
        stored(dataA())
        failFetch(DataFetchError.NO_CONNECTION)
        val viewModel = newViewModel(pacing = twoBatches)
        viewModel.onRefreshClick()
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.banner)

        val remote = repository.remote
        repository.fetchAnswer = { _, requests ->
            val ids = requests.map { it.series.seriesId }
            if ("SMH" in ids) FetchResult.Failure(DataFetchError.NO_CONNECTION) else FetchResult.Success(remote.build(ids))
        }
        viewModel.onRefreshClick()
        runCurrent()
        advanceUntil { viewModel.uiState.value.waitingBanner != null }

        assertNull(viewModel.uiState.value.banner)

        advanceUntilIdle()

        assertNull(viewModel.uiState.value.waitingBanner)
        assertEquals(UiText(R.string.banner_stale_title), viewModel.uiState.value.banner?.title)
    }

    @Test
    fun `R-114 받아 둔 값이 없어 Loading이면 첫 묶음을 보낸 뒤에도 기다림 띠가 없다`() = runTest {
        holdFetch()
        val viewModel = newViewModel(pacing = twoBatches)

        viewModel.onRefreshClick()
        runCurrent()

        assertEquals(MoodBody.Loading, viewModel.body)
        assertNull(viewModel.uiState.value.waitingBanner)
        pendingFetch.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `R-114 ETF 넷 최신이면 받는 동안 기다림 띠가 늘 없다`() = runTest {
        stored(dataA())
        repository.setFetchedAt(MoodTestData.STOCKS, Instant.parse("2026-11-23T22:00:00Z"))
        val viewModel = newViewModel(pacing = twoBatches)
        val states = mutableListOf<MarketMoodUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.toList(states) }

        viewModel.onRefreshClick()
        advanceUntilIdle()

        assertTrue(states.any { it.refreshButton == RefreshButtonState.REFRESHING })
        assertTrue(states.all { it.waitingBanner == null })
    }
}

// 넘겨받은 일을 돌리지 않는 디스패처. 한 벌이 오지 않는 때를 만든다.
private object NeverDispatcher : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) = Unit
}

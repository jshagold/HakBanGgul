package com.hak.hakbanggul.feature.marketmood.presentation

import androidx.lifecycle.SavedStateHandle
import com.hak.hakbanggul.R
import com.hak.hakbanggul.core.model.DataSource.FRED
import com.hak.hakbanggul.core.model.DataSource.TWELVE_DATA
import com.hak.hakbanggul.core.network.ApiKeyAvailability.AVAILABLE
import com.hak.hakbanggul.core.network.ApiKeyAvailability.INVALID
import com.hak.hakbanggul.core.network.ApiKeyAvailability.MISSING
import com.hak.hakbanggul.core.network.FakeApiKeyProvider
import com.hak.hakbanggul.core.ui.UiText
import com.hak.hakbanggul.feature.marketmood.domain.FakeMarketDataRepository
import com.hak.hakbanggul.feature.marketmood.domain.FakeMoodRulesRepository
import com.hak.hakbanggul.feature.marketmood.domain.MarketOverviewCalculator
import com.hak.hakbanggul.feature.marketmood.domain.MoodCalculator
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData.dataA
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData.dataB
import com.hak.hakbanggul.feature.marketmood.domain.ObserveMarketOverviewUseCase
import com.hak.hakbanggul.feature.marketmood.domain.ObserveSourceKeysUseCase
import com.hak.hakbanggul.feature.marketmood.domain.RegimeCalculator
import com.hak.hakbanggul.feature.marketmood.domain.ValuationCalculator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// 데이터 A, 키 둘 AVAILABLE, 시험용 규칙
@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModelsTest {

    private val repository = FakeMarketDataRepository().apply { setStored(dataA(), null) }
    private val rulesRepository = FakeMoodRulesRepository()
    private val keys = FakeApiKeyProvider()
    private val mapper = MoodUiMapper()

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private lateinit var overview: ObserveMarketOverviewUseCase
    private lateinit var sourceKeys: ObserveSourceKeysUseCase

    private fun TestScope.setUp(dispatcher: CoroutineDispatcher = UnconfinedTestDispatcher(testScheduler)) {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val calculator = MoodCalculator(RegimeCalculator(), ValuationCalculator())
        overview = ObserveMarketOverviewUseCase(
            rulesRepository,
            repository,
            MarketOverviewCalculator(calculator, RegimeCalculator(), ValuationCalculator()),
            dispatcher,
            CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
        )
        sourceKeys = ObserveSourceKeysUseCase(rulesRepository, keys)
    }

    private fun handle(targetId: String) = SavedStateHandle(mapOf(TARGET_ID_ARG to targetId))

    private fun TestScope.regimeVm(id: String = "broad_market") = RegimeViewModel(handle(id), overview, sourceKeys, mapper).also { runCurrent() }
    private fun TestScope.priceVm(id: String = "broad_market") = PricePositionViewModel(handle(id), overview, sourceKeys, mapper).also { runCurrent() }
    private fun TestScope.rateVm() = RateBurdenViewModel(overview, sourceKeys, mapper).also { runCurrent() }
    private fun TestScope.sectorVm(id: String = "semiconductors") = SectorViewModel(handle(id), overview, sourceKeys, mapper).also { runCurrent() }

    private fun <T> StateFlow<DetailUiState<T>>.shown(): T = (value as DetailUiState.Shown<T>).content

    private fun months(n: Int) = UiText(R.string.period_months, listOf(n))
    private fun years(n: Int) = UiText(R.string.period_years, listOf(n))
    private fun withSymbol(nameRes: Int, symbol: String) = UiText(R.string.target_with_symbol, listOf(UiText(nameRes), symbol))

    // R-87, R-89, R-90, R-93 화면의 구성

    @Test
    fun `R-87 장세 화면`() = runTest {
        setUp()
        val broad = regimeVm().uiState.shown()
        assertEquals(UiText(R.string.regime_broad_market), broad.title)
        assertNull(broad.symbol)
        assertFalse(broad.main.showLink)
        assertEquals(UiText(R.string.evidence_basis, listOf(months(3))), broad.evidenceBasis)
        assertEquals(2, broad.groups.size)
        assertEquals(2, broad.otherPeriods.size)

        val semis = regimeVm("semiconductors").uiState.shown()
        assertEquals(UiText(R.string.regime_sector, listOf(UiText(R.string.target_semiconductors))), semis.title)
        assertEquals("SMH", semis.symbol)
    }

    @Test
    fun `R-89 주가 위치 화면`() = runTest {
        setUp()
        val broad = priceVm().uiState.shown()
        assertEquals(withSymbol(R.string.target_broad_market, "SPY"), broad.targetText)
        assertEquals(UiText(R.string.valuation_title, listOf(years(2))), broad.valuationTitle)
        assertEquals(2, broad.main.evidence.size)
        assertFalse(broad.main.showLink)
        assertEquals(3, broad.otherPeriods.size)
        assertEquals(withSymbol(R.string.target_semiconductors, "SMH"), priceVm("semiconductors").uiState.shown().targetText)
    }

    @Test
    fun `R-90 금리 부담 화면`() = runTest {
        setUp()
        val state = rateVm().uiState.shown()
        assertEquals(UiText(R.string.valuation_title, listOf(years(2))), state.valuationTitle)
        assertEquals(3, state.main.evidence.size)
        assertFalse(state.main.showLink)
        assertEquals(3, state.otherPeriods.size)
    }

    @Test
    fun `R-93 섹터 화면`() = runTest {
        setUp()
        val state = sectorVm().uiState.shown()
        assertEquals(UiText(R.string.target_semiconductors), state.name)
        assertEquals("SMH", state.symbol)
        assertEquals(
            UiText(R.string.regime_card_title, listOf(UiText(R.string.regime_sector, listOf(UiText(R.string.target_semiconductors))), months(3))),
            state.regime.title,
        )
        assertTrue(state.regime.showLink)
        assertEquals(UiText(R.string.valuation_title, listOf(years(2))), state.valuationTitle)
        assertEquals(withSymbol(R.string.target_semiconductors, "SMH"), state.pricePosition.subtitle)
        assertTrue(state.pricePosition.evidence.isEmpty())
        assertTrue(state.pricePosition.showLink)
    }

    // R-99 새 값으로 바뀜, 첫 화면으로 돌아감

    @Test
    fun `R-99 받아 둔 값이 바뀌면 네 화면이 새 한 벌로 바뀐다`() = runTest {
        setUp()
        val regime = regimeVm()
        val price = priceVm()
        val rate = rateVm()
        val sector = sectorVm()
        val before = listOf(price.uiState.shown().main, rate.uiState.shown().main, sector.uiState.shown().regime)

        repository.setStored(dataB().set(MoodTestData.REAL_YIELD, "2026-11-23", 2.79), null)
        runCurrent()

        assertEquals("+0.15", regime.uiState.shown().main.compositeScoreText)
        assertNotEquals(before[0], price.uiState.shown().main)
        assertEquals(UiText(R.string.rate_burden_subtitle, listOf("2.79%")), rate.uiState.shown().main.subtitle)
        assertEquals("-0.55", sector.uiState.shown().regime.compositeScoreText)
        assertNotEquals(before[2], sector.uiState.shown().regime)
    }

    @Test
    fun `R-99 키가 틀림이나 없음이 되면 네 화면 모두 첫 화면으로 돌아간다`() = runTest {
        setUp()
        val states = listOf(regimeVm().uiState, priceVm().uiState, rateVm().uiState, sectorVm().uiState)
        states.forEach { assertTrue(it.value is DetailUiState.Shown<*>) }

        keys.set(TWELVE_DATA, INVALID)
        runCurrent()
        states.forEach { assertEquals(DetailUiState.BackToMain, it.value) }

        keys.set(TWELVE_DATA, AVAILABLE)
        keys.set(FRED, MISSING)
        runCurrent()
        states.forEach { assertEquals(DetailUiState.BackToMain, it.value) }
    }

    @Test
    fun `R-99 받아 둔 값에서 SMH가 빠지면 SMH의 화면은 돌아가고 전체 시장의 장세 화면은 그대로`() = runTest {
        setUp()
        val sector = sectorVm()
        val regimeSemis = regimeVm("semiconductors")
        val priceSemis = priceVm("semiconductors")
        val regimeBroad = regimeVm()
        assertTrue(sector.uiState.value is DetailUiState.Shown<*>)

        repository.setStored(dataA().removeSeries("SMH"), null)
        runCurrent()

        assertEquals(DetailUiState.BackToMain, sector.uiState.value)
        assertEquals(DetailUiState.BackToMain, regimeSemis.uiState.value)
        assertEquals(DetailUiState.BackToMain, priceSemis.uiState.value)
        assertTrue(regimeBroad.uiState.value is DetailUiState.Shown<*>)
    }

    @Test
    fun `R-99 규칙에 없는 대상이면 첫 화면으로 돌아간다`() = runTest {
        setUp()
        listOf(regimeVm("unknown").uiState, priceVm("unknown").uiState, sectorVm("unknown").uiState).forEach {
            assertEquals(DetailUiState.BackToMain, it.value)
        }
    }

    // R-111, R-99 계산 중

    private fun symbolText(symbol: String) = UiText(R.string.detail_target_symbol, listOf(symbol))

    private fun TestScope.calculatingVms(): List<StateFlow<out DetailUiState<*>>> = listOf(
        RegimeViewModel(handle("broad_market"), overview, sourceKeys, mapper).uiState,
        RegimeViewModel(handle("semiconductors"), overview, sourceKeys, mapper).uiState,
        PricePositionViewModel(handle("broad_market"), overview, sourceKeys, mapper).uiState,
        RateBurdenViewModel(overview, sourceKeys, mapper).uiState,
        SectorViewModel(handle("semiconductors"), overview, sourceKeys, mapper).uiState,
    )

    @Test
    fun `R-111 계산 중이면 네 화면 모두 위쪽 줄의 이름과 대상으로 Calculating`() = runTest {
        val dispatcher = HeldDispatcher(StandardTestDispatcher(testScheduler))
        setUp(dispatcher)

        val states = calculatingVms().map { it.value }

        assertEquals(
            listOf(
                DetailUiState.Calculating(UiText(R.string.regime_broad_market), null),
                DetailUiState.Calculating(UiText(R.string.regime_sector, listOf(UiText(R.string.target_semiconductors))), symbolText("SMH")),
                DetailUiState.Calculating(UiText(R.string.price_position_name), withSymbol(R.string.target_broad_market, "SPY")),
                DetailUiState.Calculating(UiText(R.string.rate_burden_name), null),
                DetailUiState.Calculating(UiText(R.string.target_semiconductors), symbolText("SMH")),
            ),
            states,
        )
    }

    @Test
    fun `R-111 R-99 디스패처를 돌리면 BackToMain 없이 Shown이 되고 위쪽 줄 글은 Calculating의 것과 같다`() = runTest {
        val dispatcher = HeldDispatcher(StandardTestDispatcher(testScheduler))
        setUp(dispatcher)
        val flows = calculatingVms()
        val histories = flows.map { flow ->
            mutableListOf<DetailUiState<*>>().also { list -> backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.toList(list) } }
        }

        dispatcher.release()
        runCurrent()

        histories.forEach { history ->
            assertTrue(history.last() is DetailUiState.Shown<*>)
            assertTrue(history.none { it is DetailUiState.BackToMain })
        }
        val calculating = histories.map { it.first() as DetailUiState.Calculating }
        val broadRegime = (histories[0].last() as DetailUiState.Shown<*>).content as RegimeDetailUiState
        assertEquals(calculating[0].title, broadRegime.title)
        assertNull(broadRegime.symbol)
        val semisRegime = (histories[1].last() as DetailUiState.Shown<*>).content as RegimeDetailUiState
        assertEquals(calculating[1].title, semisRegime.title)
        assertEquals(calculating[1].target, symbolText(semisRegime.symbol!!))
        val price = (histories[2].last() as DetailUiState.Shown<*>).content as PricePositionDetailUiState
        assertEquals(calculating[2].target, price.targetText)
        val sector = (histories[4].last() as DetailUiState.Shown<*>).content as SectorUiState
        assertEquals(calculating[4].title, sector.name)
        assertEquals(calculating[4].target, symbolText(sector.symbol))
    }

    @Test
    fun `R-111 계산 중이어도 Twelve Data 키가 틀림이면 BackToMain`() = runTest {
        keys.set(TWELVE_DATA, INVALID)
        setUp(HeldDispatcher(StandardTestDispatcher(testScheduler)))
        calculatingVms().forEach { assertEquals(DetailUiState.BackToMain, it.value) }
    }

    @Test
    fun `R-111 계산 중이어도 규칙에 없는 대상이면 BackToMain`() = runTest {
        setUp(HeldDispatcher(StandardTestDispatcher(testScheduler)))
        listOf(
            RegimeViewModel(handle("unknown"), overview, sourceKeys, mapper).uiState,
            PricePositionViewModel(handle("unknown"), overview, sourceKeys, mapper).uiState,
            SectorViewModel(handle("unknown"), overview, sourceKeys, mapper).uiState,
        ).forEach { assertEquals(DetailUiState.BackToMain, it.value) }
    }

    @Test
    fun `R-111 R-99 받아 둔 값이 없으면 계산 중에는 Calculating, Ready(null)이 되면 BackToMain`() = runTest {
        repository.setStored(null, null)
        val dispatcher = HeldDispatcher(StandardTestDispatcher(testScheduler))
        setUp(dispatcher)
        val flows = calculatingVms()
        flows.forEach { assertTrue(it.value is DetailUiState.Calculating) }

        dispatcher.release()
        runCurrent()

        flows.forEach { assertEquals(DetailUiState.BackToMain, it.value) }
    }
}

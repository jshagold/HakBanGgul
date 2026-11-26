package com.hak.hakbanggul.feature.marketmood.domain

import com.hak.hakbanggul.core.model.DataSource.FRED
import com.hak.hakbanggul.core.model.DataSource.TWELVE_DATA
import com.hak.hakbanggul.core.network.ApiKeyAvailability.AVAILABLE
import com.hak.hakbanggul.core.network.FakeApiKeyProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ObserveSourceKeysUseCaseTest {

    // R-15 규칙이 쓰는 출처

    @Test
    fun `R-15 장세 항목이 모두 TWELVE_DATA여도 매매 판단의 FRED 시리즈가 있으면 두 출처가 순서대로 나온다`() = runTest {
        val base = DefaultMoodRules.rules
        // 장세의 항목에서 FRED 시리즈를 쓰는 묶음을 빼 대상의 종가(TWELVE_DATA)만 남긴다.
        val rules = base.copy(
            regime = base.regime.copy(
                groups = base.regime.groups.filter { group ->
                    group.indicators.all { (it.input as? IndicatorInput.Series)?.series?.source != FRED }
                },
            ),
        )
        assertEquals(listOf("stocks"), rules.regime.groups.map { it.id })
        val useCase = ObserveSourceKeysUseCase(
            FakeMoodRulesRepository(rules),
            FakeApiKeyProvider(mapOf(TWELVE_DATA to AVAILABLE, FRED to AVAILABLE)),
        )

        val result = useCase().first()

        assertEquals(listOf(TWELVE_DATA, FRED), result.keys.toList())
        assertEquals(listOf(AVAILABLE, AVAILABLE), result.values.toList())
    }
}

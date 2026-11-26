package com.hak.hakbanggul.feature.marketmood.domain

import java.time.LocalDate

// 용어: 콘텐츠
// 기간 하나의 콘텐츠(장세, 주가 위치, 금리 부담). value가 null이면 그 콘텐츠를 낼 수 없는 것이다.
data class PeriodContent<T>(
    val periodId: String,
    val length: PeriodLength,
    val isMain: Boolean,
    val value: T?,
)

// 대상 하나의 콘텐츠. 목록은 규칙의 기간 순서(짧은 기간부터)이고 메인도 들어 있다.
data class TargetOverview(
    val target: MarketTarget,
    // 그 대상의 가장 최근 거래일. 종가가 없으면 null
    val asOfDate: LocalDate?,
    val regimes: List<PeriodContent<RegimeAssessment>>,
    val pricePositions: List<PeriodContent<PricePositionAssessment>>,
) {
    fun mainRegime(): RegimeAssessment? = regimes.firstOrNull { it.isMain }?.value

    fun mainPricePosition(): PricePositionAssessment? = pricePositions.firstOrNull { it.isMain }?.value

    // 메인 장세와 메인 주가 위치가 모두 있으면 참
    fun isMainAvailable(): Boolean = mainRegime() != null && mainPricePosition() != null
}

// 시장 분위기 화면들이 보는 한 벌. 다른 기능에는 내주지 않는다(다른 기능은 MoodAssessment를 받는다).
// rules는 화면이 문구의 숫자(일수, 기간의 이름, 경계, 완충)를 끼우는 데 쓴다.
data class MarketOverview(
    val rules: MoodRules,
    // 전체 시장의 메인 판정. broadMarket의 메인 장세·주가 위치, rateBurdens의 메인과 같은 값이다.
    val mood: MoodAssessment,
    val broadMarket: TargetOverview,
    // 규칙의 섹터 순서
    val sectors: List<TargetOverview>,
    val rateBurdens: List<PeriodContent<RateBurdenAssessment>>,
) {
    fun target(id: String): TargetOverview? = (listOf(broadMarket) + sectors).firstOrNull { it.target.id == id }
}

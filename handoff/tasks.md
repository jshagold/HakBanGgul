# Tasks: 시장 분위기 버전업-003

기능: 시장 분위기
변경: 버전업-003 (CS-005)
기준 Plan: 3.0.0
상태: 구현 중

| 전달 | 날짜 | 이유 |
|---|---|---|
| 1 | 2026-11-26 | 최초 전달 |

## 1. 개요
시장 분위기는 여러 항목으로 지금 장세(불장, 중립, 약세, 물장)를 판정하고, 지금 장이 평소와 비교해 어디쯤인지(매매 판단: 주가 위치, 금리 부담)를 근거와 함께 보여 주는 첫 화면이다. 매달 꾸준히 사는 장기 투자자가 매일·매달 보는 것이라 '평소'는 짧게(메인 지난 2년) 두고, 기준 기간은 하나만 고르지 않는다: 메인 기간으로 판정을 내 첫 화면과 다른 기능(홈 화면 위젯)에 보이고, 다른 기간도 따로 선 판정으로 보인다. 공식은 하나이고 기간만 다르다.
이번에 할 일: 장세의 주식 묶음을 대상의 ETF 하나와 세 항목(평균 대비, 고점 대비, 평균의 기울기)으로 바꾸고, 대상을 전체 시장(SPY)과 섹터 13개로 늘려 대상마다 장세와 주가 위치를 낸다. 장세는 3개월(메인)·6개월·1년, 주가 위치와 금리 부담은 2년(메인)·1년·3년·5년으로 따로 낸다. 종가 14개를 Twelve Data의 분당 한도에 맞게 두 묶음으로 나눠 받고 묶음마다 저장한다. 화면은 첫 화면(메인 카드 셋과 섹터별 목록)과 카드마다의 상세 화면(장세, 주가 위치, 금리 부담), 섹터 화면으로 나눈다. 문구의 일수와 기간은 규칙의 숫자로 끼운다.
바꾸지 않는 것: 채권 신호 두 항목과 그 점수, 비중 0.7·0.3, 단계 경계, 3거래일 규칙, 매매 판단의 공식(괴리, 위치, 점수, 완충), 받는 때(6시간, 새로 고침, 다시 시도), 실패의 원인과 글, DB 표의 모양, 키를 붙이는 곳(`core/network`). 다른 기능에 내주는 모델(MoodAssessment와 그 매매 판단, ValuationAssessment, RefreshState의 있던 필드, RefreshCondition, RefreshRequestResult)의 모양은 바꾸지 않는다. 설정과 홈 화면 위젯의 앱 코드(`app/src/main/.../feature/settings`, `feature/homewidget`)는 고치지 않는다. 홈 화면 위젯의 테스트만 테스트 데이터, 규칙, 객체 만들기를 맞춘다(단위 1, 3, 4).

## 2. 구현 단위

아래 "시안"은 `https://claude.ai/artifact/LuRSF8bSPDqruAbnUSfye9`다. 단위 6에서 쓴다.

### 단위 1: 규칙과 모델, 장세의 계산, 테스트 데이터

**만들거나 고칠 것**
- 신규 PeriodLength, PeriodUnit (feature/marketmood/domain)
- 신규 MarketTarget (feature/marketmood/domain)
- 신규 RegimeRules, RegimePeriod (feature/marketmood/domain)
- 신규 IndicatorInput (feature/marketmood/domain)
- 수정 IndicatorGroupRule, IndicatorRule (feature/marketmood/domain, `MoodRules.kt`)
- 수정 ScoringRule (feature/marketmood/domain)
- 신규 ValuationPeriod, 수정 ValuationRules (feature/marketmood/domain)
- 수정 MoodRules, DefaultMoodRules (feature/marketmood/domain)
- 신규 RegimeAssessment (feature/marketmood/domain)
- 수정 IndicatorEvidence (feature/marketmood/domain, `MoodAssessment.kt`)
- 수정 DailySeries (feature/marketmood/domain)
- 신규 RegimeCalculator (feature/marketmood/domain)
- 수정 ValuationCalculator (feature/marketmood/domain)
- 수정 MoodCalculator (feature/marketmood/domain)
- 수정 MarketMoodViewModel (feature/marketmood/presentation) — 컴파일되게만. 단위 5에서 다시 만든다
- 수정 IndicatorLabels (feature/marketmood/presentation)
- 수정 `app/src/main/res/values/strings.xml`
- 수정 MoodTestData, MarketMoodFakes (테스트, feature/marketmood/domain)
- 수정 WidgetTestFixture와 위젯 테스트에서 규칙을 고쳐 쓰는 곳 (테스트, feature/homewidget)

**지시**
- 새 용어의 영어 이름: 장세 `Regime`, 대상 `MarketTarget`, 전체 시장 `broadMarket`, 섹터 `sector`, 기간 `Period`, 메인 기간 `mainPeriod`, 기간의 이름 `PeriodLength`, 콘텐츠 `content`, 평균 대비 `aboveAverage`, 고점 대비 `fromHigh`, 평균의 기울기 `averageSlope`, 평균 괴리 `averageDeviation`, 고점 대비 낙폭 `dropFromHigh`, 평균 대비 기준 `averageBand`, 고점 일수 `highDays`, 얕은 고점 기준 `shallowDrop`, 깊은 고점 기준 `deepDrop`, 기울기 일수 `slopeDays`, 나눠 받는 묶음 `batch`, 마지막으로 받기가 끝난 때 `lastFinishedAt`, 전체 시장의 값을 받음 `broadMarketReceived`, 상세 화면 `Detail`. 처음 선언하는 곳 위에 `// 용어: (한국어 이름)` 주석을 단다. 뒤 단위에서 처음 쓰는 이름도 같다. (P-36, P-37)
- 기간의 이름: `enum class PeriodUnit { MONTHS, YEARS }`, `data class PeriodLength(val amount: Int, val unit: PeriodUnit)`. "3개월", "2년"처럼 문구에 끼우는 데만 쓰고 계산에 쓰지 않는다. (R-107, P-37)
- 대상: `data class MarketTarget(val id: String, val closeSeries: IndicatorSeries)`. 화면 이름은 Domain에 두지 않는다(단위 5의 TargetLabels). (R-30, P-36)
- 장세의 규칙 (R-100, R-101, R-102, P-13, P-36, P-37)
  - `data class RegimePeriod(val id: String, val length: PeriodLength, val averageDays: Int, val highDays: Int, val shallowDrop: Double, val deepDrop: Double, val slopeDays: Int)`. 고점 기준은 하락률(크기, 0.05는 5%)이다.
  - `data class RegimeRules(val groups: List<IndicatorGroupRule>, val upperBoundary: Double, val lowerBoundary: Double, val holdDays: Int, val periods: List<RegimePeriod>, val mainPeriodId: String)`. `periods`는 짧은 기간부터다. `fun mainPeriod(): RegimePeriod`(ID로 찾는다)를 둔다.
  - 항목이 받는 값: `sealed interface IndicatorInput { data object TargetClose : IndicatorInput; data class Series(val series: IndicatorSeries) : IndicatorInput }`. TargetClose는 그 대상의 종가다.
  - `IndicatorRule(id: String, input: IndicatorInput, scoring: ScoringRule)`로 바꾼다(`series` 대신 `input`). IndicatorGroupRule은 그대로다.
  - ScoringRule: `PriceTrend`를 지운다(R-31 삭제). 더한다: `data class AboveAverage(val averageBand: Double)`(평균 대비 기준 m, 비율), `data object FromHigh`, `data object AverageSlope`. HighIsGood, LowIsGood은 그대로다. 기간마다 다른 숫자(N, H, a, b, S)는 RegimePeriod에서 받는다.
- 매매 판단의 규칙 (R-71, R-73, R-102, P-28, P-37)
  - `data class ValuationPeriod(val id: String, val length: PeriodLength, val positionWindow: Int)`
  - ValuationRules에서 `priceSeries`, `positionWindow`를 지우고 더한다: `longAverageLength: PeriodLength`(긴 평균의 이름 "3년"), `threeMonthLength: PeriodLength`("3개월"), `periods: List<ValuationPeriod>`(짧은 기간부터), `mainPeriodId: String`, `fun mainPeriod(): ValuationPeriod`. `priceFetchYears`(대상의 종가의 받는 기간)는 그대로 둔다. 주가 위치와 금리 부담이 같은 기간 목록을 쓴다.
- MoodRules를 `data class MoodRules(val broadMarket: MarketTarget, val sectors: List<MarketTarget>, val regime: RegimeRules, val valuation: ValuationRules, val fetchYears: Int)`로 바꾼다. `sectors`의 순서가 화면의 순서다. 더한다: `fun targets(): List<MarketTarget>`(전체 시장, 그 뒤 섹터 순서), `fun target(id: String): MarketTarget?`. (R-30, P-36)
- `fetchYearsBySeries(): Map<IndicatorSeries, Int>`의 순서와 기간: 대상의 종가(`targets()` 순서) `valuation.priceFetchYears` → 장세의 항목 가운데 `IndicatorInput.Series`인 것 `fetchYears` → 실질금리 `realYieldFetchYears` → 10년물·기준금리·소비자물가 `fetchYears`. 시리즈 ID가 같으면 처음 나온 자리에 하나만 두고 긴 기간을 쓴다(지금과 같다). TargetClose는 시리즈가 아니라 넣지 않는다. (R-41, P-29)
- DefaultMoodRules (R-30, R-41, R-71, R-100, R-101, R-102, P-28, P-29)
  - 대상: 전체 시장 `broad_market` SPY. 섹터(이 순서) `nasdaq_100` QQQ, `semiconductors` SMH, `technology` XLK, `financials` XLF, `energy` XLE, `health_care` XLV, `consumer_discretionary` XLY, `consumer_staples` XLP, `industrials` XLI, `materials` XLB, `utilities` XLU, `real_estate` XLRE, `communication_services` XLC. 종가 시리즈는 모두 `IndicatorSeries(TWELVE_DATA, 티커, DAILY_CLOSE)`.
  - 장세: 묶음 `stocks` 0.7(항목 `above_average` TargetClose AboveAverage(0.02), `from_high` TargetClose FromHigh, `average_slope` TargetClose AverageSlope), `bond_signals` 0.3(`yield_curve`, `high_yield_spread`는 지금 그대로, 입력은 Series). 경계 0.5, -0.5, 유지 3. 기간 `3m`(3 MONTHS, 50, 63, 0.05, 0.10, 20), `6m`(6 MONTHS, 100, 126, 0.07, 0.15, 40), `1y`(1 YEARS, 200, 252, 0.10, 0.20, 80), 메인 `3m`.
  - 매매 판단: 짧은 평균 200, 긴 평균 756, 긴 평균의 이름 (3, YEARS), 비중 2.0 : 1.0, 평균 일수 20, 경계 30·70, 완충 5, 3개월 63과 그 이름 (3, MONTHS), 대상의 받는 기간 9, 실질금리 DFII10 6년, 10년물 DGS10, 기준금리 DFF, 소비자물가 CPIAUCSL, 1년 전 365, 물가 목표 0.02. 기간 `1y`(1 YEARS, 252), `2y`(2 YEARS, 504), `3y`(3 YEARS, 756), `5y`(5 YEARS, 1260), 메인 `2y`.
  - 받는 기간 `fetchYears` 2.
- 장세 콘텐츠 하나의 모델: `data class RegimeAssessment(asOfDate: LocalDate, displayedStage: MarketStage, computedStage: MarketStage, compositeScore: Double, computedStageStreak: Int, holdDays: Int, upperBoundary: Double, lowerBoundary: Double, groups: List<GroupAssessment>)`. MoodAssessment의 장세 부분과 같은 필드다. (P-38)
- IndicatorEvidence: `PriceEvidence`를 지우고 `data class RatioEvidence(val ratio: Double)`를 더한다. 주식 세 항목의 근거는 저마다 비율 하나다(평균 괴리, 고점 대비 낙폭(0 이하), 평균의 기울기). 반올림 전의 값을 싣는다. ValueEvidence는 그대로다. MoodAssessment의 필드는 그대로다. (R-11, P-48, P-39)
- DailySeries에 더한다: `fun averageAt(index: Int, count: Int): Double`(그 위치를 포함한 최근 count개 값의 평균), `fun highAt(index: Int, count: Int): Double`(그 위치를 포함한 최근 count개 값의 최고). 부르는 쪽이 count개가 있는지 먼저 본다. 있는 `floorIndex`, `drawdownAt`(달력 1년, R-32)은 그대로다. (R-101, R-32, P-32)
- RegimeCalculator: `class RegimeCalculator @Inject constructor()`, `fun assess(rules: RegimeRules, period: RegimePeriod, closes: List<DailyValue>?, values: Map<String, List<DailyValue>>): RegimeAssessment?`. `closes`는 그 대상의 종가, `values`는 시리즈 ID마다의 받아 둔 값(Series 항목이 쓴다). 지금 시각을 쓰지 않는다. 지금 MoodCalculator 안의 장세 계산을 옮기고 아래처럼 바꾼다. (R-33~R-37, R-39, R-100, R-101, P-20, P-38, P-49, P-54)
  - 거래일은 그 대상의 종가가 있는 날이다. 기준 날짜는 그 대상의 가장 최근 거래일이다. (R-37, R-38, P-20)
  - 계산할 수 있는 거래일: 그날까지의 종가가 `averageDays + slopeDays`개 이상이고 `highDays`개 이상이며, Series 항목마다 그날이나 그 전의 값이 있는 날. 기준 날짜를 계산할 수 없으면 null이다. 종가가 없거나 비어 있어도 null이다. (R-36, P-49)
  - 평균 대비: 평균 = 그날을 포함한 최근 `averageDays`개 종가의 평균. 평균 괴리 = 그날의 종가 ÷ 평균 - 1. 점수: 괴리 ≥ m이면 +1, -m ≤ 괴리 < m이면 0, 괴리 < -m이면 -1.
  - 고점 대비: 낙폭 = (그날의 종가 - 그날을 포함한 최근 `highDays`개 종가의 최고) ÷ 그 최고. 하락률(= -낙폭) ≤ `shallowDrop`이면 +1, ≥ `deepDrop`이면 -1, 그 사이 0.
  - 평균의 기울기: 그날의 평균 ÷ `slopeDays`개 앞 거래일의 평균 - 1. ≥ 0이면 +1, < 0이면 -1.
  - 세 값은 기준과 견주기 전에 소수 여섯 자리로 반올림한다(HALF_UP). 근거(RatioEvidence)에는 반올림 전의 값을 싣는다. (R-35, P-54)
  - Series 항목(HighIsGood, LowIsGood)은 지금과 같다: 그날이나 그 전의 가장 가까운 날의 값, 근거는 ValueEvidence(값, 그 날짜). (R-33, R-39)
  - 묶음 점수, 현재 상태(소수 여섯 자리로 반올림), 계산 단계, 첫 계산할 수 있는 날부터의 표시 단계(유지 일수), 이어진 거래일 수는 지금 MoodCalculator와 같은 규칙이다. (R-34, R-35, R-36, P-15)
- ValuationCalculator는 이 단위에서 규칙의 모양만 맞춘다(기간마다 내는 것은 단위 2). `assess(rules: ValuationRules, closes: List<DailyValue>?, values: Map<String, List<DailyValue>>, asOfDate: LocalDate): ValuationAssessment?`로 바꾼다: 주가 위치는 넘겨받은 `closes`로, 위치 기간은 `rules.mainPeriod().positionWindow`로 낸다. 금리 부담도 메인 기간의 위치 기간이다. 공식과 null 조건은 그대로다. (R-72~R-80, P-27, P-28)
- MoodCalculator: `class MoodCalculator @Inject constructor(private val regimeCalculator: RegimeCalculator, private val valuationCalculator: ValuationCalculator)`. `assess(rules: MoodRules, values): MoodAssessment?`의 모양은 그대로다. 전체 시장의 종가(`values[rules.broadMarket.closeSeries.seriesId]`)로 `regimeCalculator.assess(rules.regime, rules.regime.mainPeriod(), …)`를 부르고, 그 기준 날짜로 `valuationCalculator.assess(rules.valuation, 전체 시장의 종가, values, asOfDate)`를 부른다. 하나라도 null이면 null이다. 장세의 값을 MoodAssessment의 필드로 옮겨 싣는다. 섹터와 다른 기간은 계산하지 않는다. (R-40, R-58, R-81, P-27, P-38, P-39)
- MarketMoodViewModel은 이 단위에서 컴파일되게만 고친다: 항목 줄의 근거에서 PriceEvidence 갈래를 지우고 RatioEvidence는 `signedPercent(ratio)` 하나로 적는다. 화면 상태는 단위 5에서 다시 만든다.
- IndicatorLabels에서 주식 항목 넷(`broad_market`, `nasdaq_100`, `technology`, `semiconductors`)을 지우고 셋을 더한다: `above_average` "평균 대비", `from_high` "고점 대비", `average_slope` "평균의 기울기"(덧붙이는 글 없음). `strings.xml`에서 `indicator_broad_market`, `indicator_nasdaq_100`, `indicator_technology`, `indicator_semiconductors`와 그 `_suffix` 넷, `evidence_price`를 지우고 세 이름을 더한다. 근거 글의 모양은 단위 5에서 더한다. (R-10)
- 테스트 데이터와 가짜 (Plan Q-18)
  - MoodTestData의 데이터 A는 그대로다(SPY, QQQ, XLK, SMH 평일마다 100 등).
  - 데이터 B를 바꾼다: 데이터 A에서 SPY 2026-11-23 99.0, T10Y2Y 2026-11-23 0.29, BAMLH0A0HYM2는 2026-11-20까지만 있고 그날 값 3.76, XLK 2026-01-05 120.0, SMH 2026-11-23 80.0. (전체 시장의 메인 장세가 "불장 표시, 중립 계산, 1일째"가 되게 한 것이다.)
  - 시험용 규칙 `RULES`: 기본 규칙에서 대상을 전체 시장 SPY와 섹터 셋(QQQ `nasdaq_100`, SMH `semiconductors`, XLK `technology`, 기본 규칙의 순서)으로 줄이고, 매매 판단의 짧은 평균 5, 긴 평균 10, 평균 일수 3, 3개월 5, 기간 `1y`(1 YEARS, 10), `2y`(2 YEARS, 20), `3y`(3 YEARS, 30), `5y`(5 YEARS, 50), 메인 `2y`로 줄인 것. 이름(PeriodLength)과 장세의 기간, 나머지는 기본 규칙 그대로다. 데이터 A(약 2년, 평일 520일)로 모든 콘텐츠를 낼 수 있다.
  - 시험용 규칙에서 데이터 A의 판정: 전체 시장의 메인 장세 현재 상태 0.766667, 표시·계산 단계 BULL, 주식 묶음 0.666667(평균 대비 0, 고점 대비 +1, 기울기 +1, 세 근거 모두 0.0), 채권 신호 1.0. 매매 판단은 지금과 같다(주가 위치·금리 부담 모두 50 · MIDDLE 등). 모든 대상과 기간이 같다.
  - Builder의 `setStocks`는 그대로 둔다(네 종목을 함께 바꾼다).
  - 위젯 테스트: WidgetTestFixture의 `BUFFER_RULES`는 시험용 규칙에서 매매 판단의 메인 기간(`2y`)의 위치 기간만 101, 평균 일수만 1로 바꾼 것이다. `MoodCalculator(...)`를 만드는 곳을 새 생성자에 맞춘다. 위젯 테스트의 기대 값(`ASSESSMENT_A`의 BULL, "35 · 보통" 등)은 그대로 나온다.

**테스트 지시**
아래에서 "작은 장세 기간"은 `RegimePeriod("t", (1, MONTHS), averageDays = 3, highDays = 4, shallowDrop = 0.05, deepDrop = 0.10, slopeDays = 2)`이고, 그 밖은 기본 규칙의 장세(AboveAverage 0.02, 채권 신호, 0.7·0.3, 경계 ±0.5, 유지 3)다. 종가는 2026-11-02부터 이어진 평일에 차례로 놓고, T10Y2Y 0.60, BAMLH0A0HYM2 3.00을 같은 날들에 둔다(채권 신호 +1, +1). 첫째 날을 d1이라 한다.

- R-30: Given 기본 규칙 / When 대상을 본다 / Then 전체 시장 `broad_market` SPY, 섹터는 위 지시의 열셋이 그 순서로 있고, 모두 TWELVE_DATA, DAILY_CLOSE다
- R-100: Given 기본 규칙 / When 장세의 묶음을 본다 / Then `stocks` 0.7: `above_average`(TargetClose, AboveAverage 0.02), `from_high`(TargetClose, FromHigh), `average_slope`(TargetClose, AverageSlope). `bond_signals` 0.3: `yield_curve`(Series T10Y2Y, HighIsGood 0.0/0.5), `high_yield_spread`(Series BAMLH0A0HYM2, LowIsGood 4.0/6.0). 비중의 합 1, 경계 0.5·-0.5, 유지 3
- R-102: Given 기본 규칙 / When 기간을 본다 / Then 장세 `3m`(3개월, 50, 63, 0.05, 0.10, 20)·`6m`(6개월, 100, 126, 0.07, 0.15, 40)·`1y`(1년, 200, 252, 0.10, 0.20, 80), 메인 `3m`. 매매 판단 `1y` 252·`2y` 504·`3y` 756·`5y` 1260, 메인 `2y`
- R-71: Given 기본 규칙 / When `valuation`을 본다 / Then 200, 756, 긴 평균의 이름 (3, YEARS), 2.0, 1.0, 20, 30, 70, 5, 63, 그 이름 (3, MONTHS), 대상의 받는 기간 9, DFII10 6, DGS10, DFF, CPIAUCSL(MONTHLY_VALUE), 365, 0.02
- R-41 (시리즈마다의 받는 기간)
  - Given 기본 규칙 / When `fetchYearsBySeries()` / Then 20개가 이 순서다: SPY, QQQ, SMH, XLK, XLF, XLE, XLV, XLY, XLP, XLI, XLB, XLU, XLRE, XLC 모두 9, T10Y2Y 2, BAMLH0A0HYM2 2, DFII10 6, DGS10 2, DFF 2, CPIAUCSL 2
  - Given 기본 규칙에서 대상의 받는 기간만 1로 바꾼 규칙 / Then 종가 열넷은 모두 1이다(장세 항목이 종가 시리즈를 따로 받지 않는다)
- R-101 (세 값과 세 점수, 작은 장세 기간, RegimeCalculator)
  - Given 종가 100, 100, 100, 100, 104, 95 / When assess / Then 기준 날짜 d6, 평균 괴리 95 ÷ ((100 + 104 + 95) ÷ 3) - 1 = -0.046823, 고점 대비 (95 - 104) ÷ 104 = -0.086538, 기울기 99.6667 ÷ 100 - 1 = -0.003333(근거는 이 값들, 소수 여섯 자리까지 맞음). 점수 -1, 0, -1, 주식 묶음 -0.666667, 현재 상태 -0.166667, 계산 단계 WEAK, 표시 단계 BULL(d5가 첫날이고 BULL), 이어진 거래일 1
  - Given 종가 100, 100, 100, 99, 99, 102 / Then 평균 괴리 +0.02(평균 100)라 평균 대비 +1 (경계는 위 칸)
  - Given 종가 100, 100, 100, 101, 101, 98 / Then 평균 괴리 -0.02라 평균 대비 0
  - Given 종가 100 다섯 날 뒤 95 / Then 하락률 0.05라 고점 대비 +1. 뒤가 90이면 하락률 0.10이라 -1. 89.9면 -1
  - Given 종가 100 다섯 날 / Then 기울기 0이라 +1, 평균 괴리 0이라 0
  - Given 기본 규칙의 3m과 데이터 A에서 SPY 2026-11-23만 104.1 / Then 평균 괴리 (104.1 ÷ 100.082) - 1 = +0.040147, 고점 대비 0, 기울기 100.082 ÷ 100 - 1 = +0.00082, 점수 +1, +1, +1
- R-36 (계산할 수 있는 날, 작은 장세 기간): Given 종가 넷 / Then null. 다섯 / Then 기준 날짜 d5, 표시·계산 단계 BULL(현재 상태 0.766667), 이어진 거래일 1. Given 고점 일수만 6으로 바꾼 기간, 종가 다섯 / Then null. 여섯 / Then 첫 계산할 수 있는 날 d6. Given 종가 다섯, T10Y2Y는 d5에만 있음 / Then d5만 계산할 수 있다(이어진 거래일 1). Given 종가 다섯, T10Y2Y가 d6에만 있음(d5에 없음) / Then null
- R-37, R-38 (대상마다의 거래일): Given 데이터 A, 종가로 QQQ를 넘김, QQQ의 2026-11-23을 뺌 / Then 기준 날짜 2026-11-20. Given SPY를 넘김(SPY는 그대로) / Then 2026-11-23 (다른 대상의 날과 상관없다)
- R-39, R-12: Given 데이터 B, 3m, SPY / Then `high_yield_spread`의 근거 3.76, 날짜 2026-11-20, 점수 +1. `yield_curve` 0.29, 2026-11-23, 점수 0
- R-100 (주식 항목은 그 대상의 종가): Given 데이터 B, 3m / When SMH의 종가로 assess / Then 세 점수 -1, -1, -1, 채권 신호 0.5, 현재 상태 -0.55, 계산 단계 BEAR, 표시 단계 BULL. SPY의 종가로 assess / Then 0, +1, -1, 채권 신호 0.5, 현재 상태 0.15
- MoodCalculator의 있던 테스트는 경우를 줄이지 않고 D1과 시험용 규칙에 맞춰 아래처럼 바꾼다. 따로 적지 않은 것(예: R-33 채권 신호 항목의 경계 값들, R-12, R-39, R-40 매매 판단 쪽, R-37 매매 판단의 시리즈)은 그대로 통과해야 한다. 기본 규칙의 3m이 메인이다.
  - R-30 묶음과 항목 테스트 → 위 R-100 규칙 테스트로 바꾼다
  - R-38 데이터 A의 기준 날짜 2026-11-23: 그대로
  - R-37 "QQQ의 2026-11-23이 없으면 2026-11-20" → SPY의 2026-11-23을 빼면 2026-11-20(현재 상태 0.766667, BULL). QQQ의 2026-11-23을 빼도 2026-11-23 (섹터는 판정의 기준 날짜와 상관없다)
  - R-31 넷 → R-101 "평균 대비"로: 데이터 A면 `above_average` 0.0점·근거 0.0, SPY 2026-11-23이 104.1이면 +1(위 값), 99.0이면 0(근거 -0.009802)
  - R-32 넷과 "1년 전 같은 날" 둘 → R-101 "고점 대비"로: SPY 2026-11-23이 95.0이면 +1, 93.0이면 0(근거 -0.07), 90.0이면 -1, 80.0이면 -1(근거 -0.2). 장세의 고점은 그날을 포함한 최근 63거래일이라, SPY 2026-08-27(2026-11-23을 포함해 63번째 평일)에 120을 두면 2026-11-23의 고점 대비 근거는 -0.166667이고, 2026-08-17(그보다 앞)에 두면 0.0이다. 달력 1년 낙폭(`drawdownAt`)의 경우는 ValuationCalculator의 R-68 테스트에 그대로 남는다
  - R-33 "데이터 B의 technology" → 데이터 B의 SMH(위 R-100)로
  - R-34 데이터 B: 주식 0.0, 채권 신호 0.5, 현재 상태 0.15
  - R-35 "경계에 꼭 걸린 0.5" → 시험용 규칙에서 비중만 주식 0.75·채권 신호 0.25로 바꾼 규칙, 데이터 A에 T10Y2Y 2026-11-23 0.29, BAMLH0A0HYM2 2026-11-23 5.00 / Then 현재 상태 0.5(주식 0.666667 × 0.75), 계산 단계 BULL
  - R-35 "데이터 B는 중립": 그대로(현재 상태 0.15)
  - R-35 "현재 상태 0" → 데이터 A에 SPY 2026-11-23 99.0, T10Y2Y 0.29, BAMLH0A0HYM2 5.00 / Then 0.0, NEUTRAL
  - R-35 "-0.5는 약세" → 위 0.75·0.25 규칙, SPY 2026-11-23 93.0, T10Y2Y 0.29, BAMLH0A0HYM2 5.00 / Then 주식 -0.666667, 현재 상태 -0.5, WEAK
  - R-35 "-0.85는 물장" → SPY 2026-11-23 80.0, T10Y2Y -0.01, BAMLH0A0HYM2 5.00 / Then -0.85, BEAR
  - R-36 나쁜 날 셋(이틀, 사흘, 끊김): 그대로(나쁜 날의 현재 상태는 0.166667, NEUTRAL)
  - R-36·R-08 "다른 단계가 끼면" 둘 → 나쁜 날 2026-11-18, 19, 20, 23과 SPY 2026-11-20 80.0 / Then 2026-11-20은 BEAR, 2026-11-23은 현재 상태 -0.3, 계산 단계 WEAK, 표시 단계 BULL, 이어진 거래일 1
  - R-36 "첫 거래일의 계산 단계가 표시 단계" → SPY를 마지막 72평일(2026-08-14부터)만 남기고 나쁜 날 2026-11-19 / Then 첫 계산할 수 있는 날 2026-11-19(NEUTRAL), 표시 단계 NEUTRAL, 계산 단계 BULL, 이어진 거래일 2
  - R-36 "하루뿐": 그대로
  - R-08 데이터 B: 그대로(표시 BULL, 계산 NEUTRAL, 1일째)
  - R-11 "SPY 104.1" → 위 R-101의 마지막 경우. "데이터 B의 technology와 semiconductors" → 데이터 B의 SPY: 평균 괴리 99 ÷ 99.98 - 1 = -0.009802, 고점 대비 -0.01, 기울기 99.98 ÷ 100 - 1 = -0.0002
  - R-40 "XLK 시리즈가 없으면 null" → XLK가 없어도 판정이 있다. SPY가 없으면 null
  - R-40 "종가가 200개에 못 미치면 null" → SPY를 마지막 70평일만 남기면 판정이 있고, 69평일이면 null (3m은 종가 50 + 20개가 든다)
  - R-40 "데이터 A면 판정이 있고 …": 현재 상태 0.766667, BULL, 매매 판단은 그대로
- R-58, R-81: Given 받아 둔 값 데이터 A, 시험용 규칙 / When ObserveMoodUseCase의 첫 값 / Then 표시 단계 BULL, 기준 날짜 2026-11-23, 주가 위치·금리 부담 50 · MIDDLE. Given 데이터 A에서 XLK, QQQ, SMH를 모두 뺌 / Then 판정이 그대로 있다(섹터는 판정의 조건이 아니다)
- ValuationCalculator의 있던 테스트는 경우와 기대 값을 줄이지 않고, 대상 시리즈 대신 종가를 넘기고 위치 기간을 메인 기간 하나(작은 규칙의 기간 목록에 그 위치 기간 하나, 메인)로 주도록만 바꾼다
- 있던 시장 분위기 테스트(ObserveMoodUseCase, MarketDataRefresher, RefreshMarketDataUseCase, 다른 기능에 내주는 UseCase, ViewModel)와 홈 화면 위젯의 테스트가 모두 통과한다. ViewModel 테스트 가운데 주식 항목 넷(`broad_market` 등)의 줄을 보던 것은 단위 5에서 장세 화면의 테스트로 옮기므로, 이 단위에서는 새 항목 셋의 이름과 임시 근거(부호 붙인 %)로만 맞춘다

**확인 기준**
- 테스트 통과, 컴파일 오류 없음

### 단위 2: 기간마다의 매매 판단과 화면의 한 벌

**만들거나 고칠 것**
- 수정 ValuationCalculator (feature/marketmood/domain)
- 신규 MarketOverview, TargetOverview, PeriodContent (feature/marketmood/domain)
- 신규 MarketOverviewCalculator (feature/marketmood/domain)
- 신규 ObserveMarketOverviewUseCase (feature/marketmood/domain)

**지시**
- ValuationCalculator에 기간마다 내는 함수 둘을 더한다. 공식은 지금 그대로다(R-72~R-80). (R-72~R-80, R-104, P-53, P-14)
  - `fun assessPricePositions(rules: ValuationRules, closes: List<DailyValue>?, asOfDate: LocalDate, periods: List<ValuationPeriod>): Map<String, PricePositionAssessment>`: 키는 기간 ID다. 두 괴리, 낙폭, 3개월 수익률은 한 번만 내고(기간과 상관없이 같다), 위치·점수·점수로 정한 단계·표시 단계만 기간마다 그 기간의 위치 기간으로 낸다. 낼 수 없는 기간은 맵에 넣지 않는다. 기준 날짜에 종가가 없거나 3개월 수익률을 낼 수 없으면 빈 맵이다.
  - `fun assessRateBurdens(rules: ValuationRules, values: Map<String, List<DailyValue>>, asOfDate: LocalDate, periods: List<ValuationPeriod>): Map<String, RateBurdenAssessment>`: 실질금리의 날, 1년 전과의 차이, 10년물, 기준금리, 물가는 한 번만 내고, 실질금리의 위치·점수·단계만 기간마다 낸다. 기간과 상관없는 값 가운데 하나라도 낼 수 없으면 빈 맵이다. 낼 수 없는 기간은 넣지 않는다.
  - 단위 1의 `assess`(메인 하나)는 이 둘에 메인 기간 하나만 넘겨 만든다. 그래서 MoodCalculator는 메인 기간만 계산한다.
- 한 벌의 모델 (R-102, R-104, R-105, R-38, P-38, P-39)
  - `data class PeriodContent<T>(val periodId: String, val length: PeriodLength, val isMain: Boolean, val value: T?)`. `value`가 null이면 그 콘텐츠를 낼 수 없는 것이다.
  - `data class TargetOverview(val target: MarketTarget, val asOfDate: LocalDate?, val regimes: List<PeriodContent<RegimeAssessment>>, val pricePositions: List<PeriodContent<PricePositionAssessment>>)`. 목록은 규칙의 기간 순서(짧은 기간부터)이고 메인도 들어 있다. `asOfDate`는 그 대상의 가장 최근 거래일이다(종가가 없으면 null). 더한다: `fun mainRegime(): RegimeAssessment?`, `fun mainPricePosition(): PricePositionAssessment?`, `fun isMainAvailable(): Boolean`(둘 다 있으면 참, R-105).
  - `data class MarketOverview(val rules: MoodRules, val mood: MoodAssessment, val broadMarket: TargetOverview, val sectors: List<TargetOverview>, val rateBurdens: List<PeriodContent<RateBurdenAssessment>>)`. `rules`는 화면이 문구의 숫자(일수, 기간의 이름, 경계, 완충)를 끼우는 데 쓴다(R-107). `sectors`는 규칙의 섹터 순서다. 더한다: `fun target(id: String): TargetOverview?`. 다른 기능에는 내주지 않는다.
- MarketOverviewCalculator: `class MarketOverviewCalculator @Inject constructor(moodCalculator: MoodCalculator, regimeCalculator: RegimeCalculator, valuationCalculator: ValuationCalculator)`, `fun assess(rules: MoodRules, values: Map<String, List<DailyValue>>): MarketOverview?`. (R-40, R-102, R-104, R-105, R-38, R-78, P-38)
  - MoodCalculator로 전체 시장의 메인 판정을 낸다. null이면 한 벌도 null이다.
  - 대상마다(전체 시장과 섹터): 기준 날짜, 장세의 기간마다 RegimeCalculator, 주가 위치의 기간마다 `assessPricePositions`(그 대상의 종가와 그 대상의 기준 날짜). 섹터 하나나 기간 하나를 낼 수 없어도 그 칸만 null이다.
  - 금리 부담의 기간마다 `assessRateBurdens`(전체 시장의 기준 날짜). (R-78)
  - 전체 시장의 메인 장세와 메인 주가 위치·메인 금리 부담은 `mood`와 같은 값이다(같은 계산기, 같은 값). 그래서 화면과 위젯의 값이 어긋나지 않는다. (P-23)
- ObserveMarketOverviewUseCase: `@Singleton class ObserveMarketOverviewUseCase @Inject constructor(rulesRepository: MoodRulesRepository, marketDataRepository: MarketDataRepository, calculator: MarketOverviewCalculator, @DefaultDispatcher dispatcher: CoroutineDispatcher, @ApplicationScope scope: CoroutineScope)`, `operator fun invoke(): Flow<MarketOverview?>`. 시장 분위기 안에서만 쓴다. (R-23, R-99, P-31, P-40)
  - 규칙과 받아 둔 값 가운데 시리즈의 값(`StoredMarketData.values`)만 본다. 값이 같으면(성공한 때만 바뀐 것) 다시 계산하지 않는다.
  - 계산은 넘겨받은 디스패처에서 돈다. 계산하는 동안 새 값이 여러 번 오면 쌓아 두지 않고 마지막 것만 계산한다.
  - 앱 범위에서 한 번만 계산해 받는 쪽 모두에 나눠 준다. 받는 쪽이 없어지고 5초가 지나면 멈춘다. 마지막 값 하나를 들고 있어 새로 받기 시작한 쪽은 그 값을 바로 받는다.

**테스트 지시**
- R-72, R-73, R-74 (기간마다): Given 작은 규칙(짧은 평균 2, 긴 평균 3, 비중 2 : 1, 평균 일수 1, 3개월 2), 기간 `a`(위치 기간 3), `b`(위치 기간 4), SPY 종가 10, 12, 11, 13, 12, 기준 날짜는 다섯째 날 / When `assessPricePositions` / Then `a` 42, `b`는 맵에 없다. Given 종가 10, 12, 11, 13, 12, 15, 14, 기준 날짜는 일곱째 날 / Then `a` 50, `b` 33이고, 두 기간의 짧은 괴리(14 ÷ 14.5 - 1 = -0.0345), 긴 괴리(14 ÷ 13.6667 - 1 = +0.0244), 낙폭, 3개월 수익률(14 ÷ 12 - 1)은 같다
- R-75 (기간마다): Given 평균 일수 2, 기간 `a`(위치 기간 5), `b`(6), 실질금리 5, 4, 3, 2, 1, 1.5, 나머지는 낼 수 있음, 기준 날짜는 여섯째 날 / When `assessRateBurdens` / Then `a` 13, `b`는 없다. 1년 전과의 차이, 10년물, 기준금리, 물가는 `a`의 것이 기준 날짜의 값이다
- R-77 (콘텐츠마다): Given 위치 기간 101, 평균 일수 1인 기간 `a`와 위치 기간 20인 기간 `b`, 실질금리는 마지막 102개가 점수열 30, 35가 나오는 값(`MoodTestData.valuesForScores(listOf(30, 35))`, 그 앞은 2.00) / When `assessRateBurdens` / Then `a`의 표시 단계 LOW, 점수로 정한 단계 MIDDLE이고, `b`의 단계는 `b`의 점수로 따로 정해진다(`a`의 표시 단계를 따르지 않는다)
- R-104: Given 작은 규칙, 기간 `a`(3)만, SPY 종가 넷 / When `assessPricePositions` / Then 빈 맵. Given 기준 날짜에 SPY 종가가 없음 / Then 빈 맵. Given DGS10이 없음 / When `assessRateBurdens` / Then 빈 맵
- R-102, R-40 (한 벌): Given 데이터 A, 시험용 규칙 / When MarketOverviewCalculator.assess / Then `mood`는 MoodCalculator의 값과 같다. 전체 시장과 섹터 QQQ·SMH·XLK(이 순서) 모두 기준 날짜 2026-11-23, 장세 `3m`·`6m`·`1y`(`3m`만 메인) 모두 있고 현재 상태 0.766667 · BULL, 주가 위치 `1y`·`2y`·`3y`·`5y`(`2y`만 메인) 모두 50 · MIDDLE. 금리 부담 넷 모두 50 · MIDDLE, 실질금리의 날 2026-11-23. 전체 시장의 메인 장세·주가 위치는 `mood`의 장세·주가 위치와 같은 값이다
- R-104, R-105 (일부를 낼 수 없음)
  - Given 데이터 A에서 SMH를 뺌 / Then 한 벌이 있고, SMH의 기준 날짜 null, 장세와 주가 위치는 모두 값 null, `isMainAvailable()` 거짓. 나머지 대상은 위와 같다
  - Given 데이터 A에서 QQQ를 마지막 200평일만 남김 / Then QQQ의 장세 `3m`·`6m`은 있고 `1y`는 null(종가 280개가 든다), 주가 위치는 넷 모두 있다, `isMainAvailable()` 참
  - Given 데이터 A에서 QQQ를 마지막 60평일만 남김 / Then QQQ의 메인 장세가 null이라 `isMainAvailable()` 거짓
  - Given 시험용 규칙에서 매매 판단의 `5y` 위치 기간만 600으로 바꿈 / Then 모든 대상의 주가 위치 `5y`와 금리 부담 `5y`는 null이고, 판정과 나머지 칸은 그대로다
  - Given 데이터 A에서 SPY를 뺌 / Then null
- R-38 (대상마다의 기준 날짜): Given 데이터 A에서 QQQ의 2026-11-23을 뺌 / Then QQQ의 기준 날짜 2026-11-20(장세·주가 위치는 그날의 것), 전체 시장과 판정의 기준 날짜는 2026-11-23. Given SPY의 2026-11-23을 뺌 / Then 판정과 전체 시장의 기준 날짜 2026-11-20, 금리 부담 넷의 실질금리의 날 2026-11-20, QQQ의 기준 날짜 2026-11-23
- R-23 (계산용 디스패처에서, 마지막 값만)
  - Given 넘긴 디스패처가 StandardTestDispatcher, 받아 둔 값 데이터 A / When 다른 디스패처에서 모으기 시작하고 넘긴 디스패처는 아직 돌리지 않음 / Then 값이 아직 없다. 돌리면 한 벌이 나온다
  - Given 모으는 중, 넘긴 디스패처를 멈춘 채 받아 둔 값을 B1(데이터 A에 SPY 2026-11-23 99.0), B2(데이터 B)로 잇달아 바꿈 / When 디스패처를 돌림 / Then 마지막으로 받은 한 벌은 데이터 B의 것이고, B1의 한 벌은 내보내지 않는다
  - Given 성공한 때만 바뀐 받아 둔 값(시리즈의 값은 같음) / Then 새 한 벌을 내보내지 않는다
- R-99, P-40 (한 번만 계산): Given 받는 쪽 둘이 함께 모음 / Then 둘 다 같은 한 벌을 받고, 가짜 저장소의 받아 둔 값 Flow를 구독한 수는 1이다(`MutableStateFlow.subscriptionCount`). 받아 둔 값을 바꾸면 둘 다 새 한 벌을 받는다

**확인 기준**
- 테스트 통과, 컴파일 오류 없음

### 단위 3: 나눠 받기

**만들거나 고칠 것**
- 신규 FetchPacing (feature/marketmood/domain)
- 신규 RefreshStamp (feature/marketmood/domain)
- 수정 MarketDataRepository (feature/marketmood/domain)
- 수정 RefreshMarketDataUseCase (feature/marketmood/domain)
- 수정 MarketDataRepositoryImpl (feature/marketmood/data)
- 수정 MarketMoodModule (feature/marketmood/di)
- 수정 FakeMarketDataRepository (테스트, feature/marketmood/domain)
- 수정 시장 분위기와 홈 화면 위젯의 테스트에서 RefreshMarketDataUseCase를 만드는 곳 (테스트)

**지시**
- `data class FetchPacing(val batchSize: Int, val batchInterval: kotlin.time.Duration, val requestTimeout: kotlin.time.Duration)`. MarketMoodModule이 기본값 `FetchPacing(7, 65.seconds, 10.seconds)`를 제공한다. 숫자는 판정의 규칙이 아니라 출처의 요금제에 매인 것이라 MoodRules에 두지 않는다. (P-41)
- `enum class RefreshStamp { STARTED, FINISHED, TWELVE_DATA_REQUESTED }`: 마지막으로 받기를 시작한 때, 끝난 때, Twelve Data에 마지막으로 요청을 보낸 때. (P-26, P-42)
- MarketDataRepository: `getLastStartedAt`, `saveLastStartedAt`을 지우고 `suspend fun getStamp(stamp: RefreshStamp): Instant?`(적힌 적이 없거나 읽지 못하면 null, 예외를 던지지 않는다), `suspend fun saveStamp(stamp: RefreshStamp, at: Instant)`(앞서 적힌 것은 바꾼다, 적지 못하면 IOException)를 둔다. 나머지 함수는 그대로다. (R-62, R-103, P-26)
- MarketDataRepositoryImpl: 세 때를 `sync_stamps`의 이름 셋으로 읽고 적는다. STARTED는 지금 이름 `market_mood_refresh_started` 그대로(앱을 바꿔도 남는다), FINISHED `market_mood_refresh_finished`, TWELVE_DATA_REQUESTED `twelve_data_last_request`. 성공한 때(`market_mood_refresh`)와 받기·저장은 그대로다. 표의 모양은 바뀌지 않는다. 받아 둔 값의 Flow는 세 때가 바뀌어도 새 값을 내보내지 않는다(지금처럼 성공한 때의 행만 본다). (R-62, R-103, P-11, P-26, P-42)
- RefreshMarketDataUseCase: 생성자 `(marketDataRepository, rulesRepository, calculator: MoodCalculator, pacing: FetchPacing, clock: Clock)`, `suspend operator fun invoke(onBroadMarketReceived: () -> Unit = {}): RefreshOutcome`. 받기 한 번을 아래 순서로 한다. (R-23, R-37, R-41~R-43, R-45, R-54, R-55, R-62, R-103, R-108, P-07, P-09, P-10, P-29, P-30, P-41~P-44)
  - 시작한 때(STARTED)를 받는 때로 적는다(지금처럼 요청보다 먼저, 적지 못해도 이어 간다).
  - 시리즈마다의 시작 날짜는 지금처럼 `fetchYearsBySeries()`와 받는 때의 미국 동부 날짜로 정한다. (R-41)
  - FRED: 시리즈 모두를 `fetch(FRED, …)` 한 번으로, 시작하자마자 받는다. 10초 제한은 이 한 번에 건다.
  - Twelve Data: 대상의 종가 요청을 `rules.targets()` 순서(전체 시장이 맨 앞)로 `batchSize`개씩 묶는다. 기본 규칙에서는 [SPY, QQQ, SMH, XLK, XLF, XLE, XLV], [XLY, XLP, XLI, XLB, XLU, XLRE, XLC]다. 묶음 하나가 `fetch(TWELVE_DATA, …)` 한 번이고, 10초 제한은 묶음마다 건다. 기다리는 시간에는 제한을 걸지 않는다. (P-07, P-08)
  - 첫 묶음은 TWELVE_DATA_REQUESTED로부터 `batchInterval`이 지난 뒤에 보낸다. 그 때가 없거나, 읽지 못하거나, 지금보다 뒤로 적혀 있으면 기다리지 않는다. 다음 묶음은 앞 묶음을 보낸 때로부터 `batchInterval`이 지난 뒤에 보낸다. 기다리는 시간은 Clock으로 잰 앞 요청의 때와 지금의 차이로 정해 `delay`로 기다린다. 묶음마다 요청을 보내기 바로 전에 TWELVE_DATA_REQUESTED를 지금으로 적는다(적지 못해도 이어 간다). (R-103, P-42)
  - FRED와 첫 묶음은 동시에 진행된다(첫 묶음이 기다리는 동안 FRED는 받는다).
  - 받은 것은 요청 하나(FRED 한 번, 묶음 하나)가 성공할 때마다 바로 저장한다. 장이 끝나지 않은 날 거르기(DAILY_CLOSE만)는 그 요청의 응답을 받은 때로 한다. 실패한 요청의 시리즈는 그대로 둔다. (R-23, R-37, R-43, R-54, P-09, P-10, P-43)
  - 첫 묶음과 FRED가 모두 성공해 둘 다 저장되면 `onBroadMarketReceived`를 한 번 부른다. 둘 가운데 하나라도 실패하거나 저장하지 못하면 부르지 않는다. (R-108, P-43)
  - 출처가 키를 거절하면(묶음의 결과가 KEY_REJECTED 또는 KEY_INVALID) 그 출처의 남은 묶음은 보내지 않는다. 한도를 넘거나 연결이 안 되거나 그 밖의 실패면 남은 묶음을 이어서 보낸다. (R-43)
  - 모든 요청이 끝나야 받기가 끝난다. 성공한 때(받는 때, 즉 받기를 시작한 시각)는 모든 요청이 성공하고 받아 둔 값으로 판정을 낼 수 있을 때만, 마지막으로 끝난 요청의 저장과 같은 `save` 호출에 실어 적는다(값을 받아 둔 것에 덮은 것으로 MoodCalculator가 판정을 낼 수 있는지 본다). 저장할 값이 없으면 빈 값으로 `save`를 불러 성공한 때만 적는다. (R-20, R-42, P-43)
  - 결과의 원인은 모든 요청(FRED, 묶음 모두)에서 모아 지금처럼 정한다: 한도 넘음 > 연결 안 됨 > 그 밖(키 거절, 읽을 수 없음, 저장하지 못함, 판정을 낼 수 없음). 모두 성공하고 저장하고 판정을 낼 수 있으면 Success. (R-42, R-45)
  - 돌아오기 직전(성공, 실패, 예외 모두)에 끝난 때(FINISHED)를 지금으로 적는다. 적지 못해도 결과를 바꾸지 않는다. 코루틴이 취소된 때는 적지 않아도 된다. (R-62, P-44)
- FakeMarketDataRepository: 세 때를 맵으로 들고, `getStamp`/`saveStamp`를 기록한다(불린 차례를 `callOrder`에 "fetch", "saveStamp(STARTED)"처럼 남긴다). 읽기 실패와 적기 실패를 테스트가 켤 수 있게 한다. 있던 테스트가 쓰는 `lastStartedAt`은 STARTED의 값을 읽고 쓰는 속성으로 남겨도 된다.
- 테스트에서 기다림은 가상 시간으로 본다. Clock은 테스트 스케줄러의 `currentTime`에 묶은 것(시작 시각 + currentTime 밀리초)을 쓴다. 있던 테스트의 FakeClock(멈춘 시계)으로는 "앞 요청과 지금의 차이"가 0이라 늘 `batchInterval`을 다 기다린다. 한 테스트에서 두 번 받는 있던 테스트(시장 분위기, 홈 화면 위젯)는 두 번째 받기가 가상 시간으로 65초 기다리는 것이 테스트의 뜻을 바꾸지 않는지 보고, 바꾸면 그 테스트의 Given에서 TWELVE_DATA_REQUESTED를 비운다. 시험용 규칙(대상 넷)은 기본 FetchPacing(7)으로 묶음 하나다.

**테스트 지시**
아래에서 받는 때는 2026-11-24T01:00:00Z(미국 동부 2026-11-23), 받아 둔 값과 출처는 데이터 A, 키 둘 AVAILABLE, 세 때는 없음이다. "묶음 둘 규칙"은 시험용 규칙과 `FetchPacing(2, 65초, 10초)`로, Twelve Data가 [SPY, QQQ], [SMH, XLK] 두 묶음이 된다. 시각은 가상 시간의 처음(0초)으로부터 잰다.

- R-103 (묶음 나누기, 기다림)
  - Given 묶음 둘 규칙 / When 받기 한 번 / Then `fetch`는 셋이다: 0초에 FRED(시리즈 여섯)와 TWELVE_DATA [SPY, QQQ], 65초에 TWELVE_DATA [SMH, XLK]. TWELVE_DATA_REQUESTED는 01:00:00, 01:01:05로 두 번 적힌다. 64초에는 아직 돌아오지 않고, 65초의 묶음이 끝난 뒤 Success로 돌아온다
  - Given 기본 규칙과 기본 FetchPacing / Then TWELVE_DATA 묶음은 [SPY, QQQ, SMH, XLK, XLF, XLE, XLV]와 [XLY, XLP, XLI, XLB, XLU, XLRE, XLC], 두 번째는 65초에 보낸다
  - Given 묶음 둘 규칙, TWELVE_DATA_REQUESTED 00:59:30 / Then FRED는 0초, 첫 묶음은 35초, 둘째 묶음은 100초에 보낸다
  - Given TWELVE_DATA_REQUESTED 00:58:00(65초보다 앞) / Then 첫 묶음은 0초에 보낸다. Given 01:05:00(지금보다 뒤) / Then 0초. Given TWELVE_DATA_REQUESTED를 읽지 못함 / Then 0초
  - Given 첫 묶음의 응답이 9초 걸림 / Then 둘째 묶음은 첫 묶음을 보낸 때로부터 65초(65초 시점)에 보낸다(응답을 받은 때로부터가 아니다)
  - Given 시험용 규칙과 기본 FetchPacing / Then TWELVE_DATA는 [SPY, QQQ, SMH, XLK] 한 번이다
- R-41 (시리즈마다의 시작 날짜): Given 시험용 규칙 / Then TWELVE_DATA는 SPY, QQQ, SMH, XLK 모두 2017-11-23. FRED는 T10Y2Y·BAMLH0A0HYM2 2024-11-23, DFII10 2020-11-23, DGS10·DFF·CPIAUCSL 2024-11-23. (있던 "출처마다 한 번씩 …" 테스트의 기대 값을 이것으로 바꾼다)
- R-23, R-54 (묶음마다 저장)
  - Given 묶음 둘 규칙 / When 받기 한 번 / Then `save`는 세 번이다: FRED의 시리즈 여섯과 [SPY, QQQ](0초, 둘의 순서는 상관없음, 성공한 때 없음), [SMH, XLK](65초, 성공한 때 2026-11-24T01:00:00Z). 둘째 묶음을 기다리는 동안 받아 둔 값에는 SPY, QQQ와 FRED의 새 값이 이미 있다
  - Given 시험용 규칙(묶음 하나) / Then `save`는 두 번이고(FRED, 묶음), 성공한 때는 나중에 끝난 쪽의 `save` 하나에만 실린다. (있던 "모두 성공하면 … 한 번 저장한다" 테스트의 기대 값을 이것으로 바꾼다)
- R-108 (전체 시장의 값을 받음)
  - Given 묶음 둘 규칙 / Then `onBroadMarketReceived`는 한 번, 0초에 FRED와 첫 묶음의 저장이 모두 끝난 뒤, 둘째 묶음을 보내기 전에 불린다
  - Given 첫 묶음이 RATE_LIMITED / Then 불리지 않는다. Given FRED가 NO_CONNECTION / Then 불리지 않는다. Given FRED와 첫 묶음은 성공했는데 첫 묶음의 저장이 IOException / Then 불리지 않는다
  - Given 둘째 묶음만 실패 / Then 0초에 불린다(결과는 그 실패)
- R-43 (일부 묶음만 실패, 키 거절)
  - Given 묶음 둘 규칙, 첫 묶음이 RATE_LIMITED / Then 둘째 묶음을 65초에 보내 저장하고, FRED도 저장한다. 결과는 Failure(RATE_LIMITED), 성공한 때는 적지 않는다
  - Given 첫 묶음이 KEY_REJECTED / Then 둘째 묶음은 보내지 않는다(`fetch`는 FRED와 첫 묶음 둘), FRED는 저장한다, 결과는 Failure(OTHER), 받기는 0초에 끝난다. KEY_INVALID여도 같다
  - Given 둘째 묶음이 NO_CONNECTION / Then 첫 묶음과 FRED는 저장되고 결과는 Failure(NO_CONNECTION)
- R-45 (원인의 우선순위): Given 첫 묶음 NO_CONNECTION, 둘째 묶음 RATE_LIMITED / Then RATE_LIMITED. Given FRED OTHER(읽을 수 없음), 둘째 묶음 NO_CONNECTION / Then NO_CONNECTION
- R-42 (10초 제한은 요청마다): Given 묶음 둘 규칙, 둘째 묶음의 응답이 그 묶음을 보낸 때로부터 11초 / Then 결과는 NO_CONNECTION이고 첫 묶음과 FRED는 저장된다. 9.5초 / Then Success (65초를 기다린 시간은 제한에 들지 않는다). FRED가 11초면 FRED만 NO_CONNECTION이고 묶음 둘은 저장된다
- R-42 (있던 경우): 저장이 IOException이면 OTHER. 모두 성공했는데 판정을 낼 수 없으면(출처가 DFII10 없이 돌려줌) OTHER이고 받은 값은 저장되며 성공한 때는 적지 않는다. 그대로 통과한다
- R-37 (묶음마다 받은 때로 거름): Given 묶음 둘 규칙, 받는 때 2026-11-23T20:59:00Z(미국 동부 오후 3시 59분)를 가상 시간 0초에 묶은 시계, 출처가 2026-11-23의 값까지 돌려줌 / Then 첫 묶음(0초)의 SPY, QQQ는 2026-11-20까지 저장되고, 둘째 묶음(65초, 미국 동부 오후 4시 0분 5초)의 SMH, XLK는 2026-11-23까지 저장된다. FRED의 날마다의 값과 달마다의 값은 거르지 않는다
- R-62, P-44 (끝난 때)
  - Given 시험용 규칙 / When 받기 한 번(Success) / Then FINISHED가 한 번, 받기가 끝난 때(가상 시간의 그때)로 적히고, 저장소에 마지막으로 불린 것이 `saveStamp(FINISHED)`다. STARTED는 처음에 한 번이다(있던 R-62 테스트 그대로)
  - Given 출처가 모두 RATE_LIMITED / Then FINISHED가 적힌다. Given 저장소의 `fetch`가 예외를 던짐 / Then FINISHED가 적힌다
  - Given FINISHED를 적지 못함(IOException) / Then 결과는 그대로 Success다
  - Given 묶음 둘 규칙 / Then FINISHED는 65초의 둘째 묶음이 끝난 뒤의 때다
- R-62, R-103 (기기에 적고 읽음, MarketDataRepositoryImpl, 가짜 Dao)
  - Given `saveStamp(STARTED, 01:00)`, `saveStamp(FINISHED, 01:02)`, `saveStamp(TWELVE_DATA_REQUESTED, 01:01)` / When 셋을 읽음 / Then 각각 그 값이고, `sync_stamps`의 이름은 `market_mood_refresh_started`, `market_mood_refresh_finished`, `twelve_data_last_request`다
  - Given 같은 때를 두 번 적음 / Then 나중 것이다. Given Dao가 읽기에 실패 / Then null. Given Dao가 쓰기에 실패 / Then IOException
  - Given 받아 둔 값을 모으는 중 / When 세 때를 적음 / Then 받아 둔 값의 Flow가 새 값을 내보내지 않는다
  - 있던 시작한 때 테스트(`saveLastStartedAt`)는 `saveStamp(STARTED, …)`로 바꿔 그대로 둔다

**확인 기준**
- 테스트 통과, 컴파일 오류 없음

### 단위 4: 받기 관리자

**만들거나 고칠 것**
- 수정 RefreshState (feature/marketmood/domain)
- 수정 MarketDataRefresher (feature/marketmood/domain)
- 수정 시장 분위기와 홈 화면 위젯의 테스트에서 MarketDataRefresher를 만드는 곳 (테스트)

**지시**
- RefreshState에 `val broadMarketReceived: Boolean = false`를 맨 뒤에 더한다. 이번 받기에서 전체 시장의 값(SPY가 든 첫 묶음과 FRED)을 받아 저장했는지다. 받는 중이 아니면 늘 거짓이다. 있던 두 필드는 그대로다. (R-61, R-108, P-22, P-43)
- MarketDataRefresher의 생성자에 `rulesRepository: MoodRulesRepository`를 더한다: `(refreshMarketData, observeMood, observeSourceKeys, marketDataRepository, rulesRepository, clock, scope)`. (P-45)
- 받기를 시작할 때 상태를 받는 중, `broadMarketReceived` 거짓으로 둔다. RefreshMarketDataUseCase에 알림 함수를 넘겨, 불리면 아직 이 받기가 받는 중일 때 `broadMarketReceived`를 참으로 바꾼다. 받기가 끝나면 지금처럼 받는 중 아님, 실패 원인, `broadMarketReceived` 거짓으로 둔다. 겹쳐 시작하지 않는 것, 청, JOINED, 앱 범위에서 끝까지 받는 것은 그대로다. 묶음 사이를 기다리는 동안에도 받는 중이다. (R-22, R-55, R-59, R-61, R-103, R-108, P-24, P-43)
- 받는 조건 (R-20, R-60, R-62, P-25, P-45)
  - WHEN_STALE(화면이 보이게 될 때도 같은 코드): 키가 모두 AVAILABLE이고, 판정이 없거나, 규칙의 대상(`rules.targets()`) 가운데 받아 둔 값에 그 종가 시리즈의 값이 하나도 없는 대상이 있거나, 성공한 때로부터 6시간이 넘었을 때. 값이 한 날이라도 있으면 "있다"로 본다.
  - WHEN_NOT_RECENTLY_STARTED: 키가 모두 AVAILABLE이고, STARTED와 FINISHED 가운데 늦은 때로부터 1분이 넘었을 때. 하나만 있으면 그것, 둘 다 없으면 넘은 것으로 본다. 늦은 때가 지금보다 뒤면 넘은 것으로 본다. 꼭 1분이면 넘지 않은 것이다.
  - WHEN_KEYS_AVAILABLE은 그대로다.
- 끝난 때는 RefreshMarketDataUseCase가 돌아오기 전에 적으므로(단위 3), 상태가 받는 중 아님으로 바뀐 때에는 이미 적혀 있다. 이 순서를 바꾸지 않는다. (R-60, P-44)

**테스트 지시**
아래 Given은 시험용 규칙, 시계 2026-11-24T01:00:00Z, 키 둘 AVAILABLE, 받아 둔 값 데이터 A(성공한 때 1시간 전), 세 때 없음, 출처 데이터 A다. "묶음 둘 규칙"은 단위 3과 같다.

- R-108, R-61 (`broadMarketReceived`)
  - Given 묶음 둘 규칙 / When 새로 고침 / Then 0초에 `isRefreshing` 참·`broadMarketReceived` 거짓으로 시작해, FRED와 첫 묶음이 저장된 뒤 `broadMarketReceived` 참(받는 중 그대로), 65초의 둘째 묶음이 끝나면 `isRefreshing` 거짓·`broadMarketReceived` 거짓. `broadMarketReceived`가 참이 된 때 ObserveMoodUseCase의 판정은 새로 받은 SPY의 값으로 낸 것이다(출처의 SPY 2026-11-23을 99.0으로 두면 그때 판정의 계산 단계가 NEUTRAL)
  - Given 첫 묶음이 RATE_LIMITED / Then 받는 동안 `broadMarketReceived`는 계속 거짓이고, 끝나면 `lastFailure` RATE_LIMITED
  - Given 앞 받기에서 참이 되었던 상태 / When 다음 받기가 시작됨 / Then 시작할 때 거짓이다
- R-22, R-55, R-59 (기다리는 동안)
  - Given 묶음 둘 규칙, 둘째 묶음을 기다리는 30초 시점 / When 새로 고침을 다시 누름 / Then 새 받기를 시작하지 않는다(`fetch` 셋 그대로)
  - Given 같은 때 / When `request(WHEN_KEYS_AVAILABLE)` / Then 둘째 묶음까지 끝난 뒤 JOINED로 돌아온다
  - Given 같은 때 / When 청한 쪽이 기다리기를 그만둠 / Then 둘째 묶음까지 받아 저장한다
- R-20, P-45 (받아 둔 종가가 없는 대상)
  - Given 받아 둔 값이 데이터 A에서 XLK를 뺀 것, 성공한 때 1시간 전 / When 화면이 보이게 됨 / Then 받기가 시작된다. `request(WHEN_STALE)`이면 REFRESHED
  - Given 받아 둔 XLK가 값 하나뿐(2026-11-23) / Then 받지 않는다(SKIPPED)
  - Given 데이터 A 그대로, 성공한 때 1시간 전 / Then 받지 않는다(있던 테스트 그대로)
  - Given 데이터 A에서 XLK를 뺌, 키 하나가 MISSING / Then 받지 않는다
- R-60, R-62 (시작한 때와 끝난 때 가운데 늦은 때로부터 1분), `request(WHEN_NOT_RECENTLY_STARTED)`
  - Given STARTED 10분 전, FINISHED 30초 전 / Then SKIPPED
  - Given STARTED 3분 전, FINISHED 61초 전 / Then REFRESHED. FINISHED 60초 전 / Then SKIPPED. 59초 전 / Then SKIPPED
  - Given STARTED 30초 전, FINISHED 5분 전(받는 중에 앱이 꺼져 끝난 때가 앞선 받기의 것) / Then SKIPPED. STARTED 61초 전, FINISHED 5분 전 / Then REFRESHED
  - Given STARTED 2분 전, FINISHED 없음 / Then REFRESHED. 둘 다 없음 / Then REFRESHED
  - Given FINISHED가 지금보다 1시간 뒤 / Then REFRESHED
  - 있던 STARTED만 있는 경우(10초 전 SKIPPED, 61초 전 REFRESHED, 60초 전 SKIPPED, 59초 전 SKIPPED)가 그대로 통과한다
- R-62, P-44 (끝난 때는 받는 중이 풀리기 전): Given 상태를 모으는 중 / When 받기가 끝나 `isRefreshing`이 거짓으로 바뀐 첫 값을 받은 때 / Then 저장소의 FINISHED가 이미 적혀 있다. 그 자리에서 곧바로 `request(WHEN_NOT_RECENTLY_STARTED)`를 하면 SKIPPED다
- 있던 MarketDataRefresher, 다른 기능에 내주는 UseCase, 홈 화면 위젯의 테스트가 모두 통과한다

**확인 기준**
- 테스트 통과, 컴파일 오류 없음

### 단위 5: 화면 상태 (첫 화면, 상세 화면 셋, 섹터 화면)

**만들거나 고칠 것**
- 수정 MoodTextFormatter (feature/marketmood/presentation)
- 신규 TargetLabels (feature/marketmood/presentation)
- 수정 IndicatorLabels (feature/marketmood/presentation)
- 신규 MoodUiMapper (feature/marketmood/presentation)
- 수정 MarketMoodUiState와 화면 상태들 (feature/marketmood/presentation, 새 상태는 같은 파일이나 화면마다의 파일에 둔다)
- 수정 MarketMoodViewModel (feature/marketmood/presentation)
- 신규 RegimeViewModel, PricePositionViewModel, RateBurdenViewModel, SectorViewModel (feature/marketmood/presentation)
- 수정 `app/src/main/res/values/strings.xml`

**지시**
- 화면에 보일 글은 지금처럼 UiText(문구 리소스와 끼워 넣을 값)로 만들고, 숫자를 글로 바꾸는 일은 MoodTextFormatter에 둔다. 카드 상태를 만드는 일은 MoodUiMapper 하나에 모아 ViewModel 다섯이 함께 쓴다. 지금 MarketMoodViewModel 안의 카드 만들기(장세 카드, 매매 판단 카드, 눈금, 안내 줄, 날짜 붙이기, 묶음과 항목 줄)를 MoodUiMapper로 옮긴다. 글의 규칙(부호, 자릿수, 0의 부호, 날짜를 붙이는 조건, 안내 줄의 숫자, 눈금의 칸)은 지금 그대로다. (P-16, P-21, P-33, P-46)
- MoodTextFormatter에 `fun periodName(length: PeriodLength): UiText`를 더한다: MONTHS면 "%1$d개월", YEARS면 "%1$d년". (R-107, P-52)
- TargetLabels: 대상 ID마다의 화면 이름 문구. `broad_market` "전체 시장", `nasdaq_100` "나스닥 100", `semiconductors` "반도체", `technology` "기술", `financials` "금융", `energy` "에너지", `health_care` "헬스케어", `consumer_discretionary` "임의소비재", `consumer_staples` "필수소비재", `industrials` "산업재", `materials` "소재", `utilities` "유틸리티", `real_estate` "부동산", `communication_services` "커뮤니케이션". ETF 기호는 따로 두지 않고 대상의 종가 시리즈 ID(`closeSeries.seriesId`)를 쓴다. 장세의 이름: 전체 시장은 "전체 장세", 섹터는 "{이름} 장세". 대상을 더할 때 여기와 strings.xml에 이름을 더한다. (R-30, R-06, R-87, P-36)
- IndicatorLabels: 주식 세 항목에 근거의 모양을 둔다: `above_average` 평균 괴리, `from_high` 고점 대비, `average_slope` 평균의 기울기. 채권 신호 두 항목은 지금 그대로(SIGNED_PERCENT_POINT, PERCENT). (R-10, P-48)
- 화면 상태. 이름은 아래를 따르고, 필드의 세부(이름, 묶는 법)는 CLI가 정해도 된다. (R-05, R-06, R-63, R-64, R-82~R-93)
  - `RegimeCardUiState`: 맨 위 왼쪽 글("전체 장세 · 최근 3개월"), 기준 날짜 글, 표시 단계와 그 이름, 종합 점수 글과 색 종류, 계산 단계의 이름, 눈금 위치, 다른 날 안내(있을 때만), 링크를 보일지(`showLink`).
  - `ValuationCardUiState`(있는 것에 더함): 카드 이름("주가 위치"/"금리 부담"), `showLink`. 근거 줄 목록은 근거를 넣지 않는 카드에서 빈 목록이다.
  - `OtherPeriodCardUiState`(봉인): `Regime`(이름, 기간 글 "최근 6개월 흐름", 종합 점수 글과 색 종류, 표시 단계와 그 이름, 눈금 위치, 다른 날 안내, 근거 두 줄), `Valuation`(이름, 기간 글 "지난 1년과 비교", 점수와 단계의 글, 표시 단계, 눈금(`ValuationGaugeUiState`), 숫자와 단계가 다른 날 안내), `Unavailable`(이름, 기간 글, 한 줄).
  - `SectorRowUiState`: 대상 ID, 이름, ETF 기호, 기준 날짜 줄(있을 때만), 내용(값 두 칸: 장세의 표시 단계와 이름, 주가 위치의 표시 단계와 "9 · 싼 편" / 상태 글 하나: "받는 중" 또는 "받지 못함"), 누를 수 있는지.
  - 첫 화면의 판정 `MoodUiState`를 바꾼다: 전체 장세 카드, 매매 판단 제목과 그 아래 한 줄, 주가 위치 카드, 금리 부담 카드, 섹터 목록 머리(장세 칸 글, 주가 위치 칸 글), 섹터 줄 목록. 묶음과 항목 줄은 장세 화면으로 옮겨 여기에 두지 않는다.
  - 상세 화면과 섹터 화면의 상태는 `sealed interface DetailUiState<out T> { data object Undecided; data class Shown<T>(val content: T); data object BackToMain }` 하나로 감싼다. 내용은 `RegimeDetailUiState`(화면 이름, ETF 기호(섹터만), 메인 카드, 근거 아래 한 줄 "최근 3개월 기준", 묶음 카드들, 다른 기간 카드들), `PricePositionDetailUiState`(대상 글 "전체 시장 SPY", 제목, 그 아래 한 줄, 메인 카드(근거 포함), 다른 기간 카드들), `RateBurdenDetailUiState`(제목, 그 아래 한 줄, 메인 카드(근거 포함), 다른 기간 카드들), `SectorUiState`(섹터 이름, ETF 기호, 장세 카드, 제목, 그 아래 한 줄, 주가 위치 카드). 상세 화면과 섹터 화면의 상태에는 새로 고침, 안내 띠, 받는 중을 두지 않는다. (R-18, R-85, R-93, P-47)
- MoodUiMapper: `class MoodUiMapper @Inject constructor()`. 넘겨받은 한 벌(MarketOverview)과 그 안의 규칙(`overview.rules`)만으로 글을 만든다. 함수의 나눔은 CLI가 정한다. 아래가 만들 것이다.
  - 장세 카드 (R-06, R-07, R-08, R-13, R-21 경계, P-21): 맨 위 왼쪽 "{장세의 이름} · 최근 {메인 기간의 이름}", 오른쪽 "{월}월 {일}일 종가 기준 (미국)"(그 대상의 기준 날짜), 표시 단계, 종합 점수 "+0.77"(소수 두 자리)와 그 색 종류, 계산 단계의 이름, 눈금 위치(지금 식 그대로), 다른 날 안내(계산 단계 ≠ 표시 단계일 때 지금 문구 그대로), `showLink`는 첫 화면과 섹터 화면에서 참, 장세 화면의 메인 카드에서 거짓.
  - 묶음과 항목 줄 (R-09, R-10, R-11, R-12, R-13, P-48): 묶음 이름, 덧붙이는 글(주식은 "{대상 이름} {ETF 기호} · 비중 70%", 채권 신호는 "비중 30%"), 묶음 점수. 항목 줄은 지금 모양이고 근거 글은 메인 기간의 숫자로: 평균 대비 "{N}일 평균보다 {괴리}", 고점 대비 "최근 {기간의 이름} 고점보다 {낙폭}", 평균의 기울기 "{N}일 평균이 {S}일 전보다 {기울기}". 셋 다 `signedPercent`. 채권 신호는 지금 그대로(날짜가 다르면 " · {월}월 {일}일 값", 그 대상의 기준 날짜와 견줌).
  - 장세의 다른 기간 카드 (R-88, R-08, R-21 경계): 이름은 장세의 이름, 기간 글 "최근 {기간의 이름} 흐름", 종합 점수(소수 두 자리, 색 종류), 표시 단계 이름, 눈금 위치, 다른 날 안내(그 콘텐츠의 계산 단계와 표시 단계, 이어진 거래일), 근거 첫 줄 "{N}일 평균보다 {괴리} · {기간의 이름} 고점보다 {낙폭}", 둘째 줄 "{N}일 평균이 {S}일 전보다 {기울기}"(그 기간의 숫자).
  - 매매 판단 카드 (R-63, R-64, R-65, R-66, R-67, R-68, R-69, R-70, P-33, P-34): 주가 위치의 이름 아래 글 "{대상 이름} {ETF 기호}", 금리 부담은 "실질금리 2.79%"(날짜 붙이기 그대로). 점수와 단계, 눈금, 안내 줄은 지금 그대로(경계와 완충은 `overview.rules.valuation`). 근거는 주가 위치 화면과 금리 부담 화면의 메인 카드에만 넣는다. 주가 위치의 근거는 "{짧은 평균 일수}일 평균보다 {짧은 괴리} · {긴 평균의 이름} 평균보다 {긴 괴리}", "고점보다 {낙폭} · {3개월의 이름} 전보다 {3개월 수익률}". 금리 부담의 근거 세 줄은 지금 그대로. `showLink`는 첫 화면의 두 카드와 섹터 화면의 주가 위치 카드에서 참.
  - 매매 판단의 다른 기간 카드 (R-91, R-67): 이름("주가 위치"/"금리 부담"), 기간 글 "지난 {기간의 이름}과 비교", 점수와 단계 "8 · 싼 편", 표시 단계, 눈금, 안내 줄(그 콘텐츠의 점수와 단계로). 근거는 없다.
  - 낼 수 없는 다른 기간 카드 (R-92, R-104): 이름과 기간 글은 그대로, 매매 판단이면 "받아 둔 값이 모자라 지난 {기간의 이름}과 견줄 수 없습니다. 값이 쌓이면 나옵니다.", 장세면 "받아 둔 값이 모자라 최근 {기간의 이름} 흐름을 낼 수 없습니다. 값이 쌓이면 나옵니다."
  - 다른 기간 카드의 순서는 규칙의 기간 순서(짧은 기간부터)에서 메인을 뺀 것이다. 장세 6개월, 1년. 매매 판단 1년, 3년, 5년. (R-86)
  - 매매 판단 제목 "지난 {메인 기간의 이름}과 비교하면", 그 아래 "0은 지난 {메인 기간의 이름} 중 가장 낮은 쪽, 100은 가장 높은 쪽". (R-05, R-89, R-93, R-107)
  - 섹터 목록 (R-82, R-83, R-84, R-96, R-105, R-106, P-50): 머리 "섹터", "장세\n{장세 메인 기간의 이름}", "주가 위치\n{매매 판단 메인 기간의 이름}"(줄바꿈은 시안의 두 줄 모양을 따른 것이다). 섹터 줄은 규칙의 섹터 순서다. 그 섹터의 메인 장세와 메인 주가 위치가 모두 있으면 값 두 칸(장세의 표시 단계 이름, "{점수} · {단계 이름}")과 누를 수 있음이다. 하나라도 없으면 상태 글 하나(받는 중이면 "받는 중", 아니면 "받지 못함")이고 누를 수 없다. 받는 중인지는 RefreshState의 `isRefreshing`으로 정한다(어느 묶음을 받았는지 따지지 않는다). 기준 날짜 줄 "{월}월 {일}일 종가 기준"은 값을 보이는 줄에서 그 섹터의 기준 날짜가 전체 시장의 것과 다를 때만 있다.
  - 문구에 끼우는 일수와 기간의 이름은 모두 `overview.rules`에서 낸다. 글로 적지 않는다. (R-107, P-52)
- MarketMoodViewModel (R-04, R-05, R-15, R-18, R-21, R-22, R-24, R-28, R-44, R-47, R-49, R-53, R-106, P-46)
  - 생성자 `(observeOverview: ObserveMarketOverviewUseCase, observeSourceKeys: ObserveSourceKeysUseCase, refresher: MarketDataRefresher, mapper: MoodUiMapper)`. ObserveMoodUseCase는 더 쓰지 않는다.
  - 본문 고르기, 새로 고침 버튼, 안내 띠, 받는 중·받지 못함 카드, 키 안내 카드는 지금 규칙 그대로이고, "판정이 있다"를 한 벌이 있는지(null이 아님)로 본다.
  - 판정이면 MoodUiMapper로 전체 장세 카드, 매매 판단 제목, 두 카드(근거 없음, 링크 있음), 섹터 목록을 만든다.
  - `onRefreshClick`, `onRetryClick`, `onScreenShown`은 그대로다.
- 상세 화면과 섹터 화면의 ViewModel (R-87, R-89, R-90, R-93, R-99, P-01, P-46, P-55)
  - `RegimeViewModel`, `PricePositionViewModel`, `SectorViewModel`: 생성자 `(savedStateHandle: SavedStateHandle, observeOverview: ObserveMarketOverviewUseCase, observeSourceKeys: ObserveSourceKeysUseCase, mapper: MoodUiMapper)`. 대상 ID는 `savedStateHandle`의 `targetId`로 받는다(단위 6의 이동 경로 인자와 같은 이름). `RateBurdenViewModel`은 대상 ID가 없다.
  - `uiState: StateFlow<DetailUiState<…>>`. 한 벌과 키 쓸 수 있음 여부를 받기 전에는 Undecided. 출처의 키 가운데 하나라도 AVAILABLE이 아니거나, 한 벌이 null이거나, 그 대상을 규칙에서 찾지 못하거나, 그 대상의 메인 콘텐츠를 낼 수 없으면 BackToMain(장세 화면은 메인 장세, 주가 위치 화면은 메인 주가 위치, 섹터 화면은 둘 다, 금리 부담 화면은 메인 금리 부담). 아니면 Shown이다. 한 벌이 바뀔 때마다(묶음이 저장될 때마다 포함) 다시 만든다.
  - 장세 화면: 화면 이름은 장세의 이름("전체 장세"/"반도체 장세"), ETF 기호는 섹터만("SMH"), 메인 카드(`showLink` 거짓), 근거 아래 한 줄 "최근 {메인 기간의 이름} 기준", 묶음 카드 둘, 다른 기간 카드 둘.
  - 주가 위치 화면: 대상 글 "전체 시장 SPY"/"반도체 SMH", 매매 판단 제목과 그 아래 한 줄, 메인 카드(근거 두 줄, 링크 없음), 다른 기간 카드 셋.
  - 금리 부담 화면: 제목과 그 아래 한 줄, 메인 카드(근거 세 줄, 링크 없음), 다른 기간 카드 셋.
  - 섹터 화면: 섹터 이름, ETF 기호, 장세 카드(링크 있음), 제목과 그 아래 한 줄, 주가 위치 카드(근거 없음, 링크 있음). 금리 부담은 두지 않는다.
- `strings.xml`: 아래 문구를 더하고, 쓰지 않게 된 문구(`valuation_title`, `valuation_description`, `mood_evidence_title`, `price_position_subtitle`, `price_evidence_average`, `price_evidence_drawdown`)는 숫자를 끼우는 문구로 바꾸거나 지운다. 홈 화면 위젯이 쓰는 문구(`mood_now`, `price_position_name`, `rate_burden_name`, `valuation_*`의 단계 이름과 `valuation_score`, `stage_*`, `widget_*` 등)는 바꾸지 않는다. 이름은 CLI가 정한다. (R-05~R-14, R-63~R-70, R-82~R-93, R-107)
  - "%1$d개월", "%1$d년"
  - 대상 이름 열넷(위 TargetLabels), "전체 장세", "%1$s 장세", "%1$s %2$s"(이름과 ETF 기호), "%1$s · 최근 %2$s", "%1$s · 비중 %2$d%%"
  - "근거와 다른 기간", "근거", "최근 %1$s 기준", "다른 기간으로 보면", "기간마다 따로 낸 판정입니다. 위의 판정이 메인입니다."
  - "%1$d일 평균보다 %2$s", "최근 %1$s 고점보다 %2$s", "%1$d일 평균이 %2$d일 전보다 %3$s"
  - "최근 %1$s 흐름", "%1$d일 평균보다 %2$s · %3$s 고점보다 %4$s"
  - "지난 %1$s과 비교하면", "0은 지난 %1$s 중 가장 낮은 쪽, 100은 가장 높은 쪽", "지난 %1$s과 비교"
  - "%1$d일 평균보다 %2$s · %3$s 평균보다 %4$s", "고점보다 %1$s · %2$s 전보다 %3$s"
  - "아직 없음", "받아 둔 값이 모자라 지난 %1$s과 견줄 수 없습니다. 값이 쌓이면 나옵니다.", "받아 둔 값이 모자라 최근 %1$s 흐름을 낼 수 없습니다. 값이 쌓이면 나옵니다."
  - "섹터별", "누르면 그 섹터의 화면이 열립니다", "섹터", "장세\n%1$s", "주가 위치\n%1$s", "받는 중", "받지 못함", "%1$d월 %2$d일 종가 기준"

**테스트 지시**
아래 Given의 한 벌은 시험용 규칙과 받아 둔 값으로 MarketOverviewCalculator가 낸 것이다(모델을 직접 만들어도 된다). 글의 확인은 있던 테스트처럼 문구 리소스와 끼워 넣는 값으로 해도 된다. 기준 날짜는 2026-11-23이다.

- R-107 (MoodTextFormatter): `periodName((3, MONTHS))` → "3개월", `((2, YEARS))` → "2년", `((18, MONTHS))` → "18개월"
- R-05 (첫 화면의 구성): Given 데이터 A, 키 둘 AVAILABLE / When MarketMoodViewModel의 uiState / Then body는 Assessment이고 전체 장세 카드, 매매 판단 제목 "지난 2년과 비교하면"과 "0은 지난 2년 중 가장 낮은 쪽, 100은 가장 높은 쪽", 주가 위치 카드와 금리 부담 카드(둘 다 근거 빈 목록, `showLink` 참), 섹터 머리, 섹터 줄 셋이 있다. 안내 띠가 있을 때(가장 최근 받기 NO_CONNECTION)와 받는 중일 때도 같다
- R-06 (장세 카드): Given 데이터 A / Then 전체 장세 카드의 맨 위 "전체 장세 · 최근 3개월", "11월 23일 종가 기준 (미국)", 표시 단계 BULL "불장", 종합 점수 "+0.77" POSITIVE, 계산 단계 "불장", 눈금 위치 0.8833335(±1e-6), 다른 날 안내 없음, `showLink` 참. 섹터 화면(QQQ)의 장세 카드 맨 위 "나스닥 100 장세 · 최근 3개월", `showLink` 참. 장세 화면의 메인 카드 `showLink` 거짓
- R-08 (메인 카드): Given 데이터 B / Then 전체 장세 카드의 종합 점수 "+0.15", 계산 단계 "중립", 눈금 위치 0.575, 다른 날 안내 "종합 점수는 중립인데 장세는 아직 불장입니다. 점수가 3거래일 이어져야 장세가 바뀝니다. (1일째)"
- R-08 (다른 기간 카드): Given 데이터 B / When XLK의 장세 화면 / Then 다른 기간 카드의 둘째(1년)는 표시 단계 "중립", 종합 점수 "-0.08" NEGATIVE, 다른 날 안내 "종합 점수는 약세인데 장세는 아직 중립입니다. 점수가 3거래일 이어져야 장세가 바뀝니다. (1일째)". 첫째(6개월)는 "불장", 안내 없음
- R-09, R-10 (묶음과 항목 줄, 장세 화면)
  - Given 데이터 A / When 전체 시장의 장세 화면 / Then 묶음 둘: "주식" "전체 시장 SPY · 비중 70%" "+0.67" POSITIVE, 항목 "평균 대비" "50일 평균보다 0.0%" "0.0" ZERO, "고점 대비" "최근 3개월 고점보다 0.0%" "+1.0", "평균의 기울기" "50일 평균이 20일 전보다 0.0%" "+1.0". "채권 신호" "비중 30%" "+1.00", "장단기 금리차"(덧붙이는 글 "10년 - 2년") "+0.60%p", "하이일드 스프레드" "3.00%"
  - Given 데이터 B / Then 주식 "0.00" ZERO, 항목 근거 "50일 평균보다 -1.0%", "최근 3개월 고점보다 -1.0%", "50일 평균이 20일 전보다 0.0%", 점수 "0.0", "+1.0", "-1.0". 채권 신호 "+0.50", 장단기 금리차 "+0.29%p" "0.0", 하이일드 스프레드 "3.76% · 11월 20일 값" "+1.0" (R-12)
  - Given 데이터 B / When SMH의 장세 화면 / Then 주식 묶음의 덧붙이는 글 "반도체 SMH · 비중 70%", 근거 "50일 평균보다 -19.7%", "최근 3개월 고점보다 -20.0%", "50일 평균이 20일 전보다 -0.4%", 묶음 점수 "-1.00" NEGATIVE
- R-88 (장세의 다른 기간 카드): Given 데이터 A / When 전체 시장의 장세 화면 / Then 다른 기간 카드 둘: 이름 "전체 장세", 기간 글 "최근 6개월 흐름", 종합 점수 "+0.77" POSITIVE, "불장", 눈금 0.8833335, 근거 "100일 평균보다 0.0% · 6개월 고점보다 0.0%", "100일 평균이 40일 전보다 0.0%". 둘째는 "최근 1년 흐름", "200일 평균보다 0.0% · 1년 고점보다 0.0%", "200일 평균이 80일 전보다 0.0%". Given 데이터 B, XLK / Then 둘째의 근거 "200일 평균보다 0.0% · 1년 고점보다 -16.7%", "200일 평균이 80일 전보다 -0.1%". QQQ의 카드 이름은 "나스닥 100 장세"
- R-63, R-64 (매매 판단 카드)
  - Given 데이터 A / Then 첫 화면의 주가 위치 카드 이름 "주가 위치", 아래 글 "전체 시장 SPY", "50 · 보통", 근거 빈 목록. 금리 부담 카드 "금리 부담", "실질금리 2.00%", "50 · 보통", 근거 빈 목록. 섹터 화면(SMH)의 주가 위치 카드 아래 글 "반도체 SMH", 근거 빈 목록, `showLink` 참
  - 지금 ViewModel 테스트에 있는 매매 판단 카드의 경우(R-65 칸 폭·칸 이름·위치, R-66 단계의 이름, R-67 안내 줄과 그 숫자, R-69 금리 부담의 세 줄, R-70 날짜 붙이기)는 경우와 기대 값을 줄이지 않고 MoodUiMapper(첫 화면 또는 금리 부담 화면의 메인 카드)의 테스트로 옮긴다. 근거 줄을 보던 경우는 근거가 있는 상세 화면의 메인 카드에서 본다
- R-68 (주가 위치의 근거, 주가 위치 화면): Given 데이터 A / Then 메인 카드의 근거 "5일 평균보다 0.0% · 3년 평균보다 0.0%", "고점보다 0.0% · 3개월 전보다 0.0%" (시험용 규칙의 짧은 평균은 5, 긴 평균과 3개월의 이름은 "3년", "3개월"이다). 있던 R-68 글 테스트(+4.1%, +21.4% 등)는 같은 값으로 옮기되 첫 줄의 "200일" 자리는 규칙의 짧은 평균 일수를 따른다
- R-69 (금리 부담의 근거, 금리 부담 화면): Given 데이터 A / Then 메인 카드의 근거 "1년 전과 같음", "10년물 국채 4.00% · 기준금리 3.50%", "물가 3.0% · 목표 2%보다 +1.0%p · 10월 값"
- R-67 (다른 기간 카드의 안내 줄): Given 시험용 규칙에서 매매 판단의 `3y` 위치 기간만 101로, 평균 일수를 1로 바꾼 규칙, 실질금리의 마지막 102개를 점수열 30, 35가 나오는 값으로 덮은 데이터 A / When 금리 부담 화면 / Then 다른 기간 카드 가운데 3년 카드의 점수와 단계 "35 · 낮음", 안내 줄 "점수는 35이지만 아직 '낮음'입니다. 36 이상이 되어야 보통으로 바뀝니다."
- R-91 (매매 판단의 다른 기간 카드): Given 데이터 A / When 주가 위치 화면 / Then 다른 기간 카드 셋의 기간 글 "지난 1년과 비교", "지난 3년과 비교", "지난 5년과 비교", 모두 "50 · 보통", 표시 단계 MIDDLE, 눈금 칸 0.31·0.38·0.31과 위치 0.5, 안내 없음, 근거 없음. 금리 부담 화면도 같고 이름은 "금리 부담"이다
- R-92, R-104 (낼 수 없는 기간)
  - Given 시험용 규칙에서 `5y` 위치 기간만 600 / When 주가 위치 화면 / Then 셋째 카드는 Unavailable, 이름 "주가 위치", 기간 글 "지난 5년과 비교", 한 줄 "받아 둔 값이 모자라 지난 5년과 견줄 수 없습니다. 값이 쌓이면 나옵니다." 금리 부담 화면도 같다
  - Given 데이터 A에서 QQQ를 마지막 200평일만 남김 / When QQQ의 장세 화면 / Then 둘째 카드는 Unavailable, 기간 글 "최근 1년 흐름", 한 줄 "받아 둔 값이 모자라 최근 1년 흐름을 낼 수 없습니다. 값이 쌓이면 나옵니다." 첫째 카드는 값이 있다
- R-86 (다른 기간 카드의 순서): Given 데이터 A / Then 장세 화면은 6개월, 1년. 주가 위치 화면과 금리 부담 화면은 1년, 3년, 5년(메인 2년이 빠진다)
- R-82, R-96, R-105 (섹터 목록): Given 데이터 A / Then 머리 "섹터", "장세\n3개월", "주가 위치\n2년". 줄 셋이 "나스닥 100" QQQ, "반도체" SMH, "기술" XLK 순서이고, 모두 "불장"(BULL), "50 · 보통"(MIDDLE), 기준 날짜 줄 없음, 누를 수 있음
- R-83: Given 데이터 A에서 QQQ의 2026-11-23을 뺌 / Then QQQ 줄의 기준 날짜 줄 "11월 20일 종가 기준", 나머지 줄은 없음
- R-84, R-106
  - Given 데이터 A에서 SMH를 뺌, 받는 중 아님 / Then SMH 줄은 상태 글 "받지 못함", 누를 수 없음, 기준 날짜 줄 없음. 받는 중 / Then "받는 중"
  - Given 받아 둔 값이 SPY와 FRED의 시리즈만(데이터 A에서 QQQ, SMH, XLK를 뺌), 받는 중 / When MarketMoodViewModel의 uiState / Then body는 Assessment이고 섹터 줄 셋 모두 "받는 중", 누를 수 없음. 받기가 실패로 끝나면 셋 모두 "받지 못함"이고 안내 띠가 있다
  - Given 데이터 A에서 QQQ를 마지막 60평일만 남김, 받는 중 아님 / Then QQQ 줄은 "받지 못함"이다(메인 장세를 낼 수 없다)
- R-107 (문구의 숫자가 규칙을 따름): Given 시험용 규칙에서 장세 `3m`의 평균 일수 40, 기울기 일수 10, 이름 (2, MONTHS), 매매 판단의 짧은 평균 150, 긴 평균의 이름 (4, YEARS), 3개월의 이름 (2, MONTHS), `2y`의 이름 (18, MONTHS)로 바꾼 규칙, 데이터 A / Then 전체 장세 카드 "전체 장세 · 최근 2개월", 장세 화면의 근거 아래 "최근 2개월 기준", 항목 근거 "40일 평균보다 0.0%", "최근 2개월 고점보다 0.0%", "40일 평균이 10일 전보다 0.0%", 매매 판단 제목 "지난 18개월과 비교하면"과 "0은 지난 18개월 중 가장 낮은 쪽, 100은 가장 높은 쪽", 주가 위치 화면의 근거 "150일 평균보다 0.0% · 4년 평균보다 0.0%", "고점보다 0.0% · 2개월 전보다 0.0%", 섹터 머리 "장세\n2개월", "주가 위치\n18개월"
- R-87, R-89, R-90, R-93 (상세 화면과 섹터 화면의 구성)
  - Given 데이터 A, 키 둘 AVAILABLE / When RegimeViewModel(`targetId` = `broad_market`) / Then Shown: 화면 이름 "전체 장세", ETF 기호 없음, 메인 카드, "최근 3개월 기준", 묶음 둘, 다른 기간 둘. `semiconductors` / Then 화면 이름 "반도체 장세", ETF 기호 "SMH"
  - PricePositionViewModel(`broad_market`) / Then 대상 글 "전체 시장 SPY", 제목 "지난 2년과 비교하면", 메인 카드에 근거 두 줄, `showLink` 거짓, 다른 기간 셋. `semiconductors` / Then "반도체 SMH"
  - RateBurdenViewModel / Then 제목, 메인 카드에 근거 세 줄, `showLink` 거짓, 다른 기간 셋
  - SectorViewModel(`semiconductors`) / Then 이름 "반도체", ETF 기호 "SMH", 장세 카드 "반도체 장세 · 최근 3개월"(`showLink` 참), 제목 "지난 2년과 비교하면", 주가 위치 카드 "반도체 SMH"(근거 없음, `showLink` 참). 금리 부담 카드는 없다
- R-99 (새 값으로 바뀜, 첫 화면으로 돌아감)
  - Given 장세 화면(`broad_market`)이 데이터 A로 Shown / When 받아 둔 값이 데이터 B로 바뀜 / Then 메인 카드의 종합 점수가 "+0.15"가 된다. 주가 위치 화면, 금리 부담 화면, 섹터 화면도 새 한 벌로 바뀐다
  - Given Shown / When Twelve Data 키가 INVALID가 됨 / Then BackToMain. 키가 MISSING이 됨 / Then BackToMain (네 ViewModel 모두)
  - Given 받아 둔 값이 비어 한 벌이 null / Then BackToMain
  - Given SectorViewModel(`semiconductors`) Shown / When 받아 둔 값에서 SMH가 빠짐 / Then BackToMain. RegimeViewModel·PricePositionViewModel(`semiconductors`)도 BackToMain. RegimeViewModel(`broad_market`)은 Shown 그대로
  - Given 규칙에 없는 `targetId`(`unknown`) / Then BackToMain
  - Given 한 벌을 아직 받지 못함 / Then Undecided
- 있던 MarketMoodViewModel 테스트(R-04 본문 고르기, R-15 키 안내, R-18 안내 띠, R-21·R-28 받기, R-22·R-24 버튼, R-44, R-45·R-46 글, R-47, R-49, R-53)는 경우와 기대 값을 줄이지 않고 새 생성자로 통과한다. 장세 카드와 묶음·항목 줄을 보던 테스트는 위의 MoodUiMapper·장세 화면 테스트로 옮겨 경우를 줄이지 않는다

**확인 기준**
- 테스트 통과, 컴파일 오류 없음

### 단위 6: 공용 테마, 화면, 이동

**만들거나 고칠 것**
- 수정 AppColors, AppTypography, AppDimens (core/ui/theme)
- 수정 AppIcons (core/ui)
- 신규 MoodCards (feature/marketmood/presentation) — 지금 MarketMoodScreen 안의 카드, 눈금, 안내 줄, 작은 제목, 맨 아래 안내, 카드 바탕을 꺼낸다
- 신규 DetailTopBar (feature/marketmood/presentation)
- 수정 MarketMoodScreen (feature/marketmood/presentation)
- 신규 RegimeScreen, PricePositionScreen, RateBurdenScreen, SectorScreen (feature/marketmood/presentation)
- 수정 AppNavHost (맨 위 패키지)

**지시**
- 공용 테마에 값을 더한다. 값은 시안에서 뽑은 것이고 1px = 1dp(글자는 1sp)로 옮긴다. 있는 값은 바꾸지 않고, 있는 값과 같은 것은 있는 것을 쓴다. (R-19, R-94, P-17)
  - AppIcons에 "›"(시안의 `M9 6l6 6-6 6`, 선 굵기 2, 끝과 꺾이는 곳 둥글게)를 더한다. 링크 줄과 섹터 줄이 함께 쓴다. 뒤로 가기는 있는 `Back`(시안과 같은 모양)을 쓴다. (P-18)
  - AppColors: 섹터 줄의 "›" `#8A929C`(있는 `disabled` `#8A93A0`과 다르다). "근거와 다른 기간 ›"의 글과 화살표는 있는 `accent` `#2457C5`.
  - AppTypography: "근거와 다른 기간" 14 Medium, 다른 기간 카드의 점수와 단계(장세는 단계 이름) 18 Bold, 낼 수 없는 기간의 한 줄 13 Normal 줄 높이 1.5(있는 `captionBanner`와 같으면 그것을 쓴다). 있는 값을 쓰는 곳: 다른 기간 카드의 이름과 "아직 없음" 15 Bold `bodyBold`, 그 아래 기간 글 12 `sectionDescription`, 섹터 줄의 이름 15 Medium `bodyMedium`, ETF 기호·머리 줄·기준 날짜 줄 12 `sectionDescription`, 장세와 주가 위치 칸 15 Bold `bodyBold`, "받는 중"·"받지 못함" 14 `small`, 상세 화면의 위쪽 줄 이름 20 Bold `title`과 그 옆 대상 15 `body`(색 `textMuted`)
  - AppDimens: 링크 줄 높이 44(위에 구분선 1). 링크가 있는 카드의 아래 여백은 장세 카드 4, 매매 판단 카드 0(시안의 카드 아래 여백 12·8과 링크의 `margin-bottom: -8px`을 합친 값). 링크의 화살표 16. 다른 기간 카드 안쪽 여백 위아래 14·좌우 16, 카드 안 덩어리 사이 10, 이름과 기간 글 사이 1, 다른 기간 카드 사이 8. 섹터 목록 카드 안쪽 여백 위 12·좌우 16·아래 4, 머리 줄 아래 8, 칸 사이 8, 줄 높이(최소) 52, 장세 칸 폭 52, 주가 위치 칸 폭 84, 화살표 칸 16, 상태 글 칸 폭 160, 이름과 기준 날짜 줄 사이 1. 상세 화면의 위쪽 줄 좌우 여백 4, 버튼과 이름 사이 4, 뒤로 가기 아이콘 22(있는 `icon`, 단추 44 `iconButton`). 장세 화면의 메인 카드 안쪽 여백 20(있는 `cardPadding`)
- MoodCards: 화면 다섯이 함께 그리는 것을 여기에 둔다. 장세 카드(맨 위 줄이 "전체 장세 · 최근 3개월"로 바뀐 것 말고는 지금 모양, `showLink`면 아래에 링크 줄), 매매 판단 카드(지금 모양, 이름을 상태에서 받고, `showLink`면 링크 줄), 링크 줄(위 구분선, 왼쪽 "근거와 다른 기간", 오른쪽 "›" 아이콘, 줄 전체를 누를 수 있음), 다른 기간 카드 셋, 묶음 카드와 항목 줄(지금 모양), 눈금(지금 것), 안내 줄(지금 것), 작은 제목과 그 아래 한 줄(지금 것), 섹터 목록 카드, 맨 아래 안내(지금 것), 카드 바탕. 받은 상태를 그리기만 하고 글을 고르거나 숫자를 바꾸지 않는다. (R-06~R-14, R-63~R-70, R-82~R-92, P-51)
  - 다른 기간 카드(장세): 머리(왼쪽에 이름 15 Bold와 그 아래 기간 글 12 `textMuted` "최근 6개월 흐름 · 종합 점수 +0.15", 오른쪽에 단계 이름 18 Bold 단계의 색), 눈금, 안내 줄(있을 때), 근거 두 줄(위에 구분선, 그 아래 여백 10, 13 `textMuted`). 종합 점수는 R-13대로 점수의 색으로 적는다(시안에서는 회색이지만 글이 맞다).
  - 다른 기간 카드(매매 판단): 머리(이름, 기간 글, 오른쪽에 "8 · 싼 편" 18 Bold 단계의 색), 눈금, 안내 줄(있을 때). 근거 없음.
  - 낼 수 없는 기간 카드: 머리(이름, 기간 글, 오른쪽에 "아직 없음" 15 Bold `textMuted`), 그 아래 한 줄. 눈금과 안내 없음.
  - 섹터 목록 카드: 머리 줄(왼쪽 "섹터"가 남은 폭을 차지, 장세 칸 52 가운데 맞춤, 주가 위치 칸 84 오른쪽 맞춤, 화살표 칸 16, 두 줄 글), 섹터 줄마다 위에 구분선, 최소 높이 52, 왼쪽에 이름과 그 옆 ETF 기호(기준 날짜 줄이 있으면 이름 아래에), 장세 칸(단계 이름, 장세의 색), 주가 위치 칸("9 · 싼 편", 매매 판단 단계의 색, 한 줄), "›" `#8A929C` 16. 상태 글 줄은 두 칸 대신 폭 160 오른쪽 맞춤 "받는 중"/"받지 못함"(14 `textMuted`)이고 화살표가 없으며 눌러도 반응하지 않는다.
- DetailTopBar: 상세 화면과 섹터 화면의 위쪽 줄. 높이 56, 흰 바탕, 아래 구분선, 왼쪽에 뒤로 가기 단추(있는 `AppIcons.Back`), 그 오른쪽에 이름(20 Bold)과 같은 줄에 대상(15 `textMuted`, 있을 때만: 장세 화면의 섹터 ETF 기호, 주가 위치 화면의 "전체 시장 SPY"/"반도체 SMH", 섹터 화면의 ETF 기호). 상태 표시줄 자리도 위쪽 줄 바탕으로 칠한다(설정 화면과 같다). 새로 고침과 메뉴는 두지 않는다. 시안은 대상을 이름 옆에 그렸다(R-85의 "옆이나 아래" 가운데 옆). (R-85, R-87, R-89, R-93, P-47)
- MarketMoodScreen: 위쪽 줄, 안내 띠, 본문 고르기, 메뉴, 키 안내·받는 중·받지 못함 카드는 그대로다. 판정은 위에서 아래로 전체 장세 카드, "지난 2년과 비교하면" 제목과 한 줄, 주가 위치 카드, 금리 부담 카드, "섹터별"과 "누르면 그 섹터의 화면이 열립니다", 섹터 목록 카드, 맨 아래 안내다("장세의 근거"와 묶음 카드는 첫 화면에서 빠진다). 받는 콜백: `onOpenSettings`, `onOpenRegime(targetId: String)`, `onOpenPricePosition(targetId: String)`, `onOpenRateBurden()`, `onOpenSector(targetId: String)`. 전체 장세 카드의 링크는 `onOpenRegime("broad_market")`, 주가 위치 카드는 `onOpenPricePosition("broad_market")`, 금리 부담 카드는 `onOpenRateBurden()`, 누를 수 있는 섹터 줄은 `onOpenSector(그 대상 ID)`. 화면이 보이게 되면 ViewModel에 알리는 것은 그대로다(첫 화면만). (R-02, R-05, R-20, R-95, R-96, P-03)
- RegimeScreen, PricePositionScreen, RateBurdenScreen, SectorScreen: `hiltViewModel()`로 ViewModel을 받고, 콜백 `onBack`, `onReturnToMain`을 받는다. SectorScreen은 `onOpenRegime(targetId)`, `onOpenPricePosition(targetId)`도 받는다. 상태가 BackToMain이면 `onReturnToMain`을 한 번 부른다. Undecided면 위쪽 줄만 그린다. 화면이 보이게 되어도 받기를 알리지 않는다. 기기의 뒤로 가기는 Navigation의 기본 동작을 쓴다. 본문은 위쪽 줄을 제자리에 두고 위아래로 밀 수 있다. (R-20, R-85, R-86, R-87, R-89, R-90, R-93, R-97, R-98, R-99, P-46, P-47)
  - 장세 화면: 메인 카드(링크 없음), "근거"와 "최근 3개월 기준", 묶음 카드 둘, "다른 기간으로 보면"과 "기간마다 따로 낸 판정입니다. 위의 판정이 메인입니다.", 다른 기간 카드들(사이 8), 맨 아래 안내.
  - 주가 위치 화면: "지난 2년과 비교하면"과 한 줄, 메인 카드(근거 두 줄을 눈금 아래에, 링크 없음), "다른 기간으로 보면"과 한 줄, 다른 기간 카드들, 맨 아래 안내. 금리 부담 화면도 같은 모양이고 메인 카드의 근거는 세 줄이다.
  - 섹터 화면: 장세 카드(링크 → `onOpenRegime(그 섹터)`), "지난 2년과 비교하면"과 한 줄, 주가 위치 카드(링크 → `onOpenPricePosition(그 섹터)`), 맨 아래 안내.
- AppNavHost: 이동 목록에 네 화면을 더한다. 경로는 `regime/{targetId}`, `price_position/{targetId}`, `rate_burden`, `sector/{targetId}`이고 인자 이름 `targetId`는 ViewModel이 `SavedStateHandle`로 받는 이름과 같다. 모두 시장 분위기 화면 위에 쌓는다(`launchSingleTop`). 뒤로 가기 단추는 맨 위가 그 화면일 때만 하나를 걷는다(설정 화면과 같은 방식). `onReturnToMain`은 시장 분위기 화면 위를 모두 걷는다(위젯에서 열 때와 같은 동작). 위젯에서 열 때 위를 모두 걷는 것은 그대로다. (R-01, R-95~R-99, P-01)
- 시안과 같은 모양으로 만든다. 모양의 값(색, 크기, 간격, 모서리)은 시안의 HTML에서 가져오되, 테마에 있는 값은 테마의 것을 쓴다. 문구와 동작은 이 지시의 글이 맞다. 시안의 숫자와 날짜는 예시다. 시안의 장세 카드는 장세와 종합 점수가 같은 날이라 "종합 점수 -0.32 · 약세"처럼 적혀 있다. (R-19, R-94)

| 시안의 화면 이름 | 시안 안의 파일 | 이번에 보는 것 |
|---|---|---|
| 시장 분위기 · 둘째 묶음: 첫 화면 (전체 장세, 섹터별) | `project/Market-Sectors.dc.html` | 첫 화면의 배치, 링크 줄, 섹터 목록 |
| 첫 화면 · 장세와 종합 점수가 다른 날, 주가 위치의 숫자와 단계가 다른 날 | `project/Market-SectorsPending.dc.html` | 링크 위의 안내 줄 |
| 첫 화면 · 처음 받는 중 (섹터 일부를 아직 받는 중) | `project/Market-SectorsLoading.dc.html` | "받는 중" 줄, 위쪽 줄의 진행 표시 |
| 첫 화면 · 섹터 일부를 받지 못했을 때 (받은 적 없는 섹터, 전에 받은 값이 남은 섹터) | `project/Market-SectorsFailed.dc.html` | "받지 못함" 줄, 기준 날짜 줄, 안내 띠 |
| 전체 장세 화면 (근거와 다른 기간) | `project/Detail-Regime.dc.html` | 위쪽 줄, 근거, 다른 기간 카드 |
| 전체 장세 화면 · 메인과 다른 기간 카드의 점수와 장세가 다른 날 | `project/Detail-RegimePending.dc.html` | 다른 기간 카드의 안내 줄 |
| 주가 위치 화면 (근거와 다른 기간) | `project/Detail-Price.dc.html` | 메인 카드의 근거, 다른 기간 카드 |
| 주가 위치 화면 · 숫자와 단계가 다른 날, 아직 낼 수 없는 기간 (XLC) | `project/Detail-PriceRare.dc.html` | 다른 기간 카드의 안내 줄, "아직 없음" 카드 |
| 금리 부담 화면 (근거와 다른 기간) | `project/Detail-Rate.dc.html` | 근거 세 줄, 다른 기간 카드 |
| 섹터 화면 · 반도체 SMH | `project/Sector-Detail.dc.html` | 섹터 화면 |
| 섹터의 장세 화면 · 반도체 SMH | `project/Sector-Regime.dc.html` | 섹터의 장세 화면 |
| 섹터의 주가 위치 화면 · 반도체 SMH | `project/Sector-Price.dc.html` | 섹터의 주가 위치 화면 |
| 시장 분위기 · 키가 없을 때 / 한 번도 받지 못했을 때 / 받는 중 | `project/Market-NoKey.dc.html`, `project/Market-Failed.dc.html`, `project/Market-Loading.dc.html` | 판정이 아닐 때(바뀌지 않음) |
| 시장 분위기 · 중립인 날 / 물장인 날 | `project/Market-Neutral.dc.html`, `project/Market-Bear.dc.html` | 장세 카드의 중립·물장 색만 |
| 시장 분위기 · 금리 값의 날짜가 다르고 1년 전과 같은 날 | `project/Market-RateDate.dc.html` | 금리 부담 카드의 날짜가 붙은 모습만 |
| 시장 분위기 · 매매 판단의 숫자와 단계가 다른 날 | `project/Market-Buffer.dc.html` | 금리 부담 카드의 안내 줄만 |

- 마지막 넷은 지난 버전의 모습이라, 그 시안의 나머지(첫 화면에 묶음 카드와 근거 숫자가 있는 배치, "지금 장은")는 따르지 않는다. 섹터의 화면은 전체 시장의 화면과 대상만 다르다. 금리 부담 화면과 섹터 화면의 드문 모습(숫자와 단계가 다른 날, 낼 수 없는 기간, 장세와 종합 점수가 다른 날)은 주가 위치 화면과 첫 화면의 시안의 그 카드를 따른다.
- 미리보기: 위 시안의 화면마다 미리보기를 둔다(그런 날이 아닐 때 수동 확인을 미리보기로 하기 위함이다). 판정이 보이는 있던 미리보기는 새 상태로 맞춘다.

**확인 기준**
- 컴파일 오류 없음, 있던 테스트 통과

## 3. 수동 확인

- R-05 (표시 (제목과 카드의 순서)): Given 실제 키 둘이 저장되어 판정이 보임 / When 첫 화면을 위에서 아래로 밀어 본다 / Then 전체 장세 카드, "지난 2년과 비교하면"과 그 아래 한 줄, 주가 위치 카드, 금리 부담 카드, "섹터별"과 그 아래 한 줄, 섹터 목록, 맨 아래 안내 순서이고, 묶음 카드는 첫 화면에 없다
- R-06 (표시): Given 판정이 보임 / When 전체 장세 카드를 본다 / Then 맨 위 "전체 장세 · 최근 3개월"과 기준 날짜, 큰 단계 이름, 구분선, "종합 점수 … · …", 눈금, 아래에 "근거와 다른 기간 ›" 줄이 있다. 섹터 화면의 장세 카드는 "반도체 장세 · 최근 3개월", 장세 화면의 메인 카드에는 링크 줄이 없다
- R-07: Given 불장, 중립, 약세, 물장이 보이는 카드와 섹터 줄(그날 없는 단계는 미리보기) / When 단계 이름의 색을 본다 / Then 불장 빨강, 중립 회색, 약세 연한 파랑, 물장 파랑이다. 첫 화면, 섹터 목록, 다른 기간 카드 모두 같다
- R-08 (표시 (링크 위에 둠)): Given 장세와 종합 점수가 다른 날(아니면 미리보기) / When 첫 화면의 장세 카드와 장세 화면의 다른 기간 카드를 본다 / Then 눈금 아래 회색 상자의 안내 줄이 링크 줄 위에 있고, 시안 "첫 화면 · 장세와 종합 점수가 다른 날, 주가 위치의 숫자와 단계가 다른 날", "전체 장세 화면 · 메인과 다른 기간 카드의 점수와 장세가 다른 날"과 모양이 같다
- R-09 (표시): Given 장세 화면을 연다 / Then 주식 카드에 "전체 시장 SPY · 비중 70%"(섹터의 장세 화면은 "반도체 SMH · 비중 70%")와 묶음 점수, 채권 신호 카드에 "비중 30%"가 있다
- R-10 (표시): Given 장세 화면 / Then 주식 카드에 "평균 대비"("50일 평균보다 …"), "고점 대비"("최근 3개월 고점보다 …"), "평균의 기울기"("50일 평균이 20일 전보다 …") 세 줄과 점수가 이 순서로 있다
- R-14: Given 첫 화면, 장세 화면, 주가 위치 화면, 금리 부담 화면, 섹터 화면 / When 맨 아래까지 민다 / Then 다섯 화면 모두 참고 안내, "데이터: Twelve Data, FRED", FRED 고지, "FRED API 이용 약관"이 있다
- R-18 (표시, 상세 화면에는 없음): Given 판정이 보이는 채 비행기 모드 / When 첫 화면에서 새로 고침 뒤 섹터 화면과 상세 화면을 연다 / Then 첫 화면에는 안내 띠가 있고, 상세 화면과 섹터 화면에는 안내 띠, 새로 고침, 메뉴가 없다
- R-19: Given 판정이 보이는 여러 상태 / When 첫 화면을 연다 / Then 시안 "시장 분위기 · 둘째 묶음: 첫 화면 (전체 장세, 섹터별)", "첫 화면 · 장세와 종합 점수가 다른 날, 주가 위치의 숫자와 단계가 다른 날", "첫 화면 · 처음 받는 중 (섹터 일부를 아직 받는 중)", "첫 화면 · 섹터 일부를 받지 못했을 때", "시장 분위기 · 키가 없을 때", "한 번도 받지 못했을 때", "받는 중"과 나란히 놓고 볼 때 배치, 간격, 크기, 색이 같다. 장세 카드의 중립·물장 색, 금리 부담 카드의 날짜와 안내 줄은 "중립인 날", "물장인 날", "금리 값의 날짜가 다르고 1년 전과 같은 날", "매매 판단의 숫자와 단계가 다른 날"의 그 카드와 같다. 그런 날이 아닌 화면은 미리보기로 본다
- R-20 (첫 화면이 보이게 될 때 알림, 상세 화면은 알리지 않음): Given 받아 둔 값을 지운 뒤(앱 데이터 지우기 후 키 저장) / When 다른 앱에 갔다가 첫 화면으로 돌아온다 / Then 받기가 시작된다. 장세 화면을 연 채 다른 앱에 갔다 돌아와도 상세 화면에서는 받기가 시작되지 않는다
- R-20 (앱을 바꾼 뒤 섹터를 바로 받음): Given 이번 버전 전의 앱으로 판정을 본 기기 / When 이번 버전을 깔고 첫 화면을 연다 / Then 판정은 바로 보이고, 섹터 줄은 "받는 중"에서 1분쯤 뒤 값으로 바뀐다(6시간이 지나지 않았어도). QQQ, SMH, XLK도 값이 나온다
- R-23 (화면이 멈칫하지 않음): Given 실제 키 둘 / When 앱을 열 때, 새로 고침 뒤, 묶음이 들어올 때 / Then 화면이 멈추거나 끊기지 않고 위아래로 밀 수 있으며, 판정이 몇 초 안에 나온다(느리면 BUG로 알린다)
- R-41 (실제 요청): Given 실제 키 둘 / When 새로 고침으로 한 번 받는다 / Then 섹터 13개까지 모두 값이 나오고 안내 띠가 없다
- R-45 (실제 연결 끊김): Given 판정이 보이는 채 비행기 모드 / When 새로 고침 / Then 안내 띠 "새 값을 받지 못했습니다", "연결을 확인해 주세요. 받아 둔 값으로 보여 줍니다."
- R-46 (실제 연결 끊김): Given 받아 둔 값을 지운 뒤 비행기 모드 / When 앱을 연다 / Then 받지 못함 카드에 "연결을 확인하고 다시 해 주세요."
- R-48: Given 판정과 섹터 목록을 본 뒤 비행기 모드 / When 앱을 껐다 켠다 / Then 판정과 섹터 목록이 받아 둔 값으로 바로 보인다
- R-55 (받는 중에 앱이 꺼짐): Given 받아 둔 값을 지운 뒤 / When 첫 묶음을 받아 판정이 보인 뒤 둘째 묶음을 기다리는 동안(1분 안) 앱을 강제 종료하고 다시 켠다 / Then 판정이 보이고, 받지 못한 섹터는 "받지 못함"이거나 받기가 다시 시작되어 "받는 중"이다. 깨진 값이나 멈춤이 없다
- R-59 (화면에서 받는 것이 전과 같음): Given 실제 키 둘 / When 앱을 열 때의 받기, 새로 고침, 비행기 모드에서의 새로 고침 / Then 받는 동안 진행 표시가 돌고(1분 남짓), 끝나면 값이 바뀌거나 안내 띠가 전처럼 보인다
- R-63 (표시): Given 판정이 보임 / When 첫 화면의 주가 위치 카드를 본다 / Then "주가 위치"와 그 아래 "전체 시장 SPY", "14 · 싼 편" 모양, 눈금, "근거와 다른 기간 ›"이 있고 근거 숫자는 없다. 주가 위치 화면의 메인 카드에는 근거 두 줄이 있고 링크가 없다
- R-64 (표시): Given 판정이 보임 / When 첫 화면의 금리 부담 카드를 본다 / Then "금리 부담"과 "실질금리 …%", 점수와 단계, 눈금, 링크가 있고 근거 숫자는 없다. 금리 부담 화면의 메인 카드에는 근거 세 줄이 있다
- R-66 (색): Given 시안의 화면들(아니면 미리보기) / When 매매 판단의 점수와 단계, 섹터 줄의 주가 위치, 다른 기간 카드를 본다 / Then 싼 편·낮음 청록, 보통 회색, 비싼 편·높음 주황이다
- R-67 (표시 (링크 위에 둠)): Given 숫자와 단계가 다른 날(아니면 미리보기) / When 첫 화면의 주가 위치 카드와 주가 위치 화면의 다른 기간 카드를 본다 / Then 안내 줄이 눈금 아래, 링크 위에 있고 시안 "첫 화면 · … 주가 위치의 숫자와 단계가 다른 날", "주가 위치 화면 · 숫자와 단계가 다른 날 …"과 모양이 같다
- R-70 (표시 (줄이 두 줄로 넘어감)): Given 금리 값의 날짜가 다른 날(아니면 미리보기) / When 금리 부담 화면의 메인 카드를 본다 / Then 날짜가 붙은 값과 두 줄로 넘어가는 10년물·기준금리 줄이 시안 "금리 값의 날짜가 다르고 1년 전과 같은 날"의 금리 부담 카드와 같다
- R-82 (표시): Given 판정이 보임 / When 섹터 목록을 본다 / Then 머리 "섹터", "장세 3개월"(두 줄), "주가 위치 2년"(두 줄), 그 아래 나스닥 100, 반도체, 기술, 금융, 에너지, 헬스케어, 임의소비재, 필수소비재, 산업재, 소재, 유틸리티, 부동산, 커뮤니케이션 순서로 이름과 ETF 기호, 장세, 주가 위치, "›"가 있고 시안 "둘째 묶음: 첫 화면"과 같다
- R-83 (표시): Given 어떤 섹터의 기준 날짜가 전체 시장과 다른 날(아니면 미리보기) / Then 그 줄의 이름 아래 "11월 21일 종가 기준" 모양이 시안 "첫 화면 · 섹터 일부를 받지 못했을 때"와 같다
- R-84 (표시): Given 앱을 바꾼 뒤 처음 열 때(받는 중)와 미리보기(받지 못함) / Then 그 줄에 "받는 중" 또는 "받지 못함"만 회색으로 있고 "›"가 없으며, 시안 "첫 화면 · 처음 받는 중", "섹터 일부를 받지 못했을 때"와 같다
- R-85: Given 장세 화면, 주가 위치 화면, 금리 부담 화면 / Then 위쪽 줄에 뒤로 가기와 화면 이름(대상이 있으면 그 옆에 작게)만 있고, 새로 고침과 메뉴가 없으며, 본문을 밀어도 위쪽 줄은 제자리다
- R-86 (표시): Given 상세 화면 셋 / Then 메인 카드, 근거, "다른 기간으로 보면"과 한 줄, 다른 기간 카드(짧은 기간부터), 맨 아래 안내 순서다
- R-87 (표시): Given 첫 화면의 전체 장세 카드의 링크와 반도체 섹터 화면의 장세 카드의 링크 / Then 시안 "전체 장세 화면 (근거와 다른 기간)", "섹터의 장세 화면 · 반도체 SMH"와 같다
- R-88 (표시): Given 장세 화면 / Then 6개월, 1년 카드마다 이름, "최근 6개월 흐름 · 종합 점수 …", 단계 이름, 눈금, 근거 두 줄이 있다
- R-89 (표시): Given 첫 화면과 반도체 섹터 화면의 주가 위치 카드의 링크 / Then 시안 "주가 위치 화면 (근거와 다른 기간)", "섹터의 주가 위치 화면 · 반도체 SMH"와 같다
- R-90 (표시): Given 첫 화면의 금리 부담 카드의 링크 / Then 시안 "금리 부담 화면 (근거와 다른 기간)"과 같다
- R-91 (표시): Given 주가 위치 화면과 금리 부담 화면 / Then 1년, 3년, 5년 카드마다 이름, "지난 1년과 비교" 모양, 점수와 단계, 눈금이 있고 근거가 없다
- R-92 (표시): Given 미리보기 "주가 위치 화면 · … 아직 낼 수 없는 기간 (XLC)" / Then "아직 없음" 카드가 시안과 같다(지금 값으로는 나오지 않는다)
- R-93 (표시): Given 첫 화면의 반도체 줄 / When 누른다 / Then 시안 "섹터 화면 · 반도체 SMH"와 같고 금리 부담 카드가 없다
- R-94: Given 상세 화면 셋과 섹터 화면(드문 모습은 미리보기) / Then R-94에 적힌 시안의 화면마다 나란히 놓고 볼 때 배치, 간격, 크기, 색이 같다
- R-95: Given 첫 화면 / When 세 카드의 "근거와 다른 기간 ›"을 차례로 누른다 / Then 전체 장세 화면, 주가 위치 화면(전체 시장 SPY), 금리 부담 화면이 열린다
- R-96 (이동, 누를 수 없는 줄): Given 첫 화면 / When 섹터 줄을 누른다 / Then 그 섹터 화면이 열린다. "받는 중" 줄은 눌러도 반응이 없다
- R-97: Given 반도체 섹터 화면 / When 두 링크를 차례로 누른다 / Then 반도체 장세 화면, 반도체 주가 위치 화면이 열린다
- R-98: Given 첫 화면 → 반도체 섹터 화면 → 반도체 장세 화면 / When 위쪽 줄의 뒤로 가기, 기기의 뒤로 가기를 누른다 / Then 섹터 화면, 첫 화면 순서로 돌아간다. 뒤로 가기를 빠르게 두 번 눌러도 첫 화면을 지나 앱이 닫히지 않는다
- R-99 (실제로 돌아감): Given 반도체 장세 화면을 연 채 / When 설정에 가서 키를 지우고 돌아온다 / Then 첫 화면의 키 안내 카드가 보인다. 상세 화면을 연 채 위젯이 받기를 끝내면(또는 첫 화면에서 새로 고침 뒤 상세 화면으로 감) 상세 화면의 값이 새 값으로 바뀐다
- R-103 (실제로 한도를 넘지 않음): Given 실제 키 둘 / When 새로 고침을 눌러 받기가 끝나자마자 다시 누르고, 앱을 껐다 켜서 바로 다시 누른다 / Then "무료 한도를 넘었습니다" 안내가 나오지 않는다(두 번째 받기는 첫 묶음을 보내기 전에 기다려 더 오래 걸린다)
- R-106 (표시): Given 받아 둔 값을 지운 뒤 / When 앱을 연다 / Then 받는 중 카드 뒤 판정이 먼저 보이고(섹터 줄은 "받는 중"), 1분쯤 뒤 섹터 줄이 채워진다
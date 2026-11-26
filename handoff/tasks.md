# Tasks: 시장 분위기 버전업-002

기능: 시장 분위기
변경: 버전업-002 (CS-004)
기준 Plan: 2.0.0
상태: 작성 중

| 전달 | 날짜 | 이유 |
|---|---|---|

## 1. 개요
시장 분위기는 여러 항목으로 지금 장세(불장, 중립, 약세, 물장)를 판정해 근거와 함께 보여 주는 첫 화면이다. 쓰는 사람이 끝내 알고 싶은 것은 지금이 사기 좋은 때인지 팔기 좋은 때인지인데 장세는 그 답이 아니라서, 이번에 매매 판단을 더한다. 매매 판단은 지금 장이 평소(지난 5년)와 비교해 어디쯤인지를 0~100 점수로 날마다 읽는 것이고, 꼭지나 바닥을 맞히는 신호가 아니다. 매달 꾸준히 사는 장기 투자자가 지금이 평소에 비해 비싼 편인지를 보는 데 쓴다. 주가가 얼마나 올라와 있는지(주가 위치, 전체 시장 SPY)와 금리가 얼마나 높은지(금리 부담, 실질금리)를 둘로 나눠 보인다.
이번에 할 일: 매매 판단의 규칙과 계산(따로 둔 계산기 ValuationCalculator), 받는 시리즈와 받는 기간 늘리기(SPY 9년, 실질금리 6년, 10년물 국채 금리·기준금리·소비자물가 2년), 계산을 화면 스레드 밖으로 옮기기, 화면의 매매 판단 두 카드와 두 제목. 매매 판단의 값까지 모두 있어야 판정이 난다.
장세의 공식과 점수, 받는 때(6시간, 새로 고침, 청), 실패의 처리, 저장의 모양(DB 표)은 바꾸지 않는다. 홈 화면 위젯과 설정의 코드(`feature/homewidget`, `feature/settings`)는 고치지 않는다. 위젯의 테스트만 테스트 데이터와 객체 만들기를 맞춘다(단위 1, 3). MarketDataRefresher, MarketResponseMapper, 두 Api, DB, RefreshState 쪽은 고칠 것이 없다.

## 2. 구현 단위

### 단위 1: 매매 판단의 규칙과 모델, 테스트 데이터

**만들거나 고칠 것**
- 수정 IndicatorSeries, SeriesKind (feature/marketmood/domain)
- 신규 ValuationRules (feature/marketmood/domain)
- 수정 MoodRules (feature/marketmood/domain)
- 수정 DefaultMoodRules (feature/marketmood/domain)
- 신규 ValuationStage (feature/marketmood/domain)
- 신규 ValuationAssessment, PricePositionAssessment, RateBurdenAssessment, DatedValue (feature/marketmood/domain)
- 신규 SeriesRequest (feature/marketmood/domain)
- 수정 MoodTestData, MarketMoodFakes의 FakeMoodRulesRepository (테스트, feature/marketmood/domain)

**지시**
- 새 용어의 영어 이름: 매매 판단 `Valuation`, 주가 위치 `PricePosition`, 금리 부담 `RateBurden`, 매매 판단의 단계 `ValuationStage`, 점수로 정한 단계 `scoredStage`, 위치 `position`, 위치 기간 `positionWindow`, 짧은 괴리 `shortDeviation`, 긴 괴리 `longDeviation`, 짧은 평균 일수 `shortAverageDays`, 긴 평균 일수 `longAverageDays`, 짧은 쪽 비중 `shortWeight`, 긴 쪽 비중 `longWeight`, 평균 일수 `smoothingDays`, 낮은 경계 `lowBoundary`, 높은 경계 `highBoundary`, 완충 `buffer`, 3개월 수익률 `threeMonthReturn`, 3개월로 보는 날 수 `threeMonthDays`, 낙폭 `drawdown`, 실질금리 `realYield`, 실질금리의 날 `realYieldDate`, 1년 전과의 차이 `yearChange`, 1년 전으로 보는 날 수 `yearAgoDays`, 10년물 국채 금리 `treasury10y`, 기준금리 `policyRate`, 소비자물가 `consumerPrice`, 물가의 1년 상승률 `inflationRate`, 물가 목표 `inflationTarget`, 받는 기간 `fetchYears`. 처음 선언하는 곳 위에 `// 용어: (한국어 이름)` 주석을 단다. 표시 단계 `displayedStage`, 기준 날짜 `asOfDate`는 있는 이름을 쓴다.
- SeriesKind에 `MONTHLY_VALUE`(달마다의 값)를 더한다. 달마다의 값은 그 달 1일의 날짜로 담는다(FRED가 주는 날짜 그대로). 장이 끝나지 않은 날 거르기는 지금처럼 DAILY_CLOSE만이다. (P-35)
- ValuationRules는 매매 판단의 규칙이다. 아래 필드를 갖는 데이터 클래스다. (P-28, P-29)
  - `priceSeries: IndicatorSeries`: 주가 위치의 대상 시리즈
  - `shortAverageDays: Int`, `longAverageDays: Int`, `shortWeight: Double`, `longWeight: Double`
  - `positionWindow: Int`, `smoothingDays: Int`
  - `lowBoundary: Int`, `highBoundary: Int`, `buffer: Int`
  - `threeMonthDays: Int`
  - `priceFetchYears: Int`: 대상 시리즈의 받는 기간(해)
  - `realYieldSeries: IndicatorSeries`, `realYieldFetchYears: Int`
  - `treasury10ySeries: IndicatorSeries`, `policyRateSeries: IndicatorSeries`, `consumerPriceSeries: IndicatorSeries`
  - `yearAgoDays: Int`: 달력의 날 수
  - `inflationTarget: Double`: 비율(2%는 0.02)
- MoodRules에 `valuation: ValuationRules`와 `fetchYears: Int`(장세의 항목과 그 밖의 매매 판단 시리즈의 받는 기간)를 더한다. 그리고 `fun fetchYearsBySeries(): Map<IndicatorSeries, Int>`를 둔다: 장세의 항목이 쓰는 시리즈는 `fetchYears`, 매매 판단의 대상 시리즈는 `priceFetchYears`, 실질금리는 `realYieldFetchYears`, 10년물·기준금리·소비자물가는 `fetchYears`. 시리즈 ID가 같은 시리즈가 여럿이면 하나만 두고 그중 긴 기간을 쓴다. 순서는 묶음과 항목의 순서, 그 뒤에 대상 → 실질금리 → 10년물 → 기준금리 → 소비자물가 순이다(이미 나온 ID는 그 자리에 둔다). (R-41, P-29)
- DefaultMoodRules에 값을 더한다. 장세의 값은 바꾸지 않는다. (R-41, R-71, P-28, P-29)
  - `fetchYears` 2
  - 매매 판단: 대상 `IndicatorSeries(TWELVE_DATA, "SPY", DAILY_CLOSE)`, 짧은 평균 200, 긴 평균 756, 비중 2.0 : 1.0, 위치 기간 1260, 평균 일수 20, 낮은 경계 30, 높은 경계 70, 완충 5, 3개월 63, 대상의 받는 기간 9
  - 실질금리 `IndicatorSeries(FRED, "DFII10", DAILY_VALUE)`, 받는 기간 6. 10년물 `IndicatorSeries(FRED, "DGS10", DAILY_VALUE)`, 기준금리 `IndicatorSeries(FRED, "DFF", DAILY_VALUE)`, 소비자물가 `IndicatorSeries(FRED, "CPIAUCSL", MONTHLY_VALUE)`, 1년 전 365일, 물가 목표 0.02
- ValuationStage는 매매 판단의 단계 열거형이다: `LOW`(싼 편, 낮음) / `MIDDLE`(보통) / `HIGH`(비싼 편, 높음). 주가 위치와 금리 부담이 함께 쓴다. 이름(싼 편/낮음)은 화면이 카드에 따라 고른다. (P-34)
- 매매 판단의 모델. 다른 기능(홈 화면 위젯)도 받는 모델이다. (R-81, P-23, P-27)
  - `ValuationAssessment(pricePosition: PricePositionAssessment, rateBurden: RateBurdenAssessment, lowBoundary: Int, highBoundary: Int, buffer: Int, inflationTarget: Double)`. 뒤의 넷은 규칙의 값을 그대로 옮긴 것이다(화면이 안내 줄의 숫자와 물가 목표를 낸다).
  - `PricePositionAssessment(score: Int, scoredStage: ValuationStage, displayedStage: ValuationStage, shortDeviation: Double, longDeviation: Double, drawdown: Double, threeMonthReturn: Double)`. 괴리, 낙폭, 수익률은 비율이다(+4.1%는 0.041). 모두 기준 날짜의 것이다.
  - `RateBurdenAssessment(realYieldDate: LocalDate, score: Int, scoredStage: ValuationStage, displayedStage: ValuationStage, realYield: Double, yearChange: Double, treasury10y: DatedValue, policyRate: DatedValue, inflationRate: Double, inflationMonth: YearMonth)`. 금리 값은 받은 값 그대로(퍼센트 단위, 2.79%는 2.79)이고, `inflationRate`는 비율이다(3.7%는 0.037).
  - `DatedValue(value: Double, date: LocalDate)`
- SeriesRequest는 받을 시리즈 하나와 그 시작 날짜다: `SeriesRequest(series: IndicatorSeries, startDate: LocalDate)`. 단위 2에서 쓴다. (P-30)
- 테스트 데이터를 맞춘다. 단위 3에서 판정에 매매 판단의 값까지 들게 되므로 먼저 준비해 둔다. 있던 테스트를 지우거나 줄이지 않는다.
  - MoodTestData의 데이터 A에 시리즈 넷을 더한다: `DFII10` 평일마다 2.00, `DGS10` 평일마다 4.00, `DFF` 평일마다 3.50 (모두 데이터 A의 주식과 같은 날들), `CPIAUCSL` 2024-11-01부터 2025-10-01까지 매달 1일 300.0, 2025-11-01부터 2026-10-01까지 매달 1일 309.0. 데이터 B는 데이터 A에서 만들므로 저절로 들어간다.
  - MoodTestData에 시험용 규칙 `RULES`를 둔다: 기본 규칙에서 매매 판단의 짧은 평균 5, 긴 평균 10, 위치 기간 20, 평균 일수 3, 3개월 5로 줄인 것. 나머지(장세의 값, 경계, 완충, 받는 기간, 시리즈)는 기본 규칙 그대로다. 데이터 A(2년)로 매매 판단을 낼 수 있게 하려는 것이다.
  - FakeMoodRulesRepository의 처음 규칙을 `MoodTestData.RULES`로 바꾼다. 있던 테스트에서 `DefaultMoodRules.rules`를 판정 계산에 넘기거나 고쳐 쓰는 곳(예: `DefaultMoodRules.rules.copy(upperBoundary = 0.6)`)은 `MoodTestData.RULES`로 바꾼다. 기본 규칙의 값 자체를 확인하는 테스트(R-30 등)는 그대로 둔다.
  - 시험용 규칙에서 데이터 A의 매매 판단은 이렇게 된다(단위 3의 테스트가 쓴다): 주가 위치 50 · MIDDLE(점수로 정한 단계도 MIDDLE), 두 괴리 0, 낙폭 0, 3개월 수익률 0. 금리 부담 50 · MIDDLE, 실질금리의 날 2026-11-23, 실질금리 2.00, 1년 전과의 차이 0, 10년물 4.00(2026-11-23), 기준금리 3.50(2026-11-23), 물가 상승률 309 ÷ 300 - 1 = 0.03, 그 달 2026-10. 값이 모두 같으면 위치가 모두 0.5라서다.

**테스트 지시**
- R-71: Given 기본 규칙 / When `valuation`을 본다 / Then 대상 SPY(TWELVE_DATA, DAILY_CLOSE), 200, 756, 2.0, 1.0, 1260, 20, 30, 70, 5, 63, 대상의 받는 기간 9, 실질금리 DFII10(FRED, DAILY_VALUE) 6, 10년물 DGS10(FRED, DAILY_VALUE), 기준금리 DFF(FRED, DAILY_VALUE), 소비자물가 CPIAUCSL(FRED, MONTHLY_VALUE), 1년 전 365, 물가 목표 0.02
- R-41 (시리즈마다의 받는 기간)
  - Given 기본 규칙 / When `fetchYearsBySeries()` / Then 10개다: SPY 9, QQQ 2, XLK 2, SMH 2, T10Y2Y 2, BAMLH0A0HYM2 2, DFII10 6, DGS10 2, DFF 2, CPIAUCSL 2. SPY는 한 번만 나온다
  - Given 기본 규칙에서 대상의 받는 기간만 1로 바꾼 규칙 / When `fetchYearsBySeries()` / Then SPY는 2다(장세 항목의 2가 더 길다)
- R-30: 있던 테스트가 그대로 통과한다(장세의 묶음과 항목은 바뀌지 않는다)

**확인 기준**
- 테스트 통과, 컴파일 오류 없음

### 단위 2: 받는 시리즈와 받는 기간

**만들거나 고칠 것**
- 수정 MarketDataRepository (feature/marketmood/domain)
- 수정 RefreshMarketDataUseCase (feature/marketmood/domain)
- 수정 ObserveSourceKeysUseCase (feature/marketmood/domain)
- 수정 MarketDataRepositoryImpl (feature/marketmood/data)
- 수정 FakeMarketDataRepository (테스트, feature/marketmood/domain)

**지시**
- MarketDataRepository의 받기를 `suspend fun fetch(source: DataSource, requests: List<SeriesRequest>): FetchResult`로 바꾼다. 시리즈마다 시작 날짜를 받는다. 요청을 어떻게 나누는지는 Data 층이 정한다. 출처 안의 요청 가운데 하나라도 받지 못하거나 읽지 못하면 그 출처 전체가 Failure이고, 앞 요청의 오류가 먼저다. 나머지 함수는 그대로다. (P-30, P-09)
- RefreshMarketDataUseCase는 받는 시리즈를 `rules.fetchYearsBySeries()`에서 모은다. 시리즈마다 시작 날짜는 받는 때의 미국 동부 시간 날짜에서 그 시리즈의 받는 기간(해)을 뺀 날이다(지금의 2년과 같은 방식). 출처마다 `fetch`를 한 번 부르는 것, 출처끼리 동시에 받기, 출처마다 10초 제한, 장이 끝나지 않은 날 거르기(DAILY_CLOSE만. MONTHLY_VALUE와 DAILY_VALUE는 거르지 않는다), 저장, 성공한 때를 적는 조건, 결과의 우선순위는 그대로다. 받기를 시작한 때를 요청 전에 적는 것도 그대로다. (R-41, R-37, R-72, P-29, P-30, P-35)
- ObserveSourceKeysUseCase는 출처를 `rules.fetchYearsBySeries()`의 시리즈에서 모은다(매매 판단의 시리즈의 출처도 들어간다). 출처의 순서는 지금처럼 DataSource의 선언 순서다. 기본 규칙에서는 두 출처 그대로라 결과가 같다. (R-15, R-04)
- MarketDataRepositoryImpl (R-41, P-08, P-30)
  - Twelve Data: 시작 날짜가 같은 종목끼리 요청 하나로 묶는다(`symbol`은 쉼표로 이은 티커, `start_date`는 그 시작 날짜). 기본 규칙에서는 SPY(9년 전) 한 요청과 QQQ·XLK·SMH(2년 전) 한 요청, 둘이다. 요청끼리의 순서는 처음 나온 순서다. 요청마다 MarketResponseMapper로 읽는다(종목이 하나일 때와 여럿일 때의 두 모양을 이미 읽는다). 요청 가운데 하나라도 실패하면 그 출처 전체가 실패이고 받은 값을 하나도 돌려주지 않는다. 오류는 앞 요청의 것이 먼저다.
  - FRED: 지금처럼 시리즈마다 요청 하나이고, `observation_start`에 그 시리즈의 시작 날짜를 쓴다.
  - 요청 목록이 비면 지금처럼 빈 Success다.
- FakeMarketDataRepository의 `fetch`와 기록(FetchCall)을 새 모양에 맞춘다. 가짜가 돌려주는 값은 지금처럼 `remote`에서 요청한 시리즈만 꺼낸 것이다.
- 있던 테스트 `R-41 출처마다 한 번씩 미국 동부 날짜의 2년 전부터 받는다`는 R-41이 바뀌어 맞지 않는다. 아래 테스트 지시의 R-41 경우로 바꾼다.

**테스트 지시**
- R-41 (시리즈마다의 시작 날짜)
  - Given 기본 규칙, 받는 때 2026-11-24T01:00:00Z(미국 동부 2026-11-23) / When 받기 한 번 / Then `fetch`는 출처마다 한 번이다. TWELVE_DATA: SPY 2017-11-23, QQQ·XLK·SMH 2024-11-23. FRED: T10Y2Y·BAMLH0A0HYM2 2024-11-23, DFII10 2020-11-23, DGS10·DFF·CPIAUCSL 2024-11-23
  - Given 위와 같음, 출처가 데이터 A(시리즈 열 개)를 돌려줌 / When 받기 한 번 / Then 저장한 시리즈는 열 개이고, CPIAUCSL의 값 24개가 모두 저장된다
- R-41 (시작 날짜가 같은 종목끼리 한 요청, 하나라도 실패하면 출처 실패) (가짜 Api)
  - Given Twelve Data 요청 SPY 2017-11-23, QQQ 2024-11-23, XLK 2024-11-23, SMH 2024-11-23. 가짜 Api는 SPY 요청에 종목 하나의 응답을, 셋의 요청에 종목 여럿의 응답을 준다 / When `fetch(TWELVE_DATA, 요청 넷)` / Then Api의 `timeSeries`가 두 번 불린다: (`SPY`, `2017-11-23`), (`QQQ,XLK,SMH`, `2024-11-23`). 결과는 Success이고 네 종목의 값이 모두 있다
  - Given 위와 같은데 두 번째 요청의 응답이 HTTP 429 / When `fetch` / Then Failure(RATE_LIMITED)이고 SPY의 값도 돌려주지 않는다
  - Given 첫 번째 요청의 응답이 읽을 수 없는 본문, 두 번째 요청의 응답이 HTTP 429 / When `fetch` / Then Failure(OTHER) (앞 요청의 오류가 먼저)
  - Given FRED 요청 DFII10 2020-11-23, DGS10 2024-11-23 / When `fetch(FRED, 요청 둘)` / Then Api의 `observations`가 (`DFII10`, `2020-11-23`), (`DGS10`, `2024-11-23`)으로 불린다
- R-37 (장이 끝나지 않은 날의 값 거르기), R-72 (같음)
  - Given 받는 때 2026-11-23T20:59:00Z(미국 동부 오후 3시 59분), 출처가 2026-11-23의 값까지 돌려줌 / When 받기 한 번 / Then 저장한 SPY·QQQ·XLK·SMH는 2026-11-20까지이고, DFII10·DGS10·DFF는 2026-11-23까지 있다. CPIAUCSL은 2026-10-01까지 그대로 있다
  - 있던 R-37 테스트(오후 4시 정각)는 그대로 통과한다
- R-15 (규칙이 쓰는 출처): Given 장세의 항목이 모두 TWELVE_DATA 시리즈이고 매매 판단의 실질금리·10년물·기준금리·소비자물가가 FRED인 규칙, 두 출처의 키 AVAILABLE / When ObserveSourceKeysUseCase를 모은다 / Then TWELVE_DATA, FRED 두 출처가 그 순서로 나온다

**확인 기준**
- 테스트 통과, 컴파일 오류 없음

### 단위 3: 매매 판단의 계산

**만들거나 고칠 것**
- 신규 DailySeries (feature/marketmood/domain)
- 신규 ValuationCalculator (feature/marketmood/domain)
- 수정 MoodCalculator (feature/marketmood/domain)
- 수정 MoodAssessment (feature/marketmood/domain)
- 수정 ObserveMoodUseCase (feature/marketmood/domain)
- 신규 DefaultDispatcher (core/di)
- 수정 CoroutineScopeModule (core/di)
- 수정 시장 분위기와 홈 화면 위젯의 테스트에서 MoodCalculator, ObserveMoodUseCase를 만드는 곳 (테스트)

**지시**
- DailySeries는 날짜 오름차순으로 정리한 시리즈 하나다. MoodCalculator 안의 `Series`를 꺼내 옮긴 것이고, 장세와 매매 판단이 함께 쓴다. 같은 날짜가 여럿이면 앞의 것 하나만 둔다. 밖에 내놓는 것: `companion fun of(values: List<DailyValue>): DailySeries`, `dates: List<LocalDate>`, `values: DoubleArray`, `fun floorIndex(day: LocalDate): Int`(그날이나 그 전의 가장 가까운 날의 위치, 없으면 -1), `fun drawdownAt(index: Int): Double`(그날의 종가와 최근 1년 최고 종가로 낸 낙폭. 최근 1년 최고 종가는 그날로부터 1년 전의 같은 날 이후 그날까지의 종가 가운데 가장 높은 값). 장세의 낙폭 계산을 이것으로 바꾸고, 장세의 결과는 바꾸지 않는다. (R-32, R-11, R-68, P-32)
- ValuationCalculator는 매매 판단을 내는 순수 계산기다. `class ValuationCalculator @Inject constructor()`, `fun assess(rules: ValuationRules, values: Map<String, List<DailyValue>>, asOfDate: LocalDate): ValuationAssessment?`. values의 키는 시리즈 ID다. 지금 시각을 쓰지 않는다. 아래를 하나라도 낼 수 없으면 null이다. (R-40, P-27)
- 주가 위치 (R-72, R-73, R-74, R-76, R-77, R-78, R-68, P-33, P-34)
  - 주가 위치의 날은 대상 시리즈(`priceSeries`)의 값이 있는 날이다. 장세의 거래일과 상관없다.
  - 그날의 짧은 괴리 = 그날의 종가 ÷ 그날을 포함한 최근 `shortAverageDays`개 날의 종가의 평균 - 1. 긴 괴리는 `longAverageDays`로 같게 낸다. 각각 그 개수만큼의 종가가 있는 날에만 낸다.
  - 어떤 값의 그날의 위치(0~1) = (그날을 포함한 최근 `positionWindow`개 값 가운데 그날의 값을 뺀 나머지에서, 그날의 값보다 작은 값의 수 + 그날의 값과 같은 값의 수 × 0.5) ÷ (`positionWindow` - 1). `positionWindow`개 값이 모두 있는 날에만 낸다. 기간은 값의 개수로 센다(R-73). 날마다 창의 값을 처음부터 다시 센다(P-31).
  - 그날의 주가 위치 = (짧은 괴리의 위치 × `shortWeight` + 긴 괴리의 위치 × `longWeight`) ÷ (`shortWeight` + `longWeight`). 두 위치가 모두 있는 날에만 낸다.
  - 주가 위치 점수 = 그날을 포함한 최근 `smoothingDays`개 날의 주가 위치의 평균 × 100을 반올림한 정수(0~100). 반올림은 소수 첫째 자리가 5 이상이면 올린다(12.5는 13). 그 개수만큼 주가 위치가 있는 날에만 낸다.
  - 3개월 수익률 = 기준 날짜의 종가 ÷ 그보다 `threeMonthDays`개 앞의 주가 위치의 날의 종가 - 1. 점수와 단계에는 쓰지 않는다. 낼 수 없으면 매매 판단을 낼 수 없는 것으로 본다(기본 규칙에서는 점수가 있는 날이면 늘 낼 수 있다).
  - 낙폭은 대상 시리즈의 DailySeries의 `drawdownAt`이다. 장세의 전체 시장 항목에서 가져오지 않는다(P-32).
  - 기준 날짜에 대상 시리즈의 값이 없거나 기준 날짜의 점수를 낼 수 없으면 null이다.
- 금리 부담 (R-75, R-77, R-78, R-79, P-34)
  - 금리 부담의 날은 실질금리(`realYieldSeries`)의 값이 있는 날이다. 숫자가 아닌 값은 받을 때 이미 빠져 있다.
  - 실질금리의 날 = 기준 날짜나 그 전의 날 가운데 실질금리의 값이 있는 가장 가까운 날.
  - 금리 부담 점수 = 그날을 포함한 최근 `smoothingDays`개 금리 부담의 날의 실질금리의 위치(위의 위치와 같은 식, `positionWindow`) 평균 × 100을 위와 같이 반올림한 정수. 실질금리의 날의 점수를 낼 수 없으면 null이다.
  - 1년 전과의 차이 = 실질금리의 날의 값 - 1년 전 값. 1년 전 값은 기준 날짜에서 `yearAgoDays`일(달력) 앞의 날이나 그 전의 날 가운데 실질금리의 값이 있는 가장 가까운 날의 값이다. 없으면 null이다.
- 점수로 정한 단계와 표시 단계 (R-76, R-77, P-34)
  - 점수로 정한 단계: 점수 ≤ `lowBoundary`면 LOW, 점수 ≥ `highBoundary`면 HIGH, 그 밖이면 MIDDLE. 반올림한 정수 점수로 가른다.
  - 표시 단계는 주가 위치와 금리 부담 각각, 점수를 낼 수 있는 첫날부터 그 날짜(주가 위치는 기준 날짜, 금리 부담은 실질금리의 날)까지 그 날들마다 따진다. 저장해 이어 가지 않고 늘 처음부터 다시 따진다.
    - 첫날: 그날의 점수로 정한 단계.
    - 전날의 표시 단계가 MIDDLE: 그날의 점수로 정한 단계.
    - 전날이 LOW: 점수 ≥ `highBoundary`면 HIGH, 점수 > `lowBoundary` + `buffer`면 MIDDLE, 그 밖이면 LOW.
    - 전날이 HIGH: 점수 ≤ `lowBoundary`면 LOW, 점수 < `highBoundary` - `buffer`면 MIDDLE, 그 밖이면 HIGH.
  - 두 쪽의 규칙이 같으므로 코드를 하나만 둔다.
- 10년물 국채 금리와 기준금리: 각각 기준 날짜나 그 전의 날 가운데 값이 있는 가장 가까운 날의 값과 그 날짜(DatedValue). 없으면 null이다. (R-78)
- 물가의 1년 상승률 (R-80)
  - 소비자물가의 값은 달마다 그 달 1일의 날짜로 들어 있다. 달(YearMonth)로 다룬다.
  - 그 달 = 소비자물가의 값이 있는 달 가운데, 12달 전 달의 값도 있는 가장 최근 달. 상승률 = 그 달의 값 ÷ 12달 전 달의 값 - 1. 그런 달이 없으면 null이다.
  - 기준 날짜보다 뒤의 달은 쓰지 않는다(기준 날짜가 든 달까지 본다).
- ValuationAssessment에는 규칙의 `lowBoundary`, `highBoundary`, `buffer`, `inflationTarget`을 그대로 싣는다.
- MoodAssessment에 `valuation: ValuationAssessment`를 더한다(맨 뒤, null이 아님). 판정이 있으면 매매 판단도 늘 있다. (R-81, P-27)
- MoodCalculator는 `class MoodCalculator @Inject constructor(private val valuationCalculator: ValuationCalculator)`로 바꾼다. `assess(rules: MoodRules, values)`의 모양은 그대로다. 장세를 지금처럼 낸 뒤 기준 날짜를 넘겨 `valuationCalculator.assess(rules.valuation, values, asOfDate)`를 부르고, null이면 판정도 null이다. 매매 판단의 시리즈는 장세의 항목이 아니라 거래일에 들지 않는다(거래일은 지금처럼 장세 항목 가운데 DAILY_CLOSE인 것 모두의 값이 있는 날). 장세의 계산은 바꾸지 않는다. 판정이 나는지를 보는 곳(화면, 위젯, 받기의 성공 판정, 화면이 보이게 될 때의 판정)은 모두 이 MoodCalculator를 보므로 따로 고칠 것이 없다. (R-40, R-37, R-42, R-20, P-27)
- `core/di`에 한정자 `@DefaultDispatcher`를 두고, CoroutineScopeModule에서 `Dispatchers.Default`를 `CoroutineDispatcher`로 제공한다. 있는 `@ApplicationScope`는 그대로다. (P-31)
- ObserveMoodUseCase는 생성자로 `@DefaultDispatcher dispatcher: CoroutineDispatcher`를 더 받아(맨 뒤), 계산하는 Flow를 그 디스패처에서 돌린다. 받는 쪽이 어느 스레드에서 모으든 같다. 내주는 값(판정 또는 null)은 그대로다. (R-23, R-58, R-81, P-14, P-31)
- 테스트 코드에서 `MoodCalculator()`와 `ObserveMoodUseCase(...)`를 만드는 곳을 새 생성자에 맞춘다(시장 분위기와 홈 화면 위젯의 테스트 모두). 테스트에서는 시험용 디스패처를 넘긴다. 위젯의 테스트는 이것과 단위 1의 테스트 데이터만 맞추고, 위젯의 코드는 고치지 않는다.

**테스트 지시**
아래에서 "작은 규칙"은 ValuationCalculator를 확인하려고 숫자를 줄인 규칙이다. 시리즈는 기본 규칙의 것(SPY, DFII10, DGS10, DFF, CPIAUCSL)을 쓰고, 날짜는 평일로 이어지게 둔다. 확인하는 쪽 말고 다른 쪽(주가 위치를 볼 때의 금리 부담 등)은 낼 수 있게 값을 넣어 둔다. 점수로 정한 단계와 표시 단계를 볼 때는 위치 기간 101, 평균 일수 1인 규칙을 쓰면 점수가 "앞 100개 값 가운데 그날의 값보다 작은 값의 수"(같은 값이 없을 때)가 되어 원하는 점수열을 만들기 쉽다. 데이터를 어떻게 만들지는 CLI가 정한다.

- R-72 (두 괴리, 3개월 수익률, 낼 수 있는 날), R-73, R-74
  - Given 작은 규칙(짧은 평균 2, 긴 평균 3, 비중 2 : 1, 위치 기간 3, 평균 일수 1, 3개월 2), SPY 종가 10, 12, 11, 13, 12 (다섯 날), 기준 날짜는 다섯째 날 / When assess / Then 짧은 괴리 -0.04, 긴 괴리 0.0, 주가 위치 점수 42다. (짧은 괴리 셋 -0.0435, +0.0833, -0.04에서 오늘의 위치 0.5, 긴 괴리 셋 0.0, +0.0833, 0.0에서 같은 값 하나라 위치 0.25, (0.5 × 2 + 0.25) ÷ 3 = 0.4167) 3개월 수익률은 12 ÷ 11 - 1 = +0.0909다
  - Given 위 다섯 날에 여섯째 날 종가 15를 더함, 기준 날짜는 여섯째 날 / When assess / Then 짧은 괴리 +0.1111, 긴 괴리 +0.125, 주가 위치 점수 100, 3개월 수익률 15 ÷ 11 - 1 = +0.3636
  - Given 위 여섯 날, 평균 일수만 2로 바꾼 규칙, 기준 날짜는 여섯째 날 / When assess / Then 주가 위치 점수 71 ((0.4167 + 1.0) ÷ 2 = 0.7083)
  - Given 위 다섯 날, 기준 날짜는 넷째 날 / When assess / Then null (넷째 날에는 긴 괴리가 위치 기간 셋만큼 없다)
  - Given 기본 규칙, SPY 종가 2034개(평일, 값은 CLI가 정함), 실질금리 등 나머지는 낼 수 있음, 기준 날짜는 마지막 날 / When assess / Then 매매 판단이 있다. Given SPY 종가 2033개 / Then null (긴 괴리는 756번째 날부터, 그 위치는 1260개가 쌓인 날부터, 점수는 20개가 쌓인 날부터라 2034번째 날이 처음이다)
- R-73 (같은 값): Given 위치 기간 5, 평균 일수 1인 규칙, 실질금리 1, 2, 2, 3, 2 / When assess / Then 금리 부담 점수 50 (나머지 1, 2, 2, 3 가운데 작은 값 1, 같은 값 2 → (1 + 1) ÷ 4 = 0.5)
- R-75 (점수, 반올림)
  - Given 위치 기간 5, 평균 일수 2인 규칙, 실질금리 5, 4, 3, 2, 1, 1.5, 기준 날짜는 여섯째 날 / When assess / Then 금리 부담 점수 13 (위치 0과 0.25의 평균 0.125 × 100 = 12.5를 올림)
  - Given 위치 기간 5, 평균 일수 1인 규칙, 실질금리 값이 넷뿐 / When assess / Then null
  - Given 기본 규칙, 실질금리 값 1279개 / When assess / Then 금리 부담이 있다. 1278개면 null
- R-78
  - Given 실질금리가 기준 날짜(2026-11-23)에 없고 2026-11-20까지 있음 / When assess / Then `realYieldDate`는 2026-11-20이고 점수와 실질금리 값은 그날의 것이다
  - Given 10년물이 2026-11-20까지, 기준금리가 2026-11-23까지 있음, 기준 날짜 2026-11-23 / When assess / Then `treasury10y`는 2026-11-20의 값과 날짜, `policyRate`는 2026-11-23의 값과 날짜다
  - Given 10년물의 값이 모두 기준 날짜보다 뒤 / When assess / Then null. 기준금리도 같다
  - Given 주가 위치 / When assess / Then 점수, 두 괴리, 낙폭, 3개월 수익률은 기준 날짜의 것이다(기준 날짜 뒤에 SPY 종가가 더 있어도)
- R-79
  - Given 기준 날짜 2026-11-23, 실질금리 2025-11-21에 1.82, 2025-11-24에 1.90(2025-11-22, 23은 주말), 2026-11-23에 2.79 / When assess / Then 1년 전과의 차이 2.79 - 1.82 = +0.97 (365일 앞은 2025-11-23이라 그 전의 가장 가까운 날 2025-11-21)
  - Given 실질금리가 기준 날짜의 365일 앞보다 뒤부터만 있음 / When assess / Then null
- R-80
  - Given 소비자물가 2025-09 300.0, 2025-10 없음, 2025-11 302.0, …, 2026-09 311.1, 2026-10 312.0 (2025-09 전은 없음) / When assess / Then 상승률 311.1 ÷ 300.0 - 1 = 0.037, 그 달 2026-09 (2026-10은 12달 전인 2025-10이 없다)
  - Given 소비자물가가 2025-11부터 2026-10까지 12달뿐 / When assess / Then null (12달 전 값이 있는 달이 없다)
  - Given 소비자물가 2025-10 300.0, 2026-10 312.0, 2026-11 313.0, 기준 날짜 2026-10-30 / When assess / Then 그 달 2026-10 (기준 날짜 뒤의 달은 쓰지 않는다)
- R-76: Given 위치 기간 101, 평균 일수 1, 경계 30/70인 규칙 / When 금리 부담 점수가 30, 31, 69, 70인 날을 각각 첫날로 assess / Then 점수로 정한 단계와 표시 단계는 LOW, MIDDLE, MIDDLE, HIGH
- R-77 (위와 같은 규칙, 완충 5. 점수열은 첫날부터 기준 날짜까지 날마다의 점수, Then은 기준 날짜의 표시 단계와 점수로 정한 단계)
  - 점수열 30, 35 → 표시 LOW, 점수로 정한 단계 MIDDLE
  - 점수열 30, 36 → 표시 MIDDLE
  - 점수열 30, 70 → 표시 HIGH (보통을 거치지 않음)
  - 점수열 70, 65 → 표시 HIGH, 점수로 정한 단계 MIDDLE
  - 점수열 70, 64 → 표시 MIDDLE
  - 점수열 70, 30 → 표시 LOW
  - 점수열 30, 35, 33, 36, 34 → 표시 MIDDLE (36에서 보통으로 바뀐 뒤 34는 경계 30보다 크다)
  - 점수열 50, 33 → 표시 MIDDLE (보통에서는 바로 점수로 정한 단계)
  - 위 경우를 주가 위치에서도 하나 이상 확인한다(점수열 30, 35 → LOW). 금리 부담의 표시 단계는 실질금리의 날까지만 따진다: Given 실질금리 점수열 30, 35이고 기준 날짜에는 실질금리 값이 없음(그 전날까지) / Then 표시 LOW
- R-68 (네 값의 계산), R-11, R-32
  - Given SPY 종가가 2025-11-24에 200, 그 뒤 기준 날짜 2026-11-23까지 190 이하, 기준 날짜 184 / When assess / Then 낙폭 184 ÷ 200 - 1 = -0.08
  - Given 2025-11-21에 250(1년 전 같은 날보다 앞), 2025-11-24부터는 위와 같음 / Then 낙폭은 그대로 -0.08
  - 장세의 있던 R-11, R-32 테스트가 그대로 통과한다
- R-40 (매매 판단의 값)
  - Given 데이터 A, 시험용 규칙 / When ValuationCalculator.assess(기준 날짜 2026-11-23) / Then 단위 1에 적은 값과 같다(주가 위치 50 · MIDDLE, 금리 부담 50 · MIDDLE, 차이 0, 10년물 4.00, 기준금리 3.50, 물가 0.03 · 2026-10), 경계 30, 70, 완충 5, 물가 목표 0.02가 실린다
  - Given 데이터 A에서 DFII10을 뺌 / Then null. DGS10, DFF, CPIAUCSL을 하나씩 뺀 것도 각각 null. SPY를 뺀 것도 null
- R-40 (매매 판단이 없으면 판정도 없음)
  - Given 데이터 A, 시험용 규칙 / When MoodCalculator.assess / Then 판정이 있고, 장세의 값은 전과 같으며(현재 상태 1.0, 불장), `valuation`은 위 R-40의 값이다
  - Given 데이터 A에서 DFII10을 뺌 / When MoodCalculator.assess / Then null (장세는 낼 수 있어도)
  - Given 데이터 A에서 CPIAUCSL을 2025-11-01부터만 남김 / Then null
- R-37 (거래일에 매매 판단의 시리즈는 넣지 않음): Given 데이터 A에 DGS10, DFF, DFII10을 2026-11-24에 하나씩 더함 / When MoodCalculator.assess / Then 기준 날짜는 2026-11-23이다. Given 데이터 A에서 DFII10의 2026-11-23 값을 뺌 / Then 기준 날짜는 2026-11-23이고 `realYieldDate`는 2026-11-20이다
- R-42 (판정을 낼 수 없음): Given 받아 둔 값 없음, 출처가 데이터 A에서 DFII10을 뺀 값을 돌려줌(FRED는 Success) / When 받기 한 번 / Then 결과는 Failure(OTHER), 받은 값은 저장되고 성공한 때는 적지 않는다
- R-20 (받을지 판정): Given 받아 둔 값이 데이터 A에서 DFII10, DGS10, DFF, CPIAUCSL을 뺀 것(앱을 바꾸기 전에 받아 둔 값), 성공한 때 1시간 전, 키 둘 AVAILABLE / When 화면이 보이게 됨 / Then 받기가 시작된다 (판정을 낼 수 있는 값이 없다)
- R-23 (계산용 디스패처에서)
  - Given ObserveMoodUseCase에 시험용 디스패처(StandardTestDispatcher)를 넘김, 받아 둔 값 데이터 A / When 다른 디스패처에서 모으기 시작하고 넘긴 디스패처는 아직 돌리지 않음 / Then 판정이 아직 나오지 않는다. 넘긴 디스패처를 돌리면 판정이 나온다
  - 있던 R-23 테스트(데이터 A → 데이터 B로 바뀌면 다시 계산)가 그대로 통과한다
- R-58, R-81: Given 받아 둔 값 데이터 A, 시험용 규칙, 화면과 받기 관리자 없음 / When ObserveMoodUseCase의 첫 값 / Then 판정이 있고 `valuation.pricePosition.score` 50, `displayedStage` MIDDLE, `valuation.rateBurden.score` 50, `displayedStage` MIDDLE. Given 데이터 A에서 DFF를 뺌 / Then null
- 있던 시장 분위기 테스트(MoodCalculator, ObserveMoodUseCase, MarketDataRefresher, RefreshMarketDataUseCase, 다른 기능에 내주는 UseCase)와 홈 화면 위젯의 테스트가 모두 통과한다

**확인 기준**
- 테스트 통과, 컴파일 오류 없음

### 단위 4: 매매 판단 두 카드의 화면 상태

**만들거나 고칠 것**
- 수정 MoodTextFormatter (feature/marketmood/presentation)
- 수정 MarketMoodUiState (feature/marketmood/presentation)
- 수정 MarketMoodViewModel (feature/marketmood/presentation)
- 수정 `app/src/main/res/values/strings.xml`

**지시**
- MoodTextFormatter에 더한다. 반올림과 부호는 있는 함수들과 같다(사사오입, 음수는 하이픈, 0에는 부호 없음). 있는 함수는 바꾸지 않는다. (R-69, P-16)
  - `fun ratioPercent(ratio: Double): String`: 비율 × 100을 부호 없이 소수 한 자리. 0.037 → `3.7%`
  - `fun inflationGap(rate: Double, target: Double): String`: 상승률을 소수 한 자리 퍼센트로 반올림한 값에서 목표(퍼센트)를 뺀 차이를 부호를 붙여 소수 한 자리 `%p`로. 0이면 부호 없이. (0.041, 0.02) → `+2.1%p`, (0.018, 0.02) → `-0.2%p`, (0.0204, 0.02) → `0.0%p`
  - `fun targetPercent(target: Double): String`: 목표 × 100을 끝의 0 없이. 0.02 → `2`, 0.025 → `2.5`
  - 실질금리·10년물·기준금리의 `2.79%`는 있는 `percent`, 1년 전과의 차이 `+0.97%p`는 있는 `signedPercentPoint`, 괴리·낙폭·3개월 수익률 `+4.1%`는 있는 `signedPercent`를 쓴다.
- MarketMoodUiState의 MoodUiState에 `pricePosition: ValuationCardUiState`, `rateBurden: ValuationCardUiState`를 더한다. (R-05, R-63, R-64)
  - `ValuationCardUiState(subtitle: UiText, scoreText: UiText, stage: ValuationStage, gauge: ValuationGaugeUiState, notice: UiText?, evidence: List<UiText>)`. `subtitle`은 카드 이름 아래의 글, `scoreText`는 "35 · 보통", `stage`는 표시 단계(색을 고르는 데 쓴다), `notice`는 숫자와 단계가 다른 날만 있다, `evidence`는 근거 줄을 위에서부터. 카드 이름("주가 위치", "금리 부담")은 화면이 고정 문구로 적는다.
  - `ValuationGaugeUiState(segments: List<Float>, labels: List<UiText>, position: Float)`. `segments`는 낮은 칸, 가운데 칸, 높은 칸의 폭(합 1), `labels`는 세 칸의 이름, `position`은 표시의 자리(0은 왼쪽 끝, 1은 오른쪽 끝).
- MarketMoodViewModel이 판정의 `valuation`으로 두 카드를 만든다. 판정이 보이는 모든 때(안내 띠가 있을 때, 받는 중일 때, 장세와 종합 점수가 다른 날 포함) 함께 있다. (R-05, R-63~R-70, P-16, P-33, P-34)
  - 단계의 이름: 주가 위치는 LOW "싼 편", MIDDLE "보통", HIGH "비싼 편". 금리 부담은 LOW "낮음", MIDDLE "보통", HIGH "높음". (R-63, R-64, R-76)
  - 주가 위치 카드: `subtitle` "전체 시장 SPY", `scoreText` "점수 · 표시 단계의 이름". (R-63)
  - 금리 부담 카드: `subtitle` "실질금리 2.79%"(실질금리 값을 `percent`로), `scoreText` 같은 모양. (R-64, R-69)
  - 눈금: 낮은 칸 폭 = (`lowBoundary` + 1) ÷ 100, 높은 칸 폭 = (100 - `highBoundary` + 1) ÷ 100, 가운데 칸 = 나머지. 기본값에서 0.31, 0.38, 0.31. 표시 자리 = 점수 ÷ 100. 칸 이름은 그 카드의 단계 이름 셋. (R-65, P-33)
  - 숫자와 단계가 다른 날 안내: 점수로 정한 단계와 표시 단계가 다를 때만 있다. 표시 단계가 LOW면 "점수는 {점수}이지만 아직 '{표시 단계의 이름}'입니다. {lowBoundary + buffer + 1} 이상이 되어야 보통으로 바뀝니다.", HIGH면 "점수는 {점수}이지만 아직 '{표시 단계의 이름}'입니다. {highBoundary - buffer - 1} 이하가 되어야 보통으로 바뀝니다." 숫자는 판정에 실린 경계와 완충에서 낸다. (R-67)
  - 주가 위치의 근거 두 줄: "200일 평균보다 {짧은 괴리} · 3년 평균보다 {긴 괴리}", "고점보다 {낙폭} · 3개월 전보다 {3개월 수익률}". 넷 모두 `signedPercent`. (R-68)
  - 금리 부담의 근거 세 줄 (R-69, R-70)
    - 첫 줄: 1년 전과의 차이를 `signedPercentPoint`로 적은 값이 0보다 크면 "1년 전보다 {차이} · 오르는 중", 작으면 "1년 전보다 {차이} · 내리는 중", 0이면 줄 전체가 "1년 전과 같음". 0인지는 적는 값(소수 두 자리로 반올림한 값)으로 가른다.
    - 둘째 줄: "10년물 국채 {10년물} · 기준금리 {기준금리}". 값은 `percent`.
    - 셋째 줄: "물가 {ratioPercent(상승률)} · 목표 {targetPercent(물가 목표)}%보다 {inflationGap(상승률, 물가 목표)} · {그 달}월 값".
  - 날짜 붙이기: 실질금리(카드 이름 아래), 10년물, 기준금리 가운데 값의 날짜가 기준 날짜와 다른 것은 그 값 바로 뒤에 " · {월}월 {일}일 값"을 붙인다. 실질금리의 날짜는 `realYieldDate`다. 점수와 1년 전 줄에는 붙이지 않는다. 예: "실질금리 2.79% · 11월 21일 값", "10년물 국채 4.92% · 11월 21일 값 · 기준금리 3.88% · 11월 21일 값". 있는 `evidence_value_with_date`("%1$s · %2$d월 %3$d일 값")를 값 자리에 끼워 써도 된다. (R-70)
- `strings.xml`에 문구를 더한다. 있는 문구는 바꾸지 않는다. 문구의 이름은 CLI가 정한다. (R-05, R-63~R-70)
  - "지난 5년과 비교하면", "0은 지난 5년 중 가장 낮은 쪽, 100은 가장 높은 쪽", "장세의 근거"
  - "주가 위치", "전체 시장 SPY", "금리 부담", "실질금리 %1$s"
  - "%1$d · %2$s"
  - "싼 편", "보통", "비싼 편", "낮음", "높음"
  - "점수는 %1$d이지만 아직 '%2$s'입니다. %3$d 이상이 되어야 보통으로 바뀝니다.", "점수는 %1$d이지만 아직 '%2$s'입니다. %3$d 이하가 되어야 보통으로 바뀝니다."
  - "200일 평균보다 %1$s · 3년 평균보다 %2$s", "고점보다 %1$s · 3개월 전보다 %2$s"
  - "1년 전보다 %1$s · %2$s", "오르는 중", "내리는 중", "1년 전과 같음"
  - "10년물 국채 %1$s · 기준금리 %2$s"
  - "물가 %1$s · 목표 %2$s%%보다 %3$s · %4$d월 값"

**테스트 지시**
아래 Given의 판정 값(점수, 단계, 금리 값 등)은 받아 둔 값과 규칙으로 만든다. 단위 3의 테스트 지시 머리의 방법(위치 기간 101, 평균 일수 1인 규칙)을 쓰면 원하는 점수를 만들기 쉽다. 글의 확인은 있던 테스트처럼 문구 리소스와 끼워 넣는 값으로 해도 된다.

- R-69 (MoodTextFormatter)
  - `ratioPercent`: 0.037 → `3.7%`, 0.04149 → `4.1%`, 0.0415 → `4.2%`, 0.0 → `0.0%`
  - `inflationGap`: (0.041, 0.02) → `+2.1%p`, (0.018, 0.02) → `-0.2%p`, (0.0204, 0.02) → `0.0%p`, (0.0195, 0.02) → `0.0%p` (적는 상승률 2.0%에서 뺀다), (0.03, 0.025) → `+0.5%p`
  - `targetPercent`: 0.02 → `2`, 0.025 → `2.5`
- R-05 (판정의 구성): Given 받아 둔 값 데이터 A, 키 둘 AVAILABLE / When uiState / Then body는 Assessment이고 `pricePosition`, `rateBurden`이 모두 있다. Given 같은 값에 안내 띠가 있음(가장 최근 받기가 NO_CONNECTION) / Then banner와 두 카드가 함께 있다. Given 받는 중 / Then 두 카드가 그대로 있다
- R-63, R-64 (이름 아래 글, 점수와 단계의 글): Given 데이터 A / When uiState / Then 주가 위치 `subtitle` "전체 시장 SPY", `scoreText` "50 · 보통". 금리 부담 `subtitle` "실질금리 2.00%", `scoreText` "50 · 보통"
- R-66 (표시 단계를 상태에 실음): Given 주가 위치 표시 단계 LOW(점수 6), 금리 부담 표시 단계 HIGH(점수 98) / When uiState / Then `pricePosition.stage` LOW, `scoreText` "6 · 싼 편", `rateBurden.stage` HIGH, `scoreText` "98 · 높음". Given 주가 위치 HIGH(점수 74) / Then "74 · 비싼 편". Given 금리 부담 LOW(점수 22) / Then "22 · 낮음"
- R-65 (칸 폭, 칸 이름, 표시 위치)
  - Given 데이터 A(점수 50, 경계 30/70) / When uiState / Then 두 카드의 `segments`는 0.31, 0.38, 0.31, `position` 0.5. 주가 위치의 `labels` "싼 편", "보통", "비싼 편", 금리 부담의 `labels` "낮음", "보통", "높음"
  - Given 경계 25/80으로 바꾼 규칙 / Then `segments`는 0.26, 0.53, 0.21
  - Given 주가 위치 점수 0 / Then `position` 0.0. 점수 100 / Then 1.0
- R-67 (줄을 보일지와 그 글)
  - Given 주가 위치 점수 33, 점수로 정한 단계 MIDDLE, 표시 단계 LOW (경계 30/70, 완충 5) / When uiState / Then `pricePosition.notice`는 "점수는 33이지만 아직 '싼 편'입니다. 36 이상이 되어야 보통으로 바뀝니다."
  - Given 금리 부담 점수 67, 점수로 정한 단계 MIDDLE, 표시 단계 HIGH / Then `rateBurden.notice`는 "점수는 67이지만 아직 '높음'입니다. 64 이하가 되어야 보통으로 바뀝니다."
  - Given 금리 부담 점수 35, 표시 단계 LOW / Then "점수는 35이지만 아직 '낮음'입니다. 36 이상이 되어야 보통으로 바뀝니다."
  - Given 경계 25/75, 완충 3인 규칙, 주가 위치 점수 28, 표시 단계 LOW / Then "… 29 이상이 되어야 보통으로 바뀝니다." 점수 73, 표시 HIGH / Then "… 71 이하가 되어야 보통으로 바뀝니다."
  - Given 점수로 정한 단계와 표시 단계가 같음(데이터 A) / Then 두 카드의 `notice`는 null
- R-68 (글)
  - Given 짧은 괴리 +0.041, 긴 괴리 +0.214, 낙폭 -0.032, 3개월 수익률 +0.058 / When uiState / Then 주가 위치의 `evidence`는 "200일 평균보다 +4.1% · 3년 평균보다 +21.4%", "고점보다 -3.2% · 3개월 전보다 +5.8%"
  - Given 데이터 A / Then "200일 평균보다 0.0% · 3년 평균보다 0.0%", "고점보다 0.0% · 3개월 전보다 0.0%"
- R-69 (네 자리의 글)
  - Given 실질금리 2.79(실질금리의 날 = 기준 날짜), 1년 전과의 차이 +0.97, 10년물 4.92, 기준금리 3.88(둘 다 기준 날짜), 물가 상승률 0.041 · 2026-10, 물가 목표 0.02 / When uiState / Then `subtitle` "실질금리 2.79%", `evidence`는 "1년 전보다 +0.97%p · 오르는 중", "10년물 국채 4.92% · 기준금리 3.88%", "물가 4.1% · 목표 2%보다 +2.1%p · 10월 값"
  - Given 차이 -0.18 / Then 첫 줄 "1년 전보다 -0.18%p · 내리는 중"
  - Given 차이 0 / Then 첫 줄 "1년 전과 같음". Given 차이 +0.004 / Then "1년 전과 같음". Given 차이 +0.005 / Then "1년 전보다 +0.01%p · 오르는 중"
  - Given 물가 상승률 0.018 · 2026-09 / Then 셋째 줄 "물가 1.8% · 목표 2%보다 -0.2%p · 9월 값"
  - Given 데이터 A / Then "1년 전과 같음", "10년물 국채 4.00% · 기준금리 3.50%", "물가 3.0% · 목표 2%보다 +1.0%p · 10월 값"
- R-70 (날짜를 붙일지와 그 글)
  - Given 기준 날짜 2026-11-23, 실질금리의 날 2026-11-21, 10년물 2026-11-21, 기준금리 2026-11-21 / When uiState / Then `subtitle` "실질금리 2.79% · 11월 21일 값", 둘째 줄 "10년물 국채 4.92% · 11월 21일 값 · 기준금리 3.88% · 11월 21일 값". 첫 줄과 `scoreText`에는 날짜가 없다
  - Given 기준금리만 2026-11-20 / Then `subtitle`은 "실질금리 2.79%", 둘째 줄 "10년물 국채 4.92% · 기준금리 3.88% · 11월 20일 값"
- 있던 ViewModel 테스트가 모두 통과한다(받아 둔 값이 데이터 A, B이고 규칙이 시험용 규칙이면 장세의 글은 전과 같다)

**확인 기준**
- 테스트 통과, 컴파일 오류 없음

### 단위 5: 공용 테마와 화면

**만들거나 고칠 것**
- 수정 AppColors, AppTypography, AppDimens (core/ui/theme)
- 수정 MarketMoodScreen (feature/marketmood/presentation)

**지시**
- 공용 테마에 매매 판단의 값을 더한다. 값은 시안에서 뽑은 것이고 1px = 1dp(글자는 1sp)로 옮긴다. 있는 값은 바꾸지 않는다. 홈 화면 위젯도 다음 변경에서 이 색을 쓴다. (R-66, R-19, P-17)
  - AppColors에 더할 것. 값이 있는 색과 같아도 쓰는 곳이 달라 따로 둔다.
    - 단계의 글(점수와 단계 이름): 싼 편·낮음 `#0E7C6B`, 보통 `#4A5360`, 비싼 편·높음 `#B45309`
    - 눈금의 세 칸: 싼 편·낮음 `#5FB8A8`, 보통 `#B7BEC8`, 비싼 편·높음 `#E9A23B`
  - AppTypography에 더할 것: 매매 판단 카드의 점수와 단계 22 Bold, 매매 판단 제목 아래 한 줄 12 Normal
  - AppDimens에 더할 것: 두 제목 덩어리의 위 여백 8, 좌우 여백 4. 매매 판단 카드의 안쪽 여백 16(네 쪽). 근거 줄 덩어리의 위 여백 10(그 위에 구분선)
  - 있는 값을 쓰는 곳: 제목 15 Bold `bodyBold`, 카드 이름 17 Bold `groupName`, 카드 이름 아래 글과 근거 줄 13 `caption`(색 `textMuted`), 눈금 아래 이름 `gaugeLabel`, 안내 줄 `captionNotice`와 안내 상자(`background`, `AppShapes.notice`, `noticePaddingVertical` 10, `noticePaddingHorizontal` 12), 카드 사이와 제목 사이 `cardGap` 16, 카드 안의 덩어리 사이 `assessmentGap` 12, 이름과 그 아래 글 사이·제목과 그 아래 줄 사이·근거 줄 사이 `nameGap` 2, 이름 쪽과 점수 사이 `rowContentGap` 12, 구분선 `divider` 1 `border`, 눈금의 크기(`gaugeHeight` 18, `gaugeBarTop` 4, `gaugeBarHeight` 10, `gaugeMarkerWidth` 4, `gaugeMarkerOutline` 2, `gaugeLabelGap` 6, `AppShapes.gaugeBarRadius` 5, `gaugeMarkerRadius` 2), 카드 바탕·테두리·모서리
- MarketMoodScreen은 판정 안에 위에서 아래로 종합 판정 카드, "지난 5년과 비교하면" 제목과 그 아래 "0은 지난 5년 중 가장 낮은 쪽, 100은 가장 높은 쪽", 주가 위치 카드, 금리 부담 카드, "장세의 근거" 제목, 묶음 카드들, 맨 아래 안내를 그린다. 종합 판정 카드, 묶음 카드, 맨 아래 안내, 위쪽 줄, 안내 띠와 다른 본문(키 안내, 받는 중, 받지 못함)은 바꾸지 않는다. 받은 상태를 그리기만 하고, 글을 고르거나 숫자를 바꾸는 일을 여기에 두지 않는다. (R-05, R-63, R-64, R-65, R-66, R-67, R-68, R-69, R-70)
- 시안 `https://claude.ai/artifact/LuRSF8bSPDqruAbnUSfye9`의 아래 화면과 같은 모양으로 만든다. 모양의 값(색, 크기, 간격, 모서리)은 시안의 HTML에서 가져오되, 위 테마에 있는 값은 테마의 것을 쓴다. 문구와 동작은 이 지시의 글이 맞다. 시안의 숫자와 날짜는 예시다. (R-19)

| 시안의 화면 이름 | 시안 안의 파일 | 이번에 보는 것 |
|---|---|---|
| 시장 분위기 · 판정이 나왔을 때 | `project/Market-Main.dc.html` | 두 제목과 두 카드의 배치. 비싼 편·높음의 색 |
| 시장 분위기 · 장세와 종합 점수가 다른 날 | `project/Market-Pending.dc.html` | 다른 날 안내가 있어도 두 카드가 그대로 있음 |
| 시장 분위기 · 새로 받지 못했을 때 | `project/Market-Stale.dc.html` | 안내 띠가 있을 때. 주가 위치 보통의 색 |
| 시장 분위기 · 중립인 날 | `project/Market-Neutral.dc.html` | 두 카드 모두 보통의 색 |
| 시장 분위기 · 물장인 날 | `project/Market-Bear.dc.html` | 싼 편·낮음의 색, 음수의 근거 숫자 |
| 시장 분위기 · 매매 판단의 숫자와 단계가 다른 날 | `project/Market-Buffer.dc.html` | 두 카드의 안내 줄(눈금과 근거 사이의 회색 상자) |
| 시장 분위기 · 금리 값의 날짜가 다르고 1년 전과 같은 날 | `project/Market-RateDate.dc.html` | 날짜가 붙은 실질금리, 두 줄로 넘어가는 10년물·기준금리 줄, "1년 전과 같음" |

- 두 제목은 카드가 아니다: 바탕 없이 위 여백 8, 좌우 여백 4, 제목 15 Bold `text`, 그 아래 한 줄 12 `textMuted`(사이 2). "장세의 근거"에는 아래 줄이 없다.
- 매매 판단 카드: 흰 카드(테두리 1, 모서리 16, 안쪽 여백 16), 안의 덩어리 사이 12. 위에서 아래로 머리(왼쪽에 이름 17 Bold와 그 아래 13 `textMuted`, 오른쪽에 점수와 단계 22 Bold, 단계의 색, 한 줄로), 눈금, 안내 줄(있을 때만), 근거 줄(위에 구분선 1, 그 아래 여백 10, 줄 사이 2, 13 `textMuted`).
- 머리의 왼쪽과 오른쪽은 세로 가운데에 맞춘다(시안의 `align-items: center`). 묶음 카드의 머리와 같다.
- 눈금은 종합 판정 카드의 눈금과 같은 크기와 모양(막대, 둥근 끝, 흰 테가 둘린 표시, 아래 칸 이름)이고, 칸이 셋이며 칸의 폭은 상태의 `segments`를 따른다. 칸 이름은 칸마다 가운데에 둔다. 종합 판정 카드의 눈금을 칸 폭과 색을 받게 고쳐 함께 써도 된다. 종합 판정 카드의 모양은 바꾸지 않는다.
- 근거 줄이 화면 폭보다 길면 다음 줄로 넘어간다(시안 "금리 값의 날짜가 다르고 1년 전과 같은 날"의 10년물·기준금리 줄).
- 점수와 단계의 색은 표시 단계의 색이다. 눈금의 칸 색은 칸의 단계 색이다(표시 단계와 상관없음).
- 미리보기를 맞춘다: 판정이 보이는 있던 미리보기에 두 카드의 상태를 채우고, 시안 "매매 판단의 숫자와 단계가 다른 날"과 "금리 값의 날짜가 다르고 1년 전과 같은 날"의 미리보기를 더한다. 그런 날이 아닐 때 수동 확인을 미리보기로 하기 위함이다. (R-67, R-70)

**확인 기준**
- 테스트 통과, 컴파일 오류 없음

## 3. 수동 확인

- R-05 (표시 (두 제목과 카드의 순서)): Given 실제 키 둘이 저장되어 판정이 보임 / When 시장 분위기 화면을 위에서 아래로 밀어 본다 / Then 종합 판정 카드, "지난 5년과 비교하면"과 그 아래 한 줄, 주가 위치 카드, 금리 부담 카드, "장세의 근거", 주식 카드, 채권 신호 카드, 맨 아래 안내 순서다
- R-19: Given 판정이 보이는 여러 상태 / When 화면을 연다 / Then 시안 "시장 분위기 · 판정이 나왔을 때", "장세와 종합 점수가 다른 날", "새로 받지 못했을 때", "키가 없을 때", "한 번도 받지 못했을 때", "중립인 날", "물장인 날", "받는 중", "매매 판단의 숫자와 단계가 다른 날", "금리 값의 날짜가 다르고 1년 전과 같은 날"과 나란히 놓고 볼 때 배치, 간격, 크기, 색이 같다. 그런 날이 아닌 화면은 미리보기로 본다
- R-23 (화면이 멈칫하지 않음): Given 실제 키 둘이 저장됨 / When 앱을 열 때와 새로 고침으로 받기가 끝난 뒤 / Then 화면이 멈추거나 끊기지 않고, 받는 동안 진행 표시가 돌며 위아래로 밀 수 있다
- R-41 (실제 요청): Given 이 변경 전의 앱으로 판정을 본 적이 있는 기기에 이 앱을 깔고 실제 키 둘이 저장됨 / When 앱을 연다 / Then 처음에는 받는 중 카드가 잠시 보인 뒤 매매 판단까지 있는 판정이 나온다(전에 받아 둔 장세 값으로 판정을 먼저 보이지 않는다). 새로 고침을 다시 눌러도 판정이 그대로 나오고 안내 띠가 보이지 않는다. 금리 부담 카드의 물가 줄에 달이 적혀 있다
- R-63 (표시): Given 판정이 보임 / When 주가 위치 카드를 본다 / Then 왼쪽에 "주가 위치"와 그 아래 "전체 시장 SPY", 오른쪽에 "35 · 보통" 모양의 점수와 단계, 눈금, 근거 두 줄("200일 평균보다 … · 3년 평균보다 …", "고점보다 … · 3개월 전보다 …")이 있다
- R-64 (표시): Given 판정이 보임 / When 금리 부담 카드를 본다 / Then 왼쪽에 "금리 부담"과 그 아래 "실질금리 2.79%" 모양의 값, 오른쪽에 점수와 단계, 눈금, 근거 세 줄("1년 전보다 …" 또는 "1년 전과 같음", "10년물 국채 … · 기준금리 …", "물가 … · 목표 2%보다 … · …월 값")이 있다
- R-65 (표시): Given 판정이 보임 / When 두 카드의 눈금을 본다 / Then 세 칸의 폭이 31 : 38 : 31쯤이고, 칸 아래에 주가 위치는 "싼 편", "보통", "비싼 편", 금리 부담은 "낮음", "보통", "높음"이 있으며, 표시가 점수의 자리(예: 98이면 오른쪽 끝 가까이)에 있다
- R-66 (단계의 색): Given 판정이 보임 / When 두 카드의 점수와 단계를 본다 / Then 싼 편·낮음은 청록, 보통은 회색, 비싼 편·높음은 주황이고 장세의 빨강·파랑과 다르다. 그날 보이지 않는 단계는 미리보기(판정이 나왔을 때, 중립인 날, 물장인 날)로 시안과 견준다
- R-67 (표시): Given 점수로 정한 단계와 표시 단계가 다른 날(그런 날이 아니면 미리보기 "매매 판단의 숫자와 단계가 다른 날") / When 그 카드를 본다 / Then 눈금과 근거 줄 사이에 회색 상자로 "점수는 33이지만 아직 '싼 편'입니다. 36 이상이 되어야 보통으로 바뀝니다." 모양의 줄이 있고, 시안 "매매 판단의 숫자와 단계가 다른 날"과 모양이 같다
- R-70 (표시 (줄이 두 줄로 넘어감)): Given 실질금리·10년물·기준금리의 날짜가 기준 날짜와 다른 날(그런 날이 아니면 미리보기 "금리 값의 날짜가 다르고 1년 전과 같은 날") / When 금리 부담 카드를 본다 / Then 날짜가 다른 값 바로 뒤에 " · 11월 21일 값" 모양이 붙고, 10년물·기준금리 줄이 잘리지 않고 다음 줄로 넘어가며, 시안 "금리 값의 날짜가 다르고 1년 전과 같은 날"과 모양이 같다
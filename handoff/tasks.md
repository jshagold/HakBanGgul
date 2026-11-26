# Tasks: 홈 화면 위젯 버전업-001

기능: 홈 화면 위젯
변경: 버전업-001 (CS-004)
기준 Plan: 2.0.0
상태: 구현 중

| 전달 | 날짜 | 이유 |
|---|---|---|
| 1 | 2026-11-26 | 최초 전달 |

## 1. 개요
홈 화면 위젯은 앱을 열지 않고 홈 화면에서 지금 장세와 기준 날짜를 보여 주고, 위젯이 놓여 있는 동안 앱을 열지 않아도 값이 새로 받아지게 하는 기능이다. 이번에는 판정에 매매 판단 두 줄(주가 위치, 금리 부담)을 더한다. 매달 꾸준히 사는 장기 투자자가 위젯으로 지금 장이 지난 5년과 비교해 어디쯤인지 슥 보게 하려는 것이다. 큰 위젯은 단계 이름 아래에 "주가 위치 35 · 보통", "금리 부담 98 · 높음"을, 작은 위젯은 단계 이름 오른쪽에 "주가 보통", "금리 높음"을 적는다. 그리고 앱을 새 버전으로 바꾼 때 위젯이 놓여 있으면 앱을 열지 않아도 받게 한다.
매매 판단의 값은 시장 분위기가 판정(`MoodAssessment.valuation`)에 실어 내주는 것을 옮겨 싣기만 한다. 위젯은 따로 계산하지 않고 값을 저장하지 않는다. 시장 분위기는 매매 판단의 값까지 모두 있어야 판정을 내므로, 장세는 있고 매매 판단만 없는 위젯 모습은 생기지 않는다.
바꾸지 않는 것: 시장 분위기와 설정의 코드(`feature/marketmood`, `feature/settings`), HakBanGgulApp, MainActivity, AppNavHost, WidgetRefreshWorker, HomeWidgetUpdater, RefreshWidgetAction, 두 위젯 Receiver, 위젯 정보 파일, 키 안내·받지 못함의 모양, 새로 고침 버튼과 진행 표시, 1분·6시간 규칙. 있던 테스트는 지우거나 줄이지 않는다. 이번에 바뀐 생성자 때문에 기대 값을 고치는 것은 된다(아래 단위마다 적었다). `core`는 아래 지시대로 더하고 고치는 것 말고는 바꾸지 않는다.

## 2. 구현 단위

### 단위 1: 위젯에 보일 매매 판단 (Domain)

**만들거나 고칠 것**
- 신규 WidgetValuation (feature/homewidget/domain)
- 수정 WidgetContent (feature/homewidget/domain)
- 수정 ObserveWidgetStateUseCase (feature/homewidget/domain)
- 수정 WidgetTestFixture, ObserveWidgetStateUseCaseTest (`app/src/test/java/com/hak/hakbanggul/feature/homewidget/domain/`)

**지시**
- 새 용어의 영어 이름: 위젯의 매매 판단 값 `WidgetValuation`. 처음 선언하는 곳 위에 `// 용어: (한국어 이름)` 주석을 단다. 매매 판단 `Valuation`, 주가 위치 `PricePosition`, 금리 부담 `RateBurden`, 매매 판단의 단계 `ValuationStage`, 표시 단계 `displayedStage`는 있는 이름을 쓴다.
- 이 단위는 순수 Kotlin이다. Android와 Glance, WorkManager를 가져오지 않는다.
- WidgetValuation은 매매 판단 하나의 위젯에 쓰는 값이다(데이터 클래스): `WidgetValuation(score: Int, displayedStage: ValuationStage)`. 점수는 0~100 정수, 단계는 표시 단계다. (P-16)
- WidgetContent.Assessment를 `Assessment(stage: MarketStage, asOfDate: LocalDate, pricePosition: WidgetValuation, rateBurden: WidgetValuation)`로 고친다. KeyGuide와 NotReceived는 그대로다. (R-06, R-31, P-16)
- ObserveWidgetStateUseCase는 표에서 Assessment를 고를 때, 판정의 `valuation.pricePosition`과 `valuation.rateBurden`에서 각각 `score`와 `displayedStage`만 옮겨 싣는다. (R-31, R-46, P-16)
  - 점수로 정한 단계(`scoredStage`)는 쓰지 않는다. 점수와 표시 단계가 다른 날(완충)에도 둘을 그대로 옮긴다. (R-46)
  - 근거 숫자(괴리, 낙폭, 3개월 수익률, 실질금리, 10년물, 기준금리, 물가), 실질금리의 날, 경계와 완충 값은 옮기지 않는다. 위젯이 쓰지 않는 값이 같은 상태인지의 판단(`distinctUntilChanged`)에 섞이지 않게 하려는 것이다. ValuationAssessment를 그대로 싣지 않는다. (P-16)
  - 판정이 null인지로 받지 못함을 가르는 것, 키 안내가 먼저인 것, 그대로 두기, 받지 못함을 미루기, 버튼의 상태, 예외 처리는 지금 그대로다. (R-05, R-31)
  - 판정이 바뀌면(장세는 그대로이고 매매 판단만 바뀌어도) 새 WidgetState를 내준다. (R-17, R-30)

**테스트 지시**
상황은 지금의 WidgetTestFixture와 같다(시계 2026-11-24T01:00:00Z, 두 출처의 키 AVAILABLE, 받아 둔 값 데이터 A, 시험용 규칙 `MoodTestData.RULES`). 데이터 A는 시리즈 값이 모두 같아 주가 위치와 금리 부담이 모두 점수 50, 표시 단계 MIDDLE이다(시장 분위기의 `R-58 R-81 데이터 A면 첫 값에 매매 판단이 있다`가 확인한 값).

있던 테스트 고치기
- WidgetTestFixture의 `ASSESSMENT_A`를 `Assessment(BULL, 2026-11-23, WidgetValuation(50, MIDDLE), WidgetValuation(50, MIDDLE))`로 고친다. 나쁜 날(`badDay`)은 T10Y2Y와 BAMLH0A0HYM2만 바꾸므로, ObserveWidgetStateUseCaseTest의 `Assessment(NEUTRAL, 2026-11-23)`도 매매 판단 둘이 `WidgetValuation(50, MIDDLE)`인 것으로 고친다. 있던 테스트의 수와 확인하는 경우는 그대로 둔다.

새로 더할 테스트에서 쓰는 "완충 규칙과 완충 데이터"
- 완충 규칙: `MoodTestData.RULES`에서 valuation만 `positionWindow = 101`, `smoothingDays = 1`로 바꾼 것. 그 밖(짧은 평균 5, 긴 평균 10, 3개월 5, 경계 30·70, 완충 5, 1년 전 365일 등)은 그대로다. 위치 기간 101, 평균 일수 1이면 그날의 점수가 "앞 100개 값 가운데 그날의 값보다 작은 값의 수(같은 값은 반)"다.
- 완충 데이터: 데이터 A에서 실질금리(`MoodTestData.REAL_YIELD`)의 마지막 102개 평일(2026-11-23으로 끝남) 값만 `MoodTestData.valuesForScores(listOf(30, 35))`로 바꾼 것. 그 앞의 날은 데이터 A의 2.00을 그대로 둔다(1년 전 값이 있어야 금리 부담이 나온다). `replaceSeries`는 시리즈를 통째로 바꾸므로 쓰지 않는다. 날마다 `set`으로 덮는 등 방법은 CLI가 정한다.
- 완충 규칙과 완충 데이터로 내는 판정: 장세 BULL, 기준 날짜 2026-11-23(실질금리는 장세에 쓰지 않는다), 주가 위치 점수 50 · 표시 MIDDLE(SPY는 그대로 100이라 같은 값만 있다), 금리 부담 점수 35 · 표시 LOW · 점수로 정한 단계 MIDDLE(점수 30인 날 LOW가 되고, 35는 36보다 작아 LOW 그대로다). 시장 분위기의 `R-77 30, 35는 표시 LOW이고 점수로 정한 단계 MIDDLE`과 같은 경우다.
- 완충 규칙과 데이터 A로 내는 판정은 데이터 A와 같다(주가 위치·금리 부담 모두 50 · MIDDLE).

ObserveWidgetStateUseCase
- R-31 (장세, 기준 날짜, 매매 판단 둘의 점수와 표시 단계를 옮김)
  - Given 기본 상황 / When 첫 값 / Then `WidgetState(Assessment(BULL, 2026-11-23, WidgetValuation(50, MIDDLE), WidgetValuation(50, MIDDLE)), ENABLED)`
  - Given 규칙은 완충 규칙, 받아 둔 값은 완충 데이터 / When 첫 값 / Then `Assessment(BULL, 2026-11-23, WidgetValuation(50, MIDDLE), WidgetValuation(35, LOW))`. 주가 위치와 금리 부담이 서로 바뀌어 실리지 않았다
- R-46 (점수로 정한 단계가 아니라 표시 단계)
  - Given 규칙은 완충 규칙, 받아 둔 값은 완충 데이터 / When 첫 값 / Then 금리 부담이 `WidgetValuation(35, LOW)`다(점수로 정한 단계 MIDDLE이 아니다). 같은 판정을 `ObserveMoodUseCase`로 직접 받아 `rateBurden.scoredStage`가 MIDDLE인 것도 함께 확인해, 둘이 다른 경우임을 테스트 안에서 보인다
- R-17, R-30 (매매 판단만 바뀌어도 상태가 바뀜)
  - Given 규칙은 완충 규칙, 받아 둔 값은 데이터 A로 모으기 시작해 `Assessment(BULL, 2026-11-23, (50, MIDDLE), (50, MIDDLE))`이 나옴 / When 받아 둔 값을 완충 데이터로 바꿈(받는 중 아님) / Then `Assessment(BULL, 2026-11-23, (50, MIDDLE), (35, LOW))`가 이어서 나온다. 장세와 기준 날짜는 그대로다
- R-31 (판정이 없으면 받지 못함. 매매 판단의 값이 없어도)
  - Given 받아 둔 값이 데이터 A에서 실질금리(`MoodTestData.REAL_YIELD`)가 통째로 빠짐 / When 첫 값 / Then `WidgetState(NotReceived, ENABLED)`. 장세의 값은 모두 있어도 매매 판단의 값이 없으면 판정이 없다

**확인 기준**
- 테스트 통과, 컴파일 오류 없음

### 단위 2: 위젯의 상태에 매매 판단 줄의 글 더하기

**만들거나 고칠 것**
- 수정 HomeWidgetUiState (feature/homewidget/presentation. `toUiState` 포함)
- 수정 `app/src/main/res/values/strings.xml`
- 수정 HomeWidgetStateHolderTest (`app/src/test/java/com/hak/hakbanggul/feature/homewidget/presentation/`)

**지시**
- 새 용어의 영어 이름: 매매 판단 줄 `WidgetValuationLine`, 줄인 이름 `shortName`. 처음 선언하는 곳 위에 `// 용어: (한국어 이름)` 주석을 단다.
- WidgetValuationLine은 매매 판단 줄 하나에 그릴 것이다(데이터 클래스): `WidgetValuationLine(stage: ValuationStage, name: UiText, shortName: UiText, scoreText: UiText, stageName: UiText)`. (R-44, R-45, P-17)
  - `stage`: 표시 단계. 위젯이 단계의 색을 고르는 데 쓴다.
  - `name`: 이름. 큰 위젯이 쓴다. 주가 위치 `price_position_name`("주가 위치"), 금리 부담 `rate_burden_name`("금리 부담").
  - `shortName`: 줄인 이름. 작은 위젯이 쓴다. 주가 위치 "주가", 금리 부담 "금리"(아래에서 더하는 문구).
  - `scoreText`: 점수와 표시 단계. 큰 위젯이 쓴다. `valuation_score`("%1$d · %2$s")에 점수와 `stageName`을 넣는다. 예: "35 · 보통".
  - `stageName`: 표시 단계의 이름. 작은 위젯이 쓴다.
- 표시 단계의 이름은 카드에 따라 다르다. 시장 분위기 화면과 같은 문구를 쓴다. (R-44, R-45, P-17)

| 표시 단계 | 주가 위치 | 금리 부담 |
|---|---|---|
| LOW | `valuation_price_low` "싼 편" | `valuation_rate_low` "낮음" |
| MIDDLE | `valuation_middle` "보통" | `valuation_middle` "보통" |
| HIGH | `valuation_price_high` "비싼 편" | `valuation_rate_high` "높음" |

- 단계에서 이름을 고르는 코드는 시장 분위기의 Presentation(MarketMoodViewModel)에서 가져오지 않고 위젯 쪽에 한 벌 둔다. 문구는 `strings.xml`의 있는 것을 쓴다. (P-17)
- WidgetMain.Stage를 `Stage(stage: MarketStage, name: UiText, asOfText: UiText, pricePosition: WidgetValuationLine, rateBurden: WidgetValuationLine)`로 고친다. `toUiState`는 Assessment의 `pricePosition`과 `rateBurden`으로 두 줄을 만든다. 두 줄의 순서는 주가 위치가 먼저, 금리 부담이 다음이다. 큰 위젯용 글과 작은 위젯용 글을 둘 다 담아, 놓인 위젯이 모두 같은 상태 하나를 받게 한다(위젯마다 상태를 따로 만들지 않는다). (R-06, R-44, R-45, P-05, P-17)
- 점수와 표시 단계가 다른 날에도 그대로 적는다. 앱의 "점수는 33이지만 아직 '싼 편'입니다" 같은 줄(`valuation_notice_*`)은 위젯에 쓰지 않는다. 금리 값의 날짜도 적지 않는다. (R-46)
- Message(키 안내, 받지 못함)는 그대로다.
- `strings.xml`에 더할 것 (R-45, R-02)
  - 줄인 이름: "주가", "금리"
  - 미리 보기의 예시 값(단위 3에서 씀): "74 · 비싼 편", "98 · 높음"
  - 있는 문구를 쓰는 것: `price_position_name`, `rate_burden_name`, `valuation_score`, `valuation_price_low`, `valuation_middle`, `valuation_price_high`, `valuation_rate_low`, `valuation_rate_high`. 있는 문구는 바꾸지 않는다.

**테스트 지시**
상황은 단위 1과 같다. 단계와 점수를 판정 데이터로 만들기 어려운 경우(LOW, HIGH, 점수 33 등)는 WidgetState를 HomeWidgetUiState로 바꾸는 부분(`toUiState`)을 따로 불러 확인한다. 아래 "줄(단계, 점수)"는 `WidgetValuationLine`을 뜻하고, 글은 UiText의 문구 id와 args로 비교한다.

있던 테스트 고치기
- HomeWidgetStateHolderTest가 만드는 기대 값 `WidgetMain.Stage(...)`에 매매 판단 두 줄을 더한다. 데이터 A로 만든 판정이면 두 줄은 아래 R-06의 첫 경우와 같다. Assessment를 직접 만드는 곳은 매매 판단 둘을 `WidgetValuation(50, MIDDLE)`로 넣는다. 있던 테스트의 수와 확인하는 경우는 그대로 둔다.

HomeWidgetStateHolder와 toUiState
- R-06, R-44, R-45 (두 줄의 순서와 글)
  - Given 기본 상황(데이터 A) / When state의 첫 null 아닌 값 / Then main이 `Stage(BULL, stage_bull, widget_as_of [11, 23], pricePosition, rateBurden)`이고
    - pricePosition: stage MIDDLE, name `price_position_name`, shortName "주가", scoreText `valuation_score` [50, UiText(`valuation_middle`)], stageName `valuation_middle`
    - rateBurden: stage MIDDLE, name `rate_burden_name`, shortName "금리", scoreText `valuation_score` [50, UiText(`valuation_middle`)], stageName `valuation_middle`
  - Given `Assessment(BULL, 2026-11-23, WidgetValuation(74, HIGH), WidgetValuation(98, HIGH))` / When toUiState / Then pricePosition의 scoreText [74, UiText(`valuation_price_high`)], stageName `valuation_price_high`, stage HIGH. rateBurden의 scoreText [98, UiText(`valuation_rate_high`)], stageName `valuation_rate_high`, stage HIGH
  - Given `Assessment(BEAR, 2026-11-23, WidgetValuation(12, LOW), WidgetValuation(20, LOW))` / When toUiState / Then pricePosition의 scoreText [12, UiText(`valuation_price_low`)], stageName `valuation_price_low`. rateBurden의 scoreText [20, UiText(`valuation_rate_low`)], stageName `valuation_rate_low`. 둘 다 stage LOW
  - Given `Assessment(NEUTRAL, 2026-11-23, WidgetValuation(58, MIDDLE), WidgetValuation(52, MIDDLE))` / When toUiState / Then pricePosition의 scoreText [58, UiText(`valuation_middle`)], rateBurden의 scoreText [52, UiText(`valuation_middle`)], 둘 다 stageName `valuation_middle`
- R-46 (점수와 표시 단계가 다른 날의 글)
  - Given `Assessment(BULL, 2026-11-23, WidgetValuation(33, LOW), WidgetValuation(65, HIGH))` / When toUiState / Then pricePosition의 scoreText [33, UiText(`valuation_price_low`)], stageName `valuation_price_low`, stage LOW. rateBurden의 scoreText [65, UiText(`valuation_rate_high`)], stageName `valuation_rate_high`, stage HIGH. 상태 어디에도 `valuation_notice_low`, `valuation_notice_high`가 없다
  - Given 규칙은 완충 규칙, 받아 둔 값은 완충 데이터(단위 1) / When state의 첫 null 아닌 값 / Then rateBurden의 scoreText [35, UiText(`valuation_rate_low`)], stageName `valuation_rate_low`, stage LOW
- R-30 (받아 둔 값을 바꾸면 매매 판단도 따라 바뀜)
  - Given 규칙은 완충 규칙, 받아 둔 값은 데이터 A로 state가 판정의 상태가 됨(금리 부담 scoreText [50, `valuation_middle`]) / When 받아 둔 값을 완충 데이터로 바꿈 / Then state의 rateBurden이 scoreText [35, `valuation_rate_low`], stage LOW로 바뀐다. 단계 이름 `stage_bull`과 날짜 [11, 23]은 그대로다
- R-11 (마지막 줄의 글)
  - 위 R-06의 첫 경우에서 asOfText가 `widget_as_of` [11, 23]이고, KeyGuide·NotReceived는 지금처럼 openAppHint가 "눌러서 앱 열기"다(있던 테스트로 확인한다)

**확인 기준**
- 테스트 통과, 컴파일 오류 없음

### 단위 3: 위젯 그리기

**만들거나 고칠 것**
- 수정 AppTypography, AppDimens (core/ui/theme)
- 수정 MoodWidgetLayout (feature/homewidget/presentation)
- 수정 SmallMoodWidget, LargeMoodWidget (feature/homewidget/presentation)
- 수정 미리 보기 레이아웃 `app/src/main/res/layout/widget_small_preview.xml`, `widget_large_preview.xml`

**지시**
- 위젯의 모양
  - 작은 위젯은 시안 `https://claude.ai/artifact/LuRSF8bSPDqruAbnUSfye9`의 "위젯 · 작은 위젯 (2×1)"(파일 `project/Widget-Small.dc.html`), 큰 위젯은 같은 시안의 "위젯 · 큰 위젯 (2×2)"(파일 `project/Widget-Square.dc.html`)과 같은 모양으로 만든다. 모양의 값(색, 크기, 간격)은 시안의 HTML에서 가져오되, 아래에서 테마에 더하고 고친 값을 쓴다. 문구와 동작은 이 지시의 글이 맞다. (R-13)
  - 시안 한 장에 그 위젯의 일곱 모습이 있다: 불장·중립·약세·물장인 날, 키가 없거나 틀릴 때, 받지 못했을 때, 새로 고침을 눌러 받는 중. 판정이 있는 다섯 모습(네 단계, 받는 중)에 매매 판단 두 줄이 있다. 키 안내와 받지 못함은 지금과 같다. (R-13)
  - 시안의 회색 바탕(#DDE1E7)과 위젯 아래의 설명 글("불장인 날" 등, #3A424D)은 시안을 읽기 위한 것이다. 위젯에 넣지 않는다. 시안의 날짜와 점수(74, 98, 58 등)는 예시다. (R-13)
  - 시안의 위젯 크기(172×84, 172×172)는 한 예다. 지금처럼 위젯은 받은 크기를 채우고, 여백, 글자 크기, 간격, 버튼 자리만 시안을 따른다.
- 공용 테마를 고치고 더한다. 1px = 1dp(글자는 1sp)로 옮긴다. 다른 화면이 쓰는 값은 바꾸지 않는다. (R-13)
  - AppTypography 고칠 것: `widgetLargeStage`를 48 Bold에서 40 Bold로. 이 값은 위젯만 쓴다.
  - AppTypography 더할 것: 매매 판단 줄의 이름 쪽 12 Normal, 단계 쪽 12 Bold. 두 위젯이 같다. 시안의 줄 높이(작은 위젯 1.3, 큰 위젯 1.35)는 Glance에서 정할 수 없어 옮기지 않는다.
  - AppDimens 더할 것: 작은 위젯의 단계 이름과 두 줄 묶음 사이 가로 10, 큰 위젯의 단계 이름과 두 줄 묶음 사이 세로 4, 줄 안의 이름 쪽과 단계 쪽 사이 3.
  - AppColors의 있는 값을 쓰는 곳: 이름 쪽 글 `textMuted` #4A5360, 단계 쪽 글 LOW `valuationLow` #0E7C6B, MIDDLE `valuationMiddle` #4A5360, HIGH `valuationHigh` #B45309. 색은 더하지 않는다.
- 매매 판단 줄 하나 (R-44, R-45, R-48, P-18)
  - MoodWidgetLayout에 두 위젯이 함께 쓰는 매매 판단 줄 하나를 그리는 조각을 더한다: 이름 쪽 글과 단계 쪽 글 두 개를 가로로 나란히 놓고, 사이를 3 띄운다. Glance의 글 하나에는 크기·굵기·색을 하나만 줄 수 있어 한 글로 그리지 않는다. 띄어쓰기를 글 끝에 넣어 띄우지 않는다(글 끝의 띄어쓰기가 너비에 드는지가 기기에 따라 다를 수 있다).
  - 이름 쪽: 12 Normal, `textMuted`. 단계의 색을 쓰지 않는다.
  - 단계 쪽: 12 Bold, 표시 단계의 색(LOW `valuationLow`, MIDDLE `valuationMiddle`, HIGH `valuationHigh`). 두 카드가 같다. 단계에서 색을 고르는 코드는 시장 분위기의 Presentation에서 가져오지 않고 위젯 쪽(MoodWidgetLayout, 지금의 `stageColor` 옆)에 둔다. (R-48, P-17)
  - 줄은 한 줄로 둔다. 넘쳐도 줄을 바꾸지 않는다(시안의 `nowrap`). Glance의 글에 줄 수를 1로 준다.
- 큰 위젯 (R-06, R-11, R-44, P-18)
  - 판정에서 가운데 칸(첫 줄과 마지막 줄 사이)에 단계 이름과 그 아래 두 줄 묶음을 한 묶음으로 놓고, 그 묶음을 칸의 세로 가운데, 왼쪽 정렬에 둔다(시안의 `space-between`과 그 가운데의 세로 묶음). 단계 이름과 두 줄 묶음 사이는 4, 두 줄 사이는 0이다.
  - 두 줄은 위에서 아래로 주가 위치, 금리 부담이다. 각 줄은 이름 쪽 `name`, 단계 쪽 `scoreText`다. 예: "주가 위치" + "35 · 보통".
  - 첫 줄과 마지막 줄, 키 안내와 받지 못함은 지금 그대로다.
- 작은 위젯 (R-06, R-10, R-45, P-18)
  - 판정은 세 줄 그대로다: 첫 줄("지금 장은"), 둘째 줄, 날짜 줄. 둘째 줄은 단계 이름과 그 오른쪽의 두 줄 묶음을 가로로 놓은 것이다. 단계 이름과 두 줄 묶음 사이는 10이고, 두 줄 묶음은 단계 이름과 세로 가운데를 맞춘다(시안의 `align-items: center`). 두 줄 사이는 0이다.
  - 두 줄은 위에서 아래로 주가 위치, 금리 부담이다. 각 줄은 이름 쪽 `shortName`, 단계 쪽 `stageName`이다. 예: "주가" + "보통". 점수는 적지 않는다.
  - 줄들이 위아래로 붙어 카드 세로 가운데에 모이는 것, 키 안내와 받지 못함은 지금 그대로다.
- 미리 보기 레이아웃: 시안의 첫 모습(불장인 날) 그대로 매매 판단 두 줄을 더한다. 보통 View로 그리고 값은 위 위젯과 같다. 예시 값은 단위 2에서 더한 문구를 쓴다. (R-02, P-13)
  - 큰 위젯: 단계 이름 "불장"을 40sp로 고치고, 그 아래 4dp 띄워 "주가 위치" + "74 · 비싼 편"(#B45309, 굵게), "금리 부담" + "98 · 높음"(#B45309, 굵게) 두 줄. 단계 이름과 두 줄을 한 묶음으로 가운데 칸의 세로 가운데에 둔다.
  - 작은 위젯: 단계 이름 "불장" 오른쪽에 10dp 띄워 "주가" + "비싼 편"(#B45309, 굵게), "금리" + "높음"(#B45309, 굵게) 두 줄. 단계 이름과 세로 가운데를 맞춘다.
  - 이름 쪽은 12sp #4A5360, 단계 쪽은 12sp 굵게, 사이 3dp, 줄을 바꾸지 않는다. 레이아웃에 적는 색과 크기가 어느 테마 값인지 지금처럼 주석으로 적는다. (P-12)
- 위젯은 판정, 형식 바꾸기, 문구 고르기를 하지 않는다. 단계 → 색 고르기와 UiText를 글자로 푸는 것만 한다. (P-17)

**테스트 지시**
- 없음 (Glance와 레이아웃이라 기기에서만 보인다. 수동 확인으로 본다. 있던 테스트와 단위 1·2의 테스트가 모두 통과해야 한다)

**확인 기준**
- 컴파일 오류 없음, 있던 테스트 통과

### 단위 4: 앱을 바꾼 때 받기

**만들거나 고칠 것**
- 수정 WidgetRefreshTrigger (feature/homewidget/domain)
- 수정 WidgetRefreshCoordinator (feature/homewidget/domain)
- 수정 WidgetRefreshCoordinatorTest (`app/src/test/java/com/hak/hakbanggul/feature/homewidget/domain/`)
- 수정 WidgetRefreshScheduler (feature/homewidget/presentation)
- 신규 AppUpdatedReceiver (feature/homewidget/presentation)
- 수정 `app/src/main/AndroidManifest.xml`

**지시**
- 새 용어의 영어 이름: 앱을 바꾼 때 `AppUpdated`. 처음 선언하는 곳 위에 `// 용어: (한국어 이름)` 주석을 단다.
- WidgetRefreshTrigger에 `APP_UPDATED`(앱을 바꿈)를 더한다. (R-47, P-19)
- WidgetRefreshCoordinator는 `APP_UPDATED`면 `RefreshCondition.WHEN_STALE`로 청하고, 결과를 쓰지 않는다. FIRST_PLACED와 같이 `isManualRefreshing`을 바꾸지 않는다. 다른 까닭의 동작은 그대로다. (R-35, R-47, P-19)

| 까닭 | 조건 | 결과를 쓰는 곳 |
|---|---|---|
| `FIRST_PLACED` | `WHEN_STALE` | 쓰지 않는다 (그대로) |
| `PERIODIC` | `WHEN_KEYS_AVAILABLE` | 쓰지 않는다 (그대로) |
| `MANUAL` | `WHEN_NOT_RECENTLY_STARTED` | 그대로 |
| `APP_UPDATED` | `WHEN_STALE` | 쓰지 않는다 |

- WidgetRefreshScheduler에 `suspend fun onAppUpdated()`를 더한다: 놓인 작은 위젯과 큰 위젯을 합쳐 세어 하나라도 있으면 APP_UPDATED 작업(한 번)을 넣는다. 없으면 아무것도 하지 않는다. (R-47, P-19)
  - 인터넷 연결 조건을 붙인다. 연결이 없으면 돌지 않고 연결이 돌아온 뒤에 돈다. (R-22)
  - 작업 이름은 FIRST_PLACED·MANUAL·주기 작업과 따로 둔다(서로 막지 않게). 같은 이름의 작업이 있으면 그대로 둔다. Glance가 쓰는 작업 이름과 겹치지 않게 지금처럼 앱의 이름을 앞에 붙인다.
  - Android 12 이상에서는 급한 작업으로 넣어 곧 돌게 한다. 한도를 넘으면 보통 작업으로 돈다(MANUAL과 같은 방식). 앱을 바꾼 직후 위젯에 "데이터를 받지 못했습니다"가 보이는 몇 초를 줄이려는 것이다.
  - 다른 함수는 고치지 않는다.
- AppUpdatedReceiver(BroadcastReceiver): 앱이 새 버전으로 바뀌었다는 시스템의 알림(`Intent.ACTION_MY_PACKAGE_REPLACED`)을 받아 `WidgetRefreshScheduler.onAppUpdated()`를 부른다. 다른 action이면 아무것도 하지 않는다. 세는 일이 suspend라 `goAsync()`로 알림을 붙잡아 둔 채 끝내고, 어떻게 끝나든 `finish()`한다. 취소가 아닌 예외는 밖으로 던지지 않는다. 의존성은 HomeWidgetEntryPoint의 있는 `widgetRefreshScheduler()`로 받는다(EntryPoint에 더할 함수는 없다). 지금의 위젯 Receiver의 `onLastRemoved`와 같은 방식이다. 여기서 받기를 청하고 기다리지 않는다. 청하는 것은 WidgetRefreshWorker가 넘겨받은 까닭으로 한다(WidgetRefreshWorker는 고치지 않는다). (R-47, P-07, P-11, P-19)
- 앱을 바꾼 직후 위젯을 지금 상태로 다시 그리는 것과 주기 작업이 이어지는 것은, 알림으로 프로세스가 시작되면서 HakBanGgulApp이 이미 한다. HakBanGgulApp은 고치지 않는다. 프로세스가 시작될 때마다 받기를 청하지 않는다(앱을 바꾼 때만 청한다). (R-24, R-47, P-06, P-08, P-19)
- `AndroidManifest.xml`: AppUpdatedReceiver를 적는다. `android.intent.action.MY_PACKAGE_REPLACED`를 받고, 밖으로 내놓지 않는다(`exported="false"`). 다른 곳은 그대로 둔다. (R-47, P-19)
- 앱을 바꾼 알림이 오지 않는 경우(앱을 강제 종료해 둔 채 바꾼 때 등)는 따로 막지 않는다. 그때는 앱을 열거나 새로 고침을 누르거나 다음 주기 작업에 받는다.

**테스트 지시**
상황은 지금의 WidgetRefreshCoordinatorTest와 같다(시계 2026-11-24T01:00:00Z, 두 출처의 키 AVAILABLE, 받아 둔 값 데이터 A, 성공한 때 1시간 전, 시작한 때 없음, 받기 한 번에 fetch 횟수 2).

WidgetRefreshCoordinator
- R-35, R-47 (APP_UPDATED의 조건)
  - Given 성공한 때가 1시간 전 / When request(APP_UPDATED) / Then fetch 횟수 0
  - Given 성공한 때가 6시간 1분 전 / When request(APP_UPDATED) / Then fetch 횟수 2
  - Given 받아 둔 값 없음 / When request(APP_UPDATED) / Then fetch 횟수 2
  - Given FRED의 키 MISSING, 받아 둔 값 없음 / When request(APP_UPDATED) / Then fetch 횟수 0
- R-47 (눌러 받는 중을 바꾸지 않음)
  - Given 받아 둔 값 없음, 받기를 붙잡아 둠 / When request(APP_UPDATED), 그 뒤 받기를 풀어 줌 / Then `isManualRefreshing`은 한 번도 참이 되지 않고, 풀어 준 뒤 request가 돌아온다

**확인 기준**
- 테스트 통과, 컴파일 오류 없음

## 3. 수동 확인

- 이 변경은 시장 분위기 버전업-002가 들어간 앱 위에 덮어 설치한다. R-47을 먼저 본다(설치하는 때에 보는 항목이라서).
- 날짜가 바뀌는 것으로 보는 항목은 새 종가가 나온 뒤에 본다. 미국 장이 하루 열리고 닫힌 뒤(한국 시각으로 다음 날 아침 이후)다. 주말과 미국 휴장일에는 날짜가 바뀌지 않는다.
- 위젯 시안: `https://claude.ai/artifact/LuRSF8bSPDqruAbnUSfye9`의 "위젯 · 작은 위젯 (2×1)", "위젯 · 큰 위젯 (2×2)".

- R-02: Given Android 12 이상 기기 / When 위젯을 고르는 목록에서 두 위젯을 본다 / Then 큰 위젯의 미리 보기는 "지금 장은", 빨간 "불장"(1.0.0보다 작음), 그 아래 "주가 위치 74 · 비싼 편", "금리 부담 98 · 높음"(숫자와 단계가 주황, 굵게), "11월 23일 종가", 오른쪽 위 새로 고침 아이콘이다. 작은 위젯의 미리 보기는 "지금 장은", 빨간 "불장"과 그 오른쪽의 "주가 비싼 편", "금리 높음"(단계가 주황, 굵게), "11월 23일 종가", 오른쪽 위 새로 고침 아이콘이다
- R-06 (화면 배치): Given 키가 모두 저장되고 받아 둔 값이 있음 / When 두 위젯을 본다 / Then 위에서 아래로 "지금 장은", 단계 이름(크게, 단계의 색)과 매매 판단 두 줄, "M월 D일 종가"다. 매매 판단 두 줄은 큰 위젯에서 단계 이름 아래, 작은 위젯에서 단계 이름 오른쪽에 있다
- R-10: Given 판정 / When 작은 위젯을 본다 / Then 판정이 세 줄("지금 장은", 단계 이름과 그 오른쪽의 매매 판단 두 줄, 날짜)이고 위아래로 붙어 카드 세로 가운데에 놓인다. 두 줄 묶음이 단계 이름과 세로 가운데가 맞다
- R-11 (화면 배치): Given 판정 / When 큰 위젯을 본다 / Then "지금 장은"이 위쪽, 단계 이름과 그 아래의 매매 판단 두 줄이 한 묶음으로 가운데, 날짜가 아래쪽이다. 키 안내와 받지 못함은 전과 같다(작은 위젯에 "눌러서 앱 열기"가 없다)
- R-13: Given 그날의 단계(하나)와 새로 고침을 눌러 받는 중의 모습 / When 각 모습에서 두 위젯을 시안 "위젯 · 작은 위젯 (2×1)", "위젯 · 큰 위젯 (2×2)"의 같은 단계 모습과 나란히 놓고 본다 / Then 배치, 간격, 크기, 색이 같다. 매매 판단 줄의 글자 크기, 이름과 단계 사이 띄움, 단계 이름과 두 줄 사이 띄움을 함께 본다. 점수와 단계는 그날의 값이라 시안과 다를 수 있다. 작은 위젯에서 매매 판단 줄의 끝이 잘리지 않는지도 본다(잘리면 알린다)
- R-44: Given 판정 / When 큰 위젯을 시안과 나란히 본다 / Then 두 줄이 위에서 아래로 "주가 위치 N · 단계", "금리 부담 N · 단계"이고, 점수와 단계는 굵은 글자에 단계의 색, 이름("주가 위치", "금리 부담")은 보통 굵기의 회색이다
- R-45: Given 판정 / When 작은 위젯을 시안과 나란히 본다 / Then 두 줄이 위에서 아래로 "주가 단계", "금리 단계"이고 점수가 없다. 단계는 굵은 글자에 단계의 색, 줄인 이름("주가", "금리")은 보통 굵기의 회색이다
- R-46 (앱과 같음): Given 위젯이 판정을 보여 줌 / When 앱을 열어 주가 위치 카드와 금리 부담 카드를 본다 / Then 큰 위젯의 점수와 단계가 두 카드의 점수와 단계와 같고, 작은 위젯의 단계가 두 카드의 단계와 같다. 앱에 "점수는 …이지만 아직 …입니다" 줄이나 금리 값의 날짜가 보이는 날에도 위젯에는 그런 줄이 없다
- R-47: Given 시장 분위기 버전업-002의 앱이 설치되어 있고 두 위젯이 놓여 있음, 마지막으로 받은 뒤 6시간이 넘고 새 종가가 나옴, 앱을 강제 종료하지 않음 / When 이 변경의 앱을 Android Studio로 덮어 설치하고, 앱을 열지 않고 홈 화면으로 가서 잠시 기다린다 / Then 위젯이 매매 판단 두 줄이 있는 판정을 보여 주고, 날짜가 새 종가 날짜로 바뀐다(받기 전 몇 초 동안 "데이터를 받지 못했습니다"가 보일 수 있다). 앱을 열지 않은 채 다음 날 날짜가 또 새 종가 날짜로 바뀌어 있다(뒤에서 받는 일이 이어짐)
- R-48: Given 위젯이 판정을 보여 줌 / When 위젯과 앱의 주가 위치·금리 부담 카드를 나란히 본다 / Then 매매 판단 단계의 색이 같다(싼 편·낮음 청록, 보통 회색, 비싼 편·높음 주황). 그날 보이지 않는 단계는 시안으로 색만 본다
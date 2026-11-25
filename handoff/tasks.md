# Tasks: 홈 화면 위젯 신규 개발

기능: 홈 화면 위젯
변경: 신규 개발 (CS-003)
기준 Plan: 1.0.0
상태: 구현 중

| 전달 | 날짜 | 이유 |
|---|---|---|
| 1 | 2026-11-25 | 최초 전달 |

## 1. 개요
홈 화면 위젯은 앱을 열지 않고 홈 화면에서 지금 장세와 기준 날짜를 보여 주고, 위젯이 놓여 있는 동안 앱을 열지 않아도 값이 새로 받아지게 하는 기능이다. 이번에 처음 만든다: 작은 위젯(2×1)과 큰 위젯(2×2), 오른쪽 위의 새로 고침 버튼, 약 6시간마다 뒤에서 받기, 위젯을 누르면 앱의 시장 분위기 화면 열기.
위젯은 값을 갖지 않고 따로 계산하지 않는다. 판정과 받는 중인지는 시장 분위기가, 키의 상태는 설정이 내주는 것을 쓰고, 데이터는 시장 분위기에 받아 달라고 청하기만 한다. 무엇을 보여 줄지는 Domain의 UseCase 하나가 정하고, 위젯은 그 결과를 그리기만 한다.
시장 분위기와 설정의 코드(`feature/marketmood`, `feature/settings`)는 고치지 않는다. 시장 분위기에서 쓰는 것은 `ObserveMoodUseCase`, `RequestRefreshUseCase`, `ObserveRefreshStateUseCase`와 그 모델(MoodAssessment, MarketStage, RefreshState, RefreshCondition, RefreshRequestResult)뿐이다. MarketDataRefresher, RefreshMarketDataUseCase, MarketDataRepository, ObserveSourceKeysUseCase는 쓰지 않는다(테스트에서 진짜 UseCase를 만들 때만 쓴다). `core`와 앱 맨 위 패키지는 아래 지시대로 더하기만 하고, 있는 값과 함수, 두 화면의 모양과 동작은 바꾸지 않는다.

## 2. 구현 단위

### 단위 1: 빌드 설정과 공용 준비물

**만들거나 고칠 것**
- 수정 `gradle/libs.versions.toml`
- 수정 `app/build.gradle.kts`
- 수정 AppColors, AppTypography, AppDimens (core/ui/theme)
- 수정 UiText (core/ui)
- 신규 AppLaunchIntents (core/ui)
- 수정 `app/src/main/res/values/strings.xml`
- 신규 카드 바탕, 새로 고침 아이콘, 진행 표시의 그림 리소스 (`app/src/main/res/drawable/`. 파일 이름은 CLI가 정한다)

**지시**
- 새 용어의 영어 이름: 홈 화면 위젯 `HomeWidget`, 작은 위젯 `SmallMoodWidget`, 큰 위젯 `LargeMoodWidget`, 키 안내 `KeyGuide`, 받지 못함 `NotReceived`. 기능 패키지는 `feature/homewidget`이다. 처음 선언하는 곳 위에 `// 용어: (한국어 이름)` 주석을 단다. 단계 `MarketStage`, 기준 날짜 `asOfDate`, 표시 단계 `displayedStage`, 출처 `DataSource`, 받아 달라는 청 `RefreshRequest`는 있는 이름을 쓴다.
- 라이브러리를 더한다: Glance의 앱 위젯 `androidx.glance:glance-appwidget` 1.2.0, WorkManager `androidx.work:work-runtime-ktx` 2.12.0. 2026-11-25 기준의 안정 버전이다. 저장소의 다른 라이브러리나 빌드 도구(AGP 9.5.2, Kotlin 2.4.21, compileSdk 37.1)와 맞지 않으면 맞는 버전을 골라 decisions.md에 DECISION으로 남긴다. Hilt의 Worker 지원(`hilt-work`)은 들이지 않는다. (P-11)
- 공용 테마에 위젯의 값을 더한다. 값은 시안(단위 4의 시안 주소)에서 뽑은 것이고, 1px = 1dp(글자는 1sp)로 옮긴다. 있는 값은 바꾸지 않고, 위젯도 있는 값을 쓸 수 있는 곳은 있는 값을 쓴다. Glance는 Compose의 TextStyle, Color를 그대로 받지 못하므로, 위젯 쪽에서 값을 꺼내 Glance의 것으로 바꿔 쓴다. 어떻게 바꿀지는 CLI가 정한다. (R-13, P-12)
  - AppColors에 더할 것: 비활성 새로 고침 아이콘의 색 `#B7BEC8` (값이 `gaugeNeutral`과 같지만 쓰는 곳이 달라 따로 둔다)
  - AppColors의 있는 값을 쓰는 곳: 카드 바탕 `card` #FFFFFF, 카드 테두리 `border` #E3E6EA, 첫 줄·날짜 줄·"눌러서 앱 열기"·누를 수 있는 새로 고침 아이콘 `textMuted` #4A5360, 안내 문구 `text` #1B1F24, 단계 이름 `stageBull` #C5221F · `stageNeutral` #4A5360 · `stageWeak` #3D7BD9 · `stageBear` #1D4AA8, 진행 표시의 바탕 고리 `border` #E3E6EA, 도는 부분 `accent` #2457C5
  - AppTypography에 더할 것(크기, 굵기)
    - 작은 위젯: 첫 줄과 날짜 줄 12 Normal, 단계 이름 28 Bold, 안내 문구 14 Bold
    - 큰 위젯: 첫 줄 13 Normal, 단계 이름 48 Bold, 안내 문구 17 Bold, 마지막 줄(날짜, "눌러서 앱 열기") 12 Normal
    - 시안의 줄 높이(1.2~1.4)는 Glance에서 정할 수 없어 옮기지 않는다. 글꼴은 기기의 기본 글꼴이다(시안의 Noto Sans KR가 아니다). (P-12)
  - AppDimens에 더할 것
    - 카드: 테두리 1, 모서리 반지름 16 (있는 `cardBorder` 1과 `AppShapes.card` 16과 같은 값이다. 그림 리소스에 적을 때 이 값을 쓴다)
    - 작은 위젯: 안쪽 여백 위아래 8, 좌우 14. 키 안내와 받지 못함에서 첫 줄과 안내 문구 사이 2 (판정의 세 줄 사이는 0)
    - 큰 위젯: 안쪽 여백 16 (네 쪽 모두)
    - 작은 위젯의 버튼 자리: 가로 40, 세로 34, 카드 위쪽 끝에서 0, 오른쪽 끝에서 2. 아이콘 그림 칸 16
    - 큰 위젯의 버튼 자리: 가로 44, 세로 44, 카드 위쪽 끝에서 2, 오른쪽 끝에서 2. 아이콘 그림 칸 18
    - 진행 표시의 그림 칸: 작은 위젯 16, 큰 위젯 18 (아이콘과 같은 칸에 놓인다)
- 아이콘과 진행 표시는 시안에서 24×24 좌표의 그림을 그림 칸 크기로 줄여 그린 것이다. 그림 리소스도 24×24 좌표로 그리고 그림 칸 크기로 놓는다. 실제로 그려지는 크기는 아래와 같다. 값을 옮길 때 그림 칸 크기와 그려지는 크기를 섞지 않는다. (R-13, R-37, R-39, P-12)
  - 새로 고침 아이콘: 경로 `M20 12a8 8 0 1 1-2.6-5.9`, `M20 4v5h-5`, 선 굵기 2, 끝과 꺾이는 곳 둥글게 (`AppIcons.Refresh`와 같다). 그려지는 선 굵기는 작은 위젯 약 1.33, 큰 위젯 1.5다. 색은 놓는 곳에서 정한다(누를 수 있음 #4A5360, 비활성 #B7BEC8).
  - 진행 표시: 중심 (12, 12) 반지름 9인 바탕 고리(#E3E6EA)와, 그 위에서 12시 방향부터 시계 방향으로 3시 방향까지의 사분원 `M12 3a9 9 0 0 1 9 9`(#2457C5, 끝 둥글게), 선 굵기 2.5. 사분원이 고리를 따라 계속 돈다. 그려지는 고리의 바깥 지름(선 굵기까지 넣은 크기)과 굵기는 작은 위젯 약 13.7과 1.7, 큰 위젯 약 15.4와 1.9다.
  - 카드 바탕: 흰 바탕 #FFFFFF, 테두리 1 #E3E6EA, 모서리 16. 그림 리소스 하나로 깐다(Glance에는 테두리가 없고, 모서리를 깎는 것은 Android 12부터 된다).
  - 그림 리소스에 적는 색과 크기는 공용 테마와 같은 값이다. 어느 테마 값인지 주석으로 적는다. (P-12)
- UiText에 `fun UiText.asString(context: Context): String`을 더한다. 있는 `asString()`(Compose용)과 같이 args 안의 UiText를 먼저 풀어 끼운다. 있는 함수는 바꾸지 않는다. Glance에서는 Compose용을 쓸 수 없어서다.
- AppLaunchIntents는 위젯이 앱을 열 때 쓰는 Intent를 만들고 읽는 곳이다. 위젯(기능)이 MainActivity(앱 맨 위)를 직접 가리키지 않고 둘 다 `core`만 보게 한다. (R-14, R-15, P-14)
  - `fun marketMood(context: Context): Intent`: 이 앱을 여는 Intent에 "시장 분위기 화면을 보이라"는 표시를 단다. MainActivity 클래스를 이름으로 가리키지 않는다(예: 패키지의 실행 Intent를 쓴다). 앱이 이미 열려 있으면 새 MainActivity를 만들지 않고 있는 것이 이 Intent를 받게 하는 플래그를 단다.
  - `fun showsMarketMood(intent: Intent?): Boolean`: 받은 Intent에 그 표시가 있는지.
- `strings.xml`에 위젯 문구를 더한다. (R-01, R-06, R-11, R-26, R-02)
  - 위젯의 이름: "장세 (작게)", "장세 (크게)"
  - 기준 날짜: "%1$d월 %2$d일 종가" (있는 `mood_as_of`는 "종가 기준 (미국)"까지 있어 쓰지 않는다)
  - "눌러서 앱 열기"
  - 진행 표시의 설명: "받는 중"
  - 미리 보기의 기준 날짜(예시): "11월 23일 종가"
  - 있는 문구를 쓰는 것: "지금 장은" `mood_now`, 단계 이름 `stage_bull`·`stage_neutral`·`stage_weak`·`stage_bear`, "학반꿀" `app_name`, "키를 넣어 주세요" `key_guide_title`, "데이터를 받지 못했습니다" `mood_failed_title`, 새로 고침 버튼의 설명 `action_refresh`

**테스트 지시**
- 없음 (이 단위는 Android에 기대는 것뿐이다. 있던 테스트가 모두 통과해야 한다)

**확인 기준**
- 컴파일 오류 없음, 있던 테스트 통과

### 단위 2: 위젯에 보일 것 고르기와 받아 달라는 청 (Domain)

**만들거나 고칠 것**
- 신규 WidgetContent (feature/homewidget/domain)
- 신규 WidgetRefreshButton (feature/homewidget/domain)
- 신규 WidgetState (feature/homewidget/domain)
- 신규 WidgetRefreshTrigger (feature/homewidget/domain)
- 신규 WidgetRefreshCoordinator (feature/homewidget/domain)
- 신규 ObserveWidgetStateUseCase (feature/homewidget/domain)
- 신규 위 둘의 테스트 (`app/src/test/java/com/hak/hakbanggul/feature/homewidget/domain/`)

**지시**
- 이 단위는 순수 Kotlin이다. Android와 Glance, WorkManager를 가져오지 않는다. (P-02)
- WidgetContent는 위젯이 보여 주는 것 셋이다(봉인 인터페이스): `Assessment(stage: MarketStage, asOfDate: LocalDate)` / `KeyGuide` / `NotReceived`. (R-05)
- WidgetRefreshButton은 새로 고침 버튼 자리의 상태다(열거형): `ENABLED`(누를 수 있음) / `DISABLED`(비활성) / `IN_PROGRESS`(진행 표시). (R-37, R-39)
- WidgetState는 위젯 하나에 보일 것이다: `WidgetState(content: WidgetContent, refreshButton: WidgetRefreshButton)`.
- WidgetRefreshTrigger는 받아 달라고 청하게 된 까닭이다(열거형): `FIRST_PLACED`(첫 위젯을 놓음) / `PERIODIC`(약 6시간마다) / `MANUAL`(새로 고침을 누름).
- WidgetRefreshCoordinator(앱 전체에 하나, `@Singleton`, `@Inject constructor(requestRefresh: RequestRefreshUseCase)`) (P-09, P-10)
  - `val isManualRefreshing: StateFlow<Boolean>`: 새로 고침을 눌러 받는 중인지. 처음은 거짓. 메모리에만 두고 저장하지 않는다. 저장하면 받는 도중에 앱이 꺼졌을 때 참인 채로 남는다. (R-39, R-43, P-10)
  - `suspend fun request(trigger: WidgetRefreshTrigger)`: 까닭에 맞는 조건으로 RequestRefreshUseCase를 부르고, 그것이 돌아올 때까지 기다린 뒤 돌아온다. (R-20, R-21, R-34, R-35, R-38)

| 까닭 | 조건 | 결과를 쓰는 곳 |
|---|---|---|
| `FIRST_PLACED` | `WHEN_STALE` | 쓰지 않는다 |
| `PERIODIC` | `WHEN_KEYS_AVAILABLE` | 쓰지 않는다 |
| `MANUAL` | `WHEN_NOT_RECENTLY_STARTED` | `SKIPPED`면 1초 기다린 뒤 끝낸다. `REFRESHED`, `JOINED`면 돌아온 때 바로 끝낸다 |

- 조건(6시간, 1분, 키)은 따지지 않는다. 시장 분위기가 따진다. 이미 받는 중이면 겹쳐 시작하지 않는 것도 시장 분위기가 한다. (R-34, R-35, P-10)
- MANUAL이면: `isManualRefreshing`이 이미 참이면 아무것도 하지 않고 곧바로 돌아온다(청하지 않는다). 거짓이면 참으로 바꾸고 청한다. 보고 바꾸는 것은 한 번에 한다(두 MANUAL이 함께 와도 하나만 청한다). 끝날 때 거짓으로 되돌린다: 받지 않았으면(`SKIPPED`) 1초 뒤, 그 밖은 돌아온 때 바로. (R-39, R-40, R-41, R-42)
- 어떻게 끝나든(받기 실패, 예외, 취소, 1초를 기다리는 중의 취소) `isManualRefreshing`을 거짓으로 되돌린다. (R-43, P-09)
- FIRST_PLACED와 PERIODIC은 `isManualRefreshing`을 바꾸지 않는다.
- 취소가 아닌 예외는 밖으로 던지지 않는다. 취소는 그대로 전한다.
- ObserveWidgetStateUseCase(`@Inject constructor(observeMood: ObserveMoodUseCase, observeRefreshState: ObserveRefreshStateUseCase, apiKeyProvider: ApiKeyProvider, coordinator: WidgetRefreshCoordinator)`): `operator fun invoke(): Flow<WidgetState>`. 판정, 받는 중인지(`RefreshState.isRefreshing`만 쓴다), 출처마다의 키 쓸 수 있음 여부, `isManualRefreshing`을 합쳐 WidgetState를 내준다. (R-05, R-19, R-31, R-33, R-36, P-02, P-03, P-04)
  - 키는 `ApiKeyProvider.observeAvailability(출처)`를 `DataSource.entries` 모두에 대해 받는다. 시장 분위기의 ObserveSourceKeysUseCase는 쓰지 않는다. (R-33, P-03)
  - 표에서 고르는 것(위 행이 먼저): 출처 중 하나라도 `MISSING`이나 `INVALID` → `KeyGuide`. 판정이 있음(null이 아님) → `Assessment(displayedStage, asOfDate)`. 그 밖 → `NotReceived`. 판정의 다른 값(종합 점수, 계산 단계, 묶음)은 쓰지 않는다. (R-05, R-07, R-25, R-31)
  - 그대로 두기: 받는 중(`isRefreshing` 참)이고 판정이 null이면, 표대로 고르지 않고 전에 내준 content를 그대로 둔다. 처음 내주는 값은 표대로다(받는 중이어도). (R-16, R-19, P-04)
  - 받지 못함을 미루기: content가 `NotReceived`가 아니던 것에서 `NotReceived`로 바뀌는 것은 0.5초 뒤에도 여전히 `NotReceived`일 때에만 내준다. 그 사이에 다른 것으로 바뀌면 `NotReceived`는 내주지 않는다. 처음 내주는 값이 `NotReceived`면 기다리지 않는다. 키가 저장됐다는 것과 받기가 시작됐다는 것, 받기가 끝났다는 것과 새 판정이 나왔다는 것이 서로 다른 Flow로 와서 순서가 정해져 있지 않기 때문이다. (R-17, R-18, R-19, P-04)
  - 버튼(위가 먼저): `isManualRefreshing` 참 → `IN_PROGRESS`. 내주는 content가 `KeyGuide` → `DISABLED`. 그 밖 → `ENABLED`. 버튼은 그대로 두기와 미루기를 거치지 않고 바로 바뀐다. (R-37, R-39, R-40)
  - 판정의 Flow에서 예외가 나면 판정이 null인 것으로 보고, 다른 입력(키, 받는 중, 눌러 받는 중)의 변화에는 계속 반응한다. 내주는 Flow가 예외로 끝나지 않게 한다. (R-29, P-02)
  - 같은 WidgetState를 잇달아 내주지 않는다.
  - 받기가 실패해도 판정이 그대로면 content는 그대로다. 실패를 알리는 값은 두지 않는다(`lastFailure`는 쓰지 않는다). (R-27, R-42)
  - 위젯은 값을 저장하지 않는다. 매번 시장 분위기와 설정이 내주는 값으로 만든다. (R-30)

**테스트 지시**
아래에서 따로 적지 않으면 상황은 이렇다: 진짜 ObserveMoodUseCase, RequestRefreshUseCase, ObserveRefreshStateUseCase를 시장 분위기의 테스트가 쓰는 가짜(`FakeMarketDataRepository`, `FakeMoodRulesRepository`, `FakeClock`, `MoodTestData`)와 `FakeApiKeyProvider`로 만들어 끼운다(받기 관리자는 `MarketMoodExposedUseCaseTest`처럼 만든다). 시계 2026-11-24T01:00:00Z, 두 출처의 키 AVAILABLE, 받아 둔 값은 데이터 A(`MoodTestData.dataA()`)이고 성공한 때 2026-11-24T00:00:00Z(1시간 전), 시작한 때 없음, 출처가 돌려주는 값도 데이터 A. 데이터 A의 판정은 표시 단계 BULL, 기준 날짜 2026-11-23이다. "받기를 붙잡아 둔다"는 가짜 저장소의 fetch가 테스트가 풀어 줄 때까지 돌아오지 않게 하는 것이고, "fetch 횟수"는 받기 한 번에 2다. 시간은 가상 시간이다. 가짜가 모자라면(예: 예외를 던지는 가짜) CLI가 더 만든다.

WidgetRefreshCoordinator
- R-20 (FIRST_PLACED의 조건)
  - Given 성공한 때가 1시간 전 / When request(FIRST_PLACED) / Then fetch 횟수 0
  - Given 성공한 때가 6시간 1분 전 / When request(FIRST_PLACED) / Then fetch 횟수 2
  - Given 받아 둔 값 없음 / When request(FIRST_PLACED) / Then fetch 횟수 2
- R-21 (PERIODIC의 조건)
  - Given 성공한 때가 1분 전, 시작한 때가 10초 전 / When request(PERIODIC) / Then fetch 횟수 2
  - Given FRED의 키 MISSING / When request(PERIODIC) / Then fetch 횟수 0
- R-35, R-38 (MANUAL의 조건)
  - Given 시작한 때가 1분 1초 전 / When request(MANUAL) / Then fetch 횟수 2
  - Given 시작한 때가 꼭 1분 전 / When request(MANUAL) / Then fetch 횟수 0
  - Given 시작한 때 없음 / When request(MANUAL) / Then fetch 횟수 2
- R-34 (청하기만 하고, 앱에서 받는 중이면 겹치지 않음)
  - Given 받아 둔 값 없음, 받기를 붙잡아 두고 받기 관리자의 `refresh()`로 받기를 시작함(fetch 횟수 2) / When request(PERIODIC)을 부르고 그 뒤 받기를 풀어 줌 / Then 풀어 주기 전에는 request가 돌아오지 않고, 풀어 준 뒤 돌아온다. fetch 횟수는 2 그대로이고, 저장은 한 번 불렸고, 받아 둔 값에 시리즈 여섯이 있다
- R-39 (눌러 받는 중, 겹쳐 시작하지 않음, 이미 받는 중이면 그 받기가 끝날 때까지)
  - Given 받기를 붙잡아 둠 / When 따로 띄운 코루틴에서 request(MANUAL), 그 뒤 받기를 풀어 줌 / Then 풀어 주기 전에는 `isManualRefreshing` 참이고 request가 돌아오지 않았다. 풀어 준 뒤 request가 돌아오고 `isManualRefreshing` 거짓
  - Given 받기를 붙잡아 두고 request(MANUAL)이 기다리는 중 / When 다른 코루틴에서 request(MANUAL)을 한 번 더 / Then 둘째 request는 풀어 주기 전에 곧바로 돌아오고, fetch 횟수는 2 그대로, `isManualRefreshing`은 참 그대로다. 받기를 풀어 주면 거짓
  - Given 받기를 붙잡아 두고 받기 관리자의 `refresh()`로 받기를 시작함(앱에서 받는 중) / When request(MANUAL), 그 뒤 받기를 풀어 줌 / Then 풀어 주기 전에는 `isManualRefreshing` 참, fetch 횟수 2 그대로. 풀어 준 뒤 request가 돌아오고 `isManualRefreshing` 거짓
  - Given 받기를 붙잡아 둠 / When request(PERIODIC), 그 뒤 받기를 풀어 줌 / Then `isManualRefreshing`은 한 번도 참이 되지 않는다. FIRST_PLACED도 같다(받아 둔 값 없음으로 해서 받게 한다)
- R-40
  - Given request(MANUAL)로 받기를 마침, 출처가 데이터 A를 돌려줌 / When request가 돌아온 때 / Then `isManualRefreshing` 거짓
- R-41 (받지 않았으면 1초 뒤에 풂)
  - Given 시작한 때가 30초 전 / When request(MANUAL) / Then fetch 횟수 0. 부른 때부터 999ms에는 `isManualRefreshing` 참이고 request가 돌아오지 않았다. 1000ms에 거짓이 되고 request가 돌아온다
  - Given Twelve Data의 키 MISSING / When request(MANUAL) / Then fetch 횟수 0. 999ms에 참, 1000ms에 거짓
- R-42 (실패해도 풀림)
  - Given 두 출처가 NO_CONNECTION으로 답함 / When request(MANUAL) / Then 예외 없이 돌아오고, 돌아온 때 `isManualRefreshing` 거짓이다(1초를 더 기다리지 않는다)
- R-43 (예외나 취소로 끝나도 풀림)
  - Given 받아 달라는 청이 예외(IllegalStateException)로 끝나게 함(예: 키의 Flow가 예외를 던지는 가짜 ApiKeyProvider) / When request(MANUAL) / Then 예외가 밖으로 나오지 않고, `isManualRefreshing` 거짓
  - Given 받기를 붙잡아 두고 따로 띄운 코루틴에서 request(MANUAL) / When 그 코루틴을 취소함 / Then `isManualRefreshing` 거짓. 그 뒤 받기를 풀어 주면 받기는 끝까지 이어져 저장된다
  - Given 시작한 때가 30초 전, 따로 띄운 코루틴에서 request(MANUAL) / When 500ms에 그 코루틴을 취소함 / Then `isManualRefreshing` 거짓

ObserveWidgetStateUseCase
- R-05, R-07, R-31, R-33, R-37 (표에서 고르기와 버튼)
  - Given 기본 상황 / When 첫 값 / Then `WidgetState(Assessment(BULL, 2026-11-23), ENABLED)`
  - Given FRED의 키 MISSING, 받아 둔 값은 데이터 A / When 첫 값 / Then `WidgetState(KeyGuide, DISABLED)`
  - Given Twelve Data의 키 INVALID, 받아 둔 값은 데이터 A / When 첫 값 / Then `WidgetState(KeyGuide, DISABLED)`
  - Given 두 출처의 키 MISSING, 받아 둔 값 없음 / When 첫 값 / Then `WidgetState(KeyGuide, DISABLED)`
  - Given 받아 둔 값 없음 / When 첫 값 / Then `WidgetState(NotReceived, ENABLED)`
  - Given 받아 둔 값이 데이터 A에서 SPY가 통째로 빠짐 / When 첫 값 / Then `WidgetState(NotReceived, ENABLED)`
- R-16 (처음 값은 기다리지 않음)
  - Given 받아 둔 값 없음 / When 모으기 시작 / Then 가상 시간이 흐르기 전(0ms)에 `NotReceived`가 나온다
  - Given 받아 둔 값 없음, 받기를 붙잡아 두고 `refresh()`로 받기를 시작함 / When 모으기 시작 / Then 첫 값은 `NotReceived`다(받는 중이어도 처음 값은 표대로)
- R-25 (표시 단계와 기준 날짜)
  - Given 받아 둔 값이 데이터 A에서 2026-11-23 하루만 나쁜 날(`badDay("2026-11-23")`. 그날의 계산 단계는 NEUTRAL) / When 첫 값 / Then `Assessment(BULL, 2026-11-23)`
- R-17, R-30, R-36 (판정이 바뀌면 상태가 바뀜)
  - Given 받아 둔 값 없음으로 모으기 시작해 `NotReceived`가 나옴 / When 받아 둔 값을 데이터 A로 바꿈(받는 중 아님) / Then `Assessment(BULL, 2026-11-23)`이 이어서 나온다
  - Given 기본 상황으로 모으기 시작 / When 받아 둔 값을 데이터 A에서 2026-11-19, 2026-11-20, 2026-11-23이 나쁜 날로 바꿈 / Then `Assessment(NEUTRAL, 2026-11-23)`이 이어서 나온다
- R-18, R-28, R-36 (키의 상태가 바뀌면 상태가 바뀜)
  - Given 기본 상황으로 모으기 시작 / When FRED의 키를 MISSING으로, 그 뒤 AVAILABLE로 / Then 차례로 `KeyGuide`(DISABLED), `Assessment(BULL, 2026-11-23)`(ENABLED). AVAILABLE로 돌아온 뒤 받기 관리자가 받기를 시작해도 판정이 있으므로 그대로 `Assessment`다
  - Given 기본 상황으로 모으기 시작 / When Twelve Data의 키를 INVALID로 / Then `KeyGuide`, DISABLED
- R-19 (값이 없는데 받는 중이면 그대로 두기)
  - Given 받아 둔 값 없음, FRED의 키 MISSING으로 모으기 시작해 `KeyGuide`가 나옴, 받기를 붙잡아 둠 / When FRED의 키를 AVAILABLE로 바꾸고(받기 관리자가 받기를 시작한다) 2초 기다린 뒤 받기를 풀어 줌 / Then 풀어 주기 전까지 나온 content는 `KeyGuide` 하나뿐이다. 풀어 준 뒤 `Assessment(BULL, 2026-11-23)`가 나온다. `NotReceived`는 한 번도 나오지 않는다
  - Given 위와 같고, 출처가 NO_CONNECTION으로 답함 / When 받기를 풀어 줌 / Then 풀어 준 때부터 499ms까지는 `KeyGuide` 그대로이고, 500ms에 `NotReceived`가 나온다
  - Given 받아 둔 값 없음으로 모으기 시작해 `NotReceived`가 나옴, 받기를 붙잡아 두고 RequestRefreshUseCase(WHEN_KEYS_AVAILABLE)로 받기를 시작함 / When 받는 중에 FRED의 키를 MISSING으로 바꾸고, 그 뒤 받기를 풀어 줌(출처는 데이터 A를 돌려줌) / Then 풀어 주기 전에는 content가 `NotReceived` 그대로다. 풀어 준 뒤 `KeyGuide`가 나온다
- R-19 (받지 못함으로 바뀌는 것을 미루기)
  - Given 기본 상황으로 모으기 시작해 `Assessment`가 나옴 / When 받아 둔 값을 없음으로 바꿈(받는 중 아님) / Then 바꾼 때부터 499ms까지는 `Assessment` 그대로이고, 500ms에 `NotReceived`가 나온다
  - Given 위와 같음 / When 받아 둔 값을 없음으로 바꾸고 300ms 뒤 데이터 A로 되돌림 / Then `NotReceived`는 한 번도 나오지 않는다
- R-27 (실패해도 상태가 바뀌지 않음)
  - Given 기본 상황으로 모으기 시작, 출처가 NO_CONNECTION으로 답함 / When RequestRefreshUseCase(WHEN_KEYS_AVAILABLE)로 받고, 끝난 뒤 1초 기다림 / Then 나온 content는 모두 `Assessment(BULL, 2026-11-23)`이다
- R-29 (판정을 읽다 예외가 나면 값이 없는 것으로)
  - Given 판정의 Flow가 예외(IllegalStateException)를 던짐(예: 규칙의 Flow가 예외를 던지는 가짜 규칙 저장소) / When 모으기 시작 / Then 예외 없이 `NotReceived`(ENABLED)가 나온다
  - Given 위와 같이 `NotReceived`가 나옴 / When FRED의 키를 MISSING으로 / Then `KeyGuide`가 이어서 나온다
- R-39, R-40 (버튼의 상태)
  - Given 기본 상황으로 모으기 시작, 받기를 붙잡아 둠 / When 따로 띄운 코루틴에서 coordinator.request(MANUAL), 그 뒤 받기를 풀어 줌 / Then 풀어 주기 전에는 `WidgetState(Assessment(BULL, 2026-11-23), IN_PROGRESS)`. 풀어 준 뒤 `WidgetState(Assessment(BULL, 2026-11-23), ENABLED)`
  - Given 받아 둔 값 없음으로 모으기 시작해 `NotReceived`가 나옴, 받기를 붙잡아 둠 / When coordinator.request(MANUAL), 2초 뒤 받기를 풀어 줌(출처는 데이터 A) / Then 풀어 주기 전에는 `WidgetState(NotReceived, IN_PROGRESS)`. 풀어 준 뒤 `WidgetState(Assessment(BULL, 2026-11-23), ENABLED)`
  - Given 기본 상황, 시작한 때가 30초 전 / When coordinator.request(MANUAL) / Then 버튼이 `IN_PROGRESS`가 됐다가 1초 뒤 `ENABLED`로 돌아오고, content는 내내 `Assessment(BULL, 2026-11-23)`

**확인 기준**
- 테스트 통과, 컴파일 오류 없음

### 단위 3: 위젯의 상태 만들기

**만들거나 고칠 것**
- 신규 HomeWidgetUiState (feature/homewidget/presentation)
- 신규 HomeWidgetStateHolder (feature/homewidget/presentation)
- 신규 HomeWidgetStateHolder의 테스트 (`app/src/test/java/com/hak/hakbanggul/feature/homewidget/presentation/`)

**지시**
- HomeWidgetUiState는 위젯에 그릴 것이다. 위젯은 이것만 보고 그린다. (R-06, R-07, R-08, R-11)
  - `HomeWidgetUiState(title: UiText, main: WidgetMain, refreshButton: WidgetRefreshButton)`
  - `WidgetMain`(봉인 인터페이스): `Stage(stage: MarketStage, name: UiText, asOfText: UiText)` / `Message(text: UiText, openAppHint: UiText)`
  - `stage`는 위젯이 단계의 색을 고르는 데 쓴다.
  - `openAppHint`는 큰 위젯만 그린다. 작은 위젯은 그리지 않는다. (R-11)
- WidgetState에서 HomeWidgetUiState로 바꾸는 것 (R-06, R-07, R-08, R-11, R-26)

| content | title | main |
|---|---|---|
| `Assessment(stage, asOfDate)` | "지금 장은" | `Stage(stage, 단계 이름, "M월 D일 종가")` |
| `KeyGuide` | "학반꿀" | `Message("키를 넣어 주세요", "눌러서 앱 열기")` |
| `NotReceived` | "학반꿀" | `Message("데이터를 받지 못했습니다", "눌러서 앱 열기")` |

- 단계 이름: BULL "불장", NEUTRAL "중립", WEAK "약세", BEAR "물장" (`stage_*`). (R-06)
- 기준 날짜는 단위 1에서 더한 "%1$d월 %2$d일 종가"에 월과 일을 넣는다. 해는 넣지 않는다. 날짜는 판정의 `asOfDate` 그대로다(시간대를 바꾸지 않는다). (R-26)
- `refreshButton`은 WidgetState의 것을 그대로 옮긴다.
- HomeWidgetStateHolder(앱 전체에 하나, `@Singleton`, `@Inject constructor(observeWidgetState: ObserveWidgetStateUseCase, @ApplicationScope scope: CoroutineScope)`) (R-03, R-16, P-05)
  - `val state: StateFlow<HomeWidgetUiState?>`: 만들어질 때부터 앱 범위에서 ObserveWidgetStateUseCase를 한 번만 모아 바꿔 들고 있다. 첫 값이 나오기 전에는 null이다.
  - 위젯마다, 그릴 때마다 UseCase를 새로 모으지 않는다. 놓인 위젯이 모두 같은 값을 받고, 그대로 두는 동안에 새로 놓은 위젯도 그대로 두고 있는 것을 받게 하기 위함이다. (R-03, R-16, P-05)
  - Android의 Context를 받지 않는다(글자는 UiText로 둔다).

**테스트 지시**
상황은 단위 2의 테스트와 같다. HomeWidgetStateHolder를 진짜 ObserveWidgetStateUseCase와 테스트의 범위로 만든다. 단계와 날짜를 판정 데이터로 만들기 어려운 경우(WEAK, BEAR, 1월 5일 등)는 WidgetState를 HomeWidgetUiState로 바꾸는 부분을 따로 불러 확인해도 된다. 방법은 CLI가 정한다.

- R-06 (첫 줄, 단계 이름, 날짜 줄의 글)
  - Given 기본 상황 / When state의 첫 null 아닌 값 / Then title `mood_now`, main `Stage(BULL, stage_bull, "%1$d월 %2$d일 종가"에 [11, 23])`, refreshButton ENABLED
  - Given content가 NEUTRAL, WEAK, BEAR인 Assessment / When 바꿈 / Then 단계 이름이 차례로 `stage_neutral`, `stage_weak`, `stage_bear`이고 `stage`도 그 단계다
- R-07
  - Given FRED의 키 MISSING / When 첫 값 / Then title `app_name`, main `Message(key_guide_title, "눌러서 앱 열기")`, refreshButton DISABLED
- R-08
  - Given 받아 둔 값 없음 / When 첫 값 / Then title `app_name`, main `Message(mood_failed_title, "눌러서 앱 열기")`, refreshButton ENABLED
- R-11 (마지막 줄의 글)
  - 위 R-06의 Stage에는 날짜가, R-07·R-08의 Message에는 "눌러서 앱 열기"가 있다(위 경우로 확인한다)
- R-26
  - Given `Assessment(BULL, 2026-01-05)` / When 바꿈 / Then asOfText의 args가 [1, 5]이다(해가 없다)
  - Given `Assessment(BULL, 2026-12-31)` / When 바꿈 / Then args가 [12, 31]
- R-03 (모두 같은 값)
  - Given 기본 상황, HomeWidgetStateHolder 하나 / When state를 두 곳에서 모으고, 받아 둔 값을 데이터 A에서 2026-11-19, 2026-11-20, 2026-11-23이 나쁜 날로 바꿈 / Then 두 곳이 받는 값이 늘 같고, 둘 다 `stage_neutral`로 바뀐다. ObserveWidgetStateUseCase는 한 번만 모아진다
- R-16 (그대로 두는 동안 새로 모으는 곳도 같은 것을 받음)
  - Given 받아 둔 값 없음, FRED의 키 MISSING으로 state가 KeyGuide의 상태가 됨, 받기를 붙잡아 둠 / When FRED의 키를 AVAILABLE로 바꾸고 2초 뒤 state를 새로 모으기 시작 / Then 새로 모은 곳의 첫 값은 KeyGuide의 상태(title `app_name`, `key_guide_title`)다. 받기를 풀어 주면(출처는 데이터 A) 두 곳 모두 `Stage(BULL, …)`가 된다
- R-30 (받아 둔 값을 바꾸면 위젯의 상태가 따라 바뀜)
  - Given 받아 둔 값 없음으로 state가 받지 못함의 상태가 됨 / When 받아 둔 값을 데이터 A로 바꿈 / Then state가 `Stage(BULL, stage_bull, [11, 23])`로 바뀐다

**확인 기준**
- 테스트 통과, 컴파일 오류 없음

### 단위 4: 위젯 그리기와 뒤에서 받기

**만들거나 고칠 것**
- 신규 MoodWidgetLayout (feature/homewidget/presentation)
- 신규 SmallMoodWidget, LargeMoodWidget (feature/homewidget/presentation)
- 신규 SmallMoodWidgetReceiver, LargeMoodWidgetReceiver (feature/homewidget/presentation)
- 신규 RefreshWidgetAction (feature/homewidget/presentation)
- 신규 WidgetRefreshWorker (feature/homewidget/presentation)
- 신규 WidgetRefreshScheduler (feature/homewidget/presentation)
- 신규 HomeWidgetUpdater (feature/homewidget/presentation)
- 신규 HomeWidgetEntryPoint (feature/homewidget/di)
- 신규 위젯 정보 `app/src/main/res/xml/widget_small_info.xml`, `widget_large_info.xml`
- 신규 미리 보기 레이아웃 `app/src/main/res/layout/widget_small_preview.xml`, `widget_large_preview.xml`
- 신규 진행 표시 레이아웃 (`app/src/main/res/layout/`. ProgressBar와 단위 1의 진행 표시 그림을 돌리는 것. 파일 이름은 CLI가 정한다)
- 수정 `app/src/main/AndroidManifest.xml` (두 Receiver)

**지시**
- 위젯의 모양
  - 작은 위젯은 시안 `https://claude.ai/artifact/LuRSF8bSPDqruAbnUSfye9`의 "위젯 · 작은 위젯 (2×1)"(파일 `project/Widget-Small.dc.html`), 큰 위젯은 같은 시안의 "위젯 · 큰 위젯 (2×2)"(파일 `project/Widget-Square.dc.html`)과 같은 모양으로 만든다. 모양의 값(색, 크기, 간격, 모서리)은 시안의 HTML에서 가져오되, 단위 1에서 테마에 더한 값을 쓴다. 문구와 동작은 이 지시의 글이 맞다. (R-13)
  - 시안 한 장에 그 위젯의 일곱 모습이 나란히 있다: 불장·중립·약세·물장인 날, 키가 없거나 틀릴 때(KeyGuide), 받지 못했을 때(NotReceived), 새로 고침을 눌러 받는 중(IN_PROGRESS). 받지 못함에서 눌러 받는 중인 모습은 따로 그려져 있지 않다. 받는 중 모습처럼 버튼 자리만 진행 표시로 바뀌고 나머지는 그대로다. (R-13, R-39)
  - 시안의 회색 바탕(#DDE1E7)과 위젯 아래의 설명 글("불장인 날" 등, #3A424D)은 시안을 읽기 위한 것이다. 위젯에 넣지 않고 테마에도 더하지 않는다. 시안의 날짜(11월 23일)는 예시다. (R-13)
  - 시안의 위젯 크기(172×84, 172×172)는 한 예다. 실제 크기는 홈 화면 앱이 칸에 맞춰 정한다. 위젯은 받은 크기를 채우고, 여백, 글자 크기, 버튼 자리의 크기와 자리만 시안을 따른다.
  - 카드: 단위 1의 카드 바탕 그림을 받은 크기에 꽉 차게 깐다. (R-05, P-12)
  - 작은 위젯: 줄들이 위아래로 붙어 카드 세로 가운데에 모인다. 판정은 첫 줄("지금 장은"), 단계 이름, 날짜 줄의 세 줄이고 줄 사이 여백이 없다. KeyGuide와 NotReceived는 첫 줄("학반꿀")과 안내 문구 두 줄이고 사이가 2다. "눌러서 앱 열기"는 그리지 않는다. 글은 왼쪽 정렬이다. (R-10, R-11)
  - 큰 위젯: 첫 줄이 위쪽 끝, 마지막 줄이 아래쪽 끝, 단계 이름이나 안내 문구가 그 사이 가운데에 놓인다(시안의 `space-between`). 마지막 줄은 판정이면 날짜, KeyGuide와 NotReceived면 "눌러서 앱 열기"다. 글은 왼쪽 정렬이다. (R-11)
  - 단계 이름은 단계의 색으로 적는다: BULL `stageBull`, NEUTRAL `stageNeutral`, WEAK `stageWeak`, BEAR `stageBear`. 앱의 시장 분위기 화면과 같은 테마 값이다. 단계에서 색을 고르는 코드는 시장 분위기의 Presentation에서 가져오지 않고 위젯 쪽에 둔다. (R-06, R-32)
  - 안내 문구("키를 넣어 주세요", "데이터를 받지 못했습니다")는 한 줄에 들어가지 않으면 낱말 사이(띄어쓰기)에서만 줄을 바꾼다. Android의 기본 줄 바꿈은 한글 낱말 가운데에서도 바꿀 수 있으므로(예: "데이터를 받지 못했습" / "니다") 그렇게 되지 않게 해야 한다. 방법은 CLI가 정하고 DECISION으로 남긴다. (R-09)
  - 새로 고침 버튼 자리: 카드의 오른쪽 위에 단위 1의 크기와 자리로 둔다(카드 안쪽 여백과 상관없이 카드 끝에서 잰다). 자리 가운데에 상태에 따라 하나를 그린다: ENABLED 아이콘(#4A5360), DISABLED 아이콘(#B7BEC8), IN_PROGRESS 도는 진행 표시. 진행 표시는 Glance의 기본 진행 표시가 아니라, 단위 1의 진행 표시 그림을 ProgressBar 레이아웃으로 돌려 끼운다(기본 진행 표시는 바탕 고리와 선 굵기를 정할 수 없다). 아이콘의 설명은 "새로 고침", 진행 표시의 설명은 "받는 중"이다. (R-37, R-39, P-12)
  - 색은 모두 고정 값으로 준다. 기기가 어두운 화면이어도 바뀌지 않는다. (R-12, P-12)
  - 두 위젯이 함께 쓰는 조각(카드 바탕, 버튼 자리, 글 한 줄)은 MoodWidgetLayout에 둔다. (P-01)
- 누름 (R-14, R-37, R-38, R-39, P-14, P-15)
  - 버튼 자리 밖(카드 어디든)을 누르면 `AppLaunchIntents.marketMood(context)`로 앱을 연다. 위젯이 무엇을 보여 주든 같다.
  - 버튼 자리를 누르면: ENABLED면 RefreshWidgetAction. DISABLED와 IN_PROGRESS면 누름을 받아 아무 일도 하지 않는다(앱도 열지 않는다). 누름을 받지 않게 두면 카드의 앱 열기로 넘어가기 때문이다.
- SmallMoodWidget, LargeMoodWidget(GlanceAppWidget) (R-03, R-05, R-16, P-05, P-11)
  - HomeWidgetStateHolder의 `state`를 받아 그린다. null이 아닌 첫 값이 나올 때까지 기다린 뒤 그린다. 위젯은 판정, 형식 바꾸기, 문구 고르기를 하지 않는다(단계 → 색 고르기와 UiText를 글자로 푸는 것만 한다).
  - 글자는 단위 1의 `UiText.asString(context)`로 푼다.
- SmallMoodWidgetReceiver, LargeMoodWidgetReceiver(GlanceAppWidgetReceiver): 그 위젯의 첫 개가 놓이면(`onEnabled`) `WidgetRefreshScheduler.onWidgetPlaced()`, 마지막 개가 치워지면(`onDisabled`) `WidgetRefreshScheduler.onWidgetRemoved()`. (R-20, R-23, P-08)
- RefreshWidgetAction(Glance ActionCallback): `WidgetRefreshScheduler.requestManualRefresh()`만 부르고 돌아온다. 여기서 받기를 기다리지 않는다. 이 자리는 시스템이 10초쯤만 기다려 준다. (R-38, P-07)
- WidgetRefreshWorker(CoroutineWorker) (R-20, R-21, R-27, R-38, R-43, P-07)
  - 입력으로 WidgetRefreshTrigger의 이름을 받는다.
  - `WidgetRefreshCoordinator.request(trigger)`를 부르고 기다린 뒤, `HomeWidgetUpdater.updateAll()`로 놓인 위젯을 다시 그리고 끝낸다. MANUAL이면 다시 그리는 것은 `HomeWidgetStateHolder.state`에 진행 표시가 풀린 것(버튼이 IN_PROGRESS가 아님)이 반영된 뒤에 한다. 기다리는 방법은 CLI가 정한다.
  - 받기가 실패해도, 취소가 아닌 예외가 나도 `Result.success()`로 끝낸다. 실패를 알려 다시 돌리게 하면 한도를 넘은 때에 요청이 더 나간다. 다음 차례는 주기 작업이다.
  - 작업 도중에 앱이 꺼져 시스템이 다시 돌리면, MANUAL은 1분 조건에 걸려 받지 않고 1초 뒤 진행 표시가 풀린 채로 다시 그려진다. 따로 할 것은 없다.
- WidgetRefreshScheduler(`@Singleton`, `@Inject constructor(@ApplicationContext context: Context)`) (R-20, R-21, R-22, R-23, R-24, R-38, P-07, P-08, P-09)
  - `fun onWidgetPlaced()`: 주기 작업(6시간마다, 인터넷 연결 조건, PERIODIC)을 이름 하나로 넣되 이미 있으면 그대로 둔다. FIRST_PLACED 작업(한 번, 인터넷 연결 조건)을 넣는다. 연결이 없으면 돌지 않고 연결이 돌아온 뒤에 돈다.
  - `suspend fun onWidgetRemoved()`: 놓인 작은 위젯과 큰 위젯을 합쳐 세어 하나도 없을 때에만 주기 작업을 뺀다.
  - `fun requestManualRefresh()`: MANUAL 작업(한 번, 조건 없음)을 넣는다. 같은 작업이 있거나 돌고 있으면 넣지 않는다. 연결 조건을 붙이지 않는 것은 연결이 없을 때 누르면 곧 실패해 버튼으로 돌아오게 하기 위함이다. 누른 뒤 곧 돌게 한다(기기가 절전 중이어도 사용자가 누른 것이므로). 방법은 CLI가 정한다.
  - `suspend fun syncPeriodic()`: 놓인 위젯이 있으면 주기 작업을 넣고(이미 있으면 그대로), 없으면 뺀다. 시스템의 알림(`onEnabled`, `onDisabled`)을 놓친 경우에 대비해 프로세스가 시작될 때 부른다(단위 5).
  - 작업의 이름은 Glance가 쓰는 작업 이름과 겹치지 않게 정한다.
  - 기기를 껐다 켠 뒤에 주기 작업이 이어지는 것은 WorkManager가 스스로 한다. 따로 적어 두지 않는다. (R-24)
- HomeWidgetUpdater(`@Singleton`, `@Inject constructor(@ApplicationContext context: Context, stateHolder: HomeWidgetStateHolder, @ApplicationScope scope: CoroutineScope)`) (R-17, R-18, R-24, R-39, R-40, R-43, P-06)
  - `fun start()`: 앱 범위에서 HomeWidgetStateHolder의 `state`를 모아, null이 아닌 값이 나오거나 바뀔 때마다 놓인 두 위젯을 모두 다시 그리게 한다. 처음 null이 아닌 값이 나온 때에도 한 번 그린다. 그래서 받는 중에 앱이 꺼져 진행 표시가 그려진 채 남았더라도, 앱이 다시 깨어나는 때에 버튼으로 돌아온다. 여러 번 불려도 한 번만 모은다.
  - `suspend fun updateAll()`: 놓인 두 위젯을 지금 상태로 다시 그린다.
  - 위젯 정보의 갱신 주기(시스템이 정한 주기로 다시 그리기)는 쓰지 않는다.
- HomeWidgetEntryPoint(Hilt EntryPoint, SingletonComponent): HomeWidgetStateHolder, WidgetRefreshCoordinator, WidgetRefreshScheduler, HomeWidgetUpdater를 내준다. 시스템이 만드는 것(위젯, Receiver, ActionCallback, Worker)은 모두 이것 하나로 받는다. 그 뒤의 클래스는 모두 생성자로 받는다. (P-11)
- 위젯 정보(appwidget-provider) (R-01, R-02, R-04, P-06, P-13)
  - 작은 위젯 가로 2칸 세로 1칸, 큰 위젯 가로 2칸 세로 2칸. Android 12 이상의 칸 수와 그보다 낮은 버전의 최소 크기를 모두 적는다.
  - 크기를 바꿀 수 없다(`resizeMode` 없음).
  - 시스템이 정한 주기로 다시 그리지 않는다(`updatePeriodMillis` 0).
  - 미리 보기 레이아웃(`previewLayout`)으로 아래 미리 보기를 쓴다. 그보다 낮은 버전용 그림 파일(`previewImage`)은 두지 않는다.
  - 홈 화면에만 놓는다.
- 미리 보기 레이아웃: 그 위젯의 불장인 날의 판정 모습(시안의 첫 모습)을 보통 View로 그린다. 카드 바탕, "지금 장은", 빨간 "불장", 미리 보기의 기준 날짜("11월 23일 종가"), 오른쪽 위의 누를 수 있는 새로 고침 아이콘. 값은 위 위젯과 같다. Android 12 이상에서 보인다. (R-02, P-13)
- `AndroidManifest.xml`: 두 Receiver를 적는다. 각각 위젯 갱신 알림을 받고, 위젯 정보 파일을 가리키며, 고르는 목록의 이름은 "장세 (작게)", "장세 (크게)"다. 다른 곳은 단위 5에서 고친다. (R-01)

**테스트 지시**
- 없음 (Glance, WorkManager, 시스템 접점이라 기기에서만 보인다. 수동 확인으로 본다. 있던 테스트와 단위 2·3의 테스트가 모두 통과해야 한다)

**확인 기준**
- 컴파일 오류 없음, 있던 테스트 통과

### 단위 5: 앱 열기와 프로세스 시작

**만들거나 고칠 것**
- 수정 HakBanGgulApp (맨 위 패키지)
- 수정 MainActivity (맨 위 패키지)
- 수정 AppNavHost (맨 위 패키지)
- 수정 `app/src/main/AndroidManifest.xml` (MainActivity)

**지시**
- HakBanGgulApp: 프로세스가 시작될 때 `HomeWidgetUpdater.start()`를 부르고, 앱 범위에서 `WidgetRefreshScheduler.syncPeriodic()`을 부른다. 받기는 시작하지 않는다. (R-24, R-43, P-06, P-08)
- MainActivity: 열릴 때(`onCreate`)와 이미 열려 있는데 다시 불릴 때, 받은 Intent에 `AppLaunchIntents.showsMarketMood`의 표시가 있으면 AppNavHost에 시장 분위기 화면을 보이라고 알린다. 같은 Intent로 두 번 알리지 않는다(화면을 돌려 다시 만들어질 때 등). (R-14, R-15, P-14)
- AppNavHost: 그 알림을 받으면 시장 분위기 화면 위에 쌓인 화면(설정)을 걷어 시장 분위기 화면이 보이게 한다. 이미 시장 분위기 화면이면 아무것도 하지 않는다. 알림을 어떤 모양으로 받을지는 CLI가 정한다. 설정 화면에서 키를 확인하는 중이었으면 화면을 벗어난 것과 같고, 확인은 이어진다(설정의 동작 그대로). (R-15)
- `AndroidManifest.xml`: MainActivity가 이미 열려 있으면 새로 만들지 않고 그것을 다시 부르게 한다. 그 밖의 MainActivity 설정은 그대로 둔다. (R-15, P-14)
- 시장 분위기와 설정의 화면과 동작은 바뀌지 않는다. 바뀌는 것은 위젯에서 열었을 때 설정 화면이 걷히는 것뿐이다.

**테스트 지시**
- 없음 (수동 확인으로 본다. 있던 테스트가 모두 통과해야 한다)

**확인 기준**
- 컴파일 오류 없음, 있던 테스트 통과

## 3. 수동 확인

- 날짜가 바뀌는 것으로 보는 항목은 새 종가가 나온 뒤에 본다. 미국 장이 하루 열리고 닫힌 뒤(한국 시각으로 다음 날 아침 이후)다. 주말과 미국 휴장일에는 날짜가 바뀌지 않는다.
- R-21, R-23, R-24는 하루를 넘겨야 보인다. 한 번에 볼 수 있게 이어서 한다: 위젯을 놓은 날 R-23의 치우고 다시 놓기와 R-24의 기기 껐다 켜기를 하고, 앱을 열지 않은 채 다음 날 위젯의 날짜를 본다.

- R-01: Given 앱을 설치함 / When 홈 화면에서 위젯을 고르는 목록을 연다 / Then 학반꿀 아래에 "장세 (작게)"와 "장세 (크게)" 둘이 따로 보인다
- R-02: Given Android 12 이상 기기 / When 위젯을 고르는 목록에서 두 위젯을 본다 / Then 각 미리 보기가 그 위젯의 불장인 날 모습("지금 장은", 빨간 "불장", "11월 23일 종가", 오른쪽 위 새로 고침 아이콘)이다
- R-03 (여러 개 놓기): Given 키가 저장되어 있고 받아 둔 값이 있음 / When 작은 위젯 둘과 큰 위젯 하나를 함께 놓는다 / Then 셋 모두 놓이고 같은 단계와 날짜를 보여 준다
- R-04: Given 위젯을 놓음 / When 위젯을 길게 누른다 / Then 크기를 바꾸는 손잡이가 없다
- R-05 (표시): Given 위젯을 놓음 / When 키가 모두 저장되고 받아 둔 값이 있을 때, 설정에서 키 하나를 지웠을 때, 키는 저장되어 있는데 받아 둔 값이 없고 받기에 실패했을 때를 차례로 본다 / Then 차례로 판정, 키 안내, 받지 못함이 보인다
- R-06 (화면 배치): Given 키가 모두 저장되고 받아 둔 값이 있음 / When 두 위젯을 본다 / Then 위에서 아래로 "지금 장은", 단계 이름(크게, 단계의 색), "M월 D일 종가"다
- R-08 (화면 표시): Given 받지 못함 상태 / When 두 위젯을 본다 / Then 위에서 아래로 "학반꿀", "데이터를 받지 못했습니다"다
- R-09: Given 받지 못함 상태 / When 작은 위젯을 본다 / Then "데이터를 받지 못했습니다"가 한 줄에 들어가지 않으면 "데이터를" 다음이나 "받지" 다음처럼 띄어쓰기에서 넘어가고, 낱말 가운데("못했습" / "니다" 등)에서 넘어가지 않는다
- R-10: Given 판정, 키 안내, 받지 못함 / When 작은 위젯을 본다 / Then 판정은 세 줄, 키 안내와 받지 못함은 두 줄(문구가 넘어가면 그만큼 더)이 위아래로 붙어 카드 세로 가운데에 놓인다
- R-11 (화면 배치): Given 판정, 키 안내, 받지 못함 / When 두 위젯을 본다 / Then 큰 위젯은 첫 줄이 위쪽, 단계 이름이나 안내 문구가 가운데, 마지막 줄(판정은 날짜, 키 안내와 받지 못함은 "눌러서 앱 열기")이 아래쪽이다. 작은 위젯에는 "눌러서 앱 열기"가 없다
- R-12: Given 위젯을 놓음 / When 기기를 어두운 화면으로 바꾼다 / Then 위젯은 흰 카드와 같은 글 색 그대로다
- R-13: Given 그날의 단계(하나), 키 안내, 받지 못함, 새로 고침을 눌러 받는 중의 모습 / When 각 모습에서 두 위젯을 시안 `https://claude.ai/artifact/LuRSF8bSPDqruAbnUSfye9`의 "위젯 · 작은 위젯 (2×1)", "위젯 · 큰 위젯 (2×2)"의 같은 모습과 나란히 놓고 본다 / Then 배치, 간격, 크기, 색이 같다. 위젯 크기는 홈 화면 앱에 따라 시안과 다를 수 있고, 여백·글자 크기·버튼 자리가 같으면 된다. 그날이 아닌 세 단계는 만들어 볼 수 없어 R-32로 색만 본다
- R-14: Given 판정, 키 안내, 받지 못함 / When 각 모습에서 새로 고침 버튼 밖을 누른다 / Then 앱의 시장 분위기 화면이 열린다
- R-15: Given 앱에서 설정 화면을 연 채 홈으로 나감 / When 위젯의 버튼 밖을 누른다 / Then 설정 화면이 아니라 시장 분위기 화면이 보인다. 뒤로 가기를 누르면 앱을 나간다(설정 화면으로 돌아가지 않는다)
- R-16 (표시): Given 키가 저장되어 있고 받아 둔 값이 있음, 위젯이 없음 / When 위젯을 놓는다 / Then 놓자마자 판정이 보인다(빈 카드나 "데이터를 받지 못했습니다"가 먼저 보이지 않는다)
- R-17: Given 위젯이 놓여 있고 마지막으로 받은 뒤 새 종가가 나옴 / When 앱을 열어 새로 받은 뒤 홈으로 나간다 / Then 위젯의 날짜가 앱의 기준 날짜와 같은 새 날짜다
- R-18: Given 위젯이 판정을 보여 줌 / When 설정에서 키 하나를 지우고 홈으로 나갔다가, 다시 키를 저장하고 홈으로 나간다 / Then 지운 뒤에는 키 안내(버튼 흐림), 다시 저장한 뒤에는 판정이 보인다
- R-19 (표시): Given 위젯이 키 안내를 보여 줌(키 하나를 지움) / When 키를 다시 저장하고 곧바로 홈으로 나가 위젯을 지켜본다 / Then 키 안내에서 판정으로 바뀌고, 그 사이 "데이터를 받지 못했습니다"가 스치지 않는다
- R-20 (놓을 때 받음): Given 마지막으로 받은 뒤 6시간이 넘고 새 종가가 나옴, 앱이 꺼져 있음(최근 앱 목록에서 지움), 위젯이 없음 / When 위젯을 놓고 앱을 열지 않고 기다린다 / Then 잠시 뒤 위젯의 날짜가 새 종가 날짜로 바뀐다
- R-21 (앱을 열지 않아도 받아짐): Given 위젯을 놓고 앱을 끔 / When 앱을 열지 않고 다음 날까지 둔다 / Then 다음 날 위젯의 날짜가 놓은 날보다 뒤의 새 종가 날짜로 바뀌어 있다
- R-22: Given 마지막으로 받은 뒤 6시간이 넘고 새 종가가 나옴, 앱이 꺼져 있음, 위젯이 없음, 비행기 모드 / When 위젯을 놓고 기다린 뒤 비행기 모드를 끈다 / Then 비행기 모드 동안에는 날짜가 그대로이고, 끈 뒤 잠시 뒤 새 종가 날짜로 바뀐다
- R-23: Given 위젯이 놓여 있음 / When 놓인 위젯을 모두 치웠다가 다시 놓고, 앱을 열지 않고 다음 날까지 둔다 / Then 다음 날 위젯의 날짜가 새 종가 날짜로 바뀌어 있다
- R-24: Given 위젯이 놓여 있음 / When 기기를 껐다 켜고, 앱을 열지 않고 다음 날까지 둔다 / Then 켠 뒤 위젯이 판정을 보여 주고(빈 카드나 오류가 아님), 다음 날 날짜가 새 종가 날짜로 바뀌어 있다
- R-25 (앱과 같음): Given 위젯이 판정을 보여 줌 / When 앱을 열어 종합 판정 카드를 본다 / Then 위젯의 단계 이름과 날짜가 카드의 "지금 장은" 아래 단계 이름, 기준 날짜와 같다
- R-27 (실패해도 다시 돌리지 않음): R-42와 함께 본다
- R-32: Given 위젯이 판정을 보여 줌 / When 위젯과 앱의 종합 판정 카드를 나란히 본다 / Then 단계 이름의 색이 같다
- R-37 (그림, 흐리게 보임, 눌러도 반응 없음): Given 판정, 키 안내 / When 버튼을 보고, 키 안내에서 버튼을 누른다 / Then 버튼이 앱의 새로 고침과 같은 그림이고, 키 안내에서는 흐리게 보이며 눌러도 앱이 열리지 않고 아무 일도 없다
- R-38: Given 위젯이 판정을 보여 줌, 마지막으로 받은 뒤 새 종가가 나옴, 앱이 꺼져 있음 / When 새로 고침 버튼을 누른다 / Then 진행 표시가 돈 뒤 날짜가 새 종가 날짜로 바뀐다. 앱은 열리지 않는다
- R-39: Given 작은 위젯과 큰 위젯을 함께 놓음 / When 한 위젯의 새로 고침을 누른다 / Then 두 위젯 모두 버튼 자리에 진행 표시가 돌고, 그동안 버튼 자리를 다시 눌러도 아무 일이 없다. 나머지는 그대로다
- R-41 (1분 안에는 받지 않음, 앱을 끈 채로도): Given 위젯이 판정을 보여 줌, 마지막으로 받기를 시작한 지 1분이 넘음 / When 새로 고침을 눌러 진행 표시가 풀리면 곧바로 앱을 강제 종료(설정 > 앱 > 학반꿀 > 강제 종료)하고, 처음 누른 때부터 1분이 지나기 전에 다시 누른다 / Then 둘째에는 진행 표시가 1초쯤만 보였다가 버튼으로 돌아온다(첫째보다 짧다)
- R-42: Given 위젯이 판정을 보여 줌, 마지막으로 받기를 시작한 지 1분이 넘음, 비행기 모드 / When 새로 고침을 누른다 / Then 진행 표시가 돈 뒤 버튼으로 돌아오고, 판정과 날짜가 그대로이며 실패 안내가 없다. 잠시 기다려도 진행 표시가 다시 돌지 않는다(R-27)
- R-43 (앱이 꺼진 뒤): Given 위젯이 판정을 보여 줌 / When 새로 고침을 눌러 진행 표시가 도는 동안 앱을 강제 종료하고, 앱을 다시 열었다가 홈으로 나간다 / Then 위젯의 버튼 자리가 진행 표시가 아니라 새로 고침 버튼이다
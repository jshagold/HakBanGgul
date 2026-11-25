# Tasks: 시장 분위기 버전업-001

기능: 시장 분위기
변경: 버전업-001 (CS-003)
기준 Plan: 1.1.0
상태: 구현 중

| 전달 | 날짜 | 이유 |
|---|---|---|
| 1 | 2026-11-25 | 최초 전달 |

## 1. 개요
시장 분위기는 항목들의 값을 받아 지금 장의 단계를 판정하고 그 근거와 함께 보여 주는 기능이다. 다음 변경에서 홈 화면 위젯을 만든다. 위젯은 판정을 따로 계산하지 않고 값도 따로 받지 않으며, 앱이 꺼져 있을 때에도 시장 분위기에 판정을 묻고 받아 달라고 청한다. 이번에는 그 위젯이 쓸 것을 시장 분위기의 Domain에 더한다: 받아 달라는 청(조건 셋 가운데 하나를 골라 청하고, 받기가 끝난 뒤에 돌아온다), 받는 중인지 알기, 마지막으로 받기를 시작한 때의 저장.
청으로 받는 것도 화면에서 받을 때와 같은 받기 관리자(MarketDataRefresher)와 같은 받기(RefreshMarketDataUseCase)를 거친다. 받는 길이 둘이 되면 화면과 위젯의 값이 어긋나기 때문이다.
화면, 판정 계산, 화면에서 받는 때(화면이 보이게 될 때, 새로 고침, 다시 시도, 키가 모두 저장됨이 될 때)는 바꾸지 않는다. `feature/marketmood/presentation`, `feature/marketmood/di`, `core`, `feature/settings`, 문구, 빌드 설정, `AndroidManifest.xml`은 고치지 않는다. DB의 표와 Entity, Dao, DB 버전도 고치지 않는다(표의 모양이 바뀌면 받아 둔 값이 지워진다). 위젯은 이번에 만들지 않는다.

## 2. 구현 단위

### 단위 1: 마지막으로 받기를 시작한 때의 저장

**만들거나 고칠 것**
- 수정 MarketDataRepository (feature/marketmood/domain)
- 수정 MarketDataRepositoryImpl (feature/marketmood/data)
- 수정 RefreshMarketDataUseCase (feature/marketmood/domain)
- 수정 FakeMarketDataRepository (`app/src/test/java/com/hak/hakbanggul/feature/marketmood/domain/MarketMoodFakes.kt`)
- 수정 MarketDataRepositoryImplTest (`app/src/test/java/com/hak/hakbanggul/feature/marketmood/data/`)
- 수정 RefreshMarketDataUseCaseTest (`app/src/test/java/com/hak/hakbanggul/feature/marketmood/domain/`)

**지시**
- 새 용어의 영어 이름: 마지막으로 받기를 시작한 때 `lastStartedAt`. 처음 선언하는 곳 위에 `// 용어: 마지막으로 받기를 시작한 때` 주석을 단다.
- MarketDataRepository에 둘을 더한다. 있는 `observeStored`, `fetch`, `save`와 StoredMarketData는 그대로 둔다. (R-62, P-26)
  - `suspend fun getLastStartedAt(): Instant?`: 마지막으로 받기를 시작한 때를 돌려준다. 적힌 적이 없으면 null이다. 읽지 못해도 예외를 던지지 않고 null을 돌려준다.
  - `suspend fun saveLastStartedAt(startedAt: Instant)`: 그 때를 적는다. 앞서 적힌 것이 있으면 바꾼다. 적지 못하면 IOException을 던진다(`save`와 같다).
- 시작한 때를 StoredMarketData에 싣지 않는다. 실으면 받기를 시작할 때마다 받아 둔 값의 Flow가 새 값을 내보내, 판정을 다시 계산하고 화면을 다시 만든다. (P-26)
- MarketDataRepositoryImpl은 시작한 때를 표 `sync_stamps`에 이름 `market_mood_refresh_started`인 행으로 적고 읽는다. 성공한 때의 행(이름 `market_mood_refresh`)과 다른 행이라 서로 덮어쓰지 않는다. 시각은 성공한 때와 같이 UTC 밀리초다. SyncStampDao는 고치지 않고 있는 `observe(이름)`과 `upsert`를 쓴다(읽기는 `observe(이름)`의 첫 값). (R-62, P-26)
- `saveLastStartedAt`은 `save`의 트랜잭션과 따로 적는다. 받기를 시작할 때 적는 것이라 받은 값의 저장과 묶이지 않는다. (P-26)
- 시작한 때를 적어도 `observeStored()`가 같은 값을 다시 내보내지 않게 한다. Room은 표의 어느 행이 바뀌든 그 표를 읽는 Flow를 다시 내보내므로, 시작한 때를 적으면 성공한 때의 Flow가 같은 값을 한 번 더 내보낸다. 그대로 두면 받기를 시작할 때마다 판정을 다시 계산한다. (P-26)
- 받아 둔 값을 읽지 못해 두 표를 비우는 있는 동작(`observeStored`의 처리)은 그대로 둔다. 그때 시작한 때의 행도 함께 지워지며, 그 뒤에는 시작한 때가 없는 것이 된다. (R-62)
- RefreshMarketDataUseCase는 받기를 시작하면 출처에 요청을 보내기 전에 `saveLastStartedAt(받는 때)`을 부른다. 받는 때는 지금 코드가 받기 첫머리에서 시계로 읽는 그 값이다. 화면에서 시작했든 다른 기능의 청으로 시작했든 받기는 모두 이곳을 지나므로, 어느 길로 시작한 받기든 적힌다. 받기가 실패해도 적힌 것은 그대로 남는다. (R-62, P-26)
- `saveLastStartedAt`이 예외를 던져도(취소는 뺀다) 받기를 이어 간다. 그 때문에 받기의 결과(RefreshOutcome)를 실패로 바꾸지 않는다. (R-62, P-26)
- 그 밖의 받기 동작(받는 값, 10초 제한, 거르기, 저장, 결과 가르기)은 바꾸지 않는다. 있는 테스트의 경우는 줄이거나 바꾸지 않는다.

**테스트 지시**
- R-62 (기기에 적고 읽음, 읽지 못하면 없는 것으로. MarketDataRepositoryImpl, 가짜 Dao)
  - Given 아무것도 적지 않음 / When getLastStartedAt / Then null
  - Given saveLastStartedAt(2026-11-24T01:00:00Z) / When getLastStartedAt / Then 2026-11-24T01:00:00Z. 가짜 SyncStampDao에는 이름 `market_mood_refresh_started`인 행이 그 시각의 UTC 밀리초로 들어 있다
  - Given saveLastStartedAt(2026-11-24T01:00:00Z)를 한 뒤 saveLastStartedAt(2026-11-24T01:05:00Z) / When getLastStartedAt / Then 2026-11-24T01:05:00Z
  - Given save(SPY의 값 하나, succeededAt = 2026-11-24T00:00:00Z)를 한 뒤 saveLastStartedAt(2026-11-24T01:00:00Z) / When observeStored의 첫 값과 getLastStartedAt / Then lastSuccessAt은 2026-11-24T00:00:00Z, getLastStartedAt은 2026-11-24T01:00:00Z
  - Given saveLastStartedAt(2026-11-24T01:00:00Z)를 한 뒤 save(SPY의 값 하나, succeededAt = 2026-11-24T02:00:00Z) / When getLastStartedAt / Then 2026-11-24T01:00:00Z
  - Given save(SPY의 값 하나, succeededAt = 2026-11-24T00:00:00Z)를 한 뒤 observeStored를 모으기 시작해 값이 한 번 나옴 / When saveLastStartedAt(2026-11-24T01:00:00Z) / Then observeStored가 값을 더 내보내지 않는다(나온 횟수가 1 그대로다). 가짜 SyncStampDao의 `observe`는 지금처럼 어느 이름의 행이 바뀌든 다시 내보내는 것이어야 한다
  - Given 가짜 SyncStampDao의 `observe`가 예외(IllegalStateException)를 던짐 / When getLastStartedAt / Then 예외 없이 null
  - Given 가짜 SyncStampDao의 `upsert`가 예외(IllegalStateException)를 던짐 / When saveLastStartedAt(2026-11-24T01:00:00Z) / Then IOException이 난다
- R-62 (받기를 시작할 때 적음, 실패한 받기도, 적지 못해도 받기는 이어 감. RefreshMarketDataUseCase)
  - Given 시계 2026-11-24T01:00:00Z, 두 출처가 데이터 A(있는 `MoodTestData.dataA()`)를 돌려줌 / When 받기 / Then 결과는 Success. saveLastStartedAt이 2026-11-24T01:00:00Z로 한 번 불렸고, 첫 fetch보다 먼저 불렸다
  - Given 시계 2026-11-24T01:00:00Z, 두 출처 모두 NO_CONNECTION / When 받기 / Then 결과는 Failure(NO_CONNECTION). getLastStartedAt은 2026-11-24T01:00:00Z
  - Given 시계 2026-11-24T01:00:00Z, 두 출처가 데이터 A를 돌려줌, saveLastStartedAt이 IOException을 던짐 / When 받기 / Then fetch가 출처마다 한 번씩 불리고, 결과는 Success이며, 시리즈 여섯이 성공한 때 2026-11-24T01:00:00Z와 함께 한 번 저장된다

**확인 기준**
- 테스트 통과, 컴파일 오류 없음

### 단위 2: 받아 달라는 청과 받는 조건

**만들거나 고칠 것**
- 신규 RefreshCondition (feature/marketmood/domain)
- 신규 RefreshRequestResult (feature/marketmood/domain)
- 수정 MarketDataRefresher (feature/marketmood/domain)
- 신규 RequestRefreshUseCase (feature/marketmood/domain)
- 신규 ObserveRefreshStateUseCase (feature/marketmood/domain)
- 수정 MarketDataRefresherTest (`app/src/test/java/com/hak/hakbanggul/feature/marketmood/domain/`)
- 신규 ObserveMoodUseCase, RequestRefreshUseCase, ObserveRefreshStateUseCase의 테스트 (`app/src/test/java/com/hak/hakbanggul/feature/marketmood/domain/`. 파일을 어떻게 나눌지는 정해 두지 않는다)

**지시**
- 새 용어의 영어 이름: 받아 달라는 청 `RefreshRequest`. 처음 선언하는 곳 위에 `// 용어: 받아 달라는 청` 주석을 단다.
- RefreshCondition은 청하는 쪽이 고르는 받는 조건이다(열거형). 아래에서 "출처의 키가 모두 쓸 수 있음"은 지금 `onScreenShown`이 따지는 것과 같다: 규칙이 쓰는 출처가 하나 이상이고 모두 AVAILABLE이다. (R-60, P-25)

| 값 | 받는 때 |
|---|---|
| `WHEN_STALE` | 화면이 보이게 될 때 받는 조건과 같다. 출처의 키가 모두 쓸 수 있음이고, 판정을 낼 수 없거나 마지막으로 받기에 성공한 때가 없거나 성공한 지 6시간이 넘었거나 성공한 때가 지금보다 뒤다. 꼭 6시간이면 받지 않는다 |
| `WHEN_KEYS_AVAILABLE` | 출처의 키가 모두 쓸 수 있음이다. 그 밖의 것은 따지지 않는다 |
| `WHEN_NOT_RECENTLY_STARTED` | 출처의 키가 모두 쓸 수 있음이고, 마지막으로 받기를 시작한 지 1분이 넘었다. 꼭 1분이면 받지 않는다. 시작한 때가 없거나(`getLastStartedAt`이 null) 지금보다 뒤면 넘은 것으로 본다 |

- RefreshRequestResult는 청의 결과다(열거형). 받기가 성공했는지는 담지 않는다. 실패의 원인은 RefreshState의 `lastFailure`에 있다. (R-60, P-24)

| 값 | 뜻 |
|---|---|
| `REFRESHED` | 이 청으로 받기를 시작했고, 그 받기가 끝났다 |
| `JOINED` | 이미 받는 중이어서 새로 시작하지 않았고, 그 받기가 끝났다 |
| `SKIPPED` | 조건에 맞지 않아 받지 않았다 |

- MarketDataRefresher에 `suspend fun request(condition: RefreshCondition): RefreshRequestResult`를 더한다. 차례는 아래와 같다. (R-59, R-60, P-22, P-24, P-25)
  1. 청한 때 이미 받는 중이면 조건을 따지지 않는다. 받기를 새로 시작하지 않고, 받는 중인 그 받기가 끝날 때까지 기다린 뒤 `JOINED`를 돌려준다.
  2. 받는 중이 아니면 조건을 따진다. 맞지 않으면 받지 않고 곧바로 `SKIPPED`를 돌려준다.
  3. 맞으면 받기를 시작하고, 그 받기가 끝날 때까지 기다린 뒤 `REFRESHED`를 돌려준다. 조건을 따지는 사이에 다른 받기(화면의 새로 고침이나 다른 청)가 먼저 시작됐으면 새로 시작하지 않고 그 받기가 끝날 때까지 기다린 뒤 `JOINED`를 돌려준다.
- 청이 여럿 한꺼번에 와도 받기는 하나만 시작된다. 하나는 `REFRESHED`, 나머지는 `JOINED`다. (R-59, P-25)
- 청으로 시작한 받기도 `refresh()`가 시작하는 받기와 같은 길이다: 상태를 받는 중으로 바꾸고, 앱 범위(`@ApplicationScope`)에서 RefreshMarketDataUseCase를 돌리고, 끝나면 RefreshState에 결과를 적는다. 받는 값, 저장, 실패의 처리, 키가 거절됐을 때 설정에 알리는 것이 화면에서 받을 때와 같아진다. 청을 위한 받기 코드를 따로 두지 않는다. (R-59, P-22)
- 받기는 앱 범위에서 돌므로, `request`를 부른 코루틴이 취소되어도 받기는 끝까지 이어져 저장된다. (R-59, P-24)
- `request`가 `REFRESHED`나 `JOINED`로 돌아온 때에는 그 받기가 끝나 있다: RefreshState의 `isRefreshing`이 거짓이고, 받은 값의 저장과 `lastFailure`가 이미 반영되어 있다. (R-59, R-61, P-24)
- 받기가 실패해도 `request`는 예외를 던지지 않고 `REFRESHED`나 `JOINED`를 돌려준다. (P-24)
- 조건을 따지는 코드는 MarketDataRefresher 한 곳에 둔다. `onScreenShown()`의 판정과 `WHEN_STALE`은 같은 코드 하나를 쓴다. 6시간과 1분은 같은 방식으로 "넘었을 때"를 따진다. (R-60, P-25)
- 있는 `refresh()`, `onScreenShown()`, `state`, 키가 모두 AVAILABLE로 바뀌면 받는 것은 이름과 동작을 그대로 둔다. `refresh()`는 지금처럼 조건 없이 받기를 시작하고 바로 돌아온다. MarketMoodViewModel은 고치지 않는다. 있는 MarketDataRefresherTest의 경우는 줄이거나 바꾸지 않는다. (R-59, P-25)
- MarketDataRefresher는 처음 쓰일 때 만들어진다. 앱이 꺼져 있다가 다른 기능 때문에 깨어난 때에는 화면 없이 만들어지고, 그때에도 `request`로 받을 수 있어야 한다. 만들어진 것만으로는 받기가 시작되지 않는 지금 동작(키의 첫 값은 바뀐 것으로 보지 않음)은 그대로다. (R-59)
- RequestRefreshUseCase는 다른 기능이 받아 달라고 청하는 곳이다. `suspend operator fun invoke(condition: RefreshCondition): RefreshRequestResult`. `MarketDataRefresher.request(condition)`을 부르고 그 결과를 그대로 돌려준다. (R-59, R-60, P-22, P-24)
- ObserveRefreshStateUseCase는 다른 기능이 받는 중인지와 받기가 끝난 것을 아는 곳이다. `operator fun invoke(): Flow<RefreshState>`. `MarketDataRefresher.state`를 그대로 내준다. 모으기 시작하면 지금 상태가 먼저 나온다. `isRefreshing`이 참에서 거짓으로 바뀌면 받기가 끝난 것이고, 화면에서 시작한 받기와 청으로 시작한 받기, 성공과 실패가 모두 같다. 받기가 끝난 것을 알리는 사건을 따로 만들지 않는다. (R-61, P-22)
- 두 UseCase는 `@Inject constructor`로 MarketDataRefresher를 받는다. Hilt 모듈은 고치지 않는다.
- 다른 기능에 내놓는 것은 ObserveMoodUseCase, RequestRefreshUseCase, ObserveRefreshStateUseCase 셋과 그 모델(MoodAssessment, MarketStage, RefreshCondition, RefreshRequestResult, RefreshState, RefreshFailure)이다. MarketDataRefresher, RefreshMarketDataUseCase, MarketDataRepository는 다른 기능이 직접 쓰는 것이 아니다. 이것을 세 UseCase와 MarketDataRefresher의 주석에 적어 둔다. (P-22)
- 판정은 있는 ObserveMoodUseCase와 MoodAssessment를 그대로 내준다. 판정을 낼 수 있는 값이 받아져 있지 않으면 null, 있으면 표시 단계는 `displayedStage`, 기준 날짜는 `asOfDate`다. 장세와 기준 날짜만 담은 모델이나 UseCase를 따로 만들지 않는다. ObserveMoodUseCase는 화면이나 ViewModel, 받기 관리자에 기대지 않고 받아 둔 값과 규칙만으로 계산하므로 고칠 코드가 없다. 이번에는 그것을 확인하는 테스트만 더한다. (R-58, P-23)

**테스트 지시**
아래에서 따로 적지 않으면 상황은 이렇다: 시계 2026-11-24T01:00:00Z, 두 출처의 키 AVAILABLE, 받아 둔 값 없음, 시작한 때 없음, 두 출처가 데이터 A(`MoodTestData.dataA()`)를 돌려줌. "받기를 붙잡아 둔다"는 가짜 저장소의 fetch가 테스트가 풀어 줄 때까지 돌아오지 않게 하는 것이다. "fetch 횟수"는 가짜 저장소의 fetch가 불린 횟수이고, 받기 한 번에 출처마다 한 번씩 2다.

- R-58 (판정이 나는지, 표시 단계, 기준 날짜. ObserveMoodUseCase를 가짜 저장소와 규칙, MoodCalculator만으로 만든다. ViewModel과 MarketDataRefresher는 만들지 않는다)
  - Given 받아 둔 값 없음 / When ObserveMoodUseCase의 첫 값 / Then null
  - Given 받아 둔 값이 데이터 A / When 첫 값 / Then null이 아니고 `displayedStage`는 BULL, `asOfDate`는 2026-11-23
  - Given 받아 둔 값이 데이터 A에서 2026-11-23 하루만 나쁜 날(`badDay("2026-11-23")`: T10Y2Y -0.10, BAMLH0A0HYM2 7.00) / When 첫 값 / Then `displayedStage`는 BULL(그날의 `computedStage`는 NEUTRAL이다), `asOfDate`는 2026-11-23
  - Given 받아 둔 값이 데이터 A에서 2026-11-19, 2026-11-20, 2026-11-23이 나쁜 날 / When 첫 값 / Then `displayedStage`는 NEUTRAL, `asOfDate`는 2026-11-23
  - Given 받아 둔 값이 데이터 A에서 SPY가 통째로 빠짐 / When 첫 값 / Then null
  - Given 받아 둔 값 없음으로 모으기 시작해 null이 나옴 / When 받아 둔 값이 데이터 A로 바뀜 / Then `displayedStage`가 BULL인 판정이 이어서 나온다
- R-59 (청으로 받기가 시작되고 끝난 뒤에 돌아옴)
  - Given 받기를 붙잡아 둠 / When `request(WHEN_KEYS_AVAILABLE)`을 부르고, 그 뒤 받기를 풀어 줌 / Then 풀어 주기 전에는 `request`가 돌아오지 않았고 `state.isRefreshing`이 참이며 fetch 횟수는 2다. 풀어 준 뒤에는 `REFRESHED`가 돌아오고, 돌아온 때 `state.isRefreshing`은 거짓, `lastFailure`는 null이며, 받아 둔 값에 시리즈 여섯이 있고 `lastSuccessAt`은 2026-11-24T01:00:00Z다
  - Given 두 출처 모두 NO_CONNECTION / When `request(WHEN_KEYS_AVAILABLE)` / Then 예외 없이 `REFRESHED`가 돌아오고, 돌아온 때 `state.isRefreshing`은 거짓, `lastFailure`는 NO_CONNECTION이다
  - Given 받기를 붙잡아 둠 / When RequestRefreshUseCase(WHEN_KEYS_AVAILABLE)를 부르고, 그 뒤 받기를 풀어 줌 / Then 풀어 주기 전에는 돌아오지 않고, 풀어 준 뒤 `REFRESHED`가 돌아온다
- R-59 (이미 받는 중이면 겹쳐 시작하지 않고 그 받기가 끝나기를 기다림)
  - Given 받기를 붙잡아 두고 `refresh()`로 받기를 시작함(fetch 횟수 2) / When `request(WHEN_KEYS_AVAILABLE)`을 부르고, 그 뒤 받기를 풀어 줌 / Then 풀어 주기 전에는 `request`가 돌아오지 않고 fetch 횟수는 2 그대로다. 풀어 준 뒤 `JOINED`가 돌아오고 fetch 횟수는 2 그대로다
  - Given 받기를 붙잡아 두고 `refresh()`로 받기를 시작함(그 받기가 시작한 때를 2026-11-24T01:00:00Z로 적었다) / When `request(WHEN_NOT_RECENTLY_STARTED)`를 부르고, 그 뒤 받기를 풀어 줌 / Then `SKIPPED`가 아니라 `JOINED`가 돌아온다. fetch 횟수는 2 그대로다
  - Given 받기를 붙잡아 둠 / When `request(WHEN_KEYS_AVAILABLE)`을 두 코루틴에서 함께 부르고, 그 뒤 받기를 풀어 줌 / Then fetch 횟수는 2이고, 두 결과는 `REFRESHED` 하나와 `JOINED` 하나다
- R-59 (새로 만든 받기 관리자에서도 받음)
  - Given 받기 관리자를 새로 만들고 `refresh()`도 `onScreenShown()`도 부르지 않음. 키는 처음부터 모두 AVAILABLE / When 아무것도 하지 않고 기다림 / Then fetch 횟수는 0이다
  - Given 위와 같음 / When `request(WHEN_KEYS_AVAILABLE)` / Then `REFRESHED`가 돌아오고, 받아 둔 값에 시리즈 여섯이 있다
- R-59 (청한 쪽이 기다리기를 그만둬도 끝까지 받아 저장)
  - Given 받기를 붙잡아 두고, 따로 띄운 코루틴에서 `request(WHEN_KEYS_AVAILABLE)`을 부름 / When 그 코루틴을 취소한 뒤 받기를 풀어 줌 / Then 저장이 한 번 불리고 받아 둔 값에 시리즈 여섯이 있으며, `state.isRefreshing`은 거짓이다
- R-60 (세 조건의 판정: `WHEN_STALE`. 화면이 보이게 될 때의 조건과 같음)
  - Given 받아 둔 값 없음 / When `request(WHEN_STALE)` / Then `REFRESHED`
  - Given 받아 둔 값이 데이터 A, 성공한 때가 6시간 1분 전 / When `request(WHEN_STALE)` / Then `REFRESHED`
  - Given 받아 둔 값이 데이터 A, 성공한 때가 꼭 6시간 전 / When `request(WHEN_STALE)` / Then `SKIPPED`, fetch 횟수 0
  - Given 받아 둔 값이 데이터 A, 성공한 때가 5분 뒤(지금보다 뒤) / When `request(WHEN_STALE)` / Then `REFRESHED`
  - Given 받아 둔 값이 데이터 A에서 SPY가 통째로 빠짐(판정을 낼 수 없음), 성공한 때가 1시간 전 / When `request(WHEN_STALE)` / Then `REFRESHED`
  - Given 받아 둔 값 없음, FRED의 키 MISSING / When `request(WHEN_STALE)` / Then `SKIPPED`, fetch 횟수 0
- R-60 (세 조건의 판정: `WHEN_KEYS_AVAILABLE`)
  - Given 받아 둔 값이 데이터 A, 성공한 때가 1분 전, 시작한 때가 10초 전 / When `request(WHEN_KEYS_AVAILABLE)` / Then `REFRESHED`
  - Given FRED의 키 MISSING / When `request(WHEN_KEYS_AVAILABLE)` / Then `SKIPPED`, fetch 횟수 0
  - Given Twelve Data의 키 INVALID / When `request(WHEN_KEYS_AVAILABLE)` / Then `SKIPPED`, fetch 횟수 0
- R-60 (세 조건의 판정: `WHEN_NOT_RECENTLY_STARTED`)
  - Given 시작한 때가 1분 1초 전 / When `request(WHEN_NOT_RECENTLY_STARTED)` / Then `REFRESHED`
  - Given 시작한 때가 꼭 1분 전 / When `request(WHEN_NOT_RECENTLY_STARTED)` / Then `SKIPPED`, fetch 횟수 0
  - Given 시작한 때가 59초 전 / When `request(WHEN_NOT_RECENTLY_STARTED)` / Then `SKIPPED`, fetch 횟수 0
  - Given 시작한 때가 2시간 전, Twelve Data의 키 MISSING / When `request(WHEN_NOT_RECENTLY_STARTED)` / Then `SKIPPED`, fetch 횟수 0
  - Given 시작한 때 없음 / When `request(WHEN_NOT_RECENTLY_STARTED)`로 받기를 마친 뒤, 시계를 30초 뒤로 옮기고 다시 `request(WHEN_NOT_RECENTLY_STARTED)`, 시계를 31초 더 뒤로 옮기고(처음에서 1분 1초 뒤) 다시 `request(WHEN_NOT_RECENTLY_STARTED)` / Then 차례로 `REFRESHED`, `SKIPPED`, `REFRESHED`이고 fetch 횟수는 4다
  - Given `refresh()`로 받기를 마침(화면에서 시작한 받기) / When 시계를 30초 뒤로 옮기고 `request(WHEN_NOT_RECENTLY_STARTED)` / Then `SKIPPED`
  - Given `request(WHEN_NOT_RECENTLY_STARTED)`로 받기를 마친 뒤, 같은 가짜 저장소로 받기 관리자를 새로 만듦(앱이 꺼졌다 켜진 것) / When 시계를 30초 뒤로 옮기고 새 받기 관리자에 `request(WHEN_NOT_RECENTLY_STARTED)` / Then `SKIPPED`
- R-60 (받지 않았음을 돌려줌)
  - Given FRED의 키 MISSING / When RequestRefreshUseCase(WHEN_KEYS_AVAILABLE) / Then 기다리는 것 없이 `SKIPPED`가 돌아오고, `state.isRefreshing`은 한 번도 참이 되지 않으며 fetch 횟수는 0이다
- R-61 (받는 중이 풀림. ObserveRefreshStateUseCase가 내주는 값을 모은다)
  - Given 아무 받기도 없음 / When 첫 값 / Then `isRefreshing`은 거짓, `lastFailure`는 null
  - Given 받기를 붙잡아 두고 `refresh()`로 받기를 시작함(화면에서 시작한 받기) / When 받기를 풀어 줌 / Then 풀어 주기 전의 값은 `isRefreshing` 참, 풀어 준 뒤의 값은 `isRefreshing` 거짓에 `lastFailure` null
  - Given 받기를 붙잡아 두고 `request(WHEN_KEYS_AVAILABLE)`로 받기를 시작함(청으로 시작한 받기), 두 출처가 NO_CONNECTION으로 답하게 함 / When 받기를 풀어 줌 / Then 풀어 주기 전의 값은 `isRefreshing` 참, 풀어 준 뒤의 값은 `isRefreshing` 거짓에 `lastFailure` NO_CONNECTION
  - Given 받기를 붙잡아 두고 `request(WHEN_KEYS_AVAILABLE)`로 받기를 시작함, 두 출처가 데이터 A를 돌려줌 / When 받기를 풀어 줌 / Then 풀어 준 뒤의 값은 `isRefreshing` 거짓에 `lastFailure` null
- R-62 (시작한 때가 없거나 지금보다 뒤면 넘은 것으로 봄)
  - Given 시작한 때 없음(`getLastStartedAt`이 null) / When `request(WHEN_NOT_RECENTLY_STARTED)` / Then `REFRESHED`
  - Given 시작한 때가 10분 뒤(지금보다 뒤) / When `request(WHEN_NOT_RECENTLY_STARTED)` / Then `REFRESHED`

**확인 기준**
- 테스트 통과, 컴파일 오류 없음

## 3. 수동 확인

- R-59 (화면에서 받는 것이 전과 같음): Given 두 출처의 키가 저장되어 있고 전에 받아 둔 값이 있으며, 마지막으로 받은 지 6시간이 넘은 앱(예: 전날 받고 오늘 처음 여는 때) / When 앱을 열고, 받기가 끝나면 새로 고침을 누르고, 그 뒤 비행기 모드를 켜고 새로 고침을 누르고, 비행기 모드를 끄고 안내 띠의 "다시 시도"를 누른다 / Then 앱을 열면 판정이 바로 보이면서 새로 고침 버튼 자리에 진행 표시가 잠깐 돈다. 새로 고침을 누르면 진행 표시가 돌다가 판정이 그대로 보인다. 비행기 모드에서는 안내 띠 "새 값을 받지 못했습니다", "연결을 확인해 주세요. 받아 둔 값으로 보여 줍니다."가 보이고 판정은 그대로다. "다시 시도" 뒤에는 안내 띠가 없어진다. 이 변경 전과 달라진 것이 없다
기능: 시장 분위기
변경: 버전업-001 (CS-003)

## DECISION

### D-01 (전달 1) 시작한 때를 적어도 observeStored가 다시 내보내지 않게 하는 방법
단위 1. `MarketDataRepositoryImpl.observeStored()`에서 두 표를 합쳐 만든 StoredMarketData에 `distinctUntilChanged()`를 걸었다. Room이 `sync_stamps`의 다른 행(`market_mood_refresh_started`)이 바뀔 때 다시 내보내도, 받아 둔 값과 성공한 때가 같으면 걸러진다. SyncStampDao와 표는 고치지 않는다는 지시를 지키면서, 받기를 시작할 때마다 판정을 다시 계산하지 않게 하는 가장 작은 변경이다. 받아 둔 값이 실제로 바뀌면 값이 달라지므로 그대로 나간다. (R-62, P-26)

### D-02 (전달 1) 시작한 때를 적는 자리
단위 1. `RefreshMarketDataUseCase`가 첫머리에서 시계를 읽은 바로 뒤, 규칙을 읽기 전에 `saveLastStartedAt(받는 때)`을 부른다. 출처에 요청을 보내기 전이면 된다는 지시 안에서 가장 이른 자리라, 받기 도중 어디서 실패해도 시작한 때는 적혀 있다. 취소 말고 어떤 예외든 무시하고 받기를 이어 간다. (R-62, P-26)

### D-03 (전달 1) 받는 중인 받기를 기다리는 방법
단위 2. MarketDataRefresher가 받는 중인 받기를 `CompletableDeferred`로 들고, 받는 중인지 보는 것·받는 중으로 두는 것·받기를 끝내며 풀고 상태를 적는 것을 한 lock 안에서 한다. `refresh()`와 `request()`가 같은 내부 함수로 받기를 시작하거나 받는 중인 것에 붙는다. 그래서 청이 여럿 한꺼번에 와도 받기는 하나이고, `request`가 돌아온 때에는 `isRefreshing`이 거짓이고 결과가 이미 RefreshState에 적혀 있다. 받기는 앱 범위에서 `CoroutineStart.ATOMIC`으로 띄워, 시작 전에 취소되어도 받는 중이 풀리지 않은 채 남지 않게 했다. `refresh()`는 지금처럼 기다리지 않고 바로 돌아온다. (R-59, P-24, P-25)

### D-04 (전달 1) 조건을 따지는 코드
단위 2. `meets(condition)` 하나에 세 조건을 두고, `onScreenShown()`은 `meets(WHEN_STALE)`을 쓴다. 6시간과 1분은 같은 함수 `isOver(since, now, limit)`로 따진다: since가 없거나 지금보다 뒤면 넘은 것, 꼭 limit이면 넘지 않은 것. 키 확인은 세 조건 모두 먼저 한다. (R-60, P-25)

### D-05 (전달 1) "받아 달라는 청" 용어 주석의 자리
단위 2. 영어 이름 `RefreshRequest`와 똑같은 이름의 선언은 없다. 그 이름을 담아 처음 선언하는 `RefreshRequestResult` 위에 `// 용어: 받아 달라는 청`을 달았다.

### D-06 (전달 1) 내놓는 UseCase의 테스트 파일
단위 2. ObserveMoodUseCase(R-58), RequestRefreshUseCase(R-59, R-60의 UseCase 경우), ObserveRefreshStateUseCase(R-61)의 테스트를 `MarketMoodExposedUseCaseTest` 한 파일에 두었다. `request` 자체의 경우(R-59, R-60, R-62)는 `MarketDataRefresherTest`에 더했다.

## BACKLOG
없음

## SPEC-ISSUE
없음

## BUG
없음

## TECH-LIMIT
없음

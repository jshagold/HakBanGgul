기능: 홈 화면 위젯
변경: 신규 개발 (CS-003)

## DECISION

### D-01 (전달 1) 라이브러리 버전
단위 1. 지시한 버전(Glance `glance-appwidget` 1.2.0, WorkManager `work-runtime-ktx` 2.12.0)을 그대로 들였다. AGP 9.5.2, Kotlin 2.4.21, compileSdk 37.1에서 빌드와 테스트가 통과해 다른 버전을 고를 일이 없었다. (P-11)

### D-02 (전달 1) 아이콘과 진행 표시의 그림 리소스
단위 1. 새로 고침 아이콘은 그림 하나(`ic_widget_refresh`, 흰 선)로 두고, 놓는 곳에서 Glance의 `ColorFilter.tint`로 색(`textMuted`, `widgetRefreshDisabled`)을 입힌다. "색은 놓는 곳에서 정한다"는 지시에 맞추고 그림이 둘로 갈리지 않게 했다. 진행 표시는 한 장의 그림(`widget_progress_ring`: 바탕 고리와 사분원)을 `rotate` 그림(`widget_progress_spin`)으로 감싸 ProgressBar의 indeterminateDrawable로 돌린다. 한 바퀴 도는 시간은 지시에 없어 1초(`indeterminateDuration` 1000)로 정했다. 진행 표시 레이아웃은 그림 칸 크기가 달라 작은 위젯(16)과 큰 위젯(18)에 하나씩 두었다(`widget_progress_small`, `widget_progress_large`). (R-37, R-39, P-12)

### D-03 (전달 1) 위젯에서 앱을 여는 Intent의 플래그
단위 1, 단위 5. `AppLaunchIntents.marketMood`는 패키지의 실행 Intent에 `NEW_TASK | CLEAR_TOP | SINGLE_TOP`을 단다. 앱의 작업이 이미 있을 때 `NEW_TASK`만 달면 Android가 작업을 앞으로 가져오기만 하고 새 Intent를 넘기지 않아, "시장 분위기 화면을 보이라"는 표시가 MainActivity에 닿지 않는다. `CLEAR_TOP`과 `SINGLE_TOP`을 함께 달면 있는 MainActivity가 `onNewIntent`로 받는다(액티비티가 하나라 걷히는 다른 액티비티는 없다). 매니페스트에는 MainActivity에 `launchMode="singleTop"`을 더했다. (R-14, R-15, P-14)

### D-04 (전달 1) ObserveWidgetStateUseCase의 그대로 두기와 미루기
단위 2. 네 입력(판정, 받는 중, 키, 눌러 받는 중)을 `combine`으로 합치고, `channelFlow` 안에서 "내준 content"를 들고 고른다. 받지 못함을 미루는 0.5초는 `channelFlow` 안에서 띄운 코루틴의 `delay`로 재고, 그 사이에 다른 content가 골라지면 그 코루틴을 취소한다. 미루는 동안 받지 못함이 다시 들어와도 0.5초를 새로 재지 않는다(처음 받지 못함으로 바뀐 때부터 잰다). 모으는 쪽과 미뤄 둔 코루틴이 앱 범위(Dispatchers.Default)에서 서로 다른 스레드에서 돌 수 있어 상태는 Mutex 안에서만 만진다. 판정의 Flow는 `catch`로 null을 내고 끝나며, `combine`은 끝난 Flow의 마지막 값(null)으로 다른 입력의 변화에 계속 반응한다. (R-16, R-17, R-19, R-29, P-02, P-04)

### D-05 (전달 1) WidgetRefreshCoordinator에서 청이 예외로 끝난 때
단위 2. 취소가 아닌 예외로 청이 끝나면 결과가 없는 것으로 보고 1초를 기다리지 않고 바로 눌러 받는 중을 푼다. 지시의 "`SKIPPED`면 1초, 그 밖은 바로"에서 예외는 "그 밖"으로 보았다. 눌러 받는 중을 푸는 것은 `finally`에서 `NonCancellable`로 해, 1초를 기다리는 중에 취소되어도 풀린다. (R-41, R-43, P-09)

### D-06 (전달 1) 단위 2·3 테스트의 가짜
단위 2, 단위 3. 테스트의 기본 상황은 `WidgetTestFixture`(`app/src/test/.../homewidget/domain/`)가 만든다. 시장 분위기 테스트의 가짜(`FakeMarketDataRepository` 등)는 고치지 않고 그대로 가져다 쓴다.
- R-43 "받아 달라는 청이 예외로 끝나게 함": 지시의 예(키의 Flow가 예외를 던지는 ApiKeyProvider)는 받기 관리자가 만들어질 때 앱 범위에서 키를 모으는 코루틴도 예외로 끝나게 해, 테스트가 그 예외로 실패한다. 그래서 받기 관리자의 저장소를 "시작한 때를 읽다 예외를 던지는 저장소"(`ThrowingStartedAtRepository`, 위임 가짜)로 바꿔 MANUAL의 조건을 따지다 예외가 나게 했다.
- R-29 "판정의 Flow가 예외를 던짐": 위젯이 쓰는 ObserveMoodUseCase에만 예외를 던지는 규칙 저장소를 넣었다. 받기 관리자는 보통 규칙을 쓴다.
- R-03 "ObserveWidgetStateUseCase는 한 번만 모아진다": 위젯이 쓰는 ApiKeyProvider를 `observeAvailability`가 불린 횟수를 세는 위임 가짜로 바꿔, 출처 둘에 2번(한 번 모음)인지 본다.

### D-07 (전달 1) WidgetState를 HomeWidgetUiState로 바꾸는 곳
단위 3. 바꾸는 것은 `WidgetState.toUiState()` 확장 함수 하나로 두고, HomeWidgetStateHolder가 이것을 쓴다. 판정 데이터로 만들기 어려운 경우(WEAK, BEAR, 1월 5일, 12월 31일)는 테스트에서 이 함수를 따로 불러 확인했다. HomeWidgetStateHolder는 `stateIn(앱 범위, Eagerly, null)`로 한 번만 모은다. (R-03, R-06, R-26, P-05)

### D-08 (전달 1) 안내 문구의 줄 바꿈 (R-09)
단위 4. 안내 문구("키를 넣어 주세요", "데이터를 받지 못했습니다")를 그릴 때, 띄어쓰기가 아닌 두 글자 사이마다 보이지 않는 글자 WORD JOINER(U+2060)를 넣는다. 이 글자 앞뒤에서는 줄을 바꾸지 못하므로 줄은 띄어쓰기에서만 바뀐다. Glance의 Text에는 줄 바꿈 방식을 정하는 값이 없고, TextView의 `lineBreakWordStyle`은 Android 13부터라 최소 버전(8.0)을 채우지 못해 이 방법을 골랐다. 넣는 것은 위젯 쪽(`MoodWidgetLayout`의 `keepWordsTogether`)에서 그릴 때만 하고, UiState의 글과 strings.xml은 그대로다. 문구를 고르는 일이 아니라 그리는 방식이라 위젯 쪽에 두었다. (R-09)

### D-09 (전달 1) 비활성 버튼과 진행 표시의 누름
단위 4. 비활성과 진행 표시의 버튼 자리는 아무 일도 하지 않는 ActionCallback(`IgnoreWidgetClickAction`, RefreshWidgetAction과 같은 파일)으로 누름을 받는다. 누름을 받지 않으면 카드의 앱 열기로 넘어가기 때문이다. Glance의 람다 누름은 세션이 끝난 뒤에는 위젯을 다시 그리게 해, 아무 일도 하지 않는 데에는 ActionCallback이 맞다. (R-37, R-39)

### D-10 (전달 1) 뒤에서 받기 작업의 모양
단위 4.
- 주기 작업은 첫 차례도 6시간 뒤에 돌게 했다(`setInitialDelay` 6시간). WorkManager의 주기 작업은 넣자마자 한 번 도는데, PERIODIC은 조건 `WHEN_KEYS_AVAILABLE`이라 위젯을 놓을 때마다 6시간 조건과 상관없이 받게 된다. 놓은 때 받을지는 FIRST_PLACED(`WHEN_STALE`)가 정하게 했다. (R-20, R-21)
- FIRST_PLACED 작업은 이름 하나로 넣고, 연결을 기다리며 남아 있는 것이 있으면 그대로 둔다(KEEP). 작은 위젯과 큰 위젯의 첫 개를 잇달아 놓아도 하나만 돈다. (R-20, R-22)
- MANUAL 작업은 Android 12 이상에서 급한 작업(expedited, 한도를 넘으면 보통 작업)으로 넣어 절전 중이어도 곧 돌게 했다. 그보다 낮은 버전에서 급한 작업은 알림이 붙는 포그라운드 서비스로 돌아 매니페스트와 알림 채널이 더 필요해, 보통 작업으로 넣는다. 위젯을 누르면 앱 프로세스가 깨어 있으므로 조건 없는 작업은 WorkManager가 곧바로 돌린다. (R-38, P-07)
- Worker는 MANUAL이면 진행 표시가 풀린 상태가 HomeWidgetStateHolder에 들어올 때까지 최대 5초 기다린 뒤 다시 그린다. (R-39, R-40)

### D-11 (전달 1) 위젯이 상태를 받는 방법
단위 4. SmallMoodWidget, LargeMoodWidget은 `provideGlance`에서 HomeWidgetStateHolder의 null이 아닌 첫 값을 기다린 뒤, `provideContent` 안에서 같은 `state`를 `collectAsState`로 받아 그린다. 위젯 세션이 살아 있는 동안 상태가 바뀌면 바로 다시 그려지고, 세션이 끝난 뒤에는 HomeWidgetUpdater의 `updateAll`이 다시 그리게 한다. 두 Receiver는 한 파일(`MoodWidgetReceivers.kt`)에 두었다. `onDisabled`의 세는 일은 suspend라 `goAsync`로 알림을 붙잡아 둔 채 끝낸다. (R-03, R-16, P-05, P-08)

### D-12 (전달 1) 위젯에서 열렸다는 알림을 AppNavHost에 넘기는 모양
단위 5. MainActivity가 알릴 때마다 1씩 늘리는 숫자(`showMarketMoodSignal`)를 AppNavHost에 넘기고, AppNavHost는 이 값이 바뀌면(0보다 크면) `popBackStack(시장 분위기, inclusive = false)`로 위에 쌓인 설정 화면을 걷는다. 이미 시장 분위기 화면이면 걷을 것이 없어 아무 일도 없다. 화면을 돌려 다시 만들어질 때는 `savedInstanceState`가 있으면 알리지 않고, 숫자도 0에서 다시 시작해 같은 Intent로 두 번 걷지 않는다. (R-14, R-15, P-14)

### D-13 (전달 1) 용어 주석의 자리
- 홈 화면 위젯: `HomeWidget`과 똑같은 이름의 선언은 없다. 이 이름을 담아 처음 선언하는 `HomeWidgetUiState` 위에 달았다.
- 키 안내 `KeyGuide`, 받지 못함 `NotReceived`: WidgetContent 안의 선언 위에 달았다.
- 작은 위젯 `SmallMoodWidget`, 큰 위젯 `LargeMoodWidget`: 각 클래스 위에 달았다.

## BACKLOG
없음

## SPEC-ISSUE
없음

## BUG
없음

## TECH-LIMIT
없음

기능: 시장 분위기
변경: 버전업-003 (CS-005)

## DECISION

### D-01 (전달 1) 시험용 규칙을 고쳐 쓰는 도우미를 MoodTestData에 둔다
단위 1. 매매 판단의 기간 목록이 생겨 `copy(positionWindow = …)`로 고치던 곳을 바꿀 수 없게 되어, MoodTestData에 확장 함수 넷을 두었다. `ValuationRules.withWindow(id, window)`(기간 하나의 위치 기간만 바꿈), `ValuationRules.withSingleWindow(window)`(위치 기간 window인 메인 기간 하나만 둠), `MoodRules.withMainWindow(window)`(메인 기간의 위치 기간만 바꿈), `MoodRules.withRegimeWeights(stocks, bonds)`(장세 묶음의 비중만 바꿈). ValuationCalculator의 있던 테스트는 지시대로 `withSingleWindow`로 위치 기간을 메인 하나로 주고, 기본 규칙을 쓰던 두 경우(종가 2034/2033개, 실질금리 1279/1278개)는 기대 값이 그대로 나오게 기본 규칙에 위치 기간 1260인 메인 하나를 두었다. 위젯의 `BUFFER_RULES`는 `withMainWindow(101)`에 평균 일수 1이다.

### D-02 (전달 1) 지시에 적히지 않은 있던 테스트 넷의 Given과 기대 값을 단위 1의 데이터에 맞춘다
단위 1. 데이터 B와 장세의 공식이 바뀌고 섹터가 판정의 조건에서 빠져, 지시에 따로 적히지 않은 있던 테스트 넷이 뜻은 그대로인 채 값만 어긋났다. 뜻을 지키는 쪽으로 이렇게 바꿨다. (1) MarketDataRefresherTest "R-20 판정을 낼 수 없으면 1시간 전에 받았어도 받는다": XLK를 빼던 것을 SPY를 빼는 것으로(XLK가 없어도 판정이 있다). (2) RefreshMarketDataUseCaseTest "R-23 받아 둔 값이 바뀌면 다시 계산": 기대 값 1.0, 0.4125 → 0.766667, 0.15. (3) 같은 파일 "R-41 출처마다 한 번씩…": 단위 3에 적힌 기대 값(종가 넷 모두 2017-11-23)으로. (4) 같은 파일 "R-42 모두 성공했는데 판정을 낼 수 없으면": XLK를 하루만 두던 것을 SPY를 마지막 69평일만 두는 것으로(DFII10이 없는 경우는 따로 있는 테스트라 겹치지 않게 했다). ViewModel 테스트에서 주가 위치의 시험용 시리즈(TEST_PRICE)를 쓰던 경우는 그 시리즈 대신 SPY의 종가를 바꾸고, R-68 글 테스트는 장세도 낼 수 있게 종가 앞에 100을 70개 두었다(근거 넷은 마지막 다섯 종가로 정해진다).

### D-03 (전달 1) 장세의 시리즈 항목이 받아 둔 값에 없으면 그 기간의 장세는 null이다
단위 1. RegimeCalculator. 시리즈 항목(채권 신호 둘)의 시리즈가 받아 둔 값에 없거나 비어 있으면 어느 날도 계산할 수 없으므로 곧바로 null을 돌려준다. 점수 기준과 입력이 어긋난 항목(예: TargetClose에 HighIsGood)은 규칙에 없어 따로 다루지 않았다. 평균 대비·고점 대비·평균의 기울기는 입력과 상관없이 그 대상의 종가로 낸다.

### D-04 (전달 1) 한 벌의 모델 셋은 한 파일에 둔다
단위 2. MarketOverview, TargetOverview, PeriodContent를 `feature/marketmood/domain/MarketOverview.kt` 한 파일에 두었다. 셋이 함께만 쓰이기 때문이다. 상세 화면 ViewModel 넷도 같은 까닭으로 `presentation/DetailViewModels.kt` 한 파일에 두었다(단위 5).

### D-05 (전달 1) ObserveMarketOverviewUseCase의 흐름
단위 2. 규칙과 `observeStored().map { it.values }.distinctUntilChanged()`를 합쳐 다시 `distinctUntilChanged()` → `conflate()` → 계산 → `flowOn(넘겨받은 디스패처)` → `shareIn(앱 범위, WhileSubscribed(5초), replay = 1)` 순서로 만든다. 계산 중에 온 값은 conflate가 마지막 것 하나만 남긴다. 계산이 멈출 수 없는 순수 계산이라 mapLatest(취소)를 쓰지 않았다. 기본 규칙과 9년치 값(대상 열넷)으로 잰 계산 시간은 데스크톱 JVM에서 한 벌에 약 170ms였다.

### D-06 (전달 1) 성공한 때는 남은 요청 수를 세어 마지막 요청의 저장에 싣는다
단위 3. RefreshMarketDataUseCase. 받기 한 번에 보낼 요청 수(FRED 한 번 + 묶음 수)를 처음에 정해 두고, 요청이 끝날 때마다 Mutex 안에서 하나씩 뺀다. 0이 되는 요청이 마지막이고, 그때까지 모든 요청이 성공하고 저장에 실패한 적이 없으면 받아 둔 값에 그 요청의 값을 덮어 판정을 낼 수 있는지 보고, 낼 수 있으면 그 요청의 `save`에 성공한 때를 싣는다(값이 없으면 빈 값으로). 키 거절로 보내지 않는 묶음은 그 수만큼 빼고, 모두 성공한 것이 아니므로 성공한 때는 적지 않는다. Twelve Data 묶음은 `fetchYearsBySeries()`에서 출처가 TWELVE_DATA인 시리즈를 그 순서(대상의 종가가 targets() 순서로 맨 앞)대로 `batchSize`개씩 나눈 것이다. 장이 끝나지 않은 날 거르기는 각 요청의 `finish` 안에서 그때의 Clock 시각으로 한다. `onBroadMarketReceived`는 FRED와 첫 묶음의 저장이 둘 다 성공한 뒤 같은 Mutex 안에서 한 번 부른다.

### D-07 (전달 1) 시장 분위기 테스트의 가짜에 더한 것
단위 3. FakeMarketDataRepository에 세 때의 맵(`stamps`), `saveStampCalls`, 읽기 실패·적기 실패를 켜는 `failOnReadStamp`·`failOnSaveStamp`(때마다), 넘긴 값에 따라 저장을 실패시키는 `failOnSaveIf`(첫 묶음의 저장만 실패시키는 R-108 경우)를 더했다. `callOrder`에는 "fetch", "save", "saveStamp(STARTED)"처럼 남긴다. `lastStartedAt`은 STARTED를 읽고 쓰는 속성으로 남겼다. MarketMoodFakes.kt에 가상 시간에 묶은 `SchedulerClock(시작 시각, 스케줄러)`과 기본 나눠 받기 `DEFAULT_PACING`을 두었다. RefreshMarketDataUseCaseTest는 모두 SchedulerClock으로 바꿨고, 요청마다 보낸 때(가상 시간)를 테스트 안에서 적는다.

### D-08 (전달 1) 저장이 둘로 나뉘어 바뀐 있던 테스트의 기대 값
단위 3. 시험용 규칙에서도 FRED와 묶음을 따로 저장해 받기 한 번의 `save`가 둘이 되었다. 저장 횟수 1을 보던 있던 테스트 넷(MarketDataRefresherTest R-55, R-59, WidgetRefreshCoordinatorTest R-34, R-43)의 기대 값을 2로 바꿨다. 한 테스트에서 두 번 받는 WidgetRefreshCoordinatorTest "R-39 PERIODIC과 FIRST_PLACED는 눌러 받는 중을 바꾸지 않는다"는 두 번째 받기가 첫 묶음 전에 65초를 기다려 "받는 중에 fetch가 넷"이라는 뜻이 바뀌므로, 지시대로 두 번째 받기 전에 TWELVE_DATA_REQUESTED를 비웠다. 나머지 두 번 받는 테스트는 65초를 기다려도 뜻이 같아 그대로 두었다.

### D-09 (전달 1) 받는 조건과 전체 시장의 값을 받음의 구현
단위 4. WHEN_NOT_RECENTLY_STARTED는 STARTED와 FINISHED 가운데 null이 아닌 것의 최댓값을 "늦은 때"로 쓰고 있던 `isOver`(없거나 지금보다 뒤면 넘음, 꼭 1분이면 넘지 않음)로 가른다. 알림 함수는 lock 안에서 `running === run`일 때(아직 이 받기가 받는 중일 때)만 `broadMarketReceived`를 참으로 바꾼다. 받기가 끝날 때 상태를 통째로 `RefreshState(false, 실패 원인, false)`로 둔다.

### D-10 (전달 1) MoodUiMapper가 밖에 내놓는 함수 다섯
단위 5. `mainScreen(overview, isRefreshing)`, `regimeDetail(overview, targetId)`, `pricePositionDetail(overview, targetId)`, `rateBurdenDetail(overview)`, `sector(overview, targetId)`. 상세·섹터 함수는 대상을 찾지 못하거나 그 화면의 메인 콘텐츠를 낼 수 없으면 null을 돌려주고, ViewModel은 키 상태와 함께 이것으로 BackToMain을 가른다(`detailState` 도우미). 주식 묶음인지는 규칙에서 그 묶음의 항목이 TargetClose를 쓰는지로 가른다. 장세의 다른 기간 카드의 근거 비율은 규칙에서 AboveAverage·FromHigh·AverageSlope를 쓰는 항목을 찾아 그 근거를 꺼낸다. 첫 화면의 장세 카드는 `mood`의 장세 값으로 만든다(전체 시장의 메인 장세와 같은 값이다). 이동 경로의 인자 이름 `TARGET_ID_ARG`("targetId")는 DetailViewModels.kt에 두고 AppNavHost가 같은 상수를 쓴다.

### D-11 (전달 1) 문구의 이름
단위 5. 더한 문구: `period_months`, `period_years`, `target_*` 열넷, `regime_broad_market`, `regime_sector`, `target_with_symbol`, `regime_card_title`, `stock_group_suffix`, `link_evidence_and_periods`, `evidence_title`, `evidence_basis`, `other_periods_title`, `other_periods_description`, `evidence_above_average`, `evidence_from_high`, `evidence_average_slope`, `regime_period_label`, `regime_period_evidence`, `valuation_period_label`, `period_unavailable`, `valuation_period_unavailable`, `regime_period_unavailable`, `sectors_title`, `sectors_description`, `sectors_header_name`, `sectors_header_regime`, `sectors_header_price`, `sector_loading`, `sector_failed`, `sector_as_of`. 숫자를 끼우는 문구로 바꾼 것: `valuation_title`, `valuation_description`, `price_evidence_average`, `price_evidence_drawdown`. 지운 것: `mood_evidence_title`, `price_position_subtitle`(대상 이름과 ETF 기호는 `target_with_symbol`로 적는다). 단위 1에서 지운 것: `indicator_broad_market` 등 넷과 그 `_suffix` 넷, `evidence_price`. 더한 것: `indicator_above_average`, `indicator_from_high`, `indicator_average_slope`.

### D-12 (전달 1) 있던 ViewModel 테스트를 옮긴 곳
단위 5. 장세 카드, 묶음·항목 줄, 매매 판단 카드(R-65, R-66, R-67, R-68, R-69, R-70)를 보던 MarketMoodViewModelTest의 경우는 경우와 기대 값을 줄이지 않고 MoodUiMapperTest로 옮겼다(근거 줄은 주가 위치·금리 부담 화면의 메인 카드에서 본다). 눈금 위치의 있던 경우 둘(위 경계 0.6, 현재 상태 -0.5)도 새 데이터의 같은 상황으로 옮겼다. 상세 화면과 섹터 화면의 ViewModel은 DetailViewModelsTest에서 본다.

### D-13 (전달 1) 첫 화면의 상태에 전체 시장의 대상 ID를 싣는다
단위 6. 첫 화면의 장세 카드와 주가 위치 카드의 링크가 `onOpenRegime("broad_market")`, `onOpenPricePosition("broad_market")`을 부르도록, 화면에 글자 그대로 적지 않고 MoodUiState에 `broadMarketId`(규칙의 전체 시장 ID)를 더해 그 값을 넘긴다. 규칙의 ID가 바뀌어도 화면을 고치지 않게 하려는 것이다.

### D-14 (전달 1) 화면의 틀과 이동의 구현
단위 6. 상세 화면 넷은 `DetailScaffold`(위쪽 줄 + 위아래로 미는 본문)와 `ReturnToMainEffect`(BackToMain이 되면 `LaunchedEffect`로 한 번 부름)를 함께 쓴다. Undecided일 때 위쪽 줄의 이름은 주가 위치·금리 부담 화면은 그 화면 이름, 장세·섹터 화면은 빈 글이다(이름이 대상에 따라 정해지기 때문이다). 뒤로 가기 단추는 그 화면의 백스택 항목이 맨 위일 때만 하나를 걷는다(`currentBackStackEntry?.id == entry.id`). 설정 화면도 같은 방식으로 바꿨다(동작은 같다). `onReturnToMain`과 위젯에서 열 때는 `popBackStack(market_mood, inclusive = false)`다. 다른 기간 카드(장세)의 기간 글은 "{기간 글} · 종합 점수 {점수}"이고 점수만 점수의 색(보통 굵기)으로 적는다. 금리 부담 화면의 드문 모습은 지시대로 다른 시안의 카드를 따라 미리보기 하나에 모았다.

## BACKLOG

### D-15 (전달 1) 매매 판단의 위치를 날마다 처음부터 세는 계산을 줄이면 좋다
ValuationCalculator의 `positions`는 날마다 창(최대 1260개)을 처음부터 다시 센다. 이번 변경으로 대상 열넷 × 기간 넷만큼 돌아, 기본 규칙과 9년치 값에서 한 벌 계산이 데스크톱 JVM으로 약 170ms였다(오래된 휴대폰에서는 몇 배 걸릴 수 있다). 점수를 낼 수 있는 첫날 이전의 위치는 단계 계산에 쓰이지 않으므로, 필요한 날부터만 세거나 정렬된 창을 이어 쓰면 줄일 수 있다. 고칠 곳: `feature/marketmood/domain/ValuationCalculator`.

### D-17 (전달 1) 이동 평균의 합을 날마다 처음부터 내는 계산은 결과를 바꾸지 않고는 줄일 수 없다
D-16을 고친 뒤 한 벌 계산의 나머지 시간은 대부분 이동 평균이다. 장세(RegimeCalculator의 평균 대비·평균의 기울기, 대상 열넷 × 기간 셋)가 약 70ms, 매매 판단의 두 괴리(ValuationCalculator의 `deviations`, 긴 평균 756일)가 남은 주가 위치 시간의 대부분이다. 앞의 합에 새 값을 더하고 빠지는 값을 빼는 누적합으로 바꾸면 크게 줄지만, 더하는 차례가 달라 소수 끝자리가 달라지고(예: 같은 종가가 이어질 때 괴리가 0이 아니라 1e-16이 됨) 위치의 "같은 값" 세기와 점수가 바뀔 수 있다. 그래서 이번에는 고치지 않았다. 줄이려면 결과가 얼마나 같아야 하는지(예: 소수 여섯 자리로 반올림한 뒤 비교)를 정해야 한다. 고칠 곳: `feature/marketmood/domain/RegimeCalculator`, `DailySeries.averageAt`, `ValuationCalculator.deviations`.

## SPEC-ISSUE

없음

## BUG

### D-16 (전달 1) 앱을 켤 때 본문이 한동안 비어 있고 받는 중 표시도 없다
증상: 수동 확인 R-23 실패. 앱을 켤 때 위쪽 줄 아래 본문이 한동안 비어 있다가 나오고, 그동안 받는 중 같은 표시가 없어 고장처럼 보였다.
재현 조건: 실제 키 둘이 저장되고 받아 둔 값이 있는 기기에서 앱을 켠다(받아 둔 값이 6시간이 넘어 켜자마자 받기도 시작되는 때).
원인: (1) 첫 화면은 한 벌(MarketOverview)이 처음 와야 본문이 정해지는데, 이번 변경으로 한 벌이 대상 열넷 × 기간마다의 장세와 매매 판단을 모두 계산해 오래 걸렸다. (2) MarketMoodViewModel은 한 벌과 키 상태와 받기 상태를 `combine`으로 합쳐, 한 벌이 오기 전에는 처음 상태(Undecided, 새로 고침 비활성)에 머물렀다. 그래서 그동안 받기가 시작되어도 새로 고침 자리에 진행 표시가 나오지 않았다.
고친 것: (1) ValuationCalculator의 위치를 날마다 창(최대 1260개)을 처음부터 세지 않고, 값의 순위로 만든 펜윅 트리에 창의 값을 하나씩 넣고 빼며 이어 세게 했다. 순위 표는 시리즈마다 한 번만 만들어 기간 넷이 함께 쓴다. 비교는 전과 같이 `<`, `==`로 세고(-0.0과 0.0은 같은 값), 창에 NaN이 있는 날은 전처럼 내지 않는다. 기본 규칙과 9년치 평일 값(대상 열넷)으로 계산한 모든 대상·기간의 주가 위치 점수와 표시 단계, 금리 부담 점수가 고치기 전과 같았고, 있던 테스트는 그대로 통과했다. 한 벌 계산 시간(데스크톱 JVM, 같은 값으로 다섯 번 잰 것, 첫 번은 JIT가 데워지는 시간이라 뺌): 고치기 전 약 165ms(166, 166, 165, 165), 고친 뒤 약 105ms(116, 108, 106, 97). 그 가운데 대상 열넷의 주가 위치 계산은 약 76ms에서 약 22ms로 줄었다. 나머지는 D-17. (2) MarketMoodViewModel이 한 벌을 받기 전에도 키 상태와 받기 상태로 상태를 만들게 했다(한 벌의 Flow에 "아직 오지 않음"을 먼저 내보낸다). 한 벌이 오기 전에는 본문이 Undecided이고, 그동안 받는 중이면 새로 고침 버튼이 REFRESHING(진행 표시)이다. 키가 모두 쓸 수 있지 않으면 한 벌을 기다리지 않고 키 안내 카드다. 새 화면 상태는 더하지 않았다. 테스트: MarketMoodViewModelTest에 R-22 둘(한 벌이 오기 전 받는 중이면 REFRESHING, 키가 없으면 키 안내 카드)을 더했다.

## TECH-LIMIT

없음

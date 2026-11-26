기능: 시장 분위기
변경: 버전업-002 (CS-004)

## DECISION

### D-01 (전달 1) Twelve Data의 묶은 요청들을 동시에 보낸다
단위 2. MarketDataRepositoryImpl에서 시작 날짜가 같은 종목끼리 묶은 요청(기본 규칙에서 SPY 하나, QQQ·XLK·SMH 하나)을 FRED와 같이 동시에 보내고, 결과는 요청 순서대로 모아 앞 요청의 실패를 먼저 돌려준다. 출처마다 10초 제한이 받기 전체에 걸리므로, 차례로 보내면 9년치 SPY 요청이 제한 안의 시간을 더 쓴다. 요청 가운데 하나라도 실패하면 받은 값을 하나도 돌려주지 않는 것은 지시대로다.

### D-02 (전달 1) 저장하는 시리즈 수가 바뀐 기존 테스트의 기대 값을 맞췄다
단위 1·2. 데이터 A에 시리즈 넷이 더해지고 받는 시리즈가 열 개가 되어, 기존 테스트에서 "저장한 시리즈 6개"를 보던 곳을 10개로 바꿨다(MarketDataRefresherTest 세 곳, 위젯의 WidgetRefreshCoordinatorTest 한 곳). RefreshMarketDataUseCaseTest의 FRED 시리즈 목록(fredIds)에 DFII10·DGS10·DFF·CPIAUCSL을 더하고, 테스트 이름 "시리즈 여섯"을 "시리즈 열"로 바꿨다. 위젯 쪽은 테스트 데이터를 맞춘 것이고 위젯 코드는 고치지 않았다.

### D-03 (전달 1) 매매 판단 점수는 소수 여섯 자리로 먼저 반올림한 뒤 정수로 반올림한다
단위 3. ValuationCalculator. 위치의 평균 × 100이 계산 오차로 12.4999…가 되어 12.5를 올려야 할 때 내려가는 일을 막는다. 장세의 현재 상태를 소수 여섯 자리로 반올림하는 것과 같은 방식이다.

### D-04 (전달 1) 위치 기간이 2보다 작거나 평균 일수가 1보다 작으면 매매 판단을 낼 수 없는 것으로 본다
단위 3. ValuationCalculator. 위치의 식이 (위치 기간 - 1)로 나누므로 규칙이 잘못 들어와도 0으로 나누지 않게 한다. 기본 규칙과 시험용 규칙에는 걸리지 않는다.

### D-05 (전달 1) 소비자물가에 같은 달의 값이 여럿이면 날짜가 이른 것 하나만 쓴다
단위 3. ValuationCalculator. FRED는 달마다 1일의 값 하나를 주므로 실제로는 일어나지 않지만, 같은 날짜가 여럿이면 앞의 것 하나를 두는 DailySeries와 같은 방향으로 정했다.

### D-06 (전달 1) 테스트에서 ObserveMoodUseCase에는 그 테스트의 스케줄러를 쓰는 UnconfinedTestDispatcher를 넘긴다
단위 3. 기존 테스트의 흐름(값이 바로 나옴)을 바꾸지 않으려는 것이다. MarketMoodExposedUseCaseTest의 R-58 테스트들은 ObserveMoodUseCase를 클래스 필드로 만들고 있어 runTest의 스케줄러와 다른 스케줄러를 쓰게 되므로, TestScope에서 만드는 함수로 바꿨다. R-23(계산용 디스패처에서) 테스트만 StandardTestDispatcher를 넘긴다.

### D-07 (전달 1) ValuationCalculator 테스트의 "작은 규칙"은 1년 전으로 보는 날 수를 1로 줄였다
단위 3. 적은 수의 값으로 금리 부담(1년 전과의 차이)까지 낼 수 있게 하려는 것이다. 1년 전과의 차이를 보는 R-79 테스트만 365로 둔다. 점수열을 만드는 도우미(valuesForScores, closesForScores, weekdaysEndingAt)는 ViewModel 테스트도 쓰도록 MoodTestData에 두었다. 주가 위치의 점수열은 짧은 평균과 긴 평균을 둘 다 2로 두어 두 괴리가 같게 하고, 괴리에서 종가를 거꾸로 만들었다.

### D-08 (전달 1) 1년 전과의 차이가 0인지는 MoodTextFormatter.scoreTone(값, 2)로 가른다
단위 4. MarketMoodViewModel. 지시의 "적는 값(소수 두 자리로 반올림한 값)으로 가른다"와 같은 반올림을 쓰는 기존 함수다. 날짜가 붙는 값(실질금리, 10년물, 기준금리)은 있는 `evidence_value_with_date`를 값 자리에 UiText로 끼운다.

### D-09 (전달 1) ViewModel 테스트는 주가 위치의 대상을 시험용 시리즈로 바꾼 규칙으로 점수를 만든다
단위 4. MarketMoodViewModelTest. 주가 위치의 대상을 `TEST_PRICE`(TWELVE_DATA, DAILY_CLOSE)로 바꾸고 위치 기간 101, 평균 일수 1인 규칙을 쓴다. SPY는 장세의 항목이기도 해서, SPY의 값을 바꾸면 장세(200일 평균 등)도 함께 바뀌기 때문이다. 금리 부담은 장세 항목이 아닌 DFII10을 직접 바꾼다. 괴리·낙폭·3개월 수익률의 글(R-68)은 짧은 평균 2, 긴 평균 3, 3개월 3인 규칙에서 다섯 날의 종가를 거꾸로 계산해 만들었다.

### D-10 (전달 1) 종합 판정 카드의 눈금을 칸 폭·색·이름을 받게 고쳐 매매 판단 카드와 함께 쓴다
단위 5. MarketMoodScreen의 Gauge. 종합 판정 카드는 폭 0.25의 네 칸을 넘겨 모양이 그대로다. 회색 안내 상자(Notice)도 종합 판정 카드의 다른 날 안내와 함께 쓴다. 판정이 보이는 미리보기의 높이는 시안 화면의 높이로 늘리고, 두 카드의 값은 각 시안 화면의 예시 값을 옮겼다.

### D-11 (전달 1) 문구 이름
단위 4. strings.xml에 더한 문구: `valuation_title`, `valuation_description`, `mood_evidence_title`, `price_position_name`, `price_position_subtitle`, `rate_burden_name`, `rate_burden_subtitle`, `valuation_score`, `valuation_price_low`, `valuation_middle`, `valuation_price_high`, `valuation_rate_low`, `valuation_rate_high`, `valuation_notice_low`, `valuation_notice_high`, `price_evidence_average`, `price_evidence_drawdown`, `rate_evidence_year_change`, `rate_evidence_rising`, `rate_evidence_falling`, `rate_evidence_year_same`, `rate_evidence_rates`, `rate_evidence_inflation`. 공용 테마에 더한 이름: AppColors `valuationLow`·`valuationMiddle`·`valuationHigh`·`valuationGaugeLow`·`valuationGaugeMiddle`·`valuationGaugeHigh`, AppTypography `valuationScore`·`sectionDescription`, AppDimens `sectionTitlePaddingTop`·`sectionTitlePaddingHorizontal`·`valuationCardPadding`·`valuationEvidencePaddingTop`.

## BACKLOG

### D-12 (전달 1) 1년 전과의 차이를 Domain에서 반올림해 두면 좋다
실질금리의 차이를 Double 빼기로 내므로 2.79 - 2.785가 0.004999…가 되어, 실제 차이가 0.005인데 "1년 전과 같음"으로 적힐 수 있다(0.995와 1.0은 0.005000…이라 "+0.01%p"). 받은 값이 소수 두 자리이므로 RateBurdenAssessment.yearChange를 BigDecimal로 빼서 넘기면 화면의 반올림이 받은 값과 맞는다. 고칠 곳: feature/marketmood/domain의 ValuationCalculator.

## SPEC-ISSUE

### D-13 (전달 1) R-72 여섯째 날의 3개월 수익률 기대 값이 지시의 식과 맞지 않는다
단위 3 테스트 지시 R-72 둘째 경우(종가 10, 12, 11, 13, 12, 15, 3개월 2)의 Then은 "3개월 수익률 15 ÷ 11 - 1 = +0.3636"이다. 지시의 식(기준 날짜의 종가 ÷ 그보다 `threeMonthDays`개 앞의 주가 위치의 날의 종가 - 1, R-72·P-33)으로는 여섯째 날에서 2개 앞은 넷째 날(13)이라 15 ÷ 13 - 1 = +0.1538이다. 첫째 경우(다섯째 날, 12 ÷ 11 - 1)는 식과 맞는다. 지시의 식대로 구현하고, 이 경우의 테스트는 15 ÷ 13 - 1로 확인해 두었다. 둘째 경우의 기대 값을 바로잡거나, 식이 다른 뜻이면 알려 주어야 한다.

### D-14 (전달 1) tasks.md의 전달 기록 표가 비어 있다
tasks.md 머리말의 전달 기록 표에 줄이 없고 상태가 "작성 중"이다. decisions.md 항목 제목의 "전달 N"을 정할 수 없어 1로 적었다.

## BUG

없음

## TECH-LIMIT

없음

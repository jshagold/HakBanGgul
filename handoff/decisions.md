기능: 홈 화면 위젯
변경: 버전업-001 (CS-004)

## DECISION

### D-01 (전달 1) 완충 규칙과 완충 데이터는 WidgetTestFixture에 두고, 실질금리의 마지막 102개 평일을 날마다 덮어 만든다
단위 1. 완충 규칙 `BUFFER_RULES`, 완충 데이터 `bufferData()`, 데이터 A의 매매 판단 값 `MIDDLE_50`(WidgetValuation(50, MIDDLE))을 WidgetTestFixture의 companion에 두었다. 단위 2의 HomeWidgetStateHolderTest도 같은 상황을 쓰기 때문이다. 완충 데이터는 `MoodTestData.weekdaysEndingAt(2026-11-23, 102)`의 날마다 `set`으로 `valuesForScores(listOf(30, 35))`의 값을 덮고, 그 앞의 날은 2.00 그대로 둔다.

### D-02 (전달 1) 단계 이름 고르기는 HomeWidgetUiState.kt 안의 private 함수로 둔다
단위 2. 주가 위치와 금리 부담을 가르는 private enum(`ValuationKind`)과 단계 → 문구 id를 고르는 함수를 `toUiState`와 같은 파일에 두었다. 지시대로 MarketMoodViewModel에서 가져오지 않고 위젯 쪽에 한 벌 둔 것이다. 더한 문구의 이름: `widget_price_position_short`("주가"), `widget_rate_burden_short`("금리"), `widget_preview_price_position_score`("74 · 비싼 편"), `widget_preview_rate_burden_score`("98 · 높음").

### D-03 (전달 1) R-46의 "상태 어디에도 안내 줄 문구가 없다"는 상태의 모든 글의 문구 id를 모아 확인한다
단위 2. HomeWidgetStateHolderTest. 첫 줄, 단계 이름, 날짜 줄, 두 줄의 이름·줄인 이름·점수와 단계·단계 이름에서 문구 id를 모으고(args 안의 UiText까지), 그 안에 `valuation_notice_low`, `valuation_notice_high`가 없는지 본다.

### D-04 (전달 1) 매매 판단 줄 조각과 테마 값의 이름
단위 3. MoodWidgetLayout에 두 줄 묶음 `WidgetValuationLines(stage, short)`와 줄 하나 `WidgetValuationRow`를 두었다. 큰 위젯은 `short = false`(이름 + 점수와 단계), 작은 위젯은 `short = true`(줄인 이름 + 단계 이름)로 부른다. 줄 안의 두 글은 Row에서 세로 가운데를 맞춘다. 줄 수를 1로 주려고 `WidgetText`에 `maxLines` 매개변수(기본은 제한 없음)를 더했고, 있던 부름은 그대로다. 단계 → 색은 `stageColor` 옆에 `valuationStageColor`로 두었다. 테마에 더한 이름: AppTypography `widgetValuationName`(12 Normal)·`widgetValuationStage`(12 Bold), AppDimens `widgetSmallValuationGap`(10)·`widgetLargeValuationGap`(4)·`widgetValuationNameGap`(3).

### D-05 (전달 1) 작은 위젯 미리 보기의 단계 쪽 글은 있는 문구를 쓴다
단위 3. widget_small_preview.xml의 "비싼 편", "높음"은 단위 2에서 더한 예시 문구가 아니라 있는 `valuation_price_high`, `valuation_rate_high`를 쓴다. 문구가 같으므로 새로 더하지 않았다. 큰 위젯 미리 보기는 단위 2에서 더한 "74 · 비싼 편", "98 · 높음"을 쓴다.

### D-06 (전달 1) 앱을 바꾼 때의 작업 이름
단위 4. WidgetRefreshScheduler의 APP_UPDATED 작업 이름은 `hakbanggul.homewidget.refresh.app_updated`이고, 같은 이름의 작업이 있으면 그대로 둔다(`ExistingWorkPolicy.KEEP`). WidgetRefreshWorker는 넘겨받은 까닭의 이름으로 WidgetRefreshTrigger를 찾으므로 고치지 않아도 APP_UPDATED를 처리한다.

## BACKLOG

없음

## SPEC-ISSUE

없음

## BUG

없음

## TECH-LIMIT

없음

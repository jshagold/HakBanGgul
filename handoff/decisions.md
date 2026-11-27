기능: 시장 분위기
변경: 버전업-004 (CS-005)

## DECISION

### D-01 (전달 1) 받은 때의 행을 앞부분으로 읽는 법
단위 1의 SyncStampDao.getByPrefix는 `WHERE substr(name, 1, length(:prefix)) = :prefix`로 짰다. 지시대로 LIKE를 쓰지 않아 시리즈 ID나 앞부분에 든 `_`, `%`가 아무 글자와 맞지 않는다. 표의 모양과 스키마 버전은 그대로다. (P-56, P-11)

### D-02 (전달 1) 규칙에 FRED 시리즈가 없을 때의 fredFetchedAt
단위 1의 FetchFreshness.fredFetchedAt은 규칙에 FRED 시리즈가 하나도 없으면 null을 돌려준다(가장 이른 때가 없음). 그래서 그런 규칙이면 받기 관리자의 WHEN_STALE은 늘 받는다. 지금 규칙에는 FRED 시리즈가 여섯 있어 해당하지 않고, 받을지 모를 때 받는 쪽이 "아무것도 보이지 않는 것이 문제"라는 개요에 맞다. (R-110, P-58)

### D-03 (전달 1) 받은 값이 비어 있는 성공 요청
단위 1의 RefreshMarketDataUseCase는 성공한 요청이 돌려준 값이 비어 있으면(장이 끝나지 않은 날을 거른 뒤가 아니라 출처가 아무 시리즈도 돌려주지 않은 때) 지금처럼 save를 부르지 않는다. 그래서 받은 때도 적히지 않아 다음에 다시 받는다. 받은 값이 없는데 받은 때만 적으면 받아 둔 값이 없는 시리즈를 최신으로 볼 수 있어서다. (R-109, P-56)

### D-04 (전달 1) 진행이 더해진 상태를 통째로 견주던 테스트
단위 2에서 RefreshState에 받을 차례인 ETF와 아직 보내지 않은 종목 수가 더해져, 상태가 나올 때마다 진행도 함께 바뀐다. 상태 목록을 통째로 견주던 있던 R-108 테스트("FRED와 첫 묶음이 저장되면 받는 중인 채 참이 되고 끝나면 거짓이다")는 있던 세 필드만 남겨 같은 것이 잇달아 나오면 하나로 둔 목록으로 견준다. 기대 값은 그대로다. (R-108, P-59)

### D-05 (전달 1) 받기를 여러 번 하는 있던 테스트의 전제 지키기
단위 1의 "있던 테스트를 맞추는 법"대로 ETF를 받는 것을 전제한 테스트는 Given에서 ETF의 받은 때를 비웠다. 다만 MarketDataRefresherTest의 "R-60 WHEN_NOT_RECENTLY_STARTED 청으로 시작한 받기에서 1분을 넘어야 다시 받는다"는 첫 받기가 ETF의 받은 때를 적어 셋째 받기가 ETF를 받지 않게 되므로, 셋째 청 바로 앞에서 ETF의 받은 때를 비웠다. 기대 값(요청 수 4)은 그대로다. (R-60, P-58)

### D-06 (전달 1) 계산 중인 상세 화면의 대상 글(ETF 기호)
단위 3의 DetailUiState.Calculating은 대상을 UiText로 받는데, 장세 화면과 섹터 화면의 대상은 ETF 기호("SMH") 그 자체다. 기호를 UiText로 싣기 위해 글을 그대로 끼우기만 하는 문구 `detail_target_symbol`(`%1$s`, 번역하지 않음)을 strings.xml에 더했다. 지시에 적힌 다섯 문구 밖의 것이지만 화면에 새 글을 보이는 것은 아니다. (R-111, P-60)

### D-07 (전달 1) 위쪽 줄을 규칙만으로 만드는 함수
단위 3의 MoodUiMapper에 regimeCalculating(rules, targetId), pricePositionCalculating(rules, targetId), rateBurdenCalculating(), sectorCalculating(rules, targetId)를 두었다. 넷 모두 DetailUiState.Calculating을 돌려주고, 대상을 규칙에서 찾지 못하면 null이다(ViewModel이 BackToMain으로 바꾼다). Shown일 때 화면이 그리는 글과 같은 문구(regime_broad_market, regime_sector, price_position_name, target_with_symbol, rate_burden_name, 대상 이름)를 쓴다. detailState는 계산 중일 때 부를 함수(calculating)를 더 받는다. (R-111, P-46, P-47)

### D-08 (전달 1) 한 벌을 냈는지 적는 때
단위 3의 ObserveMarketOverviewUseCase는 앱 범위에서 나눠 주는 계산이 Ready를 만든 때 "냈음"을 적는다(받는 쪽에 닿은 때가 아니라). 그래서 계산이 끝난 뒤 모으기 시작한 쪽은 Calculating 없이 들고 있던 Ready를 바로 받는다. 아직 내지 않았을 때 모으기 시작한 쪽은 규칙의 첫 값으로 Calculating을 먼저 받는다. (R-04, R-111, P-60)

### D-09 (전달 1) MainActivity가 뒤에서 미리 받기를 넣는 법
단위 5의 MainActivity는 Hilt 필드 주입으로 CloseRefreshScheduler와 앱 범위(@ApplicationScope CoroutineScope)를 받아, onCreate에서 그 범위로 ensureScheduled()를 부른다. 취소가 아닌 예외는 삼킨다. Activity가 이미 @AndroidEntryPoint라 EntryPoint를 따로 두지 않았다. 화면을 돌려 다시 만들어질 때도 부르지만 KEEP이라 작업이 그대로다. (R-115, P-62)

### D-10 (전달 1) 뒤에서 미리 받는 작업의 예외 처리
단위 5의 CloseRefreshWorker는 받기 청과 다음 차례 잇기를 따로 감싼다. 청에서 취소가 아닌 예외가 나도 다음 차례를 잇고, 잇기에서 예외가 나도 성공으로 끝낸다(잇지 못한 때는 다음에 앱을 열 때 ensureScheduled가 다시 넣는다). 취소는 다시 던져 잇지 않는다. (R-115, P-62)

## BACKLOG
없음

## SPEC-ISSUE
없음

## BUG
없음

## TECH-LIMIT
없음

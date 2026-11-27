기능: 시장 분위기
변경: 버전업-004 (CS-005)

## DECISION

### D-01 (전달 1) 받은 때의 행을 앞부분으로 읽는 법
단위 1의 SyncStampDao.getByPrefix는 `WHERE substr(name, 1, length(:prefix)) = :prefix`로 짰다. 지시대로 LIKE를 쓰지 않아 시리즈 ID나 앞부분에 든 `_`, `%`가 아무 글자와 맞지 않는다. 표의 모양과 스키마 버전은 그대로다. (P-56, P-11)

### D-02 (전달 1) 규칙에 FRED 시리즈가 없을 때의 fredFetchedAt
단위 1의 FetchFreshness.fredFetchedAt은 규칙에 FRED 시리즈가 하나도 없으면 null을 돌려준다(가장 이른 때가 없음). 그래서 그런 규칙이면 받기 관리자의 WHEN_STALE은 늘 받는다. 지금 규칙에는 FRED 시리즈가 여섯 있어 해당하지 않고, 받을지 모를 때 받는 쪽이 "아무것도 보이지 않는 것이 문제"라는 개요에 맞다. (R-110, P-58)

### D-03 (전달 1) 받은 값이 비어 있는 성공 요청
단위 1의 RefreshMarketDataUseCase는 성공한 요청이 돌려준 값이 비어 있으면(장이 끝나지 않은 날을 거른 뒤가 아니라 출처가 아무 시리즈도 돌려주지 않은 때) 지금처럼 save를 부르지 않는다. 그래서 받은 때도 적히지 않아 다음에 다시 받는다. 받은 값이 없는데 받은 때만 적으면 받아 둔 값이 없는 시리즈를 최신으로 볼 수 있어서다. (R-109, P-56)

## BACKLOG
없음

## SPEC-ISSUE
없음

## BUG
없음

## TECH-LIMIT
없음

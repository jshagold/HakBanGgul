기능: 설정
변경: 버전업-001 (CS-002)

## DECISION

### D-01 (전달 1) 테마에 더한 값의 이름
단위 1. 있는 이름처럼 쓰는 곳을 이름으로 삼았다.
- 색(AppColors): `inputDisabledBorder`, `inputDisabledBackground`, `filledButtonDisabledBackground`, `checkingBackground`, `checkingContent`(확인 중 글과 고리의 도는 부분), `checkingTrack`.
- 글자(AppTypography): `inputLabel`, `maskedKey`, `fullKey`, `issueLink`, `issueNote`. 글자 사이 값을 넣으려고 스타일을 만드는 내부 함수에 `letterSpacing` 인자를 더했다(있는 스타일의 값은 그대로다).
- 간격과 크기(AppDimens): 설정 화면의 위쪽 줄(`settingsTopBarPaddingHorizontal`, `settingsTopBarGap`, `backIcon`), 본문(`settingsBodyPaddingVertical`), 출처 카드(`sourceCardPadding`, `sourceCardGap`, `sourceCardHeaderGap`), 입력 덩어리(`inputGroupGap`), 입력 칸(`inputHeight`, `inputBorder`, `inputBorderError`, `inputPaddingHorizontal`), 저장된 키 칸(`savedKeyMinHeight`, `savedKeyBorder`, `savedKeyBorderError`, `savedKeyPaddingStart`, `savedKeyPaddingEnd`, `savedKeyTextPaddingVertical`, `savedKeyTextButtonGap`), 칸 아래 안내(`fieldNoticeIcon`, `fieldNoticeIconGap`, `fieldNoticeIconTop`), 발급 안내(`issueLinkPaddingVertical`, `issueNoteGap`, `issueButtonGap`), 카드 안의 버튼(`cardButtonHeight`, `saveButtonPaddingHorizontal`, `deleteButtonPaddingHorizontal`, `deleteButtonBorder`), 확인 중 표시(`checkingHeight`, `checkingPaddingHorizontal`, `checkingGap`, `checkingProgress`, `checkingProgressStroke`, `checkingProgressSlot`).
- 표의 "출처 카드 안쪽 여백 16"은 값이 같은 `groupCardPadding`(묶음 카드)이 있지만 쓰는 곳이 달라 `sourceCardPadding`으로 따로 두었다. 한쪽을 고칠 때 다른 쪽이 따라 바뀌지 않게 하기 위해서다.
- 표에 없는 값은 더하지 않았다. 출처 이름(17/700)은 있는 `groupName`, 안내(14/400, 1.6배)는 `smallLoose`, 모서리 10은 `shapes.button`을 쓴다.

### D-02 (전달 1) 가려진 키를 견주는 있는 테스트
단위 2. 있는 테스트 "R-07 네 글자 이하인 키는 모두 가린다"(키 `abcd`)는 지우지 않고 기대 값만 점 12개로 고쳤다. 테스트 지시의 R-07 네 경우(32·5·4·2글자)와 R-05의 "저장된 키 없음" 경우는 새 테스트로 더했다. 같은 내용의 R-17 테스트도 있는 경우를 줄이지 않으려고 그대로 두었다. R-03 테스트는 이름과 설명을 `SourceLabels`의 값과 견주게 고쳤다.

### D-03 (전달 1) 키보드가 올라올 때 밀어 보기
단위 3. 밀어 보는 본문의 아래 여백에 내비게이션 바와 키보드 중 큰 높이를 넣고(`WindowInsets.navigationBars.union(WindowInsets.ime)`), AndroidManifest의 MainActivity에 `android:windowSoftInputMode="adjustResize"`를 더했다. Android 11(API 30) 아래에서는 이 설정이 있어야 키보드 높이가 화면에 전달된다. 입력 칸을 누르면 Compose가 초점을 받은 칸을 보이는 곳으로 옮긴다(R-10). 지시의 "만들거나 고칠 것"에 없는 파일이지만, 지시한 동작에 필요한 설정 한 줄이라 고쳤다.

### D-04 (전달 1) 테두리가 있는 칸의 안쪽 여백은 테두리 안쪽에서 잼
단위 3. 시안의 HTML은 `box-sizing: border-box`라 안쪽 여백이 테두리 안쪽에서 시작한다. Compose의 `border`는 자리를 차지하지 않으므로, 입력 칸·저장된 키 칸·키 지우기 버튼의 안쪽 여백에 테두리 굵기를 더했다. 그래서 거절된 칸(테두리 2)에서는 글이 시안처럼 1만큼 안쪽으로 들어간다.

### D-05 (전달 1) 키 전체와 가려진 키의 줄 넘김
단위 3. 키 전체는 `LineBreak.Simple`로 그려, 빈칸이 없는 긴 키도 칸 너비에서 글자 사이로 넘어가고 잘리지 않는다. 가려진 키는 늘 17글자라 한 줄로 그리고 넘치는 일이 없게 했다.

### D-06 (전달 1) "키 발급받기"의 누르는 영역
단위 3. 누르는 영역은 글자와 그 위아래 여백 6까지다. 시안의 HTML에서는 링크가 왼쪽 덩어리의 너비만큼 늘어나지만, 글이 없는 빈 곳을 눌러 브라우저가 열리지 않게 하려고 글 크기로 두었다. 보이는 모양은 같다. 시장 분위기 화면의 "FRED API 이용 약관" 링크와 같은 방식이다.

### D-07 (전달 1) 뒤로 가기에서 맨 위가 설정 화면인지 가리는 법
단위 3. AppNavHost의 onBack은 `navController.currentBackStackEntry?.destination?.route`가 `settings`일 때만 `popBackStack()`을 부른다. 화면 이동의 경로는 글자 경로라 경로 이름으로 견준다.

### D-08 (전달 1) 미리보기의 예시 키
단위 3. 미리보기 넷은 시안과 같은 예시 값을 쓴다. 가려진 키는 `SettingsViewModel.mask`로 만들어 화면 상태와 같은 모양이 되게 했다. "확인 중과 거절된 키"의 FRED 키는 시안에 끝 네 글자(`7b2e`)만 있어, 끝이 `7b2e`인 지어낸 키를 넣었다.

## BACKLOG
없음

## SPEC-ISSUE
없음

## BUG
없음

## TECH-LIMIT
없음

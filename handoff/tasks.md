# Tasks: 시장 분위기 신규 개발

기능: 시장 분위기
변경: 신규 개발 (CS-002)
기준 Plan: 1.0.0
상태: 구현 중

| 전달 | 날짜 | 이유 |
|---|---|---|
| 1 | 2026-11-24 | 최초 전달 |

## 1. 개요
시장 분위기 화면에서 여섯 항목의 값을 받아 지금 장의 단계(불장, 중립, 약세, 물장)를 판정하고, 그 근거(묶음 점수, 항목 점수, 근거 숫자)와 함께 보여 주는 기능을 새로 만든다. 판정은 참고 표시이며 매매 신호가 아니다.
판정은 저장하지 않는다. 받아 둔 일별 값과 규칙을 순수 계산기에 넣어 볼 때마다 처음부터 계산한다. 규칙(묶음, 항목, 점수 기준, 비중, 단계 경계, 유지 일수)은 데이터로 두어, 항목을 더하는 일이 기본 규칙에 한 줄과 이름 문구를 더하는 것으로 끝나게 한다.
데이터를 받는 첫 기능이라 공용 테마, 키를 붙이는 인터셉터, 출처 오류를 오류 종류로 바꾸는 코드, 공용 DB, 화면 이동도 이번에 만든다. 설정 기능의 동작과 설정 화면의 배치는 바꾸지 않는다(테마를 옮기면서 색이 조금 달라지는 것은 괜찮다).

## 2. 구현 단위

### 단위 1: 빌드 설정과 공용 화면 요소 (테마, 아이콘, 딱지, 출처 문구, 화면 글)

**만들거나 고칠 것**
- 수정 `gradle/libs.versions.toml`, `app/build.gradle.kts`
- 수정 `app/src/main/AndroidManifest.xml`
- 신규 HakBanGgulTheme, AppColors, AppTypography, AppShapes (core/ui/theme)
- 삭제 `ui/theme/Color.kt`, `ui/theme/Theme.kt`, `ui/theme/Type.kt`
- 신규 AppIcons (core/ui)
- 신규 KeyStatusBadge, KeyBadgeKind (core/ui)
- 신규 SourceLabels (core/ui)
- 신규 UiText (core/ui)
- 수정 MainActivity (맨 위 패키지), SettingsScreen (feature/settings/presentation): 테마를 가져오는 줄만

**지시**
- 라이브러리를 더한다: Room(런타임, 코루틴 지원, KSP 컴파일러), Jetpack Navigation의 Compose용 라이브러리. 인터셉터 테스트에 가짜 서버가 필요하면 테스트 의존성으로 OkHttp의 MockWebServer를 더한다. 버전은 지금 빌드 도구와 맞는 최신 안정 버전으로 하고 `gradle/libs.versions.toml`에 모은다.
- `AndroidManifest.xml`에 `android.permission.INTERNET`을 적는다. 라이브러리가 합쳐 주는 권한에 기대지 않는다.
- 공용 테마를 `core/ui/theme`에 만들고 프로젝트를 만들 때 생긴 `ui/theme`의 세 파일은 지운다. 밝은 화면 하나만 둔다. 기기의 배경 색을 따라가는 기능(dynamic color)과 어두운 화면은 두지 않는다. 글꼴은 앱에 넣지 않고 기기의 기본 글꼴을 쓴다. `HakBanGgulTheme(content)`는 아래 값을 Material3의 색·글자·모양에도 맞춰 넣어, Material 부품이 같은 색을 쓰게 한다. (P-17, R-19)
- 테마의 값은 시안 `https://claude.ai/artifact/LuRSF8bSPDqruAbnUSfye9`에서 뽑은 것이다. 시안의 1px은 1dp(글자는 1sp)로 옮긴다. 화면은 값을 직접 적지 않고 테마에서 가져온다. 화면을 만들다 테마에 없는 값이 필요하면 시안에서 가져와 테마에 더한다. (P-17, R-07, R-19)

| 색 | 값 | 쓰는 곳 |
|---|---|---|
| 바탕 | #F4F5F7 | 화면 바탕, 다른 날 안내 상자의 바탕, 저장된 키 칸의 바탕 |
| 카드 | #FFFFFF | 카드, 위쪽 줄 |
| 테두리 | #E3E6EA | 카드 테두리, 구분선, 위쪽 줄 아래 선, 진행 표시의 바탕 고리 |
| 글 | #1B1F24 | 기본 글, 아이콘, 눈금의 표시 |
| 흐린 글 | #4A5360 | 덧붙이는 글, 근거 숫자, 날짜, 맨 아래 안내 |
| 비활성 | #8A93A0 | 누를 수 없는 아이콘, 테두리만 있는 버튼의 테두리 |
| 강조 | #2457C5 | 채운 버튼의 바탕, 링크, 진행 표시의 도는 부분. 채운 버튼 위의 글은 #FFFFFF |
| 경고 글 | #9F1D14 | 안내 띠의 글, 알림 아이콘 |
| 경고 바탕 | #FCE9E6 | 안내 띠의 바탕 |
| 오류 테두리 | #B3261E | 입력 칸의 오류 테두리 |
| 단계 글자: 불장 / 중립 / 약세 / 물장 | #C5221F / #4A5360 / #3D7BD9 / #1D4AA8 | 장세의 단계 이름 |
| 눈금 칸: 물장 / 약세 / 중립 / 불장 | #1D4AA8 / #8AB4F0 / #B7BEC8 / #D93025 | 눈금의 네 칸 |
| 점수: + / 0 / - | #C5221F / #4A5360 / #1D4AA8 | 항목 점수, 묶음 점수, 종합 점수 |
| 딱지 키 없음: 바탕 / 글 | #E9ECF0 / #4A5360 | 키의 상태 딱지 |
| 딱지 저장됨: 바탕 / 글 | #E4ECFB / #1D4AA8 | 키의 상태 딱지 |
| 딱지 틀림: 바탕 / 글 | #FCE9E6 / #9F1D14 | 키의 상태 딱지 |

| 글자 | 크기 / 굵기 | 쓰는 곳 |
|---|---|---|
| 단계 이름 | 48 / 700, 줄 높이 1.2배 | 장세의 단계 이름 |
| 제목 | 20 / 700 | 위쪽 줄의 앱 이름, 안내 카드의 제목, 묶음 점수 |
| 묶음 이름 | 17 / 700 | 묶음 이름 |
| 항목 점수 | 16 / 700 | 항목 점수 |
| 본문 | 15 / 400 | 종합 점수 줄. 항목 이름과 출처 이름은 15 / 500, 버튼 글은 15 / 700 |
| 작은 본문 | 14 / 400 | "지금 장은", 안내 카드의 설명(줄 높이 1.6배). 안내 띠의 첫 줄과 "다시 시도"는 14 / 700 |
| 덧붙임 | 13 / 400 | 기준 날짜, 비중, 근거 숫자, 덧붙이는 글, 안내 띠의 둘째 줄(줄 높이 1.5배), 다른 날 안내(줄 높이 1.55배). 딱지의 글은 13 / 500 |
| 맨 아래 안내 | 12 / 400, 줄 높이 1.6배 | 맨 아래 안내. 약관 링크는 12 / 500 |
| 눈금 이름 | 11 / 400 | 눈금 아래의 단계 이름 |

| 모서리와 간격 | 값 |
|---|---|
| 모서리 | 카드 16, 버튼과 입력 칸 10, 안내 띠 12, 다른 날 안내 상자 10, 딱지는 알약 모양, 눈금 막대 5, 눈금의 표시 2 |
| 화면 | 가장자리 여백 16, 카드 사이 16 |
| 카드 | 테두리 1. 종합 판정 카드와 안내 카드는 안쪽 여백 20. 묶음 카드는 위와 좌우 16, 아래 4 |
| 줄 | 항목 줄과 출처 줄의 위아래 여백 12, 줄 위에 구분선 1. 이름과 그 아래 글 사이 2 |
| 위쪽 줄 | 높이 56, 왼쪽 여백 16, 오른쪽 여백 4. 아이콘 버튼의 누르는 영역 44, 아이콘 22 |
| 버튼 | 채운 버튼 높이 48. 딱지의 안쪽 여백은 위아래 4, 좌우 10 |
| 진행 표시 | 고리의 굵기 2.5. 위쪽 줄에서는 22, 받는 중 카드에서는 28 |

- AppIcons는 아이콘 라이브러리를 들이지 않고 시안의 모양을 ImageVector로 그린다. 모두 24×24 좌표다. (P-18, R-03, R-17)
    - `Refresh`: 선 굵기 2, 끝과 꺾이는 곳은 둥글게. 경로 `M20 12a8 8 0 1 1-2.6-5.9`와 `M20 4v5h-5`.
    - `Menu`: 채운 원 셋. 중심 (12,5), (12,12), (12,19), 반지름 1.8.
    - `Alert`: 선 굵기 2, 끝은 둥글게. 중심 (12,12) 반지름 9인 원, 경로 `M12 7v6`, `M12 16.5v.5`.
    - 설정의 키 보기 아이콘(`feature/settings/presentation/KeyVisibilityIcons.kt`)은 이번에 옮기지 않는다.
- KeyBadgeKind는 딱지의 종류다: `NONE`(키 없음) / `SAVED`(저장됨) / `INVALID`(틀림). `KeyStatusBadge(kind: KeyBadgeKind, modifier)`는 있는 문구 `key_badge_none`, `key_badge_saved`, `key_badge_invalid`와 위 표의 딱지 색으로 딱지를 그린다. 글과 색을 늘 함께 쓴다. (P-19, R-15, R-52)
- SourceLabels는 출처마다의 문구 리소스를 준다: `fun nameRes(source: DataSource): Int`, `fun descriptionRes(source: DataSource): Int`. 있는 문구 `source_twelve_data_name`, `source_twelve_data_description`, `source_fred_name`, `source_fred_description`을 가리킨다. 문구를 새로 만들지 않는다. (P-19, R-52)
- UiText는 화면에 보일 글 하나다: `data class UiText(val resId: Int, val args: List<Any> = emptyList())`. args에는 글자, 숫자, 다른 UiText가 들어갈 수 있다. 글자로 푸는 Composable 함수 `UiText.asString(): String`을 함께 둔다(args 안의 UiText를 먼저 풀어 끼운다). ViewModel이 Android의 Context 없이 문구를 고르기 위한 것이다. (P-16)
- 설정 화면은 이번에 고치지 않는다. MainActivity와 SettingsScreen이 테마를 가져오는 줄만 새 위치로 바꾼다. 설정 화면의 딱지와 출처 문구를 KeyStatusBadge와 SourceLabels로 바꾸는 일은 다음 변경에서 한다.

**테스트 지시**
- 없음. 있는 테스트(경계 검사, 설정)가 그대로 통과해야 한다.

**확인 기준**
- 테스트 통과, 컴파일 오류 없음

### 단위 2: 공용 네트워크 (오류 종류, 키를 붙이는 인터셉터, 요청 결과 바꾸기)

**만들거나 고칠 것**
- 신규 DataFetchError (core/model)
- 신규 DailyValue (core/model)
- 신규 ApiKeyUnavailableException (core/network)
- 신규 ApiKeyInterceptor (core/network)
- 신규 SourceCallResult (core/network)
- 신규 SourceCallExecutor (core/network)
- 수정 SourceResponseClassifier (core/network)
- 수정 NetworkModule, NetworkQualifiers (core/di)
- 신규 TimeModule (core/di)

**지시**
- DataFetchError는 출처에서 받기가 실패한 종류다: `KEY_MISSING` / `KEY_INVALID` / `KEY_REJECTED` / `RATE_LIMITED` / `NO_CONNECTION` / `OTHER`. (P-05, R-42)
- DailyValue는 하루의 값 하나다: `data class DailyValue(val date: LocalDate, val value: Double)`.
- ApiKeyUnavailableException은 IOException이다. 키가 없거나 틀림이라 요청을 보내지 않았음을 알린다. `source: DataSource`와 `availability: ApiKeyAvailability`(`MISSING` 또는 `INVALID`)를 갖는다.
- ApiKeyInterceptor는 OkHttp 인터셉터이고 출처 하나를 맡는다. 생성자로 `source: DataSource`, ApiKeyProvider, SourceResponseClassifier를 받는다. (P-04)
    - 요청마다 `ApiKeyProvider.getKey(source)`를 부른다. `Available(키)`면 키를 붙여 보낸다. Twelve Data는 `Authorization: apikey <키>` 헤더, FRED는 `api_key=<키>` 쿼리다. (R-51)
    - `Missing`이면 요청을 보내지 않고 `ApiKeyUnavailableException(source, MISSING)`을, `Invalid`면 `ApiKeyUnavailableException(source, INVALID)`을 던진다. (R-51)
    - 응답의 HTTP 상태와 본문을 `SourceResponseClassifier.classify`에 넣어 `KEY_REJECTED`가 나오면 `ApiKeyProvider.reportRejected(source, 요청에 붙였던 키)`를 부른다. 한도 넘음, 서버 오류 같은 그 밖의 실패는 알리지 않는다. 본문은 뒤에서 다시 읽을 수 있게 읽는다(응답을 그대로 돌려준다). (R-50, R-44)
    - `getKey`와 `reportRejected`는 suspend 함수라 인터셉터 안에서는 막는 호출로 부른다.
    - 기능의 Api, Repository, UseCase, ViewModel은 키를 읽거나 넘겨받지 않는다.
- SourceResponseClassifier에 `fun isRateLimited(source: DataSource, httpStatus: Int, body: String?): Boolean`을 더한다. HTTP 상태가 429이거나, Twelve Data이고 본문이 JSON이며 `status`가 `"error"`이고 `code`가 429면 참이다. 있는 `classify`와 SourceResponseKind는 바꾸지 않는다. (P-06, R-42)
- SourceCallResult는 요청의 결과다: `Success(body: String)` / `Failure(error: DataFetchError)`.
- SourceCallExecutor는 출처에 보내는 요청 하나를 돌려 결과로 바꾼다: `suspend fun execute(source: DataSource, call: suspend () -> Response<ResponseBody>): SourceCallResult`. 생성자로 SourceResponseClassifier를 받는다. (P-05, R-42)
    - 응답이 오면 HTTP 상태와 본문(성공이면 body, 실패면 errorBody의 글)을 본다. `classify`가 `ACCEPTED`면 `Success(본문)`, `KEY_REJECTED`면 `Failure(KEY_REJECTED)`, `OTHER_FAILURE`면 `isRateLimited`가 참일 때 `Failure(RATE_LIMITED)`, 아니면 `Failure(OTHER)`.
    - ApiKeyUnavailableException은 availability에 따라 `Failure(KEY_MISSING)` / `Failure(KEY_INVALID)`. 그 밖의 IOException(연결 안 됨, 시간 초과)은 `Failure(NO_CONNECTION)`. 그 밖의 예외는 `Failure(OTHER)`.
    - 코루틴 취소는 삼키지 않고 그대로 넘긴다(단위 5의 10초 제한이 동작해야 한다).
    - 이 판정은 이곳 한 곳에만 둔다. 다른 기능도 같은 출처에서 받을 때 이것을 쓴다.
- NetworkModule과 NetworkQualifiers에 키를 붙이는 출처별 Retrofit(`@TwelveDataAuthorizedRetrofit`, `@FredAuthorizedRetrofit`)과 SourceCallExecutor를 더한다. 키를 붙이는 Retrofit은 있는 OkHttpClient에 그 출처의 ApiKeyInterceptor를 더한 클라이언트를 쓰고, 주소는 DataSourceEndpoints에서 얻는다. 있는 `@TwelveDataRetrofit`, `@FredRetrofit`은 그대로 둔다. 설정의 키 확인 요청은 인터셉터를 거치지 않는다. (P-04)
- TimeModule은 지금 시각을 주는 `java.time.Clock`(`Clock.systemUTC()`)을 싱글턴으로 제공한다.

**테스트 지시**
- R-51 (키를 붙임, 키가 없으면 보내지 않음)
    - Given 출처 TWELVE_DATA, getKey가 `Available("tdkey")` / When 인터셉터로 요청을 보낸다 / Then 나간 요청에 헤더 `Authorization: apikey tdkey`가 있다
    - Given 출처 FRED, getKey가 `Available("fredkey")` / When 요청을 보낸다 / Then 나간 요청의 쿼리에 `api_key=fredkey`가 있다
    - Given 출처 FRED, getKey가 `Missing` / When 요청을 보낸다 / Then availability가 MISSING인 ApiKeyUnavailableException이 나고 요청은 나가지 않는다
    - Given 출처 TWELVE_DATA, getKey가 `Invalid` / When 요청을 보낸다 / Then availability가 INVALID인 ApiKeyUnavailableException이 나고 요청은 나가지 않는다
- R-50 (거절된 키와 함께 알림, 그 밖은 알리지 않음)
    - Given 출처 TWELVE_DATA, 키 `tdkey`, 응답이 HTTP 200에 본문 `{"code":401,"message":"**apikey** parameter is incorrect or not specified.","status":"error"}` / When 요청을 보낸다 / Then `reportRejected(TWELVE_DATA, "tdkey")`가 한 번 불리고, 부른 쪽이 받은 응답의 본문은 그대로 읽힌다
    - Given 출처 FRED, 키 `fredkey`, 응답이 HTTP 400에 본문 `{"error_code":400,"error_message":"Bad Request.  The value for variable api_key is not registered."}` / When 요청을 보낸다 / Then `reportRejected(FRED, "fredkey")`가 한 번 불린다
    - Given 출처 TWELVE_DATA, 응답이 HTTP 200에 본문 `{"code":429,"message":"You have run out of API credits for the current minute.","status":"error"}` / When 요청을 보낸다 / Then reportRejected가 불리지 않는다
    - Given 출처 FRED, 응답이 HTTP 500 / When 요청을 보낸다 / Then reportRejected가 불리지 않는다
- R-44 (거절을 설정에 알림): Given 출처 TWELVE_DATA, 키 `tdkey`, 응답이 HTTP 401 / When 요청을 보낸다 / Then `reportRejected(TWELVE_DATA, "tdkey")`가 한 번 불린다
- R-42 (응답과 예외를 오류 종류로)
    - Given TWELVE_DATA, HTTP 200, 본문 `{"meta":{"symbol":"SPY"},"values":[],"status":"ok"}` / When execute / Then 그 본문이 든 Success
    - Given TWELVE_DATA, HTTP 429 / When isRateLimited / Then 참. Given FRED, HTTP 429 / When isRateLimited / Then 참
    - Given TWELVE_DATA, HTTP 200, 본문 `{"code":429,"message":"You have run out of API credits for the current minute.","status":"error"}` / When isRateLimited / Then 참. When execute / Then Failure(RATE_LIMITED)
    - Given TWELVE_DATA, HTTP 200, 본문 `{"code":400,"message":"symbol not found","status":"error"}` / When isRateLimited / Then 거짓. When execute / Then Failure(OTHER)
    - Given FRED, HTTP 429 / When execute / Then Failure(RATE_LIMITED)
    - Given TWELVE_DATA, HTTP 401 / When execute / Then Failure(KEY_REJECTED)
    - Given FRED, HTTP 500 / When execute / Then Failure(OTHER)
    - Given 요청이 `ApiKeyUnavailableException(FRED, MISSING)`을 던짐 / When execute / Then Failure(KEY_MISSING). INVALID면 Failure(KEY_INVALID)
    - Given 요청이 UnknownHostException을 던짐 / When execute / Then Failure(NO_CONNECTION). SocketTimeoutException도 같다
    - Given 요청이 IllegalStateException을 던짐 / When execute / Then Failure(OTHER)
    - Given 요청이 끝나기 전에 부른 코루틴이 취소됨 / When execute / Then 결과를 돌려주지 않고 취소가 그대로 넘어간다

**확인 기준**
- 테스트 통과, 컴파일 오류 없음

### 단위 3: 공용 DB

**만들거나 고칠 것**
- 신규 AppDatabase (core/database)
- 신규 DailyValueEntity, DailyValueDao (core/database)
- 신규 SyncStampEntity, SyncStampDao (core/database)
- 신규 TransactionRunner와 Room 구현 (core/database)
- 신규 DatabaseModule (core/di)

**지시**
- AppDatabase는 앱의 Room DB 하나다. 파일 이름은 `hakbanggul.db`다. 스키마가 바뀌면 옮기지 않고 표를 지우고 새로 만든다. 다시 받으면 되는 값이기 때문이다. (P-11)
- DailyValueEntity는 표 `daily_values`의 행이다: 시리즈 ID(티커나 출처의 ID, 글자), 날짜(`YYYY-MM-DD` 글자), 값(Double). 기본 키는 시리즈 ID와 날짜다. 기능 안이 아니라 공용 표에 두는 것은, 다른 기능(차트)이 같은 값을 쓰고 앱이 가진 데이터는 판정 항목으로 쓸 수 있어야 하기 때문이다. (P-11, R-48)
- DailyValueDao가 내놓는 것 (P-12)
    - `fun observeAll(): Flow<List<DailyValueEntity>>`
    - `suspend fun deleteBySeries(seriesId: String)`
    - `suspend fun insertAll(rows: List<DailyValueEntity>)`
    - `suspend fun deleteAll()`
- SyncStampEntity는 표 `sync_stamps`의 행이다: 이름(기본 키), 시각(UTC 밀리초). SyncStampDao가 내놓는 것: `fun observe(name: String): Flow<Long?>`, `suspend fun upsert(stamp: SyncStampEntity)`, `suspend fun deleteAll()`. 받기에 마지막으로 성공한 때를 값과 같은 DB에 두어 한 트랜잭션으로 적기 위한 것이다. (P-11, R-55)
- TransactionRunner는 여러 DB 작업을 한 트랜잭션으로 돌리는 인터페이스다: `suspend fun <T> run(block: suspend () -> T): T`. Room 구현을 함께 둔다. Dao와 TransactionRunner가 인터페이스여야 테스트에서 가짜를 끼워 읽기 실패와 저장 실패를 재현할 수 있다. (P-12)
- DatabaseModule은 AppDatabase, 두 Dao, TransactionRunner를 싱글턴으로 제공한다.
- 이 DB의 파일은 백업에서 빼지 않는다. 옮겨져도 다시 받으면 되는 값이다.

**테스트 지시**
- 없음. Room은 기기 없이 돌릴 수 없어, 이 단위를 쓰는 코드는 단위 6에서 가짜 Dao로 확인한다.

**확인 기준**
- 컴파일 오류 없음

### 단위 4: 규칙과 계산기

**만들거나 고칠 것**
- 신규 MarketStage (feature/marketmood/domain)
- 신규 MoodRules, IndicatorGroupRule, IndicatorRule (feature/marketmood/domain)
- 신규 IndicatorSeries (feature/marketmood/domain)
- 신규 ScoringRule (feature/marketmood/domain)
- 신규 DefaultMoodRules (feature/marketmood/domain)
- 신규 MoodRulesRepository (feature/marketmood/domain)
- 신규 MoodAssessment, GroupAssessment, IndicatorAssessment, IndicatorEvidence (feature/marketmood/domain)
- 신규 MoodCalculator (feature/marketmood/domain)

**지시**
- 기능의 영어 이름은 `marketmood`다. 기획 용어의 영어 이름은 아래와 같이 쓰고, 처음 선언하는 곳 위에 `// 용어: (한국어 이름)` 주석을 단다. 출처 `DataSource`는 있는 이름을 쓴다.

| 용어 | 영어 이름 |
|---|---|
| 시장 분위기 | MarketMood |
| 항목 | Indicator |
| 묶음 | IndicatorGroup |
| 현재 상태(화면에서는 종합 점수) | CompositeScore |
| 단계 | MarketStage |
| 계산 단계 | computedStage |
| 표시 단계 | displayedStage |
| 기본 규칙 | DefaultMoodRules |
| 기준 날짜 | asOfDate |
| 거래일 | tradingDay |
| 유지 일수 | holdDays |
| 받기 | Refresh |

- MarketStage는 단계다: `BULL`(불장) / `NEUTRAL`(중립) / `WEAK`(약세) / `BEAR`(물장).
- 규칙은 코드가 아니라 데이터로 둔다. MoodCalculator는 항목이 무엇인지 모르고 규칙의 목록만 돈다. 항목 ID나 티커를 계산 코드에 적지 않는다. (P-13)
    - `MoodRules(groups: List<IndicatorGroupRule>, upperBoundary: Double, lowerBoundary: Double, holdDays: Int)`
    - `IndicatorGroupRule(id: String, weight: Double, indicators: List<IndicatorRule>)`
    - `IndicatorRule(id: String, series: IndicatorSeries, scoring: ScoringRule)`
    - `IndicatorSeries(source: DataSource, seriesId: String, kind: SeriesKind)`. SeriesKind는 `DAILY_CLOSE`(일 종가) / `DAILY_VALUE`(날마다의 값).
    - ScoringRule은 점수 기준의 종류와 기준 숫자다: `PriceTrend(averageDays: Int, shallowDrawdown: Double, deepDrawdown: Double)` / `HighIsGood(zeroFrom: Double, plusFrom: Double)` / `LowIsGood(plusBelow: Double, minusAbove: Double)`.
    - 목록의 순서가 화면의 순서다.
- DefaultMoodRules는 기본 규칙의 값이다. (R-30)

| 묶음 ID | 비중 | 항목 ID | 출처 | 시리즈 ID | 값의 종류 | 점수 기준 |
|---|---|---|---|---|---|---|
| `stocks` | 0.7 | `broad_market` | TWELVE_DATA | `SPY` | DAILY_CLOSE | PriceTrend(200, 0.10, 0.20) |
| `stocks` | 0.7 | `nasdaq_100` | TWELVE_DATA | `QQQ` | DAILY_CLOSE | PriceTrend(200, 0.10, 0.20) |
| `stocks` | 0.7 | `technology` | TWELVE_DATA | `XLK` | DAILY_CLOSE | PriceTrend(200, 0.10, 0.20) |
| `stocks` | 0.7 | `semiconductors` | TWELVE_DATA | `SMH` | DAILY_CLOSE | PriceTrend(200, 0.10, 0.20) |
| `bond_signals` | 0.3 | `yield_curve` | FRED | `T10Y2Y` | DAILY_VALUE | HighIsGood(0.0, 0.5) |
| `bond_signals` | 0.3 | `high_yield_spread` | FRED | `BAMLH0A0HYM2` | DAILY_VALUE | LowIsGood(4.0, 6.0) |

- 단계 경계는 위 +0.5, 아래 -0.5, 유지 일수는 3이다.
- MoodRulesRepository는 지금 쓰는 규칙을 내준다: `fun observe(): Flow<MoodRules>`. 나중에 쓰는 사람이 규칙을 고치게 되면 이 구현만 바꾼다. (P-13)
- 판정의 모양
    - `MoodAssessment(asOfDate: LocalDate, displayedStage: MarketStage, computedStage: MarketStage, compositeScore: Double, computedStageStreak: Int, holdDays: Int, upperBoundary: Double, lowerBoundary: Double, groups: List<GroupAssessment>)`
    - `GroupAssessment(id: String, weight: Double, score: Double, indicators: List<IndicatorAssessment>)`
    - `IndicatorAssessment(id: String, score: Double, evidence: IndicatorEvidence)`
    - IndicatorEvidence는 근거다: `PriceEvidence(aboveAverageRatio: Double, drawdown: Double)` / `ValueEvidence(value: Double, valueDate: LocalDate)`.
    - holdDays, upperBoundary, lowerBoundary는 규칙의 값을 그대로 옮긴 것이다. 화면이 안내 문구와 눈금 위치를 만들 때 쓴다.
- MoodCalculator는 순수 계산기다: `fun assess(rules: MoodRules, values: Map<String, List<DailyValue>>): MoodAssessment?`. values의 키는 시리즈 ID다. 목록의 순서에 기대지 않는다. 지금 시각을 쓰지 않아, 같은 값이면 늘 같은 판정이 난다. 판정은 저장하지 않고 부를 때마다 처음부터 계산한다. (P-14)
    - 거래일은 값의 종류가 DAILY_CLOSE인 항목 모두의 값이 있는 날이다. (R-37, P-20)
    - 기준 날짜는 가장 최근 거래일이다. (R-38)
    - PriceTrend 항목의 그날 점수는 추세 점수와 낙폭 점수의 평균이다. (R-33)
        - 평균은 그날을 포함해 가장 최근 averageDays개 종가의 평균이다. 추세 점수는 그날의 종가가 평균 이상이면 +1, 미만이면 -1이다. (R-31)
        - 낙폭은 `(그날의 종가 - 최근 1년 최고 종가) ÷ 최근 1년 최고 종가`다. 최근 1년 최고 종가는 그날의 1년 전 같은 날부터 그날까지(두 날 포함)의 종가 가운데 가장 높은 값이다. 하락률(낙폭의 크기)이 shallowDrawdown 이하면 +1, deepDrawdown 이상이면 -1, 그 사이면 0이다. (R-32)
        - 근거의 aboveAverageRatio는 `(그날의 종가 - 평균) ÷ 평균`, drawdown은 위의 낙폭(0 이하)이다. (R-11)
    - DAILY_VALUE 항목의 그날 값은 그날이나 그 전의 날 가운데 값이 있는 가장 가까운 날의 값이다. 근거의 valueDate는 그 값의 날짜다. (R-39, R-12)
        - HighIsGood: 값이 plusFrom 이상이면 +1, zeroFrom 이상이고 plusFrom 미만이면 0, zeroFrom 미만이면 -1. (R-33)
        - LowIsGood: 값이 plusBelow 미만이면 +1, plusBelow 이상이고 minusAbove 이하면 0, minusAbove 초과면 -1. (R-33)
    - 묶음 점수는 그 묶음의 항목 점수의 평균이다. 현재 상태는 `묶음 점수 × 비중`을 모든 묶음에 대해 더한 값이다. (R-34)
    - 현재 상태는 단계를 가르기 전에 소수 여섯 자리로 반올림한다. compositeScore도 이 반올림한 값이다. 경계에 꼭 걸리는 날에 계산 오차로 단계가 달라지지 않게 하기 위함이다. (R-35, P-15)
    - 계산 단계: upperBoundary 이상이면 BULL, 0 이상이고 upperBoundary 미만이면 NEUTRAL, lowerBoundary 이상이고 0 미만이면 WEAK, lowerBoundary 미만이면 BEAR. (R-35)
    - 계산 단계를 낼 수 있는 거래일은 아래를 모두 채우는 거래일이다. (R-36)
        - PriceTrend 항목마다 그날까지의 종가가 averageDays개 이상 있다.
        - PriceTrend 항목마다 받아 둔 가장 오래된 종가의 날짜가 그날의 1년 전 날이거나 그 전이다.
        - DAILY_VALUE 항목마다 그날이나 그 전에 값이 있다.
    - 표시 단계는 계산 단계를 낼 수 있는 첫 거래일부터 기준 날짜까지 날마다 따져 구한다. 첫 거래일의 표시 단계는 그날의 계산 단계다. 표시 단계와 다른 계산 단계가 holdDays만큼의 거래일 동안 연속으로 같은 단계로 나오면, 그 마지막 날부터 표시 단계가 그 단계로 바뀐다. 연속은 다른 단계가 한 번이라도 끼면 끊기고 새 단계부터 다시 센다. 계산 단계가 표시 단계와 같게 나온 날에도 끊긴다. (R-36)
    - computedStageStreak는 기준 날짜의 계산 단계가 기준 날짜까지 연속으로 이어진 거래일 수다(계산 단계를 낼 수 있는 첫 거래일부터 센다). (R-08)
    - 기준 날짜의 항목 점수를 모든 항목에 대해 낼 수 없으면(시리즈가 없음, 계산 단계를 낼 수 있는 거래일이 하나도 없음) null을 돌려준다. 항목 일부만으로 판정을 내지 않는다. (R-40)

**테스트 지시**
아래 데이터를 바탕으로 쓴다. 단위 5와 단위 7의 테스트도 이 데이터를 쓴다.

- 데이터 A: 기본 규칙. 주식 시리즈 넷(`SPY`, `QQQ`, `XLK`, `SMH`)은 2024-11-25(월)부터 2026-11-23(월)까지 평일마다 종가 100. `T10Y2Y`는 같은 날마다 0.60, `BAMLH0A0HYM2`는 같은 날마다 3.00. 이 데이터에서 계산 단계를 낼 수 있는 첫 거래일은 2025-11-25다.
- 데이터 B: 데이터 A에서 아래만 바꾼다. `XLK`는 2026-01-05 하루만 종가 120. `SMH`는 2026-11-23만 종가 80. `T10Y2Y`는 2026-11-23만 0.29. `BAMLH0A0HYM2`는 2026-11-20까지만 있고 2026-11-20의 값은 3.76.
- "나쁜 날"은 그날의 `T10Y2Y`가 -0.10이고 `BAMLH0A0HYM2`가 7.00인 날이다. 데이터 A에서 어느 날을 나쁜 날로 바꾸면 그날의 현재 상태는 0.4(중립)가 된다.

- R-30: Given 기본 규칙 / When 값을 읽는다 / Then 묶음이 `stocks`(비중 0.7), `bond_signals`(비중 0.3) 순서이고, 항목과 시리즈와 점수 기준이 위 표와 순서까지 같으며, 비중의 합이 1, 단계 경계가 +0.5와 -0.5, 유지 일수가 3이다
- R-38: Given 데이터 A / When assess / Then asOfDate는 2026-11-23
- R-37 (거래일): Given 데이터 A에서 `QQQ`의 2026-11-23 값만 뺀다 / When assess / Then asOfDate는 2026-11-20
- R-31
    - Given 데이터 A / When assess / Then `broad_market`의 점수는 +1.0 (종가가 평균과 같으면 평균 이상이다)
    - Given 데이터 A에서 `SPY`의 2026-11-23만 99.9 / When assess / Then `broad_market`의 점수는 0.0 (추세 -1, 낙폭 +1)
    - Given 데이터 A에서 `SPY`의 2026-02-16까지의 종가를 모두 50으로 (2026-02-17부터 2026-11-23까지가 200거래일이다) / When assess / Then `broad_market`의 aboveAverageRatio는 0.0, 점수는 +1.0
- R-32
    - Given 데이터 A에서 `SPY`의 2026-11-23만 90 / When assess / Then `broad_market`의 점수는 0.0 (하락률 10%는 얕은 기준 이하라 낙폭 +1, 추세 -1)
    - Given 같은 자리가 89.9 / When assess / Then 점수는 -0.5
    - Given 같은 자리가 80 / When assess / Then 점수는 -1.0 (하락률 20%는 깊은 기준 이상)
    - Given 같은 자리가 80.1 / When assess / Then 점수는 -0.5
    - Given 데이터 A를 2026-11-20까지만 두고 `SPY`의 2025-11-20만 200 / When assess / Then `broad_market`의 drawdown은 -0.5 (1년 전 같은 날은 들어간다)
    - Given 데이터 A를 2026-11-20까지만 두고 `SPY`의 2025-11-19만 200 / When assess / Then `broad_market`의 drawdown은 0.0 (1년보다 앞의 날은 들어가지 않는다)
- R-33
    - Given 데이터 B / When assess / Then `technology`의 점수는 +0.5 (추세 +1, 낙폭 0)
    - Given 데이터 A에서 `T10Y2Y`의 2026-11-23이 0.5 / 0.49 / 0.0 / -0.01 (네 경우) / When assess / Then `yield_curve`의 점수는 차례로 +1.0 / 0.0 / 0.0 / -1.0
    - Given 데이터 A에서 `BAMLH0A0HYM2`의 2026-11-23이 3.99 / 4.0 / 6.0 / 6.01 (네 경우) / When assess / Then `high_yield_spread`의 점수는 차례로 +1.0 / 0.0 / 0.0 / -1.0
- R-34: Given 데이터 B / When assess / Then `stocks`의 점수는 0.375, `bond_signals`의 점수는 0.5, compositeScore는 0.4125
- R-35
    - Given 데이터 A에서 주식 시리즈 넷 모두 2026-01-05만 120, `T10Y2Y`의 2026-11-23만 0.29 / When assess / Then compositeScore는 0.5, computedStage는 BULL (경계에 꼭 걸린 값은 불장이다)
    - Given 데이터 B / When assess / Then computedStage는 NEUTRAL
    - Given 데이터 A에서 주식 시리즈 넷 모두 2026-11-23만 99.9, 그날의 `T10Y2Y` 0.29, `BAMLH0A0HYM2` 5.00 / When assess / Then compositeScore는 0.0, computedStage는 NEUTRAL
    - Given 데이터 A에서 주식 시리즈 넷 모두 2026-11-23만 89.9, 그날의 `T10Y2Y` -0.01, `BAMLH0A0HYM2` 5.00 / When assess / Then compositeScore는 -0.5, computedStage는 WEAK
    - Given 위에서 주식 시리즈 넷의 2026-11-23을 80으로 / When assess / Then compositeScore는 -0.85, computedStage는 BEAR
- R-36
    - Given 데이터 A에서 2026-11-20, 2026-11-23이 나쁜 날 / When assess / Then displayedStage는 BULL, computedStage는 NEUTRAL
    - Given 데이터 A에서 2026-11-19, 2026-11-20, 2026-11-23이 나쁜 날 / When assess / Then displayedStage는 NEUTRAL (3거래일째인 날부터 바뀐다)
    - Given 데이터 A에서 2026-11-17, 2026-11-18, 2026-11-20, 2026-11-23이 나쁜 날(2026-11-19는 그대로) / When assess / Then displayedStage는 BULL (표시 단계와 같은 날이 끼면 끊긴다)
    - Given 데이터 A에서 2026-11-18, 2026-11-19, 2026-11-20, 2026-11-23이 나쁜 날이고, 2026-11-20에는 주식 시리즈 넷의 종가도 80(그날은 물장) / When assess / Then displayedStage는 BULL, computedStage는 NEUTRAL (다른 단계가 끼면 끊긴다)
    - Given 데이터 A를 2025-11-26까지만 두고 2025-11-25만 나쁜 날 / When assess / Then displayedStage는 NEUTRAL, computedStage는 BULL (첫 거래일 2025-11-25의 계산 단계가 표시 단계가 된다. 2025-11-24는 1년 전 날의 종가가 없어 세지 않는다)
    - Given 데이터 A에서 `T10Y2Y`가 2026-11-23 하루만 있음 / When assess / Then displayedStage와 computedStage 모두 BULL, computedStageStreak는 1
- R-08 (이어진 거래일 수)
    - Given 데이터 A에서 2026-11-20, 2026-11-23이 나쁜 날 / When assess / Then computedStageStreak는 2, holdDays는 3
    - Given 데이터 B / When assess / Then displayedStage는 BULL, computedStage는 NEUTRAL, computedStageStreak는 1
    - Given 데이터 A에서 2026-11-18, 2026-11-19, 2026-11-20, 2026-11-23이 나쁜 날이고 2026-11-20에는 주식 시리즈 넷의 종가도 80 / When assess / Then computedStageStreak는 1
- R-11 (두 값의 계산)
    - Given 데이터 A에서 `SPY`의 2026-11-23만 104.1 / When assess / Then `broad_market`의 aboveAverageRatio는 (104.1 - 100.0205) ÷ 100.0205, drawdown은 0.0
    - Given 데이터 B / When assess / Then `technology`의 aboveAverageRatio는 0.0, drawdown은 (100 - 120) ÷ 120. `semiconductors`의 aboveAverageRatio는 (80 - 99.9) ÷ 99.9, drawdown은 -0.2
- R-39 (가장 가까운 날의 값): Given 데이터 B / When assess / Then `high_yield_spread`의 근거는 값 3.76, 점수는 +1.0. `yield_curve`의 근거는 값 0.29
- R-12 (값의 날짜): Given 데이터 B / When assess / Then `high_yield_spread`의 valueDate는 2026-11-20, `yield_curve`의 valueDate는 2026-11-23
- R-40
    - Given 데이터 A에서 `XLK` 시리즈가 없음 / When assess / Then null
    - Given 빈 값 / When assess / Then null
    - Given 데이터 A에서 주식 시리즈 넷이 월요일의 값만 있음(종가가 200개에 못 미친다) / When assess / Then null

**확인 기준**
- 테스트 통과, 컴파일 오류 없음

### 단위 5: 받기 (받아 둔 값의 인터페이스, 받기 한 번, 받기 관리자)

**만들거나 고칠 것**
- 신규 MarketDataRepository (feature/marketmood/domain)
- 신규 StoredMarketData (feature/marketmood/domain)
- 신규 FetchResult (feature/marketmood/domain)
- 신규 ObserveMoodUseCase (feature/marketmood/domain)
- 신규 ObserveSourceKeysUseCase (feature/marketmood/domain)
- 신규 RefreshMarketDataUseCase (feature/marketmood/domain)
- 신규 RefreshOutcome, RefreshFailure (feature/marketmood/domain)
- 신규 MarketDataRefresher (feature/marketmood/domain)
- 신규 RefreshState (feature/marketmood/domain)

**지시**
- `StoredMarketData(values: Map<String, List<DailyValue>>, lastSuccessAt: Instant?)`는 받아 둔 값이다. values의 키는 시리즈 ID이고 목록은 날짜 오름차순이다. lastSuccessAt은 마지막으로 받기에 성공한 때이고 없을 수 있다.
- FetchResult는 출처 하나의 받기 결과다: `Success(values: Map<String, List<DailyValue>>)` / `Failure(error: DataFetchError)`.
- MarketDataRepository가 내놓는 것
    - `fun observeStored(): Flow<StoredMarketData>`
    - `suspend fun fetch(source: DataSource, series: List<IndicatorSeries>, startDate: LocalDate): FetchResult`: 출처 하나에서 그 시리즈들을 startDate부터 받는다. 하나라도 받지 못하거나 읽지 못하면 그 출처 전체가 Failure다. (P-09)
    - `suspend fun save(values: Map<String, List<DailyValue>>, succeededAt: Instant?)`: 넘긴 시리즈마다 받아 둔 값을 넘긴 값으로 통째로 바꾸고, succeededAt이 있으면 성공한 때로 적는다. 저장하지 못하면 IOException을 던진다. (R-54)
- UseCase는 모두 `operator fun invoke`로 부른다.
- ObserveMoodUseCase `(): Flow<MoodAssessment?>`: 규칙의 Flow와 받아 둔 값의 Flow를 MoodCalculator에 넣어 판정을 내준다. 낼 수 없으면 null이다. 받아 둔 값이 바뀌면 다시 계산해 내보낸다. (R-23, P-14)
- ObserveSourceKeysUseCase `(): Flow<Map<DataSource, ApiKeyAvailability>>`: 규칙이 쓰는 출처마다 `ApiKeyProvider.observeAvailability`의 값을 묶어 내준다. 출처의 순서는 DataSource의 선언 순서다.
- RefreshFailure는 화면이 가르는 실패 원인이다: `RATE_LIMITED` / `NO_CONNECTION` / `OTHER`. RefreshOutcome은 받기 한 번의 결과다: `Success` / `Failure(reason: RefreshFailure)`.
- RefreshMarketDataUseCase `(): RefreshOutcome`은 받기 한 번이다. 생성자로 MarketDataRepository, MoodRulesRepository, MoodCalculator, Clock을 받는다.
    - 규칙의 항목을 출처별로 묶어, 출처마다 `fetch`를 부른다. startDate는 받는 때의 미국 동부 시간(`America/New_York`) 날짜에서 2년 전이다. (R-41)
    - 출처끼리는 동시에 받는다. 출처마다의 받기 전체에 10초 제한을 코루틴의 시간 제한으로 건다. 넘기면 그 출처는 `Failure(NO_CONNECTION)`과 같게 다룬다. (R-42, P-07)
    - 값의 종류가 DAILY_CLOSE인 시리즈에서, 받는 때가 그 날짜의 미국 동부 시간 오후 4시보다 앞인 날의 값은 장이 끝나지 않은 값이라 뺀다. 꼭 오후 4시면 빼지 않는다. DAILY_VALUE 시리즈는 거르지 않는다. (R-37, P-10)
    - 성공한 출처의 값은 다른 출처가 실패해도 저장한다. 실패한 출처의 시리즈는 넘기지 않아 받아 둔 값이 그대로 남는다. 성공한 출처가 없으면 저장을 부르지 않는다. 저장은 `save`를 한 번만 부른다. (R-43, P-09)
    - 모든 출처가 성공했고, 받아 둔 값에 새로 받은 값을 덮은 것으로 MoodCalculator가 판정을 낼 수 있을 때만 succeededAt에 받는 때를 넘긴다. 그 밖에는 null을 넘긴다. (R-40, R-42)
    - 결과: 모든 출처가 성공했고 판정을 낼 수 있고 저장도 되었으면 Success다. 그 밖에는 Failure이고, 원인은 아래 표의 위 행이 먼저다. (R-42, R-45)

| 원인 | 해당하는 경우 |
|---|---|
| RATE_LIMITED | 어느 출처든 `Failure(RATE_LIMITED)` |
| NO_CONNECTION | 어느 출처든 `Failure(NO_CONNECTION)`이거나 10초를 넘김 |
| OTHER | 그 밖의 모든 경우: `Failure(OTHER)`, 키와 관련된 실패(KEY_MISSING, KEY_INVALID, KEY_REJECTED), `save`가 IOException을 던짐, 모든 출처가 성공했는데 판정을 낼 수 없음 |

- 키와 관련된 실패를 OTHER로 두는 것은, 그때는 키 쓸 수 있음 여부가 바뀌어 화면이 키 안내로 바뀌기 때문이다.
- `RefreshState(isRefreshing: Boolean, lastFailure: RefreshFailure?)`는 받는 중인지와 가장 최근 받기의 실패 원인이다. 성공했거나 받은 적이 없으면 lastFailure는 null이다.
- MarketDataRefresher는 앱 전체에 하나만 있다(Hilt 싱글턴). 생성자로 RefreshMarketDataUseCase, ObserveMoodUseCase, ObserveSourceKeysUseCase, MarketDataRepository, Clock, `@ApplicationScope` CoroutineScope를 받는다. 받기를 ViewModel이 아니라 여기서 앱 범위로 돌려, 화면을 벗어나도 끝까지 받아 저장한다. (P-02, R-55)
    - `val state: StateFlow<RefreshState>`: 메모리에만 두고 저장하지 않는다. 앱을 다시 켜면 실패가 남아 있지 않다. (R-57)
    - `fun refresh()`: 받는 중이 아니면 받기를 시작한다. 받는 중이면 아무것도 하지 않는다. 받는 중인지 보는 것과 받는 중으로 두는 것은 한 번에 한다. (R-21, R-22, R-28)
    - `fun onScreenShown()`: 출처의 키가 모두 AVAILABLE이고 아래 가운데 하나면 `refresh()`와 같게 받는다. (R-20)
        - 받아 둔 값으로 판정을 낼 수 없다(ObserveMoodUseCase의 지금 값이 null).
        - lastSuccessAt이 없다(받기에 성공한 적이 없다).
        - 지금이 lastSuccessAt에서 6시간을 넘었다. 꼭 6시간이면 넘지 않은 것이다.
        - lastSuccessAt이 지금보다 뒤다(기기의 시계를 바꾼 경우).
    - 받기를 시작하면 isRefreshing을 참으로 두고 lastFailure는 그대로 둔다. 끝나면 isRefreshing을 거짓으로, Success면 lastFailure를 null로, Failure면 그 원인으로 둔다. (R-18, R-47)
    - 만들어질 때부터 ObserveSourceKeysUseCase를 받아, 출처의 키가 모두 AVAILABLE이 아니던 것이 모두 AVAILABLE로 바뀌면 `refresh()`와 같게 받는다. 처음 받은 값은 바뀐 것으로 보지 않는다. 화면이 안 보이는 동안(설정 화면에서 키를 저장하는 때)에도 알아야 해 여기에 둔다. (R-53, P-02)

**테스트 지시**
가짜 MarketDataRepository, 가짜 MoodRulesRepository(기본 규칙), 가짜 ApiKeyProvider, 가짜 Clock을 쓰고 MoodCalculator는 진짜를 쓴다. "받기가 시작된다"는 가짜의 `fetch`가 불리는 것으로 확인한다. 데이터 A, B는 단위 4의 것이다.

- R-23: Given 받아 둔 값이 데이터 A / When ObserveMoodUseCase를 받는 중에 받아 둔 값이 데이터 B로 바뀐다 / Then compositeScore 1.0인 판정 다음에 compositeScore 0.4125인 판정을 받는다
- R-41 (받기 시작 날짜): Given 지금이 2026-11-24T01:00:00Z(미국 동부 2026-11-23 오후 8시) / When RefreshMarketDataUseCase / Then `fetch`가 TWELVE_DATA에 시리즈 넷(`SPY`, `QQQ`, `XLK`, `SMH`)과 startDate 2024-11-23으로 한 번, FRED에 시리즈 둘(`T10Y2Y`, `BAMLH0A0HYM2`)과 startDate 2024-11-23으로 한 번 불린다
- R-37 (장이 끝나지 않은 날의 값 거르기)
    - Given 지금이 2026-11-23T20:59:00Z(미국 동부 오후 3시 59분), 두 출처가 데이터 A를 돌려줌 / When RefreshMarketDataUseCase / Then 저장된 주식 시리즈 넷에는 2026-11-23이 없고 2026-11-20까지 있으며, `T10Y2Y`에는 2026-11-23이 있다
    - Given 지금이 2026-11-23T21:00:00Z(미국 동부 오후 4시), 같은 응답 / When RefreshMarketDataUseCase / Then 저장된 주식 시리즈 넷에 2026-11-23이 있다
- R-42 (10초 제한)
    - Given TWELVE_DATA의 `fetch`가 11초 뒤에 데이터 A를 돌려주고 FRED는 바로 돌려줌 / When RefreshMarketDataUseCase / Then 10초에 `Failure(NO_CONNECTION)`을 돌려주고, FRED의 두 시리즈는 저장된다
    - Given TWELVE_DATA의 `fetch`가 9.5초 뒤에 데이터 A를 돌려줌 / When RefreshMarketDataUseCase / Then Success
- R-42 (넷으로 가르기)
    - Given 두 출처가 데이터 A를 돌려주고 `save`가 IOException을 던짐 / When RefreshMarketDataUseCase / Then `Failure(OTHER)`
    - Given 두 출처가 Success인데 `XLK`의 값이 2026-11-23 하루뿐임, 받아 둔 값 없음 / When RefreshMarketDataUseCase / Then `Failure(OTHER)`이고, `save`는 받은 값과 succeededAt null로 불린다
    - Given TWELVE_DATA가 `Failure(RATE_LIMITED)`, FRED가 Success / When RefreshMarketDataUseCase / Then `Failure(RATE_LIMITED)`
    - Given TWELVE_DATA가 `Failure(KEY_REJECTED)`, FRED가 Success / When RefreshMarketDataUseCase / Then `Failure(OTHER)`
    - Given 두 출처가 데이터 A를 돌려줌, 지금이 2026-11-24T01:00:00Z / When RefreshMarketDataUseCase / Then Success이고, `save`가 시리즈 여섯과 succeededAt 2026-11-24T01:00:00Z로 한 번 불린다
- R-43: Given 받아 둔 값이 데이터 A, TWELVE_DATA가 `Failure(NO_CONNECTION)`, FRED가 데이터 B의 두 시리즈를 돌려줌 / When RefreshMarketDataUseCase / Then `save`가 FRED의 두 시리즈만 담아 succeededAt null로 불리고(주식 시리즈 넷은 넘기지 않는다), 결과는 `Failure(NO_CONNECTION)`
- R-45 (원인의 우선순위)
    - Given TWELVE_DATA가 `Failure(NO_CONNECTION)`, FRED가 `Failure(RATE_LIMITED)` / When RefreshMarketDataUseCase / Then `Failure(RATE_LIMITED)`
    - Given TWELVE_DATA가 `Failure(OTHER)`, FRED가 `Failure(NO_CONNECTION)` / When RefreshMarketDataUseCase / Then `Failure(NO_CONNECTION)`
- R-20 (받을지 판정). 따로 적지 않으면 두 출처의 키는 AVAILABLE, 지금은 2026-11-24T01:00:00Z다.
    - Given 받아 둔 값 없음(lastSuccessAt 없음) / When onScreenShown / Then 받기가 시작된다
    - Given 받아 둔 값이 데이터 A에서 `XLK` 시리즈를 뺀 것(판정을 낼 수 없다), lastSuccessAt이 1시간 전 / When onScreenShown / Then 받기가 시작된다
    - Given 받아 둔 값이 데이터 A, lastSuccessAt 없음 / When onScreenShown / Then 받기가 시작된다
    - Given 받아 둔 값이 데이터 A, lastSuccessAt이 6시간 1분 전 / When onScreenShown / Then 받기가 시작된다
    - Given 받아 둔 값이 데이터 A, lastSuccessAt이 꼭 6시간 전 / When onScreenShown / Then 받기가 시작되지 않는다
    - Given 받아 둔 값이 데이터 A, lastSuccessAt이 5시간 59분 전 / When onScreenShown / Then 받기가 시작되지 않는다
    - Given 받아 둔 값이 데이터 A, lastSuccessAt이 지금보다 1분 뒤 / When onScreenShown / Then 받기가 시작된다
    - Given 받아 둔 값 없음, FRED의 키가 MISSING / When onScreenShown / Then 받기가 시작되지 않는다
    - Given 받아 둔 값 없음, TWELVE_DATA의 키가 INVALID / When onScreenShown / Then 받기가 시작되지 않는다
- R-21: Given 받는 중이 아님, lastSuccessAt이 1분 전 / When refresh / Then 받기가 시작되고 state의 isRefreshing이 참이 된다
- R-28: Given 가장 최근 받기가 `Failure(NO_CONNECTION)`으로 끝남 / When refresh / Then 받기가 다시 시작된다
- R-22 (겹쳐 시작하지 않음): Given `fetch`가 응답 대기 중(받는 중) / When refresh를 한 번 더 부르고 onScreenShown도 부른다 / Then `fetch`는 출처마다 모두 합해 한 번만 불린다
- R-55 (화면을 벗어나도 이어짐): Given `fetch`가 1초 뒤에 데이터 A를 돌려줌 / When refresh를 부른 쪽의 코루틴 범위를 바로 취소한다 / Then 1초 뒤 `save`가 불리고 isRefreshing이 거짓이 된다
- R-47: Given 가장 최근 받기가 실패해 lastFailure가 NO_CONNECTION / When refresh가 Success로 끝난다 / Then lastFailure는 null
- R-57 (메모리에만 둠): Given 받기가 `Failure(RATE_LIMITED)`로 끝난 MarketDataRefresher / When MarketDataRefresher를 새로 만든다(앱을 다시 켠 것) / Then 새 것의 state는 isRefreshing 거짓, lastFailure null이고, 받기의 실패를 저장하는 호출은 없었다
- R-53 (모두 저장됨이 되면 받기)
    - Given TWELVE_DATA AVAILABLE, FRED MISSING인 채로 MarketDataRefresher가 만들어짐 / When FRED가 AVAILABLE로 바뀐다 / Then 받기가 시작된다
    - Given 두 출처가 처음부터 AVAILABLE인 채로 만들어짐 / When 아무것도 부르지 않는다 / Then 받기가 시작되지 않는다
    - Given TWELVE_DATA INVALID, FRED MISSING / When TWELVE_DATA만 AVAILABLE로 바뀐다 / Then 받기가 시작되지 않는다

**확인 기준**
- 테스트 통과, 컴파일 오류 없음

### 단위 6: 시장 분위기 Data (요청, 응답 바꾸기, 저장)

**만들거나 고칠 것**
- 신규 TwelveDataTimeSeriesApi (feature/marketmood/data)
- 신규 FredObservationsApi (feature/marketmood/data)
- 신규 TwelveDataTimeSeriesDto, FredObservationsDto (feature/marketmood/data)
- 신규 MarketResponseMapper (feature/marketmood/data)
- 신규 MarketDataRepositoryImpl (feature/marketmood/data)
- 신규 MoodRulesRepositoryImpl (feature/marketmood/data)
- 신규 MarketMoodModule (feature/marketmood/di)

**지시**
- TwelveDataTimeSeriesApi: `GET time_series`에 `symbol`(쉼표로 이은 티커), `interval=1day`, `start_date`(`YYYY-MM-DD`), `outputsize=5000`을 쿼리로 보낸다. FredObservationsApi: `GET fred/series/observations`에 `series_id`, `file_type=json`, `observation_start`(`YYYY-MM-DD`)를 쿼리로 보낸다. 둘 다 본문을 원문(`Response<ResponseBody>`)으로 받는다. Twelve Data가 오류를 HTTP 200의 본문으로 주는 경우가 있어, 본문을 보고 가른 뒤에 읽어야 하기 때문이다. 키는 인터셉터가 붙이므로 인자로 받지 않는다. (P-05, P-04, R-41)
- Twelve Data는 네 종목을 한 요청에 넣는다. FRED는 시리즈마다 요청 하나다. (P-08)
- MarketResponseMapper는 응답 본문을 값으로 바꾼다. 읽을 수 없으면 null을 돌려준다. 돌려주는 목록은 날짜 오름차순이다. (R-42, P-08)
    - `fun mapTwelveData(body: String, symbols: List<String>): Map<String, List<DailyValue>>?`
        - 종목이 하나면 본문이 `{"meta":{…},"values":[{"datetime":"2026-11-23","close":"722.28998",…},…],"status":"ok"}` 모양이고, 여럿이면 `{"SPY":{"meta":{…},"values":[…],"status":"ok"},"QQQ":{…}}`처럼 종목 이름 아래에 하나씩 담긴다. 두 모양을 모두 읽는다.
        - 값은 `close`를 쓴다. 글자로 오므로 숫자로 바꾼다. 최신 날이 먼저 온다.
        - 요청한 종목 가운데 하나라도 없거나, 그 종목의 `status`가 `"ok"`가 아니거나, 값이 하나도 없으면 null이다.
    - `fun mapFred(body: String): List<DailyValue>?`
        - 본문은 `{"observations":[{"date":"2026-11-23","value":"0.29"},…]}` 모양이다. 값은 글자로 온다.
        - `observations`가 없거나 값이 하나도 없으면 null이다.
    - 두 함수 모두 숫자가 아닌 값(FRED는 값이 없는 날을 `"."`으로 준다)의 날은 뺀다. (R-39)
- MarketDataRepositoryImpl은 MarketDataRepository를 구현한다. 생성자로 두 Api, SourceCallExecutor, MarketResponseMapper, DailyValueDao, SyncStampDao, TransactionRunner를 받는다.
    - `fetch`: 출처에 맞는 Api를 SourceCallExecutor로 부르고 MarketResponseMapper로 바꾼다. SourceCallExecutor가 Failure면 그 오류로 Failure를, Mapper가 null이면 `Failure(OTHER)`를 돌려준다. FRED는 시리즈 가운데 하나라도 실패하면 그 오류로 출처 전체가 Failure다. 기능 코드에 접속 주소와 키를 적지 않는다. (R-42, P-09)
    - `save`: TransactionRunner의 한 트랜잭션 안에서, 넘긴 시리즈마다 그 시리즈의 행을 모두 지우고 새로 넣으며, succeededAt이 있으면 성공한 때를 적는다. 받아 둔 값에 덧붙이지 않는다. 도중에 앱이 꺼지면 전부 들어가거나 하나도 들어가지 않는다. 어떤 예외든 IOException으로 감싸 던진다. (R-54, R-55, R-42)
    - `observeStored`: 두 Dao의 Flow를 묶어 StoredMarketData로 내준다. 읽다가 예외가 나면 앱을 멈추지 않고, 두 표를 비운 뒤 받아 둔 값이 없는 것(빈 values, lastSuccessAt 없음)으로 내준다. 비우다가 난 예외는 무시한다. (R-56, R-48)
    - 성공한 때는 `sync_stamps`에 이름 `market_mood_refresh`로 둔다. 시각은 UTC 밀리초다.
    - 받아 둔 값을 지우는 길은 위의 둘(통째로 바꾸기, 읽지 못했을 때 비우기)뿐이다. 키를 지워도 값은 지우지 않는다. (R-49)
- MoodRulesRepositoryImpl은 MoodRulesRepository를 구현한다. DefaultMoodRules를 내준다. (P-13)
- MarketMoodModule은 두 Repository의 구현과 두 Api(단위 2의 키를 붙이는 출처별 Retrofit으로 만든다)를 묶는다. 싱글턴이다.

**테스트 지시**
- R-39 (숫자가 아닌 값 빼기): Given 본문 `{"observations":[{"date":"2026-11-19","value":"0.31"},{"date":"2026-11-20","value":"."},{"date":"2026-11-23","value":"0.29"}]}` / When mapFred / Then 2026-11-19의 0.31, 2026-11-23의 0.29 둘을 이 순서로 돌려준다
- R-42 (읽을 수 없는 응답)
    - Given 본문 `{"meta":{"symbol":"SPY","interval":"1day"},"values":[{"datetime":"2026-11-23","open":"720.10","high":"723.00","low":"719.50","close":"722.28998","volume":"1000"},{"datetime":"2026-11-20","open":"715.00","high":"719.00","low":"714.00","close":"718.5","volume":"900"}],"status":"ok"}`, 종목 `SPY` / When mapTwelveData / Then `SPY`에 2026-11-20의 718.5, 2026-11-23의 722.28998을 이 순서로 돌려준다
    - Given 본문 `{"SPY":{"meta":{"symbol":"SPY"},"values":[{"datetime":"2026-11-23","close":"722.28998"}],"status":"ok"},"QQQ":{"meta":{"symbol":"QQQ"},"values":[{"datetime":"2026-11-23","close":"610.1"}],"status":"ok"}}`, 종목 `SPY`, `QQQ` / When mapTwelveData / Then `SPY`에 722.28998, `QQQ`에 610.1을 돌려준다
    - Given 위 본문에서 `QQQ`가 `{"code":400,"message":"symbol not found","status":"error"}` / When mapTwelveData / Then null
    - Given 위 본문, 종목 `SPY`, `QQQ`, `XLK` / When mapTwelveData / Then null (요청한 종목이 없다)
    - Given 본문 `<html>Service Unavailable</html>` / When mapTwelveData와 mapFred / Then 둘 다 null
    - Given 본문 `{"error_code":400,"error_message":"Bad Request.  The series does not exist."}` / When mapFred / Then null
- R-54: Given 가짜 Dao에 `SPY`의 2026-11-19(100), 2026-11-20(100)과 `QQQ`의 2026-11-20(100)이 있음 / When `save`에 `SPY`의 2026-11-20(101), 2026-11-23(102)을 넘긴다 / Then `SPY`의 행은 2026-11-20(101), 2026-11-23(102) 둘뿐이고 `QQQ`의 행은 그대로다
- R-55 (한 번에 저장)
    - Given 가짜 TransactionRunner / When `save`에 시리즈 둘과 succeededAt을 넘긴다 / Then TransactionRunner의 `run`이 한 번만 불리고, 지우기와 넣기와 성공한 때 적기가 모두 그 안에서 불린다
    - Given 가짜 TransactionRunner가 예외를 던짐 / When `save` / Then IOException이 난다
- R-56
    - Given 가짜 DailyValueDao의 `observeAll`이 예외를 던짐 / When observeStored의 첫 값을 읽는다 / Then 예외 없이 빈 values와 lastSuccessAt 없음을 받고, 두 Dao의 `deleteAll`이 불렸다
    - Given 위에서 `deleteAll`도 예외를 던짐 / When observeStored의 첫 값을 읽는다 / Then 예외 없이 같은 값을 받는다

**확인 기준**
- 테스트 통과, 컴파일 오류 없음

### 단위 7: 화면 상태 (ViewModel, 글 만들기, 문구)

**만들거나 고칠 것**
- 신규 MoodTextFormatter (feature/marketmood/presentation)
- 신규 ScoreTone (feature/marketmood/presentation)
- 신규 IndicatorLabels (feature/marketmood/presentation)
- 신규 MarketMoodUiState (feature/marketmood/presentation)
- 신규 MarketMoodViewModel (feature/marketmood/presentation)
- 수정 `app/src/main/res/values/strings.xml`

**지시**
- 화면에 보일 글은 ViewModel이 UiText(문구 리소스와 끼워 넣을 값)로 만들고, 숫자를 글로 바꾸는 일은 MoodTextFormatter에 모은다. Composable은 받은 것을 그리기만 한다. 화면 문구는 모두 `strings.xml`에 둔다. (P-16)
- ScoreTone은 점수의 색 종류다: `POSITIVE`(0보다 큼) / `ZERO`(0) / `NEGATIVE`(0보다 작음). (R-13)
- MoodTextFormatter는 Android에 기대지 않는 순수한 클래스다. 반올림은 값의 십진 표기를 기준으로 한 사사오입이다(0.005는 0.01이 된다). 음수 부호는 `-`(하이픈)를 쓴다. (R-13, R-11)
    - `fun itemScore(score: Double): String`: 소수 한 자리. `+1.0`, `0.0`, `-0.5`.
    - `fun groupScore(score: Double): String`: 소수 두 자리. `+0.38`. 종합 점수도 이것으로 적는다.
    - `fun scoreTone(score: Double, decimals: Int): ScoreTone`: 그 자릿수로 반올림해 적는 값이 0보다 크면 `POSITIVE`, 0이면 `ZERO`, 0보다 작으면 `NEGATIVE`. 부호도 적는 값을 기준으로 붙이고, 0에는 부호를 붙이지 않는다(`-0.004`는 `0.00`이고 ZERO다).
    - `fun signedPercent(ratio: Double): String`: 비율에 100을 곱해 부호와 소수 한 자리. `+4.1%`, `-3.2%`, `0.0%`.
    - `fun signedPercentPoint(value: Double): String`: 부호와 소수 두 자리. `+0.29%p`, `-0.10%p`, `0.00%p`.
    - `fun percent(value: Double): String`: 부호 없이 소수 두 자리. `3.76%`.
    - `fun weightPercent(weight: Double): Int`: 비중에 100을 곱해 반올림한 정수. 0.7은 70.
- IndicatorLabels는 묶음 ID와 항목 ID마다의 문구 리소스다. 규칙에 항목을 더할 때 여기와 문구에 이름을 더한다. (P-13, R-10)

| ID | 이름 | 덧붙이는 글 | 값을 적는 법 |
|---|---|---|---|
| `stocks` (묶음) | 주식 | | |
| `bond_signals` (묶음) | 채권 신호 | | |
| `broad_market` | 전체 시장 | SPY | |
| `nasdaq_100` | 나스닥 100 | QQQ | |
| `technology` | 기술 | XLK | |
| `semiconductors` | 반도체 | SMH | |
| `yield_curve` | 장단기 금리차 | 10년 - 2년 | signedPercentPoint |
| `high_yield_spread` | 하이일드 스프레드 | 없음 | percent |

- MarketMoodUiState (맞물리는 모양)
    - `MarketMoodUiState(refreshButton: RefreshButtonState, banner: BannerUiState?, body: MoodBody)`
    - `RefreshButtonState`: `ENABLED`(누를 수 있음) / `REFRESHING`(받는 중) / `DISABLED`(비활성)
    - `BannerUiState(title: UiText, message: UiText)`
    - `MoodBody`: `Undecided`(아직 정해지지 않음) / `KeyGuide(rows: List<KeyGuideRow>)` / `Assessment(mood: MoodUiState)` / `Loading` / `Failed(message: UiText)`
    - `KeyGuideRow(source: DataSource, nameRes: Int, descriptionRes: Int, badge: KeyBadgeKind)`
    - `MoodUiState(stage: MarketStage, stageName: UiText, asOfText: UiText, compositeScoreText: String, compositeTone: ScoreTone, computedStageName: UiText, gaugePosition: Float, pendingNotice: UiText?, groups: List<GroupUiState>)`
    - `GroupUiState(name: UiText, weightText: UiText, scoreText: String, scoreTone: ScoreTone, indicators: List<IndicatorUiState>)`
    - `IndicatorUiState(name: UiText, suffix: UiText?, evidence: UiText, scoreText: String, scoreTone: ScoreTone)`
- MarketMoodViewModel은 `@HiltViewModel`이다. 생성자로 ObserveMoodUseCase, ObserveSourceKeysUseCase, MarketDataRefresher를 받는다. 내놓는 것
    - `val uiState: StateFlow<MarketMoodUiState>`: 판정, 키 쓸 수 있음 여부, RefreshState를 합쳐 만든다. 값이 처음 오기 전에는 body가 Undecided, refreshButton이 DISABLED, banner가 null이다.
    - `fun onRefreshClick()`, `fun onRetryClick()`: `MarketDataRefresher.refresh()`를 부른다. (R-21, R-28)
    - `fun onScreenShown()`: `MarketDataRefresher.onScreenShown()`을 부른다. (R-20)
- 본문은 아래 표에서 고른다. 위 행의 조건이 먼저다. (R-04, R-44, R-49, R-53)

| 조건 | body |
|---|---|
| 출처 가운데 하나라도 키 쓸 수 있음 여부가 AVAILABLE이 아님 | KeyGuide |
| 판정이 있음 | Assessment |
| 판정이 없고 lastFailure가 없음 (받는 중이거나 곧 받기 시작함) | Loading |
| 판정이 없고 받는 중이 아니며 lastFailure가 있음 | Failed |

- 판정이 없고 받는 중인데 lastFailure가 있으면(다시 시도를 누른 뒤) Loading이다.
- 키가 없거나 틀림이면 받아 둔 값이 있어도 KeyGuide다. 받아 둔 값은 지우지 않아, 키가 다시 AVAILABLE이 되면 받아 둔 값으로 바로 Assessment가 된다.
- 새로 고침 버튼: body가 KeyGuide나 Undecided면 DISABLED, 받는 중이면 REFRESHING, 그 밖에는 ENABLED. 받는 동안에도 판정이 있으면 body는 Assessment 그대로다. (R-22, R-24)
- 안내 띠: body가 Assessment이고 lastFailure가 있을 때만 보인다. 글은 아래 표와 같다. (R-18, R-45, R-47)

| lastFailure | title | message |
|---|---|---|
| RATE_LIMITED | 무료 한도를 넘었습니다 | 잠시 뒤 다시 해 주세요. 받아 둔 값으로 보여 줍니다. |
| NO_CONNECTION | 새 값을 받지 못했습니다 | 연결을 확인해 주세요. 받아 둔 값으로 보여 줍니다. |
| OTHER | 새 값을 받지 못했습니다 | 잠시 뒤 다시 해 주세요. 받아 둔 값으로 보여 줍니다. |

- Failed의 message는 아래 표와 같다. (R-46)

| lastFailure | message |
|---|---|
| RATE_LIMITED | 무료 한도를 넘었습니다. 잠시 뒤 다시 해 주세요. |
| NO_CONNECTION | 연결을 확인하고 다시 해 주세요. |
| OTHER | 잠시 뒤 다시 해 주세요. |

- KeyGuide의 rows는 ObserveSourceKeysUseCase가 준 출처를 그 순서대로 담는다. nameRes와 descriptionRes는 SourceLabels의 것이고, badge는 MISSING이면 NONE, AVAILABLE이면 SAVED, INVALID면 INVALID다. (R-15, R-52)
- MoodUiState를 만드는 법 (R-05, R-06, R-08, R-09, R-10, R-12)
    - stage는 표시 단계, stageName은 그 이름(불장, 중립, 약세, 물장)이다.
    - asOfText는 "11월 23일 종가 기준 (미국)"처럼 기준 날짜의 월과 일을 끼운 글이다.
    - compositeScoreText와 compositeTone은 현재 상태를 groupScore와 scoreTone(소수 두 자리)으로 만든 것이다. computedStageName은 계산 단계의 이름이다.
    - gaugePosition은 눈금에서 현재 상태가 놓일 자리(0은 왼쪽 끝, 1은 오른쪽 끝)다. 네 칸은 왼쪽부터 물장, 약세, 중립, 불장이고 폭이 같다. 단계 경계가 칸의 경계에 오도록 칸마다 나눠 계산한다. 현재 상태를 s, 위 경계를 u, 아래 경계를 l이라 할 때, s가 l 미만이면 `0.25 × (s + 1) ÷ (l + 1)`, l 이상 0 미만이면 `0.25 + 0.25 × (s - l) ÷ (0 - l)`, 0 이상 u 미만이면 `0.5 + 0.25 × s ÷ u`, u 이상이면 `0.75 + 0.25 × (s - u) ÷ (1 - u)`다. (P-21)
    - pendingNotice는 계산 단계가 표시 단계와 다를 때만 있다: "종합 점수는 중립인데 장세는 아직 불장입니다. 점수가 3거래일 이어져야 장세가 바뀝니다. (1일째)". 앞의 단계 이름은 계산 단계, 뒤는 표시 단계, 거래일 수는 holdDays, 괄호 안은 computedStageStreak다. 같으면 null이다.
    - groups는 판정의 묶음을 그 순서대로 담는다. name은 IndicatorLabels의 이름, weightText는 "비중 70%", scoreText와 scoreTone은 묶음 점수를 groupScore와 scoreTone(소수 두 자리)으로 만든 것이다.
    - indicators는 그 묶음의 항목을 그 순서대로 담는다. name과 suffix는 IndicatorLabels의 것, scoreText와 scoreTone은 항목 점수를 itemScore와 scoreTone(소수 한 자리)으로 만든 것이다.
    - evidence: PriceEvidence면 "200일 평균보다 +4.1% · 고점보다 -3.2%"(두 숫자는 signedPercent). ValueEvidence면 IndicatorLabels의 값을 적는 법으로 적은 값이고, 값의 날짜가 기준 날짜와 다를 때만 뒤에 " · 11월 20일 값"을 붙인다.
- 그 밖에 `strings.xml`에 둘 문구(단위 8이 쓴다): "지금 장은", "종합 점수", 눈금과 장세의 단계 이름(불장, 중립, 약세, 물장), "장 분위기를 요약한 참고 표시입니다. 매매 신호가 아닙니다.", "데이터: Twelve Data, FRED", "This product uses the FRED® API but is not endorsed or certified by the Federal Reserve Bank of St. Louis."(영어 원문 그대로), "FRED API 이용 약관", "키를 넣어 주세요", "시장 분위기를 보려면 두 출처의 키가 모두 필요합니다.", "설정으로 가기", "데이터를 받는 중", "데이터를 받지 못했습니다", "다시 시도", "설정"(메뉴. 있는 `settings_title`을 써도 된다), 아이콘 버튼의 설명 "새로 고침", "메뉴". (R-06, R-14, R-15, R-16, R-17, R-25)

**테스트 지시**
MoodTextFormatter는 직접 부른다. ViewModel은 진짜 ObserveMoodUseCase, ObserveSourceKeysUseCase, MarketDataRefresher에 단위 5와 같은 가짜를 끼워 확인한다. 데이터 A, B는 단위 4의 것이다. 따로 적지 않으면 두 출처의 키는 AVAILABLE이고 받는 중이 아니며 lastFailure는 없다.

- R-13 (부호, 자릿수, 색의 종류)
    - Given 1.0 / 0.0 / -0.5 / When itemScore와 scoreTone(소수 한 자리) / Then `+1.0` POSITIVE / `0.0` ZERO / `-0.5` NEGATIVE
    - Given 0.375 / 0.4125 / -0.85 / When groupScore / Then `+0.38` / `+0.41` / `-0.85`
    - Given -0.004 / 0.004 / When groupScore와 scoreTone(소수 두 자리) / Then 둘 다 `0.00`, ZERO
    - Given 0.005 / -0.005 / When groupScore와 scoreTone(소수 두 자리) / Then `+0.01` POSITIVE / `-0.01` NEGATIVE
- R-11 (글)
    - Given 0.041 / -0.032 / 0.0 / -0.0004 / When signedPercent / Then `+4.1%` / `-3.2%` / `0.0%` / `0.0%`
    - Given 0.29 / -0.1 / 0.0 / When signedPercentPoint / Then `+0.29%p` / `-0.10%p` / `0.00%p`
    - Given 3.76 / 3.4 / When percent / Then `3.76%` / `3.40%`
- R-04 (본문 고르기)
    - Given 받아 둔 값이 데이터 A, FRED의 키가 MISSING / When uiState / Then body는 KeyGuide
    - Given 받아 둔 값이 데이터 A, TWELVE_DATA의 키가 INVALID / When uiState / Then body는 KeyGuide
    - Given 받아 둔 값이 데이터 A / When uiState / Then body는 Assessment
    - Given 받아 둔 값 없음, `fetch`가 응답 대기 중 / When onScreenShown 뒤 uiState / Then body는 Loading
    - Given 받아 둔 값 없음, 받기가 `Failure(NO_CONNECTION)`으로 끝남 / When uiState / Then body는 Failed
- R-05 (카드의 순서 구성): Given 받아 둔 값이 데이터 A / When uiState / Then groups는 주식, 채권 신호 순서이고, 주식의 indicators는 전체 시장, 나스닥 100, 기술, 반도체 순서, 채권 신호의 indicators는 장단기 금리차, 하이일드 스프레드 순서다
- R-06 (장세, 기준 날짜, 종합 점수의 글): Given 받아 둔 값이 데이터 B / When uiState / Then stage는 BULL, stageName은 "불장", asOfText는 "11월 23일 종가 기준 (미국)"(월 11, 일 23), compositeScoreText는 `+0.41`, compositeTone은 POSITIVE, computedStageName은 "중립"
- R-06 (눈금 위치)
    - Given 받아 둔 값이 데이터 B / When uiState / Then gaugePosition은 0.70625
    - Given 받아 둔 값이 데이터 A / When uiState / Then gaugePosition은 1.0
    - Given 받아 둔 값이 데이터 B, 규칙의 위 경계만 0.6으로 바꿈 / When uiState / Then gaugePosition은 0.671875 (0.5 + 0.25 × 0.4125 ÷ 0.6)
    - Given 데이터 A에서 주식 시리즈 넷 모두 2026-11-23만 89.9, 그날의 `T10Y2Y` -0.01, `BAMLH0A0HYM2` 5.00 (현재 상태 -0.5) / When uiState / Then gaugePosition은 0.25
- R-08 (줄을 보일지와 그 글)
    - Given 받아 둔 값이 데이터 B / When uiState / Then pendingNotice는 "종합 점수는 중립인데 장세는 아직 불장입니다. 점수가 3거래일 이어져야 장세가 바뀝니다. (1일째)"(끼운 값: 중립, 불장, 3, 1)
    - Given 받아 둔 값이 데이터 A / When uiState / Then pendingNotice는 null
- R-09 (묶음 이름, 비중, 묶음 점수의 글): Given 받아 둔 값이 데이터 B / When uiState / Then 첫 묶음은 이름 "주식", weightText "비중 70%"(끼운 값 70), scoreText `+0.38`, POSITIVE. 둘째 묶음은 이름 "채권 신호", "비중 30%", `+0.50`, POSITIVE
- R-10 (항목 이름, 덧붙이는 글, 근거 숫자): Given 받아 둔 값이 데이터 B / When uiState / Then
    - 전체 시장: suffix "SPY", evidence "200일 평균보다 0.0% · 고점보다 0.0%", scoreText `+1.0`
    - 기술: suffix "XLK", evidence "200일 평균보다 0.0% · 고점보다 -16.7%", scoreText `+0.5`
    - 반도체: suffix "SMH", evidence "200일 평균보다 -19.9% · 고점보다 -20.0%", scoreText `-1.0`, NEGATIVE
    - 장단기 금리차: suffix "10년 - 2년", evidence "+0.29%p", scoreText `0.0`, ZERO
    - 하이일드 스프레드: suffix null, evidence는 "3.76%"로 시작한다
- R-12 (날짜를 붙일지와 그 글): Given 받아 둔 값이 데이터 B / When uiState / Then 하이일드 스프레드의 evidence는 "3.76% · 11월 20일 값"(월 11, 일 20)이고, 장단기 금리차의 evidence에는 날짜가 붙지 않는다
- R-15 (출처마다의 줄과 딱지 종류): Given TWELVE_DATA의 키가 MISSING, FRED의 키가 AVAILABLE / When uiState / Then rows는 TWELVE_DATA(딱지 NONE), FRED(딱지 SAVED) 순서다. TWELVE_DATA가 INVALID면 딱지 INVALID다
- R-52 (딱지 종류와 출처 문구 고르기): Given TWELVE_DATA의 키가 MISSING / When uiState / Then TWELVE_DATA 줄의 nameRes와 descriptionRes는 `source_twelve_data_name`, `source_twelve_data_description`이고 FRED 줄은 `source_fred_name`, `source_fred_description`이다
- R-18 (띠를 보일지)
    - Given 받아 둔 값이 데이터 A, 가장 최근 받기가 `Failure(NO_CONNECTION)` / When uiState / Then body는 Assessment이고 banner가 있다
    - Given 받아 둔 값이 데이터 A, 받은 적 없음 / When uiState / Then banner는 null
    - Given 받아 둔 값 없음, 가장 최근 받기가 `Failure(NO_CONNECTION)` / When uiState / Then body는 Failed이고 banner는 null
- R-45 (글 고르기): Given 받아 둔 값이 데이터 A, 가장 최근 받기가 RATE_LIMITED / NO_CONNECTION / OTHER (세 경우) / When uiState / Then banner의 title과 message는 차례로 "무료 한도를 넘었습니다"와 "잠시 뒤 다시 해 주세요. 받아 둔 값으로 보여 줍니다." / "새 값을 받지 못했습니다"와 "연결을 확인해 주세요. 받아 둔 값으로 보여 줍니다." / "새 값을 받지 못했습니다"와 "잠시 뒤 다시 해 주세요. 받아 둔 값으로 보여 줍니다."
- R-46 (글 고르기): Given 받아 둔 값 없음, 가장 최근 받기가 RATE_LIMITED / NO_CONNECTION / OTHER (세 경우) / When uiState / Then Failed의 message는 차례로 "무료 한도를 넘었습니다. 잠시 뒤 다시 해 주세요." / "연결을 확인하고 다시 해 주세요." / "잠시 뒤 다시 해 주세요."
- R-47: Given 받아 둔 값이 데이터 A, banner가 보이는 중 / When onRetryClick 뒤 받기가 Success로 끝난다 / Then banner는 null
- R-21: Given 받아 둔 값이 데이터 A / When onRefreshClick / Then 받기가 시작된다
- R-28: Given 받아 둔 값 없음, body가 Failed / When onRetryClick / Then 받기가 시작되고 body는 Loading이 된다
- R-22 (버튼의 상태, 받아 둔 값으로 계속 보임): Given 받아 둔 값이 데이터 A, `fetch`가 응답 대기 중 / When onRefreshClick 뒤 uiState / Then refreshButton은 REFRESHING, body는 Assessment 그대로다. 받기가 끝나면 ENABLED다
- R-23: Given 받아 둔 값이 데이터 A, body가 Assessment(compositeScoreText `+1.00`) / When onRefreshClick으로 데이터 B를 받아 저장한다 / Then compositeScoreText가 `+0.41`로 바뀐다
- R-24 (버튼의 상태): Given FRED의 키가 MISSING / When uiState / Then refreshButton은 DISABLED
- R-44 (틀림이 되면 키 안내 카드): Given 받아 둔 값이 데이터 A, body가 Assessment / When TWELVE_DATA의 키가 INVALID로 바뀐다 / Then body는 KeyGuide이고 TWELVE_DATA 줄의 딱지는 INVALID다
- R-49 (값을 지우지 않음, 받아 둔 값으로 판정)
    - Given 받아 둔 값이 데이터 A, body가 Assessment / When FRED의 키가 MISSING으로 바뀐다 / Then body는 KeyGuide이고, 받아 둔 값을 지우거나 바꾸는 호출은 없다
    - Given 위 상태 / When FRED의 키가 다시 AVAILABLE로 바뀐다 / Then body는 받아 둔 값으로 Assessment가 되고 받기가 시작된다
- R-53 (본문 다시 정하기): Given 받아 둔 값 없음, FRED의 키가 MISSING이라 body가 KeyGuide / When FRED의 키가 AVAILABLE로 바뀐다 / Then body는 KeyGuide가 아니다(Loading)

**확인 기준**
- 테스트 통과, 컴파일 오류 없음

### 단위 8: 화면과 이동

**만들거나 고칠 것**
- 신규 MarketMoodScreen (feature/marketmood/presentation)
- 신규 AppNavHost (맨 위 패키지)
- 수정 MainActivity (맨 위 패키지)

**지시**
- MarketMoodScreen은 시안 `https://claude.ai/artifact/LuRSF8bSPDqruAbnUSfye9`의 아래 화면과 같은 모양으로 만든다. 모양의 값(색, 크기, 간격, 모서리)은 시안의 HTML에서 가져오되, 단위 1의 테마에 있는 값은 테마의 것을 쓴다. 문구, 링크 주소, 동작은 이 지시의 글이 맞다. 시안의 숫자와 날짜는 예시다. (R-19)

| 시안의 화면 이름 | 시안 안의 파일 | 그리는 상태 |
|---|---|---|
| 시장 분위기 · 판정이 나왔을 때 | `project/Market-Main.dc.html` | body가 Assessment |
| 시장 분위기 · 장세와 종합 점수가 다른 날 | `project/Market-Pending.dc.html` | pendingNotice가 있을 때 |
| 시장 분위기 · 새로 받지 못했을 때 | `project/Market-Stale.dc.html` | banner가 있을 때. 장세가 약세일 때의 단계 이름 색 |
| 시장 분위기 · 키가 없을 때 | `project/Market-NoKey.dc.html` | body가 KeyGuide |
| 시장 분위기 · 한 번도 받지 못했을 때 | `project/Market-Failed.dc.html` | body가 Failed |
| 시장 분위기 · 중립인 날 | `project/Market-Neutral.dc.html` | 장세가 중립일 때의 단계 이름 색. 0인 점수의 모양 |
| 시장 분위기 · 물장인 날 | `project/Market-Bear.dc.html` | 장세가 물장일 때의 단계 이름 색 |
| 시장 분위기 · 받는 중 | `project/Market-Loading.dc.html` | body가 Loading. refreshButton이 REFRESHING일 때의 위쪽 줄 |

- 시안의 진행 표시는 멈춘 그림이다. 앱에서는 같은 크기, 굵기, 색으로 돌아가게 한다(값은 단위 1의 표).
- refreshButton이 REFRESHING이면 본문이 무엇이든 위쪽 줄은 "시장 분위기 · 받는 중"의 위쪽 줄과 같다.
- 시안에 없는 상태: 메뉴는 Material의 기본 메뉴 모양을 쓴다. Undecided에는 위쪽 줄만 그린다.
- 시안은 내용 높이만큼 길게 그려져 있다. 앱에서는 아래의 밀어 보기를 따른다.
- `MarketMoodScreen(onOpenSettings: () -> Unit, viewModel: MarketMoodViewModel = hiltViewModel())`. 받은 상태를 그리고 입력을 넘기기만 한다. 판정, 계산, 숫자를 글로 바꾸는 일을 여기에 두지 않는다. 상태는 수명 주기에 맞춰 모은다(collectAsStateWithLifecycle).
- 화면은 위에서 아래로 위쪽 줄, 안내 띠(있을 때만), 본문 순서다. 내용이 화면보다 길면 위쪽 줄은 제자리에 두고 그 아래를 위아래로 밀어 본다. 상태 표시줄과 아래쪽 시스템 영역에 내용이 가리지 않게 한다. (R-02)
- 위쪽 줄: 왼쪽에 앱 이름 "학반꿀", 오른쪽에 새로 고침 버튼과 메뉴 버튼. (R-03)
    - 새로 고침 버튼: ENABLED면 누를 때 onRefreshClick. REFRESHING이면 그 자리에 돌아가는 진행 표시를 보이고 누를 수 없다. DISABLED면 흐리게(테마의 비활성 색) 보이고 눌러도 반응하지 않는다. (R-21, R-22, R-24)
    - 메뉴 버튼을 누르면 "설정" 하나가 든 메뉴가 열린다. "설정"을 누르면 메뉴를 닫고 onOpenSettings를 부른다. 메뉴가 열렸는지는 화면 안의 상태로 둔다. (R-25, R-26)
- 안내 띠: banner의 title과 message 두 줄, 오른쪽에 "다시 시도". 누르면 onRetryClick. (R-18, R-28)
- 본문이 Assessment일 때: 종합 판정 카드, 묶음 카드(groups의 순서대로 하나씩), 맨 아래 안내 순서다. (R-04, R-05)
    - 종합 판정 카드는 위에서 아래로 아래와 같다. (R-06)
        - 장세: 왼쪽에 "지금 장은", 오른쪽에 asOfText. 그 아래에 stageName을 크게, stage의 단계 글자색으로. (R-07)
        - 구분선.
        - 종합 점수: "종합 점수 +0.65 · 불장"처럼 "종합 점수", compositeScoreText, computedStageName을 한 줄에 적는다. 점수는 compositeTone의 점수 색과 굵은 글자로, 단계 이름은 단계의 색을 쓰지 않고 굵은 글자로 적는다. (R-07, R-13)
        - 눈금: 물장, 약세, 중립, 불장 순서의 폭이 같은 네 칸을 테마의 눈금 칸 색으로 그린 가로 막대. gaugePosition의 자리에 표시가 있고, 칸마다 아래에 단계 이름이 있다. 색만으로 구분하지 않게 단계 이름을 늘 함께 적는다. (R-07)
        - 다른 날 안내: pendingNotice가 있을 때만 눈금 아래에 보인다. (R-08)
    - 묶음 카드: 묶음 이름, 그 아래 weightText, 오른쪽에 묶음 점수(scoreTone의 색). 그 아래에 항목이 한 줄씩 있다. (R-09, R-13)
    - 항목 줄: 항목 이름과 그 옆에 suffix(있을 때), 그 아래 evidence, 오른쪽에 항목 점수(scoreTone의 색). 항목 줄은 눌러도 아무 일도 일어나지 않는다. (R-10, R-13)
    - 맨 아래 안내는 위에서 아래로 "장 분위기를 요약한 참고 표시입니다. 매매 신호가 아닙니다.", "데이터: Twelve Data, FRED", FRED 고지(영어 원문), "FRED API 이용 약관" 링크다. 링크를 누르면 `https://fred.stlouisfed.org/docs/api/terms_of_use.html`을 브라우저로 연다. 고지와 링크는 FRED API 약관이 앱에 두라고 요구하는 것이라 빼지 않는다. (R-14, R-29)
- 본문이 KeyGuide일 때: 카드에 위에서 아래로 "키를 넣어 주세요", "시장 분위기를 보려면 두 출처의 키가 모두 필요합니다.", rows마다 한 줄(출처 이름, 그 아래 무엇에 쓰는 키인지, 오른쪽에 KeyStatusBadge), "설정으로 가기" 버튼. 버튼을 누르면 onOpenSettings를 부른다. (R-15, R-27, R-52)
- 본문이 Loading일 때: 카드에 돌아가는 진행 표시와 그 오른쪽에 "데이터를 받는 중". (R-16)
- 본문이 Failed일 때: 카드에 알림 아이콘과 "데이터를 받지 못했습니다", 그 아래 message, "다시 시도" 버튼. 누르면 onRetryClick. (R-17, R-28)
- 화면이 보이게 될 때(수명 주기 ON_START) onScreenShown을 부른다. 앱을 열 때, 설정 화면에서 돌아올 때, 다른 앱에서 돌아올 때가 모두 여기에 든다. 화면 회전으로 다시 만들어질 때는 부르지 않는다. (R-20, P-03)
- Android Studio 미리보기(@Preview)를 둔다: 판정이 나왔을 때(불장), 중립인 날, 물장인 날, 다른 날 안내가 있을 때, 안내 띠가 있을 때, 키 안내, 받는 중, 받지 못함. 다른 날 안내와 중립·물장은 그런 날이 아니면 실제로 볼 수 없어 미리보기로 확인한다. (R-08, R-19)
- AppNavHost는 Jetpack Navigation(Compose용)으로 화면 이동을 맡는다. 시작 화면은 시장 분위기이고, onOpenSettings가 불리면 그 위에 설정 화면(SettingsScreen)을 쌓는다. 기기의 뒤로 가기로 시장 분위기로 돌아온다. 화면은 다른 기능의 화면을 가져오지 않고 갈 곳을 콜백으로만 받는다. 설정 화면의 뒤로 가기 버튼은 이번에 만들지 않는다. (P-01, R-01, R-26, R-27)
- MainActivity는 공용 테마 안에 AppNavHost를 띄운다. (R-01)

**테스트 지시**
- 없음. 화면의 표시와 이동은 "3. 수동 확인"에서 본다.

**확인 기준**
- 테스트 통과, 컴파일 오류 없음

## 3. 수동 확인

- R-01: Given 앱이 설치됨 / When 앱을 연다 / Then 시장 분위기 화면이 바로 보인다
- R-02: Given 판정이 보이는 시장 분위기 화면 / When 화면을 위로 민다 / Then 위쪽 줄은 제자리에 있고 그 아래 내용만 움직여 맨 아래 안내까지 보인다
- R-03: Given 시장 분위기 화면 / When 위쪽 줄을 본다 / Then 왼쪽에 "학반꿀", 오른쪽에 새로 고침 버튼과 메뉴 버튼이 있다
- R-04 (표시): Given 두 출처의 키가 저장됨이고 받아 둔 값이 있음 / When 앱을 연다 / Then 판정이 보인다. 설정에서 키 하나를 지우고 돌아오면 판정 대신 키 안내 카드가 보인다
- R-05 (표시): Given 판정이 보이는 화면 / When 화면을 본다 / Then 위에서 아래로 종합 판정 카드, 주식 카드, 채권 신호 카드, 맨 아래 안내 순서다
- R-06 (표시): Given 판정이 보이는 화면 / When 종합 판정 카드를 본다 / Then 위에서 아래로 "지금 장은"과 기준 날짜("11월 23일 종가 기준 (미국)" 모양), 크게 적힌 단계 이름, 구분선, "종합 점수 +0.65 · 불장" 모양의 한 줄, 네 칸 눈금과 그 위의 표시, 칸마다의 단계 이름이 있다
- R-07: Given 판정이 보이는 화면 / When 종합 판정 카드를 본다 / Then 장세의 단계 이름이 그 단계의 색(불장 빨강, 중립 회색, 약세 연한 파랑, 물장 파랑)이고, 눈금의 네 칸이 왼쪽부터 파랑, 연한 파랑, 회색, 빨강이며, 종합 점수 옆의 단계 이름은 단계의 색이 아닌 굵은 글자다
- R-08 (표시): Given 종합 점수의 단계와 장세가 다른 날(그런 날이 아니면 Android Studio의 미리보기로 본다) / When 종합 판정 카드를 본다 / Then 눈금 아래에 "종합 점수는 중립인데 장세는 아직 불장입니다. 점수가 3거래일 이어져야 장세가 바뀝니다. (1일째)" 모양의 한 줄이 보인다. 둘이 같은 날에는 이 줄이 없다
- R-09 (표시): Given 판정이 보이는 화면 / When 묶음 카드를 본다 / Then 주식 카드에 "주식", 그 아래 "비중 70%", 오른쪽에 묶음 점수가 있고, 채권 신호 카드에 "채권 신호", "비중 30%", 묶음 점수가 있다
- R-10 (표시): Given 판정이 보이는 화면 / When 항목 줄을 본다 / Then 주식 카드에 전체 시장 SPY, 나스닥 100 QQQ, 기술 XLK, 반도체 SMH가 이 순서로 있고 각각 "200일 평균보다 +4.1% · 고점보다 -3.2%" 모양의 근거 숫자와 오른쪽의 항목 점수가 있다. 채권 신호 카드에 장단기 금리차 10년 - 2년("+0.29%p" 모양), 하이일드 스프레드("3.76%" 모양)가 있다
- R-13 (색 표시): Given 판정이 보이는 화면 / When 점수들을 본다 / Then 0보다 큰 점수는 빨강, 0보다 작은 점수는 파랑, 0인 점수는 회색이고, 0에는 부호가 없다
- R-14: Given 판정이 보이는 화면 / When 맨 아래로 민다 / Then 위에서 아래로 "장 분위기를 요약한 참고 표시입니다. 매매 신호가 아닙니다.", "데이터: Twelve Data, FRED", "This product uses the FRED® API but is not endorsed or certified by the Federal Reserve Bank of St. Louis.", "FRED API 이용 약관"이 있다
- R-15 (표시): Given Twelve Data의 키가 없고 FRED의 키가 저장됨 / When 시장 분위기 화면을 연다 / Then 카드에 "키를 넣어 주세요", "시장 분위기를 보려면 두 출처의 키가 모두 필요합니다.", Twelve Data 줄(주식과 ETF 가격, 딱지 "키 없음"), FRED 줄(금리와 채권 지표, 딱지 "저장됨"), "설정으로 가기" 버튼이 보인다
- R-16: Given 두 출처의 키가 저장됨이고 받아 둔 값이 없음 / When 시장 분위기 화면을 연다 / Then 값이 올 때까지 카드에 돌아가는 진행 표시와 "데이터를 받는 중"이 보인다
- R-17: Given 받아 둔 값이 없고 받기에 실패함 / When 화면을 본다 / Then 카드에 "데이터를 받지 못했습니다", 원인에 따른 한 줄, "다시 시도" 버튼이 보인다
- R-18 (표시): Given 판정이 보이는 화면 / When 비행기 모드에서 새로 고침을 누른다 / Then 위쪽 줄 아래에 두 줄짜리 안내 띠와 "다시 시도"가 보이고, 그 아래 판정은 그대로다
- R-19 (판정이 나왔을 때): Given 판정이 보이는 화면 / When 화면을 연다 / Then 시안 "시장 분위기 · 판정이 나왔을 때"와 나란히 놓고 볼 때 배치, 간격, 크기, 색이 같다
- R-19 (장세와 종합 점수가 다른 날): Given 다른 날 안내가 있는 상태(그런 날이 아니면 Android Studio의 미리보기로 본다) / When 화면을 연다 / Then 시안 "시장 분위기 · 장세와 종합 점수가 다른 날"과 나란히 놓고 볼 때 배치, 간격, 크기, 색이 같다
- R-19 (새로 받지 못했을 때): Given 안내 띠가 보이는 상태 / When 화면을 연다 / Then 시안 "시장 분위기 · 새로 받지 못했을 때"와 나란히 놓고 볼 때 배치, 간격, 크기, 색이 같다
- R-19 (키가 없을 때): Given 키 안내 카드가 보이는 상태 / When 화면을 연다 / Then 시안 "시장 분위기 · 키가 없을 때"와 나란히 놓고 볼 때 배치, 간격, 크기, 색이 같다
- R-19 (한 번도 받지 못했을 때): Given 받지 못함 카드가 보이는 상태 / When 화면을 연다 / Then 시안 "시장 분위기 · 한 번도 받지 못했을 때"와 나란히 놓고 볼 때 배치, 간격, 크기, 색이 같다
- R-19 (중립인 날): Given 장세가 중립인 상태(그런 날이 아니면 Android Studio의 미리보기로 본다) / When 화면을 연다 / Then 시안 "시장 분위기 · 중립인 날"과 나란히 놓고 볼 때 배치, 간격, 크기, 색이 같다
- R-19 (물장인 날): Given 장세가 물장인 상태(그런 날이 아니면 Android Studio의 미리보기로 본다) / When 화면을 연다 / Then 시안 "시장 분위기 · 물장인 날"과 나란히 놓고 볼 때 배치, 간격, 크기, 색이 같다
- R-19 (받는 중): Given 받는 중 카드가 보이는 상태(받는 시간이 짧아 보기 어려우면 Android Studio의 미리보기로 본다) / When 화면을 연다 / Then 시안 "시장 분위기 · 받는 중"과 나란히 놓고 볼 때 배치, 간격, 크기, 색이 같다(진행 표시는 앱에서 돌아간다)
- R-20 (화면이 보이게 될 때 알림): Given 두 출처의 키가 저장됨, 받아 둔 값이 없고 받지 못함 카드가 보임(비행기 모드에서 앱을 연 상태) / When 비행기 모드를 끄고 홈으로 나갔다가 앱으로 돌아온다 / Then "다시 시도"를 누르지 않아도 받기가 시작되어 판정이 보인다
- R-22 (진행 표시): Given 판정이 보이는 화면 / When 새로 고침을 누른다 / Then 받는 동안 새로 고침 버튼 자리에 돌아가는 진행 표시가 보이고 다시 누를 수 없으며, 판정은 그대로 보인다
- R-24 (흐리게 보임): Given 키 안내 카드가 보이는 화면 / When 새로 고침 버튼을 본다 / Then 흐리게 보이고 눌러도 반응하지 않는다
- R-25: Given 시장 분위기 화면 / When 메뉴 버튼을 누른다 / Then "설정" 하나가 든 메뉴가 열린다
- R-26: Given 메뉴가 열림 / When "설정"을 누른다 / Then 설정 화면으로 이동하고, 기기의 뒤로 가기로 시장 분위기 화면에 돌아온다
- R-27: Given 키 안내 카드가 보이는 화면 / When "설정으로 가기"를 누른다 / Then 설정 화면으로 이동한다
- R-29: Given 판정이 보이는 화면 / When 맨 아래의 "FRED API 이용 약관"을 누른다 / Then 브라우저에서 `https://fred.stlouisfed.org/docs/api/terms_of_use.html`이 열린다
- R-41 (실제 요청): Given 두 출처에서 받은 맞는 키가 저장됨, 받아 둔 값이 없음 / When 시장 분위기 화면을 연다 / Then 판정이 나오고, 여섯 항목 모두에 근거 숫자와 점수가 보인다
- R-45 (실제 연결 끊김): Given 판정이 보이는 화면 / When 비행기 모드에서 새로 고침을 누른다 / Then 안내 띠에 "새 값을 받지 못했습니다"와 "연결을 확인해 주세요. 받아 둔 값으로 보여 줍니다."가 보인다
- R-46 (실제 연결 끊김): Given 두 출처의 키가 저장됨, 받아 둔 값이 없음 / When 비행기 모드에서 앱을 연다 / Then "데이터를 받지 못했습니다"와 "연결을 확인하고 다시 해 주세요."가 보인다
- R-48: Given 판정을 본 뒤 / When 비행기 모드를 켜고 앱을 완전히 껐다 켠다 / Then 받아 둔 값으로 같은 판정이 바로 보인다
- R-49 (키를 지웠다 다시 저장): Given 판정이 보이는 상태 / When 설정에서 키 하나를 지우고 돌아왔다가, 다시 설정에서 같은 키를 저장하고 돌아온다 / Then 키를 지운 동안에는 키 안내 카드가 보이고, 다시 저장한 뒤에는 판정이 바로 보이며 새로 받는다
- R-52 (글과 색): Given 키 안내 카드가 보이는 화면과 설정 화면 / When 두 화면을 나란히 본다 / Then 출처 이름과 무엇에 쓰는 키인지의 글이 같고, 딱지의 글이 같으며 색의 종류(키 없음 회색, 저장됨 파랑, 틀림 빨강)가 같다
- R-55 (받는 중에 앱이 꺼짐): Given 판정이 보이는 화면 / When 새로 고침을 누르고 받는 중에 앱을 강제 종료한 뒤 다시 켠다 / Then 앱이 멈추지 않고 판정이 보인다(꺼지기 전의 값이거나 새로 받은 값이다)
- R-57 (앱을 껐다 켠 뒤): Given 안내 띠가 보이는 상태(비행기 모드에서 새로 고침을 누른 뒤) / When 비행기 모드인 채로 6시간이 지나기 전에 앱을 완전히 껐다 켠다 / Then 안내 띠 없이 받아 둔 값으로 판정이 보인다
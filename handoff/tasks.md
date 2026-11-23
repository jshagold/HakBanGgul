# Tasks: 설정 신규 개발

기능: 설정
변경: 신규 개발 (CS-001)
기준 Plan: 1.0.0
상태: 구현 중

| 전달 | 날짜 | 이유 |
|---|---|---|
| 1 | 2026-11-23 | 최초 전달 |

## 1. 개요
설정 화면에서 사용자가 데이터 출처(Twelve Data, FRED)마다 API 키를 넣어 확인하고 저장하며, 저장된 키를 보고 지우는 기능을 새로 만든다. 다른 기능은 이 키를 `core/network`의 `ApiKeyProvider`로만 얻는다.
키는 출처가 받아들였다는 응답이 왔을 때만 기기에 암호화해 저장한다. 확인은 화면을 벗어나도 이어지고, 저장되지 않은 것은 앱이 꺼지면 남지 않는다.
첫 변경이라 Hilt, 네트워크·저장 라이브러리, 경계 검사 테스트, 공용 출처 모델도 이번에 들인다. 다른 기능의 데이터 요청에 키를 붙이는 인터셉터는 이번에 만들지 않는다.

## 2. 구현 단위

### 단위 1: 기반 (라이브러리, 앱 조립, 공용 출처 모델, 경계 검사)

**만들거나 고칠 것**
- 수정 `gradle/libs.versions.toml`
- 수정 `build.gradle.kts` (맨 위), `app/build.gradle.kts`
- 신규 HakBanGgulApp (맨 위 패키지)
- 수정 `app/src/main/AndroidManifest.xml`
- 신규 DataSource (core/model)
- 신규 CoroutineScopeModule, ApplicationScope 한정자 (core/di)
- 신규 경계 검사 테스트 (`app/src/test/java/com/hak/hakbanggul/architecture/`)
- 삭제 `app/src/test/java/com/hak/hakbanggul/ExampleUnitTest.kt`

**지시**
- 라이브러리를 들인다: Hilt(KSP로), hilt-navigation-compose, Retrofit, OkHttp, kotlinx.serialization과 그 Retrofit 변환기, DataStore Preferences, Tink(`tink-android`), lifecycle-runtime-compose, lifecycle-viewmodel-compose, 테스트용 kotlinx-coroutines-test와 Konsist. JSON은 kotlinx.serialization으로 읽는다. 버전은 지금 AGP 9.5.2·Kotlin 2.4.21과 맞는 최신 안정 버전으로 하고 `gradle/libs.versions.toml`에 모은다. (P-10)
- Compose BOM(2024.09.00)과 lifecycle(2.6.1)은 템플릿 기본값이라 낮다. 위 라이브러리와 맞는 최신 안정 버전으로 올린다.
- HakBanGgulApp은 `@HiltAndroidApp`인 Application이다. 매니페스트의 `application`에 등록한다.
- DataSource는 출처 목록 열거형이다. 값은 `TWELVE_DATA`, `FRED`를 이 순서로 둔다. 선언 순서가 설정 화면의 카드 순서다. 선언 위에 `// 용어: 출처`를 단다. (R-03, P-09)
- CoroutineScopeModule은 앱이 살아 있는 동안의 CoroutineScope 하나를 `@ApplicationScope` 한정자로 제공한다. SupervisorJob을 쓴다. (P-02)
- 경계 검사 테스트는 `SDD/규칙/CLI용 CLAUDE.md` > 7. 코드 규칙 > 경계 검사의 네 가지를 Konsist로 검사한다. 이때 아래처럼 본다.
  - Android 라이브러리: `android.`, `androidx.`로 시작하는 것. 네트워크 라이브러리: `okhttp3.`, `retrofit2.`. DB 라이브러리: Room, DataStore(`androidx.room.`, `androidx.datastore.`).
  - `domain`이 `kotlinx.coroutines`, `javax.inject`, `com.hak.hakbanggul.core` 아래의 패키지를 가져오는 것은 허용한다.

**테스트 지시**
- 경계 검사: 위 네 가지 검사가 지금 코드에서 통과한다. 이후 단위에서도 계속 통과해야 한다.

**확인 기준**
- 테스트 통과, 컴파일 오류 없음

### 단위 2: 공용 네트워크 (키 제공 인터페이스, 접속 주소, 응답 판정)

**만들거나 고칠 것**
- 신규 ApiKeyProvider (core/network)
- 신규 ApiKeyResult (core/network)
- 신규 ApiKeyAvailability (core/network)
- 신규 DataSourceEndpoints (core/network)
- 신규 SourceResponseKind (core/network)
- 신규 SourceResponseClassifier (core/network)
- 신규 NetworkModule (core/di)

**지시**
- ApiKeyResult는 키 얻기의 결과다: `Available(key: String)` / `Missing` / `Invalid`. (R-23, R-41)
- ApiKeyAvailability는 키 쓸 수 있음 여부다: `AVAILABLE` / `MISSING` / `INVALID`. (R-25, R-41)
- ApiKeyProvider는 다른 기능이 키를 얻는 유일한 인터페이스다. 내놓는 것 (R-23, R-24, R-25, R-41, P-01)
  - `suspend fun getKey(source: DataSource): ApiKeyResult`
  - `fun observeAvailability(source: DataSource): Flow<ApiKeyAvailability>`
  - `suspend fun reportRejected(source: DataSource, rejectedKey: String)`
- 이 단위에서는 인터페이스만 만든다. 구현은 단위 4에서 설정이 한다. (P-01)
- DataSourceEndpoints는 출처마다의 접속 주소를 준다: `fun baseUrl(source: DataSource): String`. Twelve Data `https://api.twelvedata.com/`, FRED `https://api.stlouisfed.org/`. 기능 코드에는 주소를 적지 않는다.
- SourceResponseKind는 출처 응답의 종류다: `ACCEPTED`(받아들임) / `KEY_REJECTED`(키 거절) / `OTHER_FAILURE`(그 밖의 실패).
- SourceResponseClassifier는 `fun classify(source: DataSource, httpStatus: Int, body: String?): SourceResponseKind`를 내놓는다. 기준 (R-18, R-30, R-31, P-04)
  - Twelve Data: HTTP 상태가 401이거나, 본문이 JSON이고 `status`가 `"error"`이면서 `code`가 401이면 KEY_REJECTED. HTTP 2xx이고 본문의 `status`가 `"error"`가 아니면 ACCEPTED. 그 밖(429, 403, 5xx, 본문 `code`가 401이 아닌 오류 등)은 OTHER_FAILURE. Twelve Data는 오류를 HTTP 200에 본문 `code`로 돌려주는 경우가 있어 본문도 본다.
  - FRED: HTTP 2xx면 ACCEPTED. 400이면서 본문 `error_message`에 `api_key`가 들어 있으면 KEY_REJECTED. 401, 403도 KEY_REJECTED. 그 밖(429, 5xx, `api_key`가 없는 400 등)은 OTHER_FAILURE.
  - 본문을 JSON으로 읽지 못하면 HTTP 상태만으로 위 기준을 따른다.
  - 이 판정은 이곳 한 곳에만 둔다. 나중에 데이터 요청의 거절을 가를 때도 이것을 쓴다.
- NetworkModule은 OkHttpClient 하나, kotlinx.serialization의 Json, DataSourceEndpoints, SourceResponseClassifier, 출처별 Retrofit(`@TwelveDataRetrofit`, `@FredRetrofit` 한정자. 주소는 DataSourceEndpoints에서)을 제공한다. OkHttp의 연결·읽기 시간 제한은 15초로 둔다. 10초 제한은 단위 3이 따로 건다. (P-03)

**테스트 지시**
- R-18 (거절 판정)
  - Given Twelve Data, HTTP 401 / When classify / Then KEY_REJECTED
  - Given Twelve Data, HTTP 200, 본문 `{"code":401,"message":"**apikey** parameter is incorrect or not specified.","status":"error"}` / When classify / Then KEY_REJECTED
  - Given FRED, HTTP 400, 본문 `{"error_code":400,"error_message":"Bad Request.  The value for variable api_key is not registered."}` / When classify / Then KEY_REJECTED
  - Given FRED, HTTP 400, 본문 `{"error_code":400,"error_message":"Bad Request.  The value for variable api_key is not a 32 character alpha-numeric lower-case string."}` / When classify / Then KEY_REJECTED
  - Given FRED, HTTP 401 / When classify / Then KEY_REJECTED
  - Given Twelve Data, HTTP 200, 본문 `{"timestamp":"2026-11-23 10:00:00","current_usage":1,"plan_limit":8}` / When classify / Then ACCEPTED
  - Given FRED, HTTP 200, 본문 `{"seriess":[{"id":"DGS10"}]}` / When classify / Then ACCEPTED
- R-30 (그 밖의 실패 판정)
  - Given Twelve Data, HTTP 429 / When classify / Then OTHER_FAILURE
  - Given Twelve Data, HTTP 200, 본문 `{"code":429,"message":"You have run out of API credits for the current minute.","status":"error"}` / When classify / Then OTHER_FAILURE
  - Given Twelve Data, HTTP 403 / When classify / Then OTHER_FAILURE
  - Given FRED, HTTP 400, 본문 `{"error_code":400,"error_message":"Bad Request.  The series does not exist."}` / When classify / Then OTHER_FAILURE
  - Given FRED, HTTP 500, 본문 없음 / When classify / Then OTHER_FAILURE
- R-31 (한도 넘음·서버 오류는 거절이 아님)
  - Given FRED, HTTP 429 / When classify / Then OTHER_FAILURE
  - Given Twelve Data, HTTP 500, 본문이 JSON이 아님 / When classify / Then OTHER_FAILURE

**확인 기준**
- 테스트 통과, 컴파일 오류 없음

### 단위 3: 설정 Domain (키 저장·지우기·내주기, 키 확인 관리자)

**만들거나 고칠 것**
- 신규 ApiKeyStatus (feature/settings/domain)
- 신규 StoredApiKey (feature/settings/domain)
- 신규 KeyCheckResult (feature/settings/domain)
- 신규 SaveKeyOutcome (feature/settings/domain)
- 신규 KeyCheckState (feature/settings/domain)
- 신규 ApiKeyRepository (feature/settings/domain)
- 신규 ApiKeyCheckRepository (feature/settings/domain)
- 신규 ObserveApiKeysUseCase (feature/settings/domain)
- 신규 SaveApiKeyUseCase (feature/settings/domain)
- 신규 DeleteApiKeyUseCase (feature/settings/domain)
- 신규 GetApiKeyUseCase (feature/settings/domain)
- 신규 ObserveKeyAvailabilityUseCase (feature/settings/domain)
- 신규 ReportKeyRejectedUseCase (feature/settings/domain)
- 신규 KeyCheckManager (feature/settings/domain)

**지시**
- 모델
  - ApiKeyStatus: 저장되는 키의 상태 `SAVED`(저장됨) / `INVALID`(틀림). 키 없음은 저장된 키가 없는 것으로 나타낸다. 선언 위에 `// 용어: 키의 상태`. (R-17)
  - StoredApiKey: `source: DataSource`, `key: String`, `status: ApiKeyStatus`.
  - KeyCheckResult: 시험 요청의 결과 `ACCEPTED` / `REJECTED` / `NO_CONNECTION` / `UNAVAILABLE`. 선언 위에 `// 용어: 키 확인`.
  - SaveKeyOutcome: 저장 시도의 결과 `SAVED` / `REJECTED` / `NO_CONNECTION` / `UNAVAILABLE` / `STORE_FAILED`.
  - KeyCheckState: 출처 하나의 진행 중이거나 끝난 확인. `Checking(enteredKey: String)` / `Failed(enteredKey: String, outcome: SaveKeyOutcome)`. enteredKey는 사용자가 넣은 그대로(빈칸을 빼기 전)다.
- ApiKeyRepository가 내놓는 것
  - `fun observeKeys(): Flow<Map<DataSource, StoredApiKey>>`: 저장된 키가 있는 출처만 담는다.
  - `suspend fun save(source: DataSource, key: String)`: 상태 SAVED로 저장한다. 저장하지 못하면 IOException을 던진다.
  - `suspend fun delete(source: DataSource)`
  - `suspend fun updateStatus(source: DataSource, status: ApiKeyStatus)`: 저장된 키가 없으면 아무것도 하지 않는다.
- ApiKeyCheckRepository가 내놓는 것: `suspend fun check(source: DataSource, key: String): KeyCheckResult`
- UseCase는 모두 `operator fun invoke`로 부른다.
  - ObserveApiKeysUseCase `(): Flow<Map<DataSource, StoredApiKey>>`: ApiKeyRepository의 것을 그대로 내준다.
  - SaveApiKeyUseCase `(source: DataSource, rawKey: String): SaveKeyOutcome`
    - 키의 앞뒤 빈칸을 빼고 쓴다. (R-27)
    - 그 출처에 시험 요청을 한 번 보낸다. 다른 출처에는 아무것도 하지 않는다. (R-11, R-16)
    - 시험 요청에 10초 제한을 코루틴의 시간 제한으로 건다. 넘기면 NO_CONNECTION으로 다룬다. (R-40, R-19, P-03)
    - ACCEPTED일 때만 저장하고 SAVED를 돌려준다. 저장이 IOException으로 실패하면 STORE_FAILED를 돌려준다. (R-12, R-29, R-43)
    - REJECTED, NO_CONNECTION, UNAVAILABLE이면 저장하지 않고 같은 이름의 결과를 돌려준다. (R-18, R-19, R-30, R-29)
  - DeleteApiKeyUseCase `(source: DataSource)`: 그 출처의 키만 지운다. (R-34, R-16)
  - GetApiKeyUseCase `(source: DataSource): ApiKeyResult`: 상태 SAVED면 `Available(키)`, 저장된 키가 없으면 `Missing`, INVALID면 `Invalid`. 틀림인 키는 내주지 않는다. (R-23, R-41)
  - ObserveKeyAvailabilityUseCase `(source: DataSource): Flow<ApiKeyAvailability>`: 위와 같은 기준으로 AVAILABLE / MISSING / INVALID를 내보낸다. 값이 바뀔 때만 내보낸다. (R-25, R-41)
  - ReportKeyRejectedUseCase `(source: DataSource, rejectedKey: String)`: 그 출처에 저장된 키가 있고 rejectedKey와 같을 때만 상태를 INVALID로 바꾼다. 다르거나 저장된 키가 없으면 아무것도 바꾸지 않는다. (R-24, R-39)
- KeyCheckManager는 앱 전체에 하나만 있다(Hilt 싱글턴). 생성자로 SaveApiKeyUseCase와 `@ApplicationScope` CoroutineScope를 받는다. (R-36, P-02)
  - `val states: StateFlow<Map<DataSource, KeyCheckState>>`: 확인 중이거나 확인에 실패한 출처만 담는다. 메모리에만 두고 저장하지 않는다. (R-20, R-38, P-02)
  - `fun startSave(source: DataSource, rawKey: String)`: 그 출처를 `Checking(rawKey)`로 두고, 받은 CoroutineScope에서 SaveApiKeyUseCase를 돌린다. 부른 쪽(ViewModel)의 범위와 상관없이 끝까지 돈다. (R-36)
  - 결과가 SAVED면 그 출처를 states에서 뺀다. 그 밖이면 `Failed(rawKey, 결과)`로 둔다. (R-12, R-37)
  - 그 출처가 이미 Checking이면 startSave를 무시한다. 다른 출처는 따로 돈다. (R-32, R-11, R-16)

**테스트 지시**
- R-11 (시험 요청 한 번, 다른 출처에 영향 없음)
  - Given FRED에 키 `fredkey00000000000000000000000001`이 SAVED로 저장됨 / When SaveApiKeyUseCase(TWELVE_DATA, `tdkey000000000000000000000000001`) / Then ApiKeyCheckRepository.check가 TWELVE_DATA로 정확히 한 번 불리고, FRED에는 불리지 않으며, FRED의 저장된 키는 그대로다
  - Given TWELVE_DATA 확인이 응답 대기 중 / When KeyCheckManager.startSave(FRED, 키) / Then FRED도 Checking이 되고 FRED의 확인이 끝나면 TWELVE_DATA는 여전히 Checking이다
- R-32 (확인 중 상태)
  - Given 확인이 응답 대기 중 / When startSave(TWELVE_DATA, `abc`) / Then states[TWELVE_DATA]는 `Checking("abc")`
  - Given TWELVE_DATA가 Checking / When startSave(TWELVE_DATA, `abc`)를 한 번 더 / Then check는 모두 합해 한 번만 불린다
- R-12: Given check가 ACCEPTED / When SaveApiKeyUseCase(TWELVE_DATA, `goodkey`) / Then SAVED를 돌려주고 TWELVE_DATA에 `goodkey`가 SAVED로 저장된다
- R-36: Given check가 1초 뒤 ACCEPTED / When startSave를 부른 쪽의 코루틴 범위를 바로 취소 / Then 1초 뒤 키가 SAVED로 저장되고 states에서 TWELVE_DATA가 빠진다
- R-16
  - Given TWELVE_DATA·FRED 모두 SAVED / When DeleteApiKeyUseCase(FRED) / Then TWELVE_DATA는 그대로 SAVED
  - Given FRED가 SAVED / When SaveApiKeyUseCase(TWELVE_DATA, 키)가 SAVED / Then FRED의 키와 상태는 그대로
- R-29: Given check가 REJECTED / NO_CONNECTION / UNAVAILABLE (세 경우) / When SaveApiKeyUseCase / Then 세 경우 모두 ApiKeyRepository.save가 불리지 않는다
- R-18 (저장하지 않음): Given check가 REJECTED / When SaveApiKeyUseCase(TWELVE_DATA, `badkey`) / Then REJECTED를 돌려주고 TWELVE_DATA에 저장된 키가 없다
- R-40
  - Given check가 11초 뒤에 ACCEPTED / When SaveApiKeyUseCase / Then 10초에 NO_CONNECTION을 돌려주고 저장하지 않는다
  - Given check가 9.5초 뒤에 ACCEPTED / When SaveApiKeyUseCase / Then SAVED
- R-19 (저장하지 않음, 결과): Given check가 NO_CONNECTION / When SaveApiKeyUseCase / Then NO_CONNECTION을 돌려주고 저장하지 않는다
- R-30 (저장하지 않음, 결과): Given check가 UNAVAILABLE / When SaveApiKeyUseCase / Then UNAVAILABLE을 돌려주고 저장하지 않는다
- R-43: Given check가 ACCEPTED, ApiKeyRepository.save가 IOException을 던짐 / When SaveApiKeyUseCase / Then STORE_FAILED를 돌려주고 observeKeys에 그 출처가 없다
- R-27: Given 입력 `"  abcd1234  "`, check가 ACCEPTED / When SaveApiKeyUseCase / Then check가 `"abcd1234"`로 불리고 `"abcd1234"`가 저장된다
- R-20 (저장되지 않은 것은 메모리에만)
  - Given check가 REJECTED / When startSave 뒤 끝남 / Then states[TWELVE_DATA]는 `Failed(입력한 키, REJECTED)`이고 ApiKeyRepository.save는 불리지 않았다
  - Given 위 상태의 KeyCheckManager / When KeyCheckManager를 새로 만든다(앱을 다시 켠 것) / Then 새 것의 states는 비어 있다
- R-34 (지우기): Given TWELVE_DATA가 SAVED / When DeleteApiKeyUseCase(TWELVE_DATA) / Then observeKeys에 TWELVE_DATA가 없다
- R-23: Given TWELVE_DATA에 `goodkey`가 SAVED / When GetApiKeyUseCase(TWELVE_DATA) / Then `Available("goodkey")`
- R-41
  - Given FRED에 저장된 키 없음 / When GetApiKeyUseCase(FRED) / Then `Missing`
  - Given FRED에 키가 INVALID로 저장됨 / When GetApiKeyUseCase(FRED) / Then `Invalid` (키를 내주지 않는다)
- R-24: Given TWELVE_DATA에 `k1`이 SAVED / When ReportKeyRejectedUseCase(TWELVE_DATA, `k1`) / Then TWELVE_DATA의 상태가 INVALID, 키는 `k1` 그대로
- R-39
  - Given TWELVE_DATA에 `k2`가 SAVED / When ReportKeyRejectedUseCase(TWELVE_DATA, `k1`) / Then 상태는 SAVED 그대로
  - Given TWELVE_DATA에 저장된 키 없음 / When ReportKeyRejectedUseCase(TWELVE_DATA, `k1`) / Then 저장된 키가 여전히 없다
- R-25
  - Given TWELVE_DATA에 키 없음 / When ObserveKeyAvailabilityUseCase(TWELVE_DATA)를 받는 중에 키를 저장하고, 그다음 지운다 / Then MISSING, AVAILABLE, MISSING 순서로 받는다
  - Given TWELVE_DATA가 SAVED / When 같은 키로 거절이 알려진다 / Then AVAILABLE 다음에 INVALID를 받는다
  - Given TWELVE_DATA를 받는 중 / When FRED의 키를 저장 / Then TWELVE_DATA 쪽에는 새 값이 오지 않는다

**확인 기준**
- 테스트 통과, 컴파일 오류 없음

### 단위 4: 설정 Data (암호화 저장, 시험 요청, 키 제공 구현, 백업 제외)

**만들거나 고칠 것**
- 신규 ApiKeyCipher (feature/settings/data)
- 신규 TinkApiKeyCipher (feature/settings/data)
- 신규 ApiKeyRepositoryImpl (feature/settings/data)
- 신규 TwelveDataKeyCheckApi (feature/settings/data)
- 신규 FredKeyCheckApi (feature/settings/data)
- 신규 ApiKeyCheckRepositoryImpl (feature/settings/data)
- 신규 ApiKeyProviderImpl (feature/settings/data)
- 신규 SettingsModule (feature/settings/di)
- 수정 `app/src/main/res/xml/data_extraction_rules.xml`
- 수정 `app/src/main/res/xml/backup_rules.xml`

**지시**
- 암호화 저장은 DataStore + Tink로 한다. EncryptedSharedPreferences(androidx.security:security-crypto)는 쓰지 않는다. 지원이 끝난 라이브러리다. (R-21, P-05)
- ApiKeyCipher는 `fun encrypt(plain: String): String`, `fun decrypt(cipherText: String): String`을 내놓는다. 복호화하지 못하면 예외를 던진다. (P-05)
- TinkApiKeyCipher는 ApiKeyCipher를 Tink AEAD(AES-256-GCM)로 구현한다. Tink 키셋은 Android Keystore의 키(`android-keystore://hakbanggul_api_key_master`)로 감싸 앱 전용 SharedPreferences `api_key_keyset`에 둔다. (R-21, P-05)
- ApiKeyRepositoryImpl은 ApiKeyRepository를 구현한다. 생성자로 `DataStore<Preferences>`와 ApiKeyCipher를 받는다. (R-20, R-21, R-42)
  - DataStore 파일 이름은 `api_keys`다. 출처마다 암호화한 키와 상태(SAVED / INVALID)를 둔다. 키는 늘 ApiKeyCipher로 암호화해 넣고 꺼낼 때 복호화한다. (R-21)
  - 어떤 출처의 키를 복호화하지 못하면 앱을 멈추지 않는다. 그 항목을 지우고 그 출처는 저장된 키가 없는 것으로 본다. (R-42, P-05)
  - 파일이 손상되었거나 읽다가 IOException이 나도 빈 것으로 본다(손상 처리기와 예외 처리). (R-42)
  - 저장에 실패하면 IOException을 그대로 던진다. (R-43)
  - 저장된 키와 상태는 앱을 껐다 켜도 남는다. (R-20)
- TwelveDataKeyCheckApi: `GET api_usage`. 키는 `Authorization: apikey <키>` 헤더로 보낸다. 본문은 판정에만 쓰므로 원문(`Response<ResponseBody>`)으로 받는다. (R-11, P-04)
- FredKeyCheckApi: `GET fred/series`에 `series_id=DGS10`, `file_type=json`, `api_key=<키>`를 쿼리로 보낸다. 원문으로 받는다. (R-11, P-04)
- 두 API는 키를 인자로 직접 받는다. ApiKeyProvider에서 읽지 않는다. 아직 저장되지 않은 키를 확인하는 것이기 때문이다. (R-11, P-07)
- ApiKeyCheckRepositoryImpl은 ApiKeyCheckRepository를 구현한다. (R-11, R-18, R-19, R-30, P-04)
  - 출처에 맞는 API를 부르고, HTTP 상태와 본문(성공이면 body, 실패면 errorBody의 글)을 SourceResponseClassifier에 넘긴다.
  - ACCEPTED → ACCEPTED, KEY_REJECTED → REJECTED, OTHER_FAILURE → UNAVAILABLE.
  - IOException(연결 안 됨, 시간 초과 포함) → NO_CONNECTION. 그 밖의 예외 → UNAVAILABLE. 코루틴 취소는 삼키지 않고 그대로 넘긴다(단위 3의 10초 제한이 동작해야 한다).
- ApiKeyProviderImpl은 `core/network`의 ApiKeyProvider를 구현한다. getKey는 GetApiKeyUseCase로, observeAvailability는 ObserveKeyAvailabilityUseCase로, reportRejected는 ReportKeyRejectedUseCase로 넘기기만 한다. (R-23, R-24, R-25, R-39, R-41, P-01)
- SettingsModule은 ApiKeyRepository, ApiKeyCheckRepository, ApiKeyCipher의 구현, `api_keys` DataStore, 두 KeyCheckApi(단위 2의 출처별 Retrofit으로 만듦)를 묶고, ApiKeyProvider에 ApiKeyProviderImpl을 끼운다. 모두 싱글턴이다. (P-01)
- 백업 제외: `allowBackup`은 그대로 두고, DataStore 파일(`file` 영역의 `datastore/api_keys.preferences_pb`)과 Tink 키셋(`sharedpref` 영역의 `api_key_keyset.xml`)을 뺀다. `data_extraction_rules.xml`은 `cloud-backup`과 `device-transfer` 둘 다에서, `backup_rules.xml`(Android 11 이하)에서도 뺀다. 파일의 예시 주석은 지운다. (R-22, P-06)

**테스트 지시**
- R-42
  - Given 가짜 ApiKeyCipher가 복호화할 때 예외를 던지게 하고, 임시 파일의 DataStore에 TWELVE_DATA 키가 저장되어 있음 / When observeKeys의 첫 값을 읽는다 / Then 예외 없이 TWELVE_DATA가 없는 맵을 받고, 그 항목은 DataStore에서 지워져 있다
  - Given 같은 상태에서 FRED 키는 정상으로 복호화됨 / When observeKeys / Then FRED는 그대로 있다

**확인 기준**
- 테스트 통과, 컴파일 오류 없음

### 단위 5: 설정 화면

**만들거나 고칠 것**
- 신규 SourceCardInfo (feature/settings/presentation)
- 신규 SettingsUiState (feature/settings/presentation)
- 신규 SettingsViewModel (feature/settings/presentation)
- 신규 SettingsScreen (feature/settings/presentation)
- 수정 `app/src/main/res/values/strings.xml`
- 수정 MainActivity (맨 위 패키지)

**지시**
- SourceCardInfo는 출처마다 화면에 쓰는 고정 정보다: 이름 문구, 무엇에 쓰는 키인지 문구(둘 다 문자열 리소스 ID), 키 발급 페이지 주소. (R-03, R-04, R-15)

| 출처 | 이름 | 무엇에 쓰는 키인지 | 키 발급 페이지 |
|---|---|---|---|
| TWELVE_DATA | Twelve Data | 주식과 ETF 가격 | `https://twelvedata.com/register` |
| FRED | FRED | 금리와 채권 지표 | `https://fredaccount.stlouisfed.org/apikeys` |

- SettingsUiState (맞물리는 모양)
  - `SettingsUiState(cards: List<SourceCardUiState>)`
  - `SourceCardUiState(source, nameRes, descriptionRes, badge: KeyBadge, issueUrl, mode: CardMode)`
  - `KeyBadge`: `NONE`(키 없음) / `SAVED`(저장됨) / `INVALID`(틀림)
  - `CardMode.Input(text, inputEnabled, saveEnabled, checking, errorBorder, messageRes: Int?)`
  - `CardMode.Saved(displayKey, revealed, showRejectedNotice)`
- SettingsViewModel은 `@HiltViewModel`이다. 생성자로 ObserveApiKeysUseCase, DeleteApiKeyUseCase, KeyCheckManager를 받는다. 내놓는 것
  - `val uiState: StateFlow<SettingsUiState>`
  - `fun onKeyInputChange(source: DataSource, text: String)`
  - `fun onSaveClick(source: DataSource)`: KeyCheckManager.startSave(source, 입력 칸 글자)를 부른다. (R-11)
  - `fun onRevealToggle(source: DataSource)`
  - `fun onDeleteConfirm(source: DataSource)`: DeleteApiKeyUseCase를 부른다. (R-34)
  - `fun onScreenHidden()`
- 카드는 `DataSource.entries`를 차례로 돌며 만든다. (R-03, P-09)
- 카드 모양을 고르는 규칙
  - 저장된 키가 있으면 Saved 모드다. 입력 칸, 발급 안내, 저장 버튼은 없다. (R-07)
    - displayKey는 키 보기가 꺼져 있으면 끝 네 글자만 두고 나머지 글자 수만큼 `•`로 가린 것, 켜져 있으면 키 전체다. 키가 네 글자 이하면 모두 가린다. (R-07, R-13)
    - 상태가 INVALID면 badge INVALID, showRejectedNotice 참. 키는 가린 채로 둔다. (R-28)
    - 상태가 SAVED면 badge SAVED. (R-05, R-17)
  - 저장된 키가 없으면 Input 모드이고 badge는 NONE이다. 단, KeyCheckManager가 그 출처를 `Failed(…, REJECTED)`로 갖고 있으면 badge INVALID, errorBorder 참이다. 이 틀림은 화면에만 있는 표시이고 저장된 상태는 키 없음 그대로다. (R-06, R-08, R-18, R-17)
  - Input 모드의 text: ViewModel이 가진 그 출처의 입력 글자. 없으면 KeyCheckManager의 그 출처 enteredKey, 그것도 없으면 빈 글자. (R-08, R-37)
  - Checking이면 checking 참, inputEnabled 거짓, saveEnabled 거짓. 다른 출처의 카드는 그대로다. (R-32)
  - saveEnabled: checking이 아니고, text의 앞뒤 빈칸을 뺀 것이 비어 있지 않을 때만 참. (R-26)
  - messageRes: Failed의 결과마다 REJECTED → "키가 맞지 않습니다. 다시 확인해 주세요." (R-18), NO_CONNECTION → "연결이 안 되어 키를 확인하지 못했습니다. 연결을 확인하고 다시 눌러 주세요." (R-19), UNAVAILABLE → "지금은 키를 확인할 수 없습니다. 잠시 뒤 다시 눌러 주세요." (R-30), STORE_FAILED → "키를 저장하지 못했습니다. 다시 눌러 주세요." (R-43). Checking이거나 Failed가 없으면 null. REJECTED 말고는 badge를 바꾸지 않는다. (R-19, R-30)
  - 안내는 다음에 저장을 누르거나 앱이 꺼질 때까지 남는다. (R-18, R-20)
- 키 보기 여부는 출처마다 ViewModel이 메모리에 갖는다. 처음은 꺼짐. onRevealToggle은 켜짐과 꺼짐을 바꾼다. (R-13, R-33)
- onScreenHidden은 모든 출처의 입력 글자와 키 보기 여부를 비운다. 확인 중이거나 실패한 키는 KeyCheckManager에 있어 남는다. (R-33, R-37, P-08)
- SettingsScreen (R-02, R-04, R-05, R-06, R-07, R-10, R-14, R-15, R-32, R-35)
  - 위에서 아래로: 위쪽 줄(화면 이름 "설정", 뒤로 가기 없음), 안내 "데이터를 받으려면 출처마다 키가 필요합니다. 키는 이 기기에만 저장됩니다.", 출처 카드들.
  - 카드: 출처 이름, 그 아래 무엇에 쓰는 키인지 한 줄, 출처 이름 오른쪽에 상태 딱지. 딱지는 글과 색을 함께 쓴다: NONE "키 없음" 회색, SAVED "저장됨" 파랑, INVALID "틀림" 빨강.
  - Input 모드: 이름표 "API 키" 입력 칸(비어 있으면 "키를 붙여 넣으세요"), 왼쪽에 "키 발급받기"와 그 아래 "무료, 카드 등록 없음", 오른쪽에 "저장" 버튼. errorBorder면 입력 칸 테두리를 빨갛게, messageRes가 있으면 입력 칸 아래에 보인다. checking이면 저장 버튼 자리에 돌아가는 진행 표시와 "확인 중"을 보이고 입력 칸을 비활성으로 둔다. saveEnabled가 거짓이면 저장 버튼을 비활성(흐리게, 눌러도 반응 없음)으로 보인다. 입력 칸은 치기와 붙여 넣기가 된다.
  - Saved 모드: displayKey와 그 오른쪽에 키 보기(눈 모양) 버튼, "키 지우기" 버튼. showRejectedNotice면 키 아래에 "키가 거절됐습니다. 지우고 다시 넣어 주세요."
  - "키 지우기"를 누르면 "이 키를 지울까요?" 창을 띄운다. "확인"이면 onDeleteConfirm, "취소"면 창만 닫는다. 창을 띄울지는 화면 안의 상태로 둔다.
  - "키 발급받기"를 누르면 issueUrl을 브라우저로 연다.
  - 화면이 보이지 않게 되면(수명 주기 ON_STOP) onScreenHidden을 부른다. 화면 회전 같은 구성 변경 때는 부르지 않는다. 다른 화면으로 옮겨 가는 것, 홈으로 나가는 것, 브라우저를 여는 것이 모두 여기에 든다. (P-08)
  - 상태는 수명 주기에 맞춰 모은다(collectAsStateWithLifecycle).
- SettingsScreen의 카드에 Android Studio 미리보기(@Preview)를 둔다: 키 없음, 확인 중, 거절됨(Input 모드 틀림), 저장됨, 틀림(Saved 모드). 틀림(Saved 모드)은 이 변경에서 실제로 만들 수 없어 미리보기로 확인한다. (R-28)
- 화면 문구는 모두 `strings.xml`에 둔다. 위에 나온 문구 말고도 키 보기 버튼의 설명("키 보기" / "키 가리기")을 둔다.
- MainActivity는 `@AndroidEntryPoint`로 하고, 첫 화면으로 SettingsScreen을 띄운다. 템플릿의 Greeting 예시는 지운다. (R-01)

**테스트 지시**
- R-03 (카드 목록과 순서): Given 저장된 키 없음 / When uiState / Then 카드가 TWELVE_DATA, FRED 순서이고, 각각 이름·설명 리소스가 Twelve Data·주식과 ETF 가격, FRED·금리와 채권 지표다
- R-05 (딱지 고르기): Given TWELVE_DATA SAVED, FRED INVALID / When uiState / Then TWELVE_DATA 딱지 SAVED, FRED 딱지 INVALID
- R-17: Given 처음 설치(저장된 키 없음, 확인 상태 없음) / When uiState / Then 두 카드 모두 딱지 NONE
- R-06 (입력 모드 고르기): Given TWELVE_DATA에 저장된 키 없음 / When uiState / Then Input 모드, text 빈 글자, messageRes null, errorBorder 거짓
- R-07 (저장 모드, 가리기)
  - Given TWELVE_DATA에 `abcdefgh12345678`이 SAVED / When uiState / Then Saved 모드, displayKey `••••••••••••5678`, revealed 거짓
  - Given 키 `abcd` / When uiState / Then displayKey `••••`
- R-13: Given 위 SAVED 키 / When onRevealToggle(TWELVE_DATA) / Then displayKey `abcdefgh12345678`, revealed 참
- R-33
  - Given 키 보기가 켜짐 / When onRevealToggle을 한 번 더 / Then displayKey `••••••••••••5678`
  - Given 키 보기가 켜짐 / When onScreenHidden / Then displayKey `••••••••••••5678`
- R-08 (넣은 키 남기기, 빨간 테두리): Given 확인이 REJECTED로 끝남, 입력한 키 `badkey123` / When uiState / Then Input 모드, text `badkey123`, errorBorder 참
- R-18 (틀림 딱지와 안내, 저장된 상태는 그대로): Given 위와 같음 / When uiState / Then 딱지 INVALID, messageRes는 "키가 맞지 않습니다…", ObserveApiKeysUseCase에 TWELVE_DATA 없음
- R-28: Given TWELVE_DATA에 `abcdefgh12345678`이 INVALID / When uiState / Then Saved 모드, 딱지 INVALID, showRejectedNotice 참, displayKey `••••••••••••5678`
- R-32 (확인 중): Given TWELVE_DATA가 Checking / When uiState / Then TWELVE_DATA는 checking 참, inputEnabled 거짓, saveEnabled 거짓. FRED는 inputEnabled 참
- R-37 (돌아왔을 때의 상태)
  - Given `newkey123`을 넣고 저장을 눌러 Checking / When onScreenHidden / Then TWELVE_DATA는 여전히 checking 참, text `newkey123`
  - Given Checking 중 onScreenHidden, 그 뒤 확인이 REJECTED로 끝남 / When uiState / Then text `newkey123`, 딱지 INVALID, messageRes "키가 맞지 않습니다…"
  - Given Checking 중 onScreenHidden, 그 뒤 확인이 받아들여져 저장됨 / When uiState / Then Saved 모드, revealed 거짓
- R-19 (안내): Given 확인이 NO_CONNECTION으로 끝남, 입력 `key1` / When uiState / Then text `key1`, messageRes "연결이 안 되어…", 딱지 NONE, errorBorder 거짓
- R-30 (안내): Given 확인이 UNAVAILABLE로 끝남 / When uiState / Then messageRes "지금은 키를 확인할 수 없습니다…", 딱지 NONE
- R-43 (안내): Given 확인이 STORE_FAILED로 끝남, 입력 `key1` / When uiState / Then Input 모드, text `key1`, messageRes "키를 저장하지 못했습니다…", 딱지 NONE
- R-26
  - Given 입력 `""` / When uiState / Then saveEnabled 거짓
  - Given 입력 `"   "` / When uiState / Then saveEnabled 거짓
  - Given 입력 `"a"` / When uiState / Then saveEnabled 참
- R-34: Given TWELVE_DATA SAVED, FRED SAVED / When onDeleteConfirm(TWELVE_DATA) / Then TWELVE_DATA는 Input 모드 딱지 NONE
- R-16: Given TWELVE_DATA SAVED, FRED SAVED / When onDeleteConfirm(TWELVE_DATA) / Then FRED는 Saved 모드 딱지 SAVED 그대로
- R-15 (주소): Given 아무 상태 / When uiState / Then TWELVE_DATA issueUrl `https://twelvedata.com/register`, FRED issueUrl `https://fredaccount.stlouisfed.org/apikeys`

**확인 기준**
- 테스트 통과, 컴파일 오류 없음

## 3. 수동 확인

- R-01: Given 앱이 설치됨 / When 앱을 연다 / Then 설정 화면이 바로 보인다
- R-02: Given 설정 화면 / When 화면을 본다 / Then 위에서 아래로 위쪽 줄("설정", 뒤로 가기 없음), 안내 "데이터를 받으려면 출처마다 키가 필요합니다. 키는 이 기기에만 저장됩니다.", 출처 카드들이 놓인다
- R-03 (카드 표시): Given 설정 화면 / When 화면을 본다 / Then Twelve Data 카드, 그 아래 FRED 카드가 보인다
- R-04: Given 키 없음인 카드와 저장됨인 카드 / When 화면을 본다 / Then 두 카드 모두 출처 이름, 그 아래 무엇에 쓰는 키인지 한 줄(Twelve Data: 주식과 ETF 가격 / FRED: 금리와 채권 지표), 출처 이름 오른쪽에 상태 딱지가 있다
- R-05 (딱지의 글과 색): Given 키 없음, 저장됨, 틀림인 카드 / When 화면을 본다 / Then 딱지가 "키 없음" 회색, "저장됨" 파랑, "틀림" 빨강으로 보이고 글이 늘 함께 있다
- R-06 (요소 표시): Given 키 없음인 카드 / When 화면을 본다 / Then 이름표 "API 키" 입력 칸(빈 칸에 "키를 붙여 넣으세요"), "키 발급받기"와 그 아래 "무료, 카드 등록 없음", "저장" 버튼이 보인다
- R-07 (요소 표시): Given 키 `…5678`이 저장된 카드 / When 화면을 본다 / Then 입력 칸·발급 안내·저장 버튼은 없고, 끝 네 글자 `5678`만 보이게 가려진 키, 그 오른쪽 키 보기 버튼, "키 지우기" 버튼이 보인다
- R-08 (표시): Given 키 없음인 Twelve Data 카드 / When 틀린 키 `wrongkey123`을 넣고 저장 / Then 입력 칸에 `wrongkey123`이 남고 테두리가 빨갛고, 딱지는 "틀림", 발급 안내와 저장 버튼은 그대로 있다
- R-10: Given 키 없음인 카드 / When 입력 칸에 키를 치고, 지운 뒤 복사한 키를 붙여 넣는다 / Then 두 방법 모두 입력 칸에 들어간다
- R-11 (출처별 요청 모양): Given 두 출처에서 받은 맞는 키 / When 각 카드에 넣고 저장 / Then 두 카드 모두 "저장됨"이 된다
- R-14: Given 저장됨인 카드 / When "키 지우기"를 누른다 / Then "이 키를 지울까요?" 창이 뜬다
- R-15 (브라우저 열기): Given 키 없음인 두 카드 / When 각각 "키 발급받기"를 누른다 / Then 브라우저에서 Twelve Data는 `https://twelvedata.com/register`, FRED는 `https://fredaccount.stlouisfed.org/apikeys`가 열린다
- R-18 (실제 틀린 키): Given 두 출처 카드가 키 없음 / When 각 카드에 틀린 키 `wrongkey123`을 넣고 저장 / Then 두 카드 모두 "키가 맞지 않습니다. 다시 확인해 주세요."가 보이고 저장되지 않는다(앱을 껐다 켜면 키 없음)
- R-19 (실제 연결 끊김): Given 비행기 모드 / When 맞는 키를 넣고 저장 / Then "연결이 안 되어 키를 확인하지 못했습니다. 연결을 확인하고 다시 눌러 주세요."가 보이고, 넣은 키가 남고, 딱지는 "키 없음" 그대로다
- R-20 (저장된 키와 상태가 남음): Given Twelve Data 저장됨 / When 앱을 완전히 껐다 켠다 / Then Twelve Data는 저장됨, 가려진 키가 그대로 보인다
- R-21: Given 키를 저장함 / When Android Studio의 Device Explorer로 `datastore/api_keys.preferences_pb`를 연다 / Then 저장한 키 글자가 그대로 보이지 않는다
- R-22: Given 키를 저장함 / When 앱을 지우고 다시 설치해 연다 / Then 두 출처 모두 키 없음이다
- R-28 (표시): Given 저장되어 있던 키가 나중에 거절된 상태의 카드(이 변경에는 데이터를 받는 기능이 없어 실제로 만들 수 없으므로 Android Studio의 미리보기로 본다) / When 미리보기를 연다 / Then 딱지 "틀림", 키는 가려진 채, 그 아래 "키가 거절됐습니다. 지우고 다시 넣어 주세요."와 키 보기·"키 지우기" 버튼이 보인다
- R-32 (진행 표시와 "확인 중"): Given 키 없음인 Twelve Data 카드 / When 키를 넣고 저장 / Then 응답이 올 때까지 저장 버튼 자리에 돌아가는 진행 표시와 "확인 중"이 보이고, 그 카드의 입력 칸은 비활성이며, FRED 카드는 그대로 쓸 수 있다
- R-33 (앱을 다시 켜면 가림): Given 키 보기로 키 전체를 보는 중 / When 앱을 완전히 껐다 켠다 / Then 키가 가려져 있다
- R-35: Given "이 키를 지울까요?" 창 / When "취소" / Then 창이 닫히고 키와 딱지가 그대로다
- R-37 (화면을 벗어났다 돌아오기): Given 맞는 키를 넣고 저장해 확인 중 / When 바로 홈으로 나갔다가 몇 초 뒤 돌아온다 / Then 아직 확인 중이면 확인 중 표시가, 끝났으면 저장됨 모양이 보인다. 틀린 키로 같은 일을 하면 넣은 키와 "키가 맞지 않습니다…"가 보인다
- R-38: Given 맞는 키를 넣고 저장해 확인 중 / When 응답 전에 앱을 강제 종료하고 다시 켠다 / Then 그 출처는 키 없음이다(받아들여지기 전에 종료한 경우)

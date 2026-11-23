기능: 설정
변경: 신규 개발 (CS-001)

## DECISION

### D-01 (전달 1) Compose BOM은 2026.09.00으로 둠
단위 1. 최신 안정판 Compose BOM 2026.11.01(Compose 1.13.0)은 compileSdk 37.1 이상을 요구한다. 지금 프로젝트는 compileSdk 37(37.0)이고 이 PC에도 android-37.0까지만 깔려 있어 빌드가 실패했다. compileSdk는 지시에 없으므로 바꾸지 않고, compileSdk 37.0에서 빌드되는 가장 최신판인 2026.09.00(Compose 1.12.1)을 골랐다. 개요의 "라이브러리를 들인다"를 빌드 설정을 건드리지 않고 이루기 위해서다.
그 밖의 버전(2026-11-23 Maven 기준 최신 안정판): KSP 2.3.14, Hilt 2.60.1, androidx.hilt 1.4.0, Retrofit 3.0.0(kotlinx.serialization 변환기 포함), OkHttp 5.5.1, kotlinx.serialization 1.13.0, DataStore 1.3.0, Tink 1.24.0, lifecycle 2.11.1, kotlinx-coroutines-test 1.11.0, Konsist 0.18.1.

### D-02 (전달 1) 경계 검사는 제품 코드만, 패키지 이름의 마디로 가름
단위 1. Konsist의 `scopeFromProduction()`으로 `main` 코드만 검사한다(테스트 코드의 가짜는 경계 대상이 아니다). `domain`·`data`·`presentation`·`di`는 `com.hak.hakbanggul` 아래 패키지 이름의 마디(점으로 나눈 한 칸)로 가른다. 기능 이름은 `feature.` 바로 다음 마디다. 검사 대상을 하나도 읽지 못하면 나머지 검사가 늘 통과하므로, `core.model`을 읽었는지 보는 검사를 하나 더 두었다. 일부러 `domain`에 `android.`·`data` import를 넣어 검사가 실패하는 것을 확인했다.

### D-03 (전달 1) Retrofit 한정자와 공용 객체 제공 방식
단위 2. `@TwelveDataRetrofit`, `@FredRetrofit` 한정자는 `core/di/NetworkQualifiers.kt`에 두었다. DataSourceEndpoints와 SourceResponseClassifier는 `@Inject` 생성자 없이 NetworkModule의 `@Provides`로만 제공한다(지시대로 NetworkModule이 제공하고, 두 곳에서 묶이지 않게). Json은 `ignoreUnknownKeys = true`로 둔다. 출처가 응답에 칸을 더해도 깨지지 않게 하기 위해서다.

### D-04 (전달 1) KeyCheckManager의 중복 시작 막기
단위 3. startSave는 "그 출처가 Checking인지 보기"와 "Checking으로 두기"를 `MutableStateFlow.update` 한 번 안에서 한다. 저장을 빠르게 두 번 눌러도 시험 요청이 한 번만 가게 하기 위해서다(R-32).

### D-05 (전달 1) 암호화 실패도 저장 실패(IOException)로 다룸
단위 4. ApiKeyRepositoryImpl.save에서 ApiKeyCipher.encrypt가 예외를 던지면 IOException으로 감싸 던진다. 그러면 SaveApiKeyUseCase가 STORE_FAILED를 돌려준다(R-43). 감싸지 않으면 예외가 앱 범위 코루틴까지 올라가 앱이 멈춘다.

### D-06 (전달 1) 상태를 읽을 수 없는 항목도 복호화 실패처럼 지움
단위 4. DataStore에 키는 있는데 상태 값이 없거나 SAVED·INVALID가 아니면, 복호화하지 못한 경우와 같이 그 항목을 지우고 키가 없는 것으로 본다(R-42와 같은 처리). 지우다가 IOException이 나면 무시한다(그 출처는 어차피 키 없음으로 보인다).

### D-07 (전달 1) api_keys DataStore에 한정자를 붙임
단위 4. `DataStore<Preferences>`는 다른 기능도 나중에 쓸 수 있는 흔한 타입이라, 설정의 것에 `@ApiKeysDataStore` 한정자(feature/settings/di)를 붙였다. 손상 처리기는 `ReplaceFileCorruptionHandler { emptyPreferences() }`, 읽기 IOException은 `catch`로 빈 것으로 바꾼다.

### D-08 (전달 1) 시험 요청의 실행 위치와 취소 처리, 암호화 세부
단위 4. ApiKeyCheckRepositoryImpl은 요청과 본문 읽기를 `Dispatchers.IO`에서 한다(본문 `string()`이 막히는 호출이라서). 10초 제한으로 취소되어 OkHttp가 IOException을 던지는 경우에는 `ensureActive()`로 취소를 그대로 넘겨, NO_CONNECTION으로 잘못 바꾸지 않는다. TinkApiKeyCipher는 암호문을 Base64(`java.util.Base64`, API 26부터 있음)로 저장하고, AEAD의 연관 데이터는 고정 문자열을 쓴다. Keystore를 여는 일은 느리므로 처음 쓸 때 한 번만 한다.

### D-09 (전달 1) 저장을 누르면 ViewModel의 입력 사본을 비움
단위 5. onSaveClick은 그 출처의 입력 글자를 KeyCheckManager.startSave에 넘긴 뒤 ViewModel이 가진 입력 글자를 비운다. 그 뒤 입력 칸은 KeyCheckManager의 enteredKey를 보인다(지시의 "없으면 KeyCheckManager의 enteredKey"). 같은 글자를 두 곳에 두면 어긋날 수 있어서다. 입력이 빈칸뿐이거나 이미 확인 중이면 onSaveClick은 아무것도 하지 않는다(R-26, R-32).

### D-10 (전달 1) 틀림인 저장된 키도 키 보기로 볼 수 있음
단위 5. 지시의 "상태가 INVALID면 … 키는 가린 채로 둔다"(R-28)를 "처음에는 가린 채로 보인다"로 읽었다. 수동 확인 R-28이 틀림 카드에도 키 보기 버튼이 있다고 하므로, 그 버튼을 눌렀을 때 아무 반응이 없지 않도록 SAVED와 같이 켜고 끌 수 있게 했다. 키를 지우면 그 출처의 키 보기도 꺼진다.

### D-11 (전달 1) uiState의 첫 값은 카드 없음
단위 5. uiState는 `WhileSubscribed(5초)`로 돌고, 첫 값은 카드가 없는 `SettingsUiState(emptyList())`다. 저장된 키를 읽기 전에 모든 카드가 "키 없음" 입력 모양으로 잠깐 보였다가 바뀌는 것을 막기 위해서다.

### D-12 (전달 1) 키 보기 아이콘은 직접 그림
단위 5. 눈 모양(Visibility / VisibilityOff) 아이콘은 material-icons-core에 없고, material-icons-extended는 매우 커서 들이지 않았다. Material 아이콘의 모양 데이터를 옮겨 `feature/settings/presentation/KeyVisibilityIcons.kt`에 ImageVector로 두었다. 아이콘 라이브러리는 의존성에 넣지 않았다.

### D-17 (전달 1) compileSdk 37.1로 올리고 Compose BOM을 최신판으로
단위 1. Hak이 Android SDK Platform 37.1을 설치해, D-01에서 낮춰 둔 것을 되돌렸다. `app/build.gradle.kts`의 compileSdk를 `release(37) { minorApiLevel = 1 }`(37.1)로, `gradle/libs.versions.toml`의 composeBom을 2026.11.01(Compose 1.13.0)로 올렸다. 지시의 "최신 안정 버전"을 따르기 위해서다. targetSdk(36)와 minSdk(26)는 그대로다. 올린 뒤 전체 테스트 통과와 컴파일 오류 없음을 확인했다. D-01과 BACKLOG D-14는 이것으로 처리되었다.

### D-13 (전달 1) 화면이 가려질 때의 처리와 hiltViewModel
단위 5. SettingsScreen은 `LifecycleEventEffect(ON_STOP)`에서 `LocalActivity.current?.isChangingConfigurations`가 참이 아닐 때만 onScreenHidden을 부른다. 화면 회전 때도 ON_STOP이 오기 때문이다(P-08). `hiltViewModel()`은 hilt-navigation-compose 1.4.0이 함께 들여오는 `androidx.hilt.lifecycle.viewmodel.compose`의 것을 쓴다(navigation 쪽의 것은 옮겨 가는 중이다). 딱지 색은 회색 #757575, 파랑 #1E66D0, 빨강 #C62828이고 글은 흰색이다.

## BACKLOG

### D-14 (전달 1) compileSdk를 37.1 이상으로 올리고 Compose BOM 최신판으로
D-01 때문에 Compose BOM이 최신보다 한 단계 낮다. Android SDK Platform 37.1 이상을 설치하고 `app/build.gradle.kts`의 compileSdk를 올리면 `gradle/libs.versions.toml`의 composeBom을 2026.11.01 이상으로 올릴 수 있다.

### D-15 (전달 1) 키 보기 아이콘을 공용 화면 요소로 옮기기
다른 기능도 아이콘을 쓰게 되면 `feature/settings/presentation/KeyVisibilityIcons.kt`를 `core/ui`로 옮기거나, 아이콘을 들이는 방식(벡터 리소스 등)을 하나로 정하는 것이 좋다.

## SPEC-ISSUE

### D-16 (전달 1) 경계 검사 지시가 가리키는 파일이 저장소에 없음
단위 1 지시의 "`SDD/규칙/CLI용 CLAUDE.md` > 7. 코드 규칙 > 경계 검사"에서 그 경로의 파일이 이 저장소에 없다. 저장소 맨 위의 `CLAUDE.md`가 그 사본이라 보고, 그 파일의 "7. 코드 규칙 (Constitution) > 경계 검사" 네 가지를 검사했다.

## BUG

없음

## TECH-LIMIT

없음

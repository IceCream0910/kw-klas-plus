# KLAS+ 기여자 가이드

KLAS+에 관심을 가져주셔서 감사합니다.

이 저장소는 기존 Android 전용 앱을 다음 구조로 1차 마이그레이션한 코드베이스입니다.

- 비즈니스 로직과 데이터 처리는 Kotlin Multiplatform `shared` 모듈에서 공유
- Android UI는 Jetpack Compose로 구현
- iOS UI는 SwiftUI로 별도 구현
- 주요 콘텐츠는 Android `WebView`와 향후 iOS `WKWebView`에서 렌더링
- QR 출석, 앱 잠금, 생체인식, PIP, 위젯 등 OS 기능은 플랫폼별로 구현

현재 Android 마이그레이션과 Compose 전환은 1차 완료 상태이며 세부 회귀 검증과 구조 개선이 계속 진행 중입니다. iOS 앱은 초기 골격만 존재하며 본격적인 기능 구현 전입니다. 작업 상태는 [TASKS.md](./TASKS.md)와 [기능 패리티 매트릭스](./docs/FEATURE_PARITY_MATRIX.md)를 기준으로 판단해 주세요.

## 시작하기 전에

기능을 수정하기 전 다음 문서를 확인합니다.

1. [AGENTS.md](./AGENTS.md): 저장소의 호환성·보안·코딩 규칙
2. [TASKS.md](./TASKS.md): 현재 단계, 선행 작업과 완료 조건
3. [마이그레이션 아키텍처](./docs/MIGRATION_ARCHITECTURE.md): 계층 경계와 장기 설계
4. [기능 패리티 매트릭스](./docs/FEATURE_PARITY_MATRIX.md): 화면, 브리지, 저장 키 계약

## 프로젝트 구조

```text
kw-klas-plus/
├── androidApp/                  Android 애플리케이션
│   └── src/
│       ├── main/
│       │   ├── kotlin/
│       │   │   └── com/icecream/kwklasplus/
│       │   │       ├── feature/    화면별 Compose UI
│       │   │       ├── ui/         테마, 공용 UI, WebView host
│       │   │       ├── platform/   Activity/WebView 결합 플랫폼 구현
│       │   │       ├── manager/    Android 시스템 기능 연결
│       │   │       ├── modal/      Compose 기반 modal host
│       │   │       └── *Activity   화면 진입점과 lifecycle 연결
│       │   ├── res/                Manifest, 위젯 및 Android 리소스
│       │   └── AndroidManifest.xml
│       ├── test/                Android JVM 테스트
│       └── androidTest/         Compose/UI/플랫폼 계측 테스트
├── shared/                      단일 KMP 공통 코어
│   └── src/
│       ├── commonMain/          플랫폼 중립 Kotlin 로직
│       ├── commonTest/          공통 단위·계약 테스트
│       ├── androidMain/         Android용 공통 API 구현
│       ├── androidHostTest/     Android source set host 테스트
│       └── iosMain/             iOS용 공통 API 구현
├── iosApp/                      SwiftUI 애플리케이션과 Xcode 프로젝트
├── docs/                        아키텍처, ADR, 패리티 및 검증 문서
├── gradle/libs.versions.toml    공통 버전 카탈로그
├── TASKS.md                     마이그레이션 백로그
└── AGENTS.md                    저장소 작업 규칙
```

### `shared/commonMain`

두 플랫폼에서 동일해야 하는 의미와 정책을 둡니다.

- Ktor 기반 API 요청과 응답 파싱
- 직렬화 가능한 DTO, 도메인 모델과 결과 타입
- 인증·세션·학기·시간표·출석·도서관·미디어 repository
- use case와 플랫폼 중립 상태
- navigation route와 URL 정책
- 브리지 command/event 모델, 검증기와 JSON codec
- `SecureStore`, `QRScanner`, `FileTransfer` 같은 플랫폼 port

이 source set에서는 Android, AndroidX, Compose, UIKit 및 기타 플랫폼 타입을 사용할 수 없습니다. `Context`, `Activity`, `WebView`, `Intent`, `UIViewController`, `WKWebView`도 공개 API에 노출하지 않습니다.

### `shared/androidMain`

UI나 Activity lifecycle을 몰라도 되는 Android 구현을 둡니다.

- Ktor OkHttp 엔진
- Android Keystore 기반 보안 저장소
- SharedPreferences 및 세션 저장 adapter
- Android 전용 암호화·캐시 호환 구현
- 공통 repository와 use case를 조립하는 `AndroidSharedDependencies`

`androidApp` 클래스나 앱 리소스를 역으로 참조하면 안 됩니다.

### `shared/iosMain`

공통 API에 필요한 iOS 구현을 둡니다. 현재는 Darwin HTTP 엔진을 중심으로 구성되어 있으며 Keychain, 저장소 및 기타 작은 OS adapter가 이후 추가될 예정입니다.

SwiftUI 화면, `WKWebView` lifecycle, WidgetKit과 AVKit 연결은 이 source set이 아니라 `iosApp`이 소유합니다.

### `androidApp`

Android 화면과 앱 lifecycle에 결합된 코드를 둡니다.

- Compose 화면, 테마와 반응형 레이아웃
- Activity 진입점과 route 연결
- WebView 생성·보존·폐기 및 bridge adapter
- Activity Result 기반 QR scanner와 파일 선택
- 생체인식 prompt, PIP, DownloadManager와 AppWidget

새 네트워크 요청, JSON/XML 파싱 또는 재사용 가능한 정책을 `androidApp`에 직접 추가하지 마세요. 먼저 `shared/commonMain`으로 옮길 수 있는지 검토합니다.

### `iosApp`

SwiftUI 화면과 iOS 앱 lifecycle을 소유합니다. 장기적으로 다음 구현이 이곳에 위치합니다.

- SwiftUI 화면과 navigation
- `WKWebView` host와 bridge shim
- LocalAuthentication, AVKit, 파일 선택과 외부 이동
- WidgetKit extension과 entitlements

## 의존성 방향

```text
androidApp ─┐
            ├──> shared/commonMain
iosApp ─────┘          ▲
                      │
          shared/androidMain 또는 shared/iosMain
```

플랫폼 앱은 `shared`를 사용하지만 `shared`는 플랫폼 앱을 참조하지 않습니다. UI 타입은 플랫폼 앱 경계를 넘지 않습니다.

새 코드의 위치가 모호할 때는 다음 기준을 사용합니다.

| 코드의 성격 | 위치 |
|---|---|
| API, DTO, parser, 정책, use case, 상태 모델 | `shared/commonMain` |
| 플랫폼에 따라 구현이 다르지만 UI lifecycle과 무관한 adapter | `shared/androidMain`, `shared/iosMain` |
| Activity, WebView, Compose 또는 Android 리소스에 결합 | `androidApp` |
| SwiftUI, WKWebView, UIKit 또는 iOS extension에 결합 | `iosApp` |
| 플랫폼 기능의 공통 의미 | `commonMain` port/result 모델 |
| 실제 OS API 호출 | 해당 플랫폼 구현 |

복잡한 플랫폼 기능을 무조건 `expect`/`actual` 클래스로 만들지 않습니다. 공통 인터페이스와 생성자 주입을 우선하며, 작은 플랫폼 값이나 factory에만 `expect`/`actual`을 검토합니다.

## 주요 실행 흐름

일반적인 기능 요청은 다음 방향으로 흐릅니다.

```text
Compose/SwiftUI
  → 플랫폼 진입점 또는 adapter
  → shared use case/repository
  → 공통 port
  → androidMain/iosMain 또는 플랫폼 앱 구현
  → typed result/state
  → 플랫폼 UI
```

WebView 기능에서는 웹이 브리지 command를 보내고, 플랫폼 adapter가 origin과 payload를 검증한 뒤 공통 router 또는 use case로 전달합니다. 결과는 직렬화된 command/event나 기존 호환 callback을 통해 웹으로 돌아갑니다.

## WebView 브리지 변경

WebView 브리지는 앱과 별도로 배포되는 웹 코드와의 공개 계약입니다.

- 기존 객체명 `Android`, 메서드명, 인자 순서와 callback을 임의로 변경하지 않습니다.
- `evaluteKLASScript`처럼 오타로 보이는 이름도 호환 계약입니다.
- 브리지는 허용된 HTTPS origin과 top-level frame에서만 활성화합니다.
- URL, 인자 타입, 길이와 payload 크기를 검증합니다.
- JavaScript 문자열을 직접 결합하지 않고 공통 `WebScript` 또는 JSON codec을 사용합니다.
- 새 프로토콜 도입 시 기존 방식과 최소 한 릴리스 동안 함께 동작해야 합니다.

## 인증, 저장소와 개인정보

다음 값은 비밀 데이터입니다.

- 서버가 반환한 암호화 비밀번호
- KLAS `SESSION`
- 도서관 비밀번호, secret와 auth key
- 앱 잠금 hash와 salt

비밀 값은 로그, 테스트 fixture, 분석 이벤트, screenshot 또는 crash 첨부에 포함하지 않습니다. 평문 비밀번호는 암호화 API 호출에 필요한 시간 동안만 메모리에 유지합니다.

기존 SharedPreferences 키와 저장 형식은 사용자 데이터 호환 계약입니다. 키나 형식을 바꿀 때는 구 데이터 읽기, 신규 저장소 기록, 검증, 구 데이터 삭제 순서의 명시적인 migration과 실패 테스트가 필요합니다.

## UI 기여

Android 네이티브 UI는 Compose로 구현합니다.

- compact: 600dp 미만
- medium: 600dp 이상 840dp 미만
- expanded: 840dp 이상

고정 기기 모델이나 픽셀 크기로 분기하지 말고 `ui/layout`의 공통 크기 정책을 사용합니다. safe drawing inset, IME, 스크롤, 최소 터치 영역과 접근성을 함께 확인합니다.

WebView 자체의 웹 콘텐츠를 Compose로 재작성하지 않습니다. Compose는 WebView host, 로딩·오류·modal과 네이티브 제어 UI를 담당합니다.

Android 홈 화면 위젯은 `RemoteViews` 제약으로 XML 레이아웃을 계속 사용합니다. 새로운 일반 앱 화면에 View/XML 레이아웃을 추가하지 마세요.

iOS UI는 Compose Multiplatform UI가 아니라 SwiftUI로 구현합니다. 공통화 대상은 UI가 아니라 상태, 정책, use case와 repository입니다.

## 개발 환경

현재 버전은 [gradle/libs.versions.toml](./gradle/libs.versions.toml)을 기준으로 합니다.

- JDK 21
- Android Studio와 Android SDK
- Android minSdk 29, compileSdk/targetSdk 37
- iOS 작업 시 macOS와 Xcode

`local.properties`, 서명 키, 인증정보와 Xcode 사용자별 데이터는 커밋하지 않습니다.

Android 빌드:

```shell
./gradlew :androidApp:assembleDebug
```

Windows PowerShell:

```powershell
.\gradlew.bat :androidApp:assembleDebug
```

iOS 앱은 macOS에서 `iosApp/iosApp.xcodeproj`를 열어 실행합니다. Windows에서는 iOS framework와 simulator 테스트를 실행할 수 없습니다.

## 테스트

변경한 계층에 맞는 테스트를 함께 작성합니다.

공통 로직과 source-set 경계:

```shell
./gradlew :shared:testAndroidHostTest :shared:check
```

Android JVM 테스트와 컴파일:

```shell
./gradlew :androidApp:testDebugUnitTest :androidApp:compileDebugKotlin
```

Android 계측 테스트 소스 검증:

```shell
./gradlew :androidApp:compileDebugAndroidTestKotlin
```

연결된 emulator 또는 기기의 계측 테스트:

```shell
./gradlew :androidApp:connectedDebugAndroidTest
```

릴리스와 R8 검증:

```shell
./gradlew :androidApp:assembleRelease
```

macOS의 iOS 공통 테스트:

```shell
./gradlew :shared:iosSimulatorArm64Test
```

## 기여 절차

1. 이슈와 `TASKS.md`에서 작업 범위와 선행 조건을 확인합니다.
2. 변경 대상 기능의 패리티 행과 기존 테스트를 확인합니다.
3. 기존 동작을 설명하는 테스트를 먼저 추가하거나 기존 테스트가 계약을 충분히 고정하는지 확인합니다.
4. 가장 작은 기능 단위로 구현합니다.
5. 관련 자동 테스트와 필요한 실기기 검증을 수행합니다.
6. 동작이나 구조가 바뀌면 `TASKS.md`와 관련 문서를 갱신합니다.
7. PR에 변경 이유, 검증 결과, 호환성 영향과 rollback 방법을 적습니다.

코드 주석은 최소화합니다. 코드만으로 제약을 표현할 수 없을 때만 한국어로 작성합니다.

## PR 체크리스트

- [ ] 코드가 올바른 KMP source set 또는 플랫폼 앱에 위치한다.
- [ ] `shared/commonMain`에 플랫폼 API나 UI 타입이 없다.
- [ ] Android의 기존 사용자 동작과 저장 데이터 호환성을 확인했다.
- [ ] 인증정보, 세션과 개인정보가 로그나 fixture에 포함되지 않는다.
- [ ] 브리지 이름·인자·callback 또는 저장 키를 무단 변경하지 않았다.
- [ ] 성공뿐 아니라 실패, 취소, 세션 만료 경로를 테스트했다.
- [ ] 변경 범위에 맞는 Gradle 테스트와 빌드를 실행했다.
- [ ] 플랫폼 기능은 필요한 실기기 시나리오를 확인했다.
- [ ] 관련 `TASKS.md`, 패리티 매트릭스와 ADR을 갱신했다.
- [ ] rollback 또는 구 구현과의 호환 경로를 설명했다.

## 현재 전환 상태에서 주의할 점

- Activity 클래스는 Compose 화면의 진입점과 WebView lifecycle host로 여전히 사용됩니다. Activity가 남아 있다는 이유만으로 View UI가 남아 있다고 판단하지 마세요.
- 일부 미사용 XML은 실기기 패리티 완료 전 rollback 자산으로 보존되어 있습니다. 참조 여부와 패리티 상태를 확인하지 않고 일괄 삭제하지 않습니다.
- AppWidget의 `RemoteViews` 레이아웃 XML은 현재도 실제 사용 중입니다.
- 기존 JavaScript bridge façade와 신규 typed bridge 경로가 호환을 위해 공존합니다.
- `iosApp`은 아직 Android와 기능 패리티 상태가 아닙니다.

# KLAS+ KMP 마이그레이션 백로그

이 문서는 실행 순서와 완료 조건을 관리한다. 체크박스는 코드가 작성되었을 때가 아니라 각 작업의 acceptance criteria가 증거와 함께 충족되었을 때만 완료한다.

## 운영 규칙

- 우선순위: `P0` 필수/회귀 차단, `P1` 출시 기능, `P2` 개선
- 크기: `S` 단일 경계, `M` 여러 파일/테스트, `L` 기능 슬라이스, `XL` 분할 필요
- 상태: `[ ]` 미착수, `[~]` 진행 중(마크다운 호환상 설명 병기), `[x]` 완료
- 각 작업은 `Depends on`, `Acceptance`를 만족해야 한다.
- 구현 전 Native/Web 기준 commit SHA를 고정한다.
- Android `P0` 패리티 없이는 해당 legacy 경로를 삭제하지 않는다.

## 마일스톤 요약

| Milestone | 목적 | 종료 게이트 |
|---|---|---|
| M0 | 기준선과 이력 확보 | 기존 Android 앱 재현 빌드 + 기준 SHA/fixture |
| M1 | 숨은 계약 고정 | 인증/브리지/화면 특성 테스트 |
| M2 | KMP 기반 정렬 | Android+iOS shared UI smoke build |
| M3 | 공통 코어 추출 | 신규 core를 기존 Android UI가 사용 |
| M4 | Android Web/Compose 전환 | Web 경로와 브리지 Android 패리티 |
| M5 | Android 네이티브 기능 전환 | P0/P1 Android 전체 패리티 |
| M6 | iOS 기본 제품 경로 | 로그인→홈→강의/과제/게시판 |
| M7 | iOS 플랫폼 기능 | 잠금/QR/PIP/Widget 승인 범위 |
| M8 | 보안·관측·출시 | 단계 rollout과 rollback 준비 |

## M0 — 기준선, 저장소, 빌드 재현

- [ ] **M0-001 (P0, S)** 현재 작업 폴더를 Git 저장소/정식 remote와 연결하는 방식을 확정한다.
  - Depends on: 없음
  - Acceptance:
    - 현재 KMP 보일러플레이트의 출처와 기본 브랜치가 기록됨
    - 기존 파일을 덮어쓰지 않는 초기 commit 또는 import branch가 존재
    - `.gitignore`가 local.properties, signing material, Xcode user data를 제외

- [x] **M0-002 (P0, M)** 원본 Android 앱을 비교 가능한 모듈/브랜치로 가져온다.
  - 완료: `legacyAndroidApp`, Native `76be3b50ba6f3f28ab81c58918542203c6b5933c`
  - Depends on: M0-001
  - 권장: `androidApp`에 legacy 기준을 먼저 복원하고 Compose를 병행 활성화
  - 대안: `legacyAndroidApp` + applicationId suffix의 비교 빌드
  - Acceptance:
    - 원본 `main`과 import diff가 빌드 통합에 필요한 변경으로만 제한됨
    - 운영 applicationId/version/signing 연속성 문서화

- [x] **M0-003 (P0, S)** Native/Web 기준 commit SHA를 고정한다.
  - 완료: `docs/BASELINE.md`, `docs/FEATURE_PARITY_MATRIX.md`
  - Depends on: M0-001
  - Acceptance:
    - `docs/FEATURE_PARITY_MATRIX.md` 기준선 표 갱신
    - 각 remote URL과 SHA로 재현 가능
    - 이후 upstream 변경 수용 절차 정의

- [x] **M0-004 (P0, M)** Android 기준 앱을 debug/release-like로 빌드한다.
  - 완료: JDK 21/Gradle 9.4.1/AGP 9.2.1 조합에서 debug와 R8 minify release APK 및 manifest processing을 완료했다. 현재 release 산출물이 production signing 미설정에 따른 unsigned APK임을 확인했으며 배포 서명 연결은 M8-004에서 수행한다.
  - 검증: `./gradlew :androidApp:assembleDebug :androidApp:assembleRelease --console=plain` 통과(2026-07-17).
  - Depends on: M0-002, M0-003
  - Acceptance:
    - 빌드 명령과 JDK/Gradle/SDK 버전 기록
    - minify/resource shrink 조건을 포함한 빌드 성공
    - manifest merge 및 signing config 확인

- [ ] **M0-005 (P0, M)** KMP 보일러플레이트 빌드 기준을 기록한다.
  - Depends on: M0-001
  - Acceptance:
    - Android debug assemble 성공
    - `shared` Android/iOS 테스트 task 확인
    - macOS CI/개발 환경에서 iOS framework와 Xcode build 경로 문서화

- [x] **M0-006 (P0, M)** 기준 기기/OS 매트릭스와 수동 smoke checklist를 만든다.
  - 완료: `docs/ANDROID_REGRESSION_CHECKLIST.md`에 API 29 phone, 최신 Android phone, tablet, 생체 미등록/미지원 환경과 F-001~F-032/A-001~A-012 실행 조건·기대 결과·증거 형식을 고정했다. 실제 실행 결과는 M5-007에서 기록한다.
  - Depends on: M0-003
  - 최소 범위:
    - Android API 29, 최신 target 수준, 폰, 태블릿
    - 신규 설치, 기존 설치 업그레이드, 프로세스 종료/복원
    - 생체인식 가능/불가 기기, PIP, 위젯
  - Acceptance: 각 F-001~F-032 기능의 최소 기기 조건 연결

- [ ] **M0-007 (P1, M)** CI 기본 파이프라인을 추가한다.
  - Depends on: M0-004, M0-005
  - Acceptance:
    - formatting/static analysis/common tests/Android build
    - macOS runner에서 iOS framework/test와 Xcode build
    - dependency cache가 lock/버전 변경을 숨기지 않음

## M1 — 기존 동작 특성화 및 계약 고정

- [ ] **M1-001 (P0, L)** 인증 흐름 fixture와 상태 전이 테스트를 만든다.
  - 진행: 로그인 URL, 허용 host, SESSION cookie(`=` 포함), 로그인 페이지 재노출, CAPTCHA/일반 alert, timeout 및 저장 실패를 순수 정책·유스케이스 테스트로 고정. 실제 KLAS redirect/alert fixture와 전체 A-001~A-012 실기기 증거는 미완료.
  - Depends on: M0-003, M0-004
  - 시나리오: A-001~A-012
  - Acceptance:
    - 로그인 URL/성공 redirect/SESSION cookie 형식 fixture
    - 암호화 API 성공/실패/malformed 응답 fixture
    - CAPTCHA, 임시 비밀번호, timeout을 구분
    - fixture와 test log에 실제 credential/session 없음

- [x] **M1-002 (P0, L)** legacy Web → Native 브리지 스키마를 코드로 캡처한다.
  - 완료: `LegacyBridgeCatalog`, `BridgeValidatorTest`; 8개 surface, 64개 메서드
  - 검증: `./gradlew :shared:testAndroidHostTest`
  - Depends on: M0-003
  - Acceptance:
    - `FEATURE_PARITY_MATRIX` 5장의 모든 메서드, 인자 수/타입, return 방식 포함
    - `getAppLockSettings` 동기 반환과 `evaluteKLASScript` 철자 고정
    - 알 수 없는 메서드/잘못된 인자 거부 테스트

- [x] **M1-003 (P0, L)** Native → Web callback과 JS 주입 fixture를 캡처한다.
  - 완료: `LegacyWebScripts`, JSON-safe 인자 encoder, callback별 arity, localStorage/login/KLAS lecture/온라인 강의/player 명령 팩토리와 `docs/BRIDGE_INJECTION_INVENTORY.md`를 추가. 특수문자·따옴표·역슬래시·Unicode·개행·70 KiB JSON·숫자 범위 fixture를 고정하고 `WebScript.toString()`을 redaction 처리했다.
  - 검증: `WebScriptTest`, `WebAutomationScriptsTest`, `BridgeJsonCodecTest`, `:shared:testAndroidHostTest` 114개 통과.
  - Depends on: M0-003
  - Acceptance:
    - token/timetable/deadline/lecture/board/settings/player callback 포함
    - 특수문자, 따옴표, Unicode, 큰 JSON payload 테스트
    - 문자열 연결 취약 사용처 목록화

- [ ] **M1-004 (P0, M)** Web 저장소에 bridge contract test 계획/이슈를 만든다.
  - Depends on: M1-002, M1-003
  - Acceptance:
    - `window.Android` legacy mock으로 모든 호출 검증
    - 새 `KlasNativeBridge` Promise mock 계획
    - 브라우저 fallback 동작 포함
    - 앱/Web 독립 배포 호환표 작성

- [ ] **M1-005 (P0, L)** Android 화면별 golden flow를 기록한다.
  - 진행: `docs/ANDROID_REGRESSION_CHECKLIST.md`에 화면별 P0/P1 단계, back/회전/백그라운드/프로세스 재시작, 민감정보 마스킹과 기대 결과를 기록했다. 기준 화면 캡처/동영상 증거는 일괄 수동 검증 때 추가한다.
  - Depends on: M0-006
  - Acceptance:
    - F-001~F-032의 P0/P1 수동 단계와 기대 결과
    - 화면 캡처 또는 동영상(민감정보 마스킹)
    - back/회전/백그라운드/프로세스 재생성 포함

- [ ] **M1-006 (P0, M)** 저장 데이터 migration fixture를 만든다.
  - 진행: SecureStore write-read/충돌/실패, 세션 timestamp 누락, legacy fallback·미러링 공통 테스트 구현. 실제 1.1.x/1.2.0 설치 데이터와 backup key 불일치 fixture, 동적 library cache는 미완료.
  - Depends on: M0-003
  - Acceptance:
    - 일반 prefs의 구 `kwPWD`, secure prefs의 신규 `kwPWD`
    - session timestamp 정상/누락/깨진 값
    - library cache와 app lock key
    - 백업 복원 시 키 불일치 사례

- [ ] **M1-007 (P1, M)** 플랫폼 기능 특성화 테스트를 작성한다.
  - 진행: QR typed 결과/workflow, PIP state, biometric 결과, 14개 haptic mapping, download URL/MIME/filename, file picker MIME/다중 선택 요청을 공통 또는 Android host 테스트로 고정했다. Widget observable 결과와 Activity lifecycle instrumentation/실기기 증거는 일괄 회귀 검증에서 보완한다.
  - Depends on: M0-006
  - 범위: PIP event/state, Widget 진입/갱신, QR 결과, download/file chooser, biometric lifecycle
  - Acceptance: 신규 구현이 비교할 observable result 정의

## M2 — 프로젝트/모듈 기반 정렬

- [x] **M2-001 (P0, M)** ADR-001로 shared module 노출 전략을 확정한다.
  - 완료: 단일 `shared` KMP 코어와 플랫폼별 UI(Android Compose/iOS SwiftUI) 구조를 `docs/adr/ADR-001-shared-module-strategy.md`에 확정. `sharedLogic`을 `shared`로 변경하고 `sharedUI`를 제거했으며 Android WebView 어댑터를 `androidApp`으로 이전했다. Android Kotlin 54개 파일은 `src/main/kotlin`으로 정렬하고 `commonMain` 플랫폼 import 검증 task를 추가했다.
  - 검증: 2026-07-19 `:shared:testAndroidHostTest` 강제 재실행, `:androidApp:compileDebugKotlin` 강제 재컴파일, Android unit/instrumentation Kotlin/debug/R8 release 빌드 통과. Windows에서는 iOS 실행 불가하며 `iosArm64`/`iosSimulatorArm64` task와 Xcode `:shared:embedAndSignAppleFrameworkForXcode` 연결만 확인했다.
  - Depends on: M0-005
  - Acceptance:
    - 단일 `shared` 모듈 사용 근거
    - iOS에 노출되는 Kotlin framework는 하나
    - public Swift API surface 최소화

- [ ] **M2-002 (P0, M)** Kotlin/Compose/AGP/Gradle/JDK/Xcode 호환 조합을 고정한다.
  - 진행: Android Compose는 Kotlin 2.4.0 Compose Compiler plugin과 안정 Compose BOM 2026.06.00, Material 3 1.4.0 조합으로 첫 잠금 화면 debug 빌드를 검증했다. macOS iOS framework/Xcode 조합 검증 전이므로 완료 처리하지 않는다.
  - Depends on: M0-004, M0-005
  - Acceptance:
    - legacy SDK/app version 퇴행 없음
    - Android와 iOS CI 모두 통과
    - version catalog와 문서 일치

- [ ] **M2-003 (P0, M)** `shared` iOS device/simulator 타깃과 framework 연결을 검증한다.
  - Depends on: M2-001, M2-002
  - Acceptance:
    - `iosArm64`, `iosSimulatorArm64` build
    - SwiftUI 앱이 `Shared.framework`의 공통 API를 호출
    - framework 중복/링커 경고 없음

- [ ] **M2-004 (P0, M)** iOS SwiftUI entry와 공통 상태/ViewModel을 연결한다.
  - Depends on: M2-003
  - Acceptance:
    - SwiftUI lifecycle과 공통 상태/event 전달
    - safe area/keyboard/orientation smoke test
    - `Shared.framework` 단일 import와 Swift API surface 검증

- [x] **M2-005 (P0, M)** 공통 coroutine, serialization, Ktor 의존성을 도입한다.
  - 완료: Ktor 3.5.0/serialization 1.11.0/coroutines 1.10.2, 공통 client 설정, Android OkHttp/iOS Darwin engine, timeout·오류 mapping 및 body/header logging 비활성 정책 구현.
  - 검증: MockEngine/fake 기반 공통 테스트와 `:shared:testAndroidHostTest` 통과. iOS binary 실행 검증은 M2-003에서 추적.
  - Depends on: M2-002
  - Acceptance:
    - common core + Android OkHttp engine + iOS Darwin engine
    - timeout/redaction/error mapping 기본 설정
    - Android/iOS MockEngine 또는 fake 기반 테스트

- [ ] **M2-006 (P0, L)** 플랫폼 port와 app dependency container를 정의한다.
  - 진행: SecureStore, PreferencesStore, ExternalNavigator, Biometrics, Haptics, FileTransfer, FilePicker, QRScanner, PictureInPicture, capability 모델을 `commonMain`에 정의. Android dependency container에 인증 repository, Keystore SecureStore, SessionCoordinator와 QR/외부 이동/햅틱/생체인식/PIP/다운로드/파일 선택 adapter를 명시적으로 주입. Android 구현과 테스트 fake는 완료했으며 iOS entry의 명시적 구현 주입만 M6/M7에서 진행한다.
  - Depends on: M2-001, M2-005
  - Ports: SecureStore, PreferencesStore, WebCookieStore, WebAuthDriver, ExternalNavigator, Biometrics, Haptics, FileTransfer, QRScanner, PictureInPicture, Clock
  - Acceptance:
    - commonMain에 플랫폼 타입 없음
    - Android/iOS entry에서 명시적으로 구현 주입
    - 테스트 fake 제공

- [x] **M2-007 (P1, M)** 공통 navigation 및 capability 모델을 정의한다.
  - 완료: Web/Lecture/LecturePlan/Task/Video/Board/overlay/platform feature를 `AppRoute`로 분리하고 잘못된 URL·필수 payload·제어문자를 route factory에서 거부. Android navigator가 legacy Intent extra를 한 곳에서 기록하며 unsupported/permission-required capability 상태를 공통 모델로 표현한다.
  - 검증: `AppRouteFactoryTest`, `ExternalNavigationPolicyTest`, `:shared:testAndroidHostTest`, `:androidApp:compileDebugKotlin` 통과.
  - Depends on: M2-003, M2-006
  - Acceptance:
    - Web route, native overlay, modal, platform feature route 구분
    - unsupported/permission-required 상태 표현
    - deep link/Intent extra가 typed route로 검증됨

- [x] **M2-008 (P0, S)** 최소 Android/iOS OS와 폰/태블릿 정책 ADR을 확정한다.
  - 완료: ADR-005에서 Android API 29, iOS/iPadOS 16.0, arm64, phone 세로 기본, tablet 회전/멀티윈도우, PIP 예외와 검증 matrix를 확정. Xcode deployment target을 18.2에서 16.0으로 조정.
  - Depends on: M0-006, M2-002
  - Acceptance: Store 배포 조건, Compose 지원 범위, QR/PIP/Widget API 요구사항 반영

- [x] **M2-009 (P0, L)** Android 플랫폼 구현을 KMP source set 경계에 맞게 재배치한다.
  - 완료: credential/session/cookie/legacy secret migration/app lock secret/library crypto·cache/external navigation/haptics와 repository·use case 조립을 `shared/androidMain`으로 이동. `AndroidSharedDependencies`가 공통 코어를 조립하고 `androidApp`은 Activity·View·WebView 기반 기능만 생성한다. 앱의 잔여 플랫폼 구현은 biometric/file/PIP/QR/navigation/web/legacy bridge 패키지로 분리했다.
  - 검증: `commonMain` 플랫폼 import와 `androidMain`/`iosMain`의 플랫폼 앱 역참조를 Gradle task로 차단. legacy session key/string timestamp, secret migration store, 도서관 AES/cache migration·expiry, 앱 잠금 SHA-256/Base64 형식 테스트를 추가했다. 2026-07-19 공통 Android host test 147개, Android unit, instrumentation Kotlin, debug APK, R8 release APK 통과.
  - Depends on: M2-005, M2-006, M5-001
  - Acceptance:
    - `shared/androidMain`이 `androidApp` 클래스·리소스·전역 dependency를 참조하지 않음
    - 앱 UI 수명주기 구현과 공통 port의 Android 구현이 패키지·생성 책임으로 분리됨
    - 기존 preference/cookie/cache/암호화 계약 테스트 유지

## M3 — 공통 코어 추출

- [x] **M3-001 (P0, M)** URL, preference key, intent payload를 typed model로 옮긴다.
  - 완료: `SecureKey`/`PreferenceKey`, `ExternalDestination`, `AppRoute`를 공통 모델로 정의. `http/https/mailto/tel` scheme·길이·제어문자·authority 정책을 테스트로 고정하고 Android bridge 외부 이동과 Web/Lecture/LecturePlan/Task/Video/Board Activity payload를 검증형 navigator로 전환. legacy key/extra reader는 대상 Activity에서 유지한다.
  - 검증: `ExternalNavigationPolicyTest`, `AppRouteFactoryTest`, `:shared:testAndroidHostTest`, `:androidApp:compileDebugKotlin` 통과.
  - Depends on: M1-006, M2-006
  - Acceptance:
    - legacy key reader는 유지
    - URL scheme/host 정책 테스트
    - common model에 Android Intent/Bundle 없음

- [ ] **M3-002 (P0, L)** AuthStateMachine과 LoginUseCase를 구현한다.
  - 진행: `AuthStateMachine`, `PrepareCredentialUseCase`, `LoginUseCase`, PasswordEncryptionApi/WebAuthDriver 계약과 성공·네트워크·timeout·CAPTCHA·임시 비밀번호·저장 실패 테스트 구현. Android WebAuthDriver를 기존 Main WebView에 연결하고 `LoginActivity`의 암호화→저장을 공통 유스케이스로 전환. 실제 KLAS redirect/alert와 전체 A-001~A-012 실기기 검증은 미완료.
  - 검증: `:shared:testAndroidHostTest`, `:androidApp:compileDebugKotlin`, `:androidApp:compileDebugAndroidTestKotlin` 통과.
  - Depends on: M1-001, M2-005, M2-006
  - Acceptance:
    - A-001~A-012 공통 테스트 통과
    - network/security/user-action 오류 분리
    - 평문 password 저장/로그 없음

- [ ] **M3-003 (P0, L)** SessionCoordinator를 구현한다.
  - 진행: 1시간 정책, restore/observe/expire, 저장소-cookie rollback과 Android View 시작·로그인·로그아웃·만료 경로 연결 완료. SESSION은 Keystore SecureStore를 primary로 사용하며 기존 View reader 호환을 위해 legacy prefs를 한시적으로 미러링. 실기기 신규/복구/만료/프로세스 재시작 검증은 미완료.
  - 검증: `:shared:testAndroidHostTest`, `:androidApp:compileDebugKotlin`, `:androidApp:compileDebugAndroidTestKotlin` 통과.
  - Depends on: M1-001, M2-006
  - Acceptance:
    - secure store와 cookie store 동기화의 단일 소유자
    - set/refresh/expire/logout 원자적 의미
    - Android legacy 1시간 instant session 동작 보존

- [x] **M3-004 (P0, L)** KLAS API repository를 구현한다.
    - 완료: 비밀번호 암호화, 학기별 수강과목, 시간표, 온라인 강의·과제·팀 프로젝트 마감일, QR 출석 workflow를 공통 repository로 구현하고 Android에 연결. SESSION/User-Agent, 30초 legacy 조회 timeout, 교시/날짜/Web JSON 계약과 typed 오류 mapping 포함.
    - 검증: 2026-07-18 과거 학기 응답의 누락 교수명을 허용하는 호환 fixture 추가. 공통 host test 121개, `:androidApp:assembleDebug` 통과. 수정 빌드 실기기 재검증과 iOS 날짜 parser 연결은 후속 단계에서 추적.
  - Depends on: M2-005, M3-003
  - 우선 범위: password encryption, QR check-in, attendance/semester 요청
  - Acceptance:
    - `SESSION`과 User-Agent 동작이 legacy fixture와 일치
    - JSON DTO 및 오류 매핑 테스트
    - HTTP body/header 로그 redaction

- [ ] **M3-005 (P1, L)** LibraryRepository를 공통화한다.
    - 진행: 비밀키→로그인→QR workflow, typed 실패, legacy Java hash 캐시 identity, XML parser, 캐시 만료/실패 clear 정책과 세 API의 Ktor form gateway를 `commonMain`으로 이동. Android `LibraryManager`는 cache/crypto adapter만 연결하며 Android Base64 및 AES/CBC/zero-IV fixture 통과. 2026-07-18 실서버 CDATA parser 회귀를 수정. `device_gb=A`는 ADR-004에 따라 Android에서 유지하며 iOS 서버 허용값 확인은 미완료.
    - 검증: CDATA/주석/비정상 XML 및 HTTP form/오류 fixture 포함 공통 host test 136개, `:androidApp:testDebugUnitTest`, `:androidApp:compileDebugKotlin` 통과. 실서버 재검증 대기.
  - Depends on: M1-006, M2-005, M2-006
  - Acceptance:
    - XML parsing, Base64, AES 호환 fixture 통과
    - `device_gb=A`의 iOS 호환 여부 조사/ADR 기록
    - secret/authKey cache expiry와 clear 정책

- [ ] **M3-006 (P0, L)** SecureStore migration을 구현한다.
  - 진행: write-read 검증 후 삭제, 중간 실패/충돌 시 구 데이터 보존 정책과 공통 테스트 구현. Android AES-GCM Keystore store를 실제 로그인 credential reader/writer, SESSION primary store, 앱 잠금 hash/salt에 연결. SESSION은 레거시 View 호환 미러를 유지하고 credential·PIN은 read-through 검증 후 구 키를 삭제한다. Keystore/legacy secure/session mirror/QR cache SharedPreferences를 cloud backup 및 device transfer에서 제외. 동적 도서관 캐시 키, 기존 설치·백업 복원 실기기 검증은 미완료.
  - Depends on: M1-006, M2-006, ADR-003
  - Acceptance:
    - Android 구 일반/EncryptedSharedPreferences → 신규 store
    - write-read 검증 후 구 데이터 삭제
    - 중간 실패 재시도 가능, rollback에서 구 앱이 치명적으로 깨지지 않음
    - backup rules 테스트

- [ ] **M3-007 (P0, M)** 앱 잠금 도메인과 정책을 추출한다.
  - 진행: `AppLockSettings`, `AppLockPolicy`를 구현하고 Android lifecycle 및 동기 settings bridge에 연결. PIN hash/salt는 Keystore SecureStore 우선 read-through migration 및 신규 쓰기로 전환. 실제 업그레이드·전체 lifecycle·위젯 예외 fixture는 미완료.
  - Depends on: M2-006, M3-006
  - Acceptance:
    - enabled/biometric/password/lifecycle 상태 모델
    - hash migration 및 검증 테스트
    - 위젯 예외 정책 명시

- [ ] **M3-008 (P0, L)** 기존 Android View UI가 신규 auth/session/repository를 사용하게 연결한다.
  - 진행: `MainActivity`의 credential restore·WebAuthDriver·LoginUseCase·세션 판정/관찰, `LoginActivity`의 PrepareCredentialUseCase, Home 로그아웃/만료를 신규 core에 연결. Home 학기/과목/시간표/마감일, Home/Lecture/QR 출석, 학생증·도서관 QR, 온라인 강의 metadata/state/진도/Web 자동화 경로를 공통 repository·codec·script factory로 전환. 2026-07-17 사용자 Android 빌드·실행 smoke 성공. A-001~A-012 업그레이드/실서버 실기기 검증은 미완료.
  - Depends on: M3-002~M3-007
  - Acceptance:
    - 아직 Compose 전환 전인 UI에서 A-001~A-012 통과
    - 신규 core 문제 시 legacy implementation으로 되돌릴 수 있음
    - Android 사용자 동작 차이 없음

- [x] **M3-009 (P0, L)** Compose/iOS 전 Android 앱 계층 공통화 감사를 완료한다.
  - 완료: `androidApp`의 직접 OkHttp/Jsoup/JSON/XML 요청·파싱을 제거하고 학생증 QR, 도서관 gateway, 강의 metadata와 player bridge 파싱을 `shared`로 이동. Ktor client는 `shared/androidMain` 컨테이너 내부에 숨기고 URL/prefs 계약과 KLAS/Web/player script를 공통 단일 원본으로 통합했다. 잔여 Android-only 경계와 iOS adapter 범위를 `docs/ANDROID_COMMONIZATION_AUDIT.md`에 고정했다.
  - 검증: 2026-07-19 `:shared:testAndroidHostTest` 136개(실패 0), `:androidApp:testDebugUnitTest`, `:androidApp:compileDebugAndroidTestKotlin`, `:androidApp:assembleDebug`, R8 `:androidApp:assembleRelease` 통과. Android 직접 HTTP/응답 parser 검색 0건, 직접 JS 실행은 executor와 legacy `evaluteKLASScript` 전달 2건만 존재.
  - Depends on: M3-004, M3-005, M3-008
  - Acceptance:
    - Android 앱 계층에 직접 HTTP client/응답 parser 없음
    - iOS가 repository/DTO/cache policy/Web script를 재작성하지 않고 재사용 가능
    - 플랫폼 SDK와 lifecycle adapter만 Android에 잔류

## M4 — 버전형 브리지와 Android Web/Compose 전환

- [x] **M4-001 (P0, L)** Bridge v1 command/event schema와 router를 구현한다.
  - 완료: strict JSON request/response/event envelope, UTF-8 64 KiB 측정, 안정된 오류 code, async/sync router, handler 오류 redaction과 cancellation 전파 구현. 64개 legacy 계약을 `BridgeMethodId`로 1:1 typed mapping하고 13개 Native→Web event ID 정의. `ADR-002` 승인.
  - 검증: `:shared:testAndroidHostTest` 93개 및 `:androidApp:assembleDebug` 통과. 64개 catalog mapping, origin/main-frame/인자/크기/version/id, malformed JSON, 동기 반환, 특수문자 event payload 테스트 포함.
  - Depends on: M1-002, M1-003, M2-006
  - Acceptance:
    - version/id/method/args/result envelope
    - allowlist, 타입/길이/origin/main-frame 검증
    - 모든 legacy 메서드가 typed command로 매핑

- [ ] **M4-002 (P0, L)** Android legacy `Android` façade를 router에 연결한다.
  - 진행: AndroidX WebKit origin-aware message listener를 8개 surface와 해당 WebView 생명주기에 연결하고 64개 typed command를 기존 façade에 위임. `window.Android`와 동기 `getAppLockSettings`는 Web 저장소 전환 전까지 병행 유지. Link/Web modal은 exact HTTPS trusted top-level에서만 façade를 등록하고 외부 top-level 이동 시 제거한다. trusted top-level의 untrusted subframe 격리는 Web 저장소의 M4-008 전환과 실기기 전체 bridge flow 검증이 남았다.
  - 검증: `TrustedOriginPolicy` exact-origin fixture 포함 공통 테스트 114개, `:androidApp:compileDebugKotlin` 통과.
  - Depends on: M4-001
  - Acceptance:
    - 기존 Web commit 변경 없이 P0/P1 flow 동작
    - 동기 `getAppLockSettings` 보존
    - 외부 origin에는 façade 미노출

- [x] **M4-003 (P0, M)** Native → JS 전달을 JSON-safe dispatcher로 교체한다.
  - 완료: legacy callback/localStorage/KLAS 자동화/온라인 강의 payload를 `WebScript`와 JSON encoder 기반 팩토리로 전환. 특수문자·따옴표·역슬래시·Unicode·개행 및 숫자 범위를 테스트하고 동적 비디오 강의 인자의 직접 문자열 보간을 제거했다. 공개 계약인 `evaluteKLASScript`의 trusted Web 제공 script 실행은 호환 경로로 유지한다.
  - 검증: `LegacyWebScriptsTest`, `WebAutomationScriptsTest`, `:shared:testAndroidHostTest` 114개 통과.
  - Depends on: M4-001
  - Acceptance:
    - callback 문자열 직접 결합 제거
    - M1-003 특수문자/Unicode/큰 payload 통과
    - secret이 오류 로그에 포함되지 않음

- [ ] **M4-004 (P0, L)** `WebSurface` 공통 계약과 Android WebView holder를 구현한다.
  - 진행: loading/ready/error/disposed, back/forward/reload/stop/evaluate snapshot 계약을 `shared/commonMain`에 정의하고 Android holder/client를 `androidApp`에 구현. Home/강의/게시판/계획서/Task/Link/설정/비디오 3면/Web modal의 page callback과 dispose를 연결했다. SESSION cookie 소유권은 `SessionCoordinator`/`WebCookieStore`로 분리 유지하며 configuration change 보존/누수 instrumentation과 실기기 검증이 남았다.
  - 검증: 공통 테스트 114개, `:shared:testAndroidHostTest`, `:androidApp:compileDebugKotlin` 통과.
  - Depends on: M2-006, M2-007, M4-001
  - Acceptance:
    - navigation/loading/error/back/reload/evaluate/cookie
    - lifecycle 재구성에서 WebView 불필요 재생성 없음
    - handler/delegate 해제 및 누수 검사

- [ ] **M4-005 (P0, L)** Compose 앱 셸과 startup/auth 화면을 구현한다.
  - 진행: Android 전용 `ui/theme`, `ui/layout` 기반과 Compose UI/instrumentation test 의존성을 추가했다. 너비 600/840dp 경계와 짧은 가로 화면을 고려하는 반응형 잠금 화면을 연결했다. `MainActivity`의 startup/loading 루트는 Compose로 교체하고 자동 로그인용 숨김 WebView는 Activity에 부착된 `AndroidView`로 유지했다. `LoginActivity`의 온보딩 WebView와 ID/PW/동의 폼도 Compose로 전환하고, expanded 너비에서는 안내/입력 2열 배치를 적용했다. 온보딩 WebView는 edge-to-edge 전체 크기와 명시적 match-parent를 적용하고 동의 문구 전체를 토글 대상으로 확장했다. 채워진 일반 버튼은 반대 테마의 primary/onPrimary 색상 쌍을 사용한다. 비밀번호는 저장 상태나 `Bundle`에 넣지 않고 Activity 메모리에만 유지하며 종료 시 지운다. 단일 WebView Activity도 Compose `AndroidView` 호스트로 전환하되 WebView 콘텐츠 자체에는 별도 반응형 재배치를 적용하지 않는다.
  - 검증: login 상태 단위 테스트와 startup/login Compose 계측 테스트 Kotlin 컴파일, `:androidApp:testDebugUnitTest`, `:androidApp:assembleDebug`, R8 `:androidApp:assembleRelease` 통과. 온보딩/IME/회전, 신규·저장 credential·유효/만료 session과 timeout 문구 전환의 실기기 검증 대기.
  - 검증: `AppWindowSizeTest`, `LockScreenTest` instrumentation Kotlin 컴파일, `:androidApp:testDebugUnitTest`, `:androidApp:assembleDebug`, R8 `:androidApp:assembleRelease` 통과. 폰/태블릿/가로/접근성 실기기 검증 대기.
  - Depends on: M3-008, M4-004
  - Acceptance:
    - F-001~F-006 Android parity
    - network error/CAPTCHA/timeout UI
    - 폰/태블릿/IME/접근성

- [ ] **M4-006 (P0, XL)** Home Web surface를 Compose route로 이전한다.
  - 진행: Home 학기/과목/시간표/마감일/학생증 repository와 Web JSON 계약을 공통화해 Android legacy bridge façade에 연결했다. `HomeActivity`의 XML loading/WebView 루트는 Compose host로 전환하되 WebView는 기존 XML처럼 네이티브 `FrameLayout` 안에 유지한다. Home은 `adjustPan`과 Compose `imePadding`이 중복되어 feed 높이가 과도하게 줄어드는 회귀를 막기 위해 공용 호스트의 IME padding을 선택적으로 비활성화하고 시스템 pan에 맡긴다. 이전 구현에서 IME가 수행하던 실제 WebView 높이 재측정이 사라져 `dvh` 기반 React Calendar가 최초 렌더되지 않는 회귀를 확인했으며, 페이지 완료 시 1px 높이 변경·복원 후 `window`/`visualViewport` resize를 동기화한다. Calendar Web BottomSheet는 3버튼 내비게이션 모드에서 IME가 표시될 때만 navigation bar inset만큼 WebView 가용 높이를 추가 조정해 하단 취소/확인 버튼이 키보드에 가리지 않도록 한다. Home 및 Lecture QR 준비 과정의 비취소형 로딩 다이얼로그도 공용 Compose 컴포넌트로 교체했다. Web 탭·BottomSheet·날짜/시간 picker·업데이트·뒤로가기 계약은 유지한다.
  - 추가 진행: 학기 선택과 홈 메뉴 BottomSheet를 Compose 공통 선택 UI로 전환했다. 기존 `YearHakgiBottomSheetDialog`/`MenuBottomSheetDialog` 클래스와 callback 계약은 유지한다.
  - 검증: 공통 WebView host 계측 테스트 Kotlin 컴파일, `:androidApp:testDebugUnitTest`, `:androidApp:assembleDebug`, R8 `:androidApp:assembleRelease` 통과. feed/timetable/calendar/profile, 캘린더 최초 진입, 성적 sheet 콘텐츠, 과거 학기, IME BottomSheet, modal, 업데이트 Snackbar와 back 종료의 실기기 회귀 검증 대기.
  - Depends on: M4-002~M4-005
  - 분할: feed/timetable/calendar/profile/settings/modal
  - Acceptance:
    - F-007~F-011, F-016, F-024 패리티
    - tab/back/modal/year-semester callbacks
    - 구 HomeActivity fallback 가능

- [ ] **M4-007 (P0, XL)** Lecture/Board/Task/Link/Plan Web surface를 이전한다.
  - 진행: 공통 `ComposeWebViewHost`, `ComposeRefreshableWebViewHost`, 다중 View용 `ComposePlatformViewHost`를 추가하고 `LctPlanActivity`, `SettingsActivity`, `LinkViewActivity`, `BoardActivity`, `TaskViewActivity`, `LectureActivity`의 XML 루트를 Compose `AndroidView`와 Material UI로 전환했다. 공용 호스트의 Scaffold는 시스템 바·컷아웃만 처리하고 해당 inset을 소비하며, IME padding은 화면 정책에 따라 한 번만 적용해 단일 WebView Activity의 중복 높이 축소를 제거했다. Home은 기존 `adjustPan`과 `applyImePadding=false` 정책을 유지한다. Board/Task의 WebView pull gesture와 Lecture의 UI/KLAS 이중 WebView 전환은 회귀 방지를 위해 프로그래밍 방식 `SwipeRefreshLayout`/`FrameLayout` interop으로 유지한다. 기존 Bridge v1/legacy façade, session/subj/localStorage callback, 파일 선택·다운로드·전체화면·back 동작은 유지한다. 사용하지 않는 WebView Modal은 구현·레이아웃·`WEB_VIEW_MODAL` surface와 `openCustomBottomSheet` Home bridge 계약까지 제거했다.
  - 검증: 일반/refreshable/다중 View `ComposeWebViewHostTest` instrumentation Kotlin 컴파일, `:androidApp:testDebugUnitTest`, `:androidApp:assembleDebug`, R8 `:androidApp:assembleRelease` 통과. 여섯 화면의 실기기 페이지 로드·브리지·back·modal·pull refresh·파일·전체화면 회귀 검증 대기.
  - Depends on: M4-004, M4-006
  - 분할: 각 route별 별도 PR 권장
  - Acceptance:
    - F-012~F-016 패리티
    - refresh/fullscreen/download/file chooser/mailto
    - 구 Activity별 fallback 또는 비교 build

- [ ] **M4-008 (P0, M)** Web 저장소에 플랫폼 중립 bridge adapter를 추가한다.
  - Depends on: M1-004, M4-001, 앱/Web 동시 변경 승인
  - Acceptance:
    - `KlasNativeBridge.call()` Promise API
    - Android legacy fallback
    - bridge version negotiation 및 unsupported 응답
    - Web CI 계약 테스트

- [ ] **M4-009 (P1, M)** XML/View 자산을 사용처별로 정리한다.
  - 진행: 사용처가 제거된 `bottom_sheet_webview.xml`을 WebView Modal 코드와 함께 삭제했다. Compose 전환된 나머지 XML은 실기기 패리티 완료 전 rollback 자산으로 유지한다.
  - Depends on: 해당 기능의 Android Parity
  - Acceptance:
    - 기능별 삭제, 전체 일괄 삭제 금지
    - resource shrink/minify build
    - 패리티 증거 없는 Activity는 유지

## M5 — Android 네이티브 기능 패리티

- [ ] **M5-001 (P0, L)** QR 출석을 `QRScanner`/`AttendanceRepository`에 연결한다.
    - 진행: `HomeActivity`/`LectureActivity`의 3단계 전처리와 스캔 성공 이후 체크인 판정을 공통 `AttendanceRepository`로 교체. Google ML Kit #1018과 동일한 AGP 9 R8 full-mode 내부 생성자 제거 문제로 확정하고 `mlkit_code_scanner` keep 규칙을 추가했다. 직접 Google Activity/내부 Parcelable 우회는 제거했으며 `QRScanActivity`가 공통 `QrScanner` port의 `AndroidQrScanner`를 사용한다. adapter는 Activity context에서 공식 `GmsBarcodeScanning.getClient()`를 1회 호출하고 typed 결과를 반환한다. Home/Lecture의 연속 탭은 scanner Activity 반환까지 single-flight로 제한했다. 2026-07-19 무스캔 종료가 일반 `scanner_start_failed`로 반환되는 실기기 회귀를 확인해 공식 취소 코드·빈 결과·중첩 취소와 스캐너 화면 표시 후 일반 종료 실패를 무알림 취소로 정규화했다. `QRScanActivity`의 인증 진행 루트를 Compose로 교체하되 scanner/check-in/result 계약은 유지했다. 단위 테스트, Compose 계측 테스트 Kotlin 컴파일, debug 및 release R8 빌드는 통과했으며 수정 빌드 실기기 재검증이 남았다.
    - 검증: `QrScanLaunchGuardTest` 포함 공통 host test 137개, Android unit test, instrumentation Kotlin 컴파일, debug APK 및 R8 release APK 빌드 통과.
  - Depends on: M3-004, M2-006
  - Acceptance: F-019와 성공/실패/취소/권한/세션 만료 통과

- [ ] **M5-002 (P0, L)** 앱 잠금/생체인식을 신규 core와 Compose UI에 연결한다.
  - 진행: `LockActivity`의 XML 진입을 Compose `LockScreen`으로 교체하고 UNLOCK/SET/CHANGE/VERIFY, 6자리 자동 제출, 물리 키보드, 뒤로가기, 생체인식 prompt와 기존 Activity result 계약을 보존했다. 모바일 키패드는 하단에 고정하고 상단 요약 영역이 남은 높이를 채워 중앙 정렬되며, 삭제는 접근성 설명이 있는 아이콘으로 표시하고 자동 제출과 중복되는 화면 확인 키는 빈 공간으로 교체했다. PIN 원문은 Compose 상태에 전달하지 않고 Activity 메모리 버퍼에만 유지한다. 기존 XML은 실기기 패리티 완료 전 rollback 자산으로 보존한다.
  - Depends on: M3-007, M4-005
  - Acceptance: F-022, F-023; foreground/background race와 widget 예외 포함

- [ ] **M5-003 (P0, XL)** 비디오 플레이어와 Android PIP를 새 경계로 이전한다.
    - 진행: 공통 `PictureInPictureState`/port와 Android PIP adapter를 연결하고 기존 RemoteAction을 보존. player command와 온라인 강의 payload를 typed `PlayerWebScripts`로 이동해 동적 문자열 주입을 제거했으며 3개 WebView를 공통 WebSurface 수명주기에 연결. `VideoPlayerActivity`의 XML root와 제어 UI는 Compose로 전환하고, WebView 3개는 하나의 platform container에 유지한다. bridge callback은 재생/음소거/진행률/속도/강의 정보를 Compose 상태로 갱신하며 seek는 슬라이더 드래그 종료 시 한 번만 전달한다. 2026-07-18 KLAS 영상 subdomain이 exact app origin에서 제외되어 state 주입이 중단된 회귀를 별도 HTTPS host-family 정책으로 수정하고, callback 문자열 변환·유효 범위 처리와 PIP 종료 fullscreen/방향 복구 상태를 추가. Android 12 이상은 player 표시 중 auto-enter PIP params를 선설정해 최근 앱/제스처 앱 전환의 명시적 진입 거절을 방지하고, 이전 버전은 `onUserLeaveHint` 명시 진입을 유지한다. 수정 빌드 실기기 재검증 대기.
  - Depends on: M4-001, M4-007
  - Acceptance:
    - F-017, F-018 Android parity
    - 재생/정지/seek/speed/progress/PIP remote action
    - 회전/백그라운드/복귀 시 상태 보존

- [ ] **M5-004 (P1, L)** 도서관 QR UI와 AppWidget을 신규 repository/store에 연결한다.
  - 진행: `LibraryManager`를 공통 `LibraryRepository`의 Android gateway/cache/crypto adapter로 전환해 Home/도서관 modal/Widget 진입 Activity가 동일 workflow와 SecureStore read-through 경로를 사용한다. cache 만료·오류 clear 정책과 AES/XML fixture 완료. 도서관 QR 본체와 credential 설정 BottomSheet를 Compose로 전환했으며 QR 원문은 UI 상태에 보관하지 않고 생성 Bitmap만 전달한다. 이름·설정 제목·경고 문구는 Material 테마 색상을 명시하고 새로고침은 접근성 설명이 있는 아이콘 버튼으로 변경했으며 저장·위젯 추가 버튼은 반대 테마의 primary/onPrimary 색상 쌍을 사용한다. 모든 네이티브 BottomSheet는 IME가 표시되면 `adjustResize`로 키보드 위 영역을 확보하고 시트 높이를 `MATCH_PARENT`로 전환하며, 설정 입력 영역만 스크롤하고 저장 버튼은 최하단에 고정한다. 30초 갱신, cache 재시도, 화면 밝기 복구, 위젯 추가/진입 종료와 Fragment no-arg 재생성 계약을 유지한다. IME 전체 높이·하단 버튼, 작은 화면, 위젯 만료/테마/잠금 및 수정 빌드 실서버 회귀가 남았다.
    - 검증: 공통 bridge host test, Android 단위 테스트, Compose 계측 테스트 Kotlin 컴파일, debug APK 및 R8 release APK 빌드 통과. Home/Widget 진입, 설정 저장, 실서버 QR 표시·자동/수동 갱신, 밝기 복구 실기기 검증 대기.
  - Depends on: M3-005, M3-006
  - Acceptance: F-020, F-021 Android parity; 만료/테마/잠금 포함

- [ ] **M5-005 (P1, L)** download/file chooser/external navigation을 port에 연결한다.
  - 진행: `FileTransfer`/`FilePicker` 요청·결과 모델과 URL/MIME/파일명/header 정책을 공통화. Android DownloadManager adapter가 SESSION cookie/User-Agent/content-disposition을 보존하며 Board/Lecture/Link/Task 다운로드에 연결되고, 진행률·파일명·취소 동작을 Compose 다이얼로그로 교체했다. Activity Result 기반 단일·다중 파일 picker가 네 화면의 legacy requestCode 경로를 대체했으며 외부 이동 7개 bridge 경로도 allowlist navigator를 사용한다. 실기기 다운로드/업로드/취소 회귀만 남았다.
  - 검증: `FileTransferPolicyTest`, `FilePickerRequestTest`, `ExternalNavigationPolicyTest`, `:androidApp:compileDebugKotlin` 통과.
  - Depends on: M4-004, M4-007
  - Acceptance: F-025~F-027; session cookie, MIME, filename, malicious URL 검증

- [ ] **M5-006 (P1, M)** 테마/방향/태블릿/햅틱/인앱 업데이트를 정리한다.
  - 진행: ADR-005에 phone/tablet 방향·멀티윈도우 정책을 고정하고 14개 legacy haptic 이름을 semantic port와 Android 상수 adapter로 이전했다. Compose 공통 너비 정책을 compact(<600dp), medium(600~839dp), expanded(>=840dp)로 구현하고 잠금 화면에 1열/2열 적응형 배치를 적용했다. light/dark 상태바 아이콘 대비를 Material 테마와 동기화하고 XML 테마에도 light status bar 정책을 명시했다. 3버튼 내비게이션 바는 Material background 색상과 아이콘 명암을 테마에 동기화하고 시스템 대비 스크림을 제거해 앱 배경과 이어지도록 했다. 기존 테마/인앱 업데이트 경로는 회귀 방지를 위해 유지하며 나머지 Compose shell 연결과 phone/tablet/멀티윈도우/업데이트 실기기 검증이 남았다.
  - Depends on: M4-006
  - Acceptance: F-024, F-028~F-030 Android parity

- [ ] **M5-007 (P0, L)** Android 전체 업그레이드/회귀 테스트를 통과한다.
  - Depends on: M5-001~M5-006
  - Acceptance:
    - F-001~F-032의 Android P0/P1가 `Parity`
    - 구 설치 fixture 업그레이드
    - release minify build + 실기기 matrix
    - staged rollout/rollback plan 승인

## M6 — iOS 기본 제품 경로

- [ ] **M6-001 (P0, L)** WKWebView holder와 navigation/cookie adapter를 구현한다.
  - Depends on: M2-004, M4-004
  - Acceptance:
    - persistent data store와 cookie observation
    - back/reload/modal/external URL
    - delegate/message handler lifecycle 안전

- [ ] **M6-002 (P0, L)** iOS legacy `window.Android` shim과 Bridge v1 handler를 구현한다.
  - Depends on: M4-001, M6-001
  - Acceptance:
    - document start 주입
    - trusted origin/main-frame 검증
    - legacy 동기 설정 조회 전략 구현
    - M1-002/M1-003 계약 테스트 통과

- [ ] **M6-003 (P0, XL)** iOS 인증/세션 경로를 완성한다.
  - Depends on: M3-002, M3-003, M3-006, M6-001, M6-002
  - Acceptance:
    - F-002~F-006 iOS parity 또는 승인 차이
    - Keychain과 WKHTTPCookieStore 재시작 복구
    - CAPTCHA/temp PW/network/timeout

- [ ] **M6-004 (P0, XL)** iOS Home/Lecture/Board/Task 기본 경로를 연결한다.
  - Depends on: M6-003, M4-008
  - Acceptance:
    - F-007~F-016의 P0 경로
    - bridge unsupported가 crash가 아닌 명시 UI로 표시
    - 앱/Web 버전 조합 테스트

- [ ] **M6-005 (P1, L)** iOS 다운로드/파일 선택/외부 링크를 구현한다.
  - Depends on: M6-001, M6-004
  - Acceptance: F-025~F-027 iOS 승인 시나리오

- [ ] **M6-006 (P1, M)** iOS 테마/키보드/safe area/회전/접근성을 검증한다.
  - Depends on: M6-004
  - Acceptance: Dynamic Type/VoiceOver 기본 경로, dark mode, 폰/패드

## M7 — iOS 플랫폼 기능

- [ ] **M7-001 (P0, L)** iOS 앱 잠금과 LocalAuthentication을 구현한다.
  - Depends on: M3-007, M6-003
  - Acceptance: F-022/F-023 iOS parity 또는 ADR로 승인된 차이

- [ ] **M7-002 (P0, L)** iOS QR 출석 스캐너를 구현한다.
  - Depends on: M3-004, M6-004
  - Acceptance: F-019 iOS; 카메라 권한/취소/세션 만료 포함

- [ ] **M7-003 (P0, M)** iOS PIP 기술 spike와 ADR-005를 완료한다.
  - Depends on: M6-004
  - 비교: WKWebView native media PIP vs URL 추출 + AVPlayer
  - Acceptance:
    - 진도/인증/remote control/배경 정책 비교
    - 실기기 결과와 선택 근거
    - 불가능한 동작은 제품 차이로 명시

- [ ] **M7-004 (P1, XL)** 선택한 iOS 비디오/PIP 경로를 구현한다.
  - Depends on: M7-003
  - Acceptance: F-017/F-018 iOS 승인 시나리오, App Store entitlement 검토

- [ ] **M7-005 (P1, M)** WidgetKit/App Group 기술 spike와 ADR-006을 완료한다.
  - Depends on: M3-005, M3-006
  - Acceptance:
    - 위젯에 공유할 최소 파생 데이터 정의
    - 잠금/만료/Keychain 접근 정책
    - timeline refresh와 앱 진입 검증

- [ ] **M7-006 (P1, XL)** iOS 도서관 QR 및 WidgetKit extension을 구현한다.
  - Depends on: M7-005
  - Acceptance: F-020/F-021 iOS 승인 시나리오, 실기기 검증

- [ ] **M7-007 (P1, M)** Android 전용 기능의 iOS 차이를 제품 문구와 telemetry에 반영한다.
  - Depends on: M7-001~M7-006
  - Acceptance: 미지원 capability가 숨은 실패가 아니며 matrix에 `Approved difference`

## M8 — 보안, 관측, 릴리스

- [ ] **M8-001 (P0, L)** WebView 보안 경계를 강화한다.
  - 진행: cleartext traffic 기본 차단, 내부 Activity exported 제거, exact trusted origin/main-frame Bridge v1, Link/Web modal 외부 top-level legacy façade 제거, SSL 오류 우회 금지를 구현. trusted top-level 내 legacy 하위 프레임 노출은 M4-008 완료 시 제거하고 Android/iOS 전체 exported/deep-link 검증은 남았다.
  - Depends on: M4-004, M6-001
  - Acceptance:
    - cleartext traffic 필요성 조사 후 기본 차단
    - trusted origin/main frame bridge
    - SSL 오류 우회 없음
    - exported Activity/Intent/deep link 검증

- [ ] **M8-002 (P0, M)** 민감정보 redaction과 crash attachment 정책을 적용한다.
  - 진행: 네트워크 body/header logging 비활성, bridge/repository 오류의 일반화된 메시지, Sentry screenshot/view hierarchy 첨부 비활성화를 적용. 알려진 secret key 전체 자동 redaction 테스트와 iOS 설정은 미완료.
  - Depends on: M3-006, M4-003
  - Acceptance:
    - credential/session/library secret/QR payload 로그 없음
    - Sentry screenshot/view hierarchy masking
    - 자동 테스트로 알려진 키 redaction

- [ ] **M8-003 (P0, M)** 관측 지표를 추가한다.
  - Depends on: M3-002, M4-004
  - 지표: 로그인 단계별 성공률/지연, session recovery, Web load/bridge errors, player/PIP, migration result
  - Acceptance: 사용자 식별·토큰·페이지 개인정보 없이 원인 분류 가능

- [ ] **M8-004 (P0, L)** Android staged rollout과 rollback drill을 수행한다.
  - Depends on: M5-007, M8-001~M8-003
  - Acceptance:
    - feature flag 단계와 중단 기준
    - 신규 store 이후 구 버전 rollback 영향 검증
    - Play pre-launch/내부 테스트 결과

- [ ] **M8-005 (P0, L)** iOS TestFlight 및 App Store 준비를 완료한다.
  - Depends on: M6 전체, 출시 범위의 M7, M8-001~M8-003
  - Acceptance:
    - signing/entitlements/privacy/permission 문구
    - TestFlight 기기 matrix와 crash-free 기준
    - 지원 기능/차이 문서

- [ ] **M8-006 (P0, M)** 문서와 실제 구현을 최종 대조한다.
  - Depends on: M8-004, M8-005
  - Acceptance:
    - `AGENTS.md`, 아키텍처, ADR, matrix, task 상태 일치
    - 모든 `Parity` 행에 증거
    - 남은 P2와 기술 부채를 별도 backlog로 이동

## 즉시 시작할 첫 작업 묶음

다음 순서가 첫 구현 사이클의 권장 범위다.

1. M0-001~M0-005: 이력/기준 SHA/두 빌드 확보
2. M1-001~M1-003: 인증과 브리지 계약 fixture
3. M2-001~M2-004: iOS SwiftUI에서 `Shared.framework` 공통 API를 호출하는 smoke slice
4. M2-005~M2-006: 공통 port와 네트워크 기반
5. M3-002~M3-003: 인증 상태 머신 + SessionCoordinator
6. M3-008: 신규 core를 기존 Android UI에 먼저 연결

이 묶음이 끝나기 전에는 `HomeActivity`나 `VideoPlayerActivity`를 대규모로 Compose 재작성하지 않는다.

## 열린 질문

- [ ] 기존 Android 저장소의 어느 commit/tag를 출시 기준선으로 고정할 것인가?
- [ ] 현재 KMP 폴더의 remote와 브랜치 전략은 무엇인가?
- [ ] iOS 최소 버전과 iPad 지원 범위는 어디까지인가?
- [ ] Web 저장소를 앱과 같은 릴리스 열에서 변경할 수 있는가?
- [ ] 서버가 도서관 API의 iOS `device_gb` 값을 지원하는가?
- [ ] iOS PIP에서 현재 웹 플레이어 진도 보고를 동일하게 유지할 수 있는가?
- [ ] Widget 잠금 중 QR 노출 정책은 Android/iOS에서 동일해야 하는가?
- [ ] Sentry 화면 캡처를 민감 WebView에서 완전히 끌 것인가, 선택 마스킹할 것인가?

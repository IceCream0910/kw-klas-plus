# KLAS+ KMP 마이그레이션 작업 현황

- 기준일: 2026-08-23
- 현재 단계: **Android 마이그레이션 완료, iOS 기본 제품 경로(M6) 진행 중**
- Android 상태: KMP 공통 코어, Compose UI, WebView 브리지, 네이티브 기능의 P0/P1 패리티 완료
- iOS 상태: 툴체인·framework·WKWebView navigation/cookie·Native bridge·인증/세션·화면별 제품 경로·다운로드/외부 이동 완료. 파일 업로드는 실계정 검증 남음. 다음: UI 환경

## 개요

| Milestone              | 상태          | 결과 / 다음 게이트                               |
| ---------------------- |-------------| ----------------------------------------- |
| M1 계약 고정               | **완료(6/6)** | Android 인증·브리지·저장소·플랫폼 계약 고정 완료           |
| M2 KMP/Android 기반      | **완료(6/6)** | 공통 모듈과 Android source set 경계 정렬 완료        |
| M3 공통 코어               | **완료(9/9)** | 인증·세션·API·보안 저장소·앱 잠금 공통화 및 Android 연결 완료 |
| M4 Android Web/Compose | 진행 중(7/8)   | Android 화면 전환 완료. legacy 자산 정리만 남음        |
| M5 Android 기능 패리티      | **완료(7/7)** | QR·잠금·PIP·위젯·파일·테마 및 전체 Android 회귀 통과     |
| M6 iOS 기본 경로           | 진행 중(9/11)  | M6-010 다운로드·외부 이동 완료. 파일 업로드 실계정 검증 남음. 다음: M6-011 UI 환경 |
| M7 iOS 플랫폼 기능          | 진행 중(1/7)    | M7-001 앱 잠금 완료. 다음: M7-002 QR 출석 |

### M1 — 기존 계약 고정

- [x] **M1-001 (P0, L)** 인증 A-001~A-012 fixture와 상태 전이 테스트
- [x] **M1-002 (P0, L)** Web → Native legacy 브리지 8개 surface/64개 메서드 고정
- [x] **M1-003 (P0, L)** Native → Web callback과 JSON-safe 주입 fixture 고정
- [x] **M1-005 (P0, L)** Android 화면별 golden flow 기록 및 검증
- [x] **M1-006 (P0, M)** 기존 저장 데이터 migration fixture와 업그레이드 검증
- [x] **M1-007 (P1, M)** QR·PIP·Widget·생체·파일 기능 특성화

### M2 — KMP/Android 실행 기반 · 완료

- [x] **M2-001 (P0, M)** 단일 `shared` 모듈 전략 확정
- [x] **M2-005 (P0, M)** Ktor·serialization·coroutine 공통 의존성 도입
- [x] **M2-006 (P0, L)** 공통 platform port와 테스트 fake 정의
- [x] **M2-007 (P1, M)** typed navigation과 capability 모델 정의
- [x] **M2-008 (P0, S)** Android 최소 OS·기기·반응형 정책 확정
- [x] **M2-009 (P0, L)** Android 구현을 KMP source set 경계로 재배치

### M3 — 공통 코어 추출 · 완료

- [x] **M3-001 (P0, M)** URL·preference key·route typed model
- [x] **M3-002 (P0, L)** AuthStateMachine과 LoginUseCase
- [x] **M3-003 (P0, L)** SessionCoordinator와 cookie/store 동기화
- [x] **M3-004 (P0, L)** KLAS API repository
- [x] **M3-005 (P1, L)** LibraryRepository와 Android 호환 crypto/cache
- [x] **M3-006 (P0, L)** Android Keystore SecureStore migration
- [x] **M3-007 (P0, M)** 앱 잠금 도메인·lifecycle 정책
- [x] **M3-008 (P0, L)** 기존 Android UI의 신규 core 연결
- [x] **M3-009 (P0, L)** Android 앱 계층 공통화 감사

### M4 — Android Web/Compose 전환

- [x] **M4-001 (P0, L)** Bridge v1 schema와 router
- [x] **M4-002 (P0, L)** Android legacy façade와 Bridge v1 병행 연결
- [x] **M4-003 (P0, M)** Native → JS JSON-safe dispatcher
- [x] **M4-004 (P0, L)** WebSurface와 Android WebView holder
- [x] **M4-005 (P0, L)** Compose startup/auth shell
- [x] **M4-006 (P0, XL)** Home Web surface Compose 전환
- [x] **M4-007 (P0, XL)** Lecture/Board/Task/Link/Plan Compose 전환
- [ ] **M4-009 (P1, M)** 사용하지 않는 XML/View 자산을 기능별 정리
  - Depends on: M5-007
  - 완료 기준: rollback 자산 목록 확인 후 삭제, debug와 R8 release build 통과

### M5 — Android 네이티브 기능 패리티 · 완료

- [x] **M5-001 (P0, L)** QR 출석과 공통 AttendanceRepository
- [x] **M5-002 (P0, L)** Compose 앱 잠금과 Android 생체인식
- [x] **M5-003 (P0, XL)** Compose 비디오 플레이어와 Android PIP
- [x] **M5-004 (P1, L)** 도서관 QR과 AppWidget
- [x] **M5-005 (P1, L)** 다운로드·파일 선택·외부 이동
- [x] **M5-006 (P1, M)** 테마·방향·태블릿·햅틱·인앱 업데이트 패리티
- [x] **M5-007 (P0, L)** Android P0/P1 업그레이드·회귀·release 검증

### M6 — iOS 기본 기능 구현

진행 순서: `M6-003` WKWebView smoke → `M6-006` navigation/cookie → `M6-007` Native bridge → `M6-008` 인증/세션 → `M6-009` 화면별 제품 경로 → `M6-010` 다운로드·파일. 완료된 `M6-004`·`M6-005`는 Web 측 선행 계약이다. `M6-010` 다운로드·외부 이동은 완료, 파일 업로드 실계정 검증은 후속.

- [x] **M6-001 (P0, M)** iOS 툴체인과 최소 지원 환경 고정
  - 브랜치: `m6-001-002/ios-toolchain-framework`
  - 정책: iOS/iPadOS 16.0, ADR-007, `Config.xcconfig` / CONTRIBUTING 툴체인 표
  - 완료 기준: macOS에서 Xcode·Kotlin·Gradle 조합, iOS/iPadOS 최소 버전, device/simulator 빌드 경로 확인
  - 검증:
    - Xcode `iosApp` Simulator 빌드·실행
    - 실기기 실행: 미검증 (Simulator 경로로 완료)
- [x] **M6-002 (P0, M)** iOS device/simulator framework 연결
  - Depends on: M6-001
  - 브랜치: `m6-001-002/ios-toolchain-framework`
  - 완료 기준: `iosArm64`·`iosSimulatorArm64` 빌드와 Xcode 링크
- [x] **M6-003 (P0, M)** SwiftUI에서 유지되는 WKWebView 최소 실행 경로
  - Depends on: M6-002
  - 브랜치: `m6-003/ios-wkwebview-holder`
  - `WebViewHolder`가 생성·보존·dispose를 소유하고 `WebViewContainer`는 표시만 담당
  - App `@StateObject`로 holder 수명 분리. smoke URL은 Shared `KlasUrls.KLAS_PLUS_BASE` + `/feed`
  - 검증: Simulator에서 고정 URL 로드, 재렌더·재진입 시 인스턴스 유지, 명시적 dispose
- [x] **M6-004 (P0, M)** Next.js bridge contract test 작성
  - 대상: 별도 `kw-klas-plus-webview` 저장소
  - 검증: Bridge v1 request/response, timeout, unknown-method legacy retry, 미지원 브라우저 동작
- [x] **M6-005 (P0, M)** Next.js 플랫폼 중립 bridge adapter 추가
  - 페이지 호출을 `KlasNativeBridge.*`로 통일하고 `KlasNativeBridgeNative.postMessage` Bridge v1을 우선 사용
  - 구 Android 앱은 adapter 내부 `window.Android` fallback으로 지원
  - 신 Android 앱은 legacy `Android` JS façade를 제거하고 Bridge v1만 노출
  - Native 계약 테스트에서 활성 Web 메서드와 7개 surface/57개 command를 대조
- [x] **M6-006 (P0, L)** WKWebView navigation·cookie·수명주기 adapter
  - Depends on: M6-003
  - `WebViewHolder`가 persistent `WKWebsiteDataStore.default()`, navigation snapshot, back/reload/dispose 소유
  - `TrustedOriginPolicy`/`ExternalNavigationPolicy`로 main-frame decidePolicy·새 창 분기. SESSION은 `IosWebCookieStore`(iosMain)
  - 검증: `:shared:iosSimulatorArm64Test`(cookie set/get/clear·속성), `xcodebuild` iosApp, DEBUG Back/Reload/external·dispose
- [x] **M6-007 (P0, L)** `KlasNativeBridgeNative` transport와 Bridge router 연결
  - Depends on: M6-005, M6-006
  - `IosBridgeMessageAdapter`가 `WKScriptMessageHandlerWithReply` → 공통 `JsonBridgeRouter`로 Promise response 반환
  - document-start에 WebKit transport shim + `KlasNativeBridge` adapter 주입. 로그인/링크·브리지 테스트 host는 `AcceptingBridgeCommandHandler`
  - 완료 기준: 앱 origin과 Video용 HTTPS `*.kw.ac.kr` 정책, main-frame, payload·메서드·인자 검증 및 handler 등록/해제 구현
  - 검증:
    - `./gradlew :shared:iosSimulatorArm64Test --tests 'com.icecream.kwklasplus.core.web.WebAutomationScriptsTest' --tests 'com.icecream.kwklasplus.core.bridge.*'`
    - `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -destination 'platform=iOS Simulator,name=iPhone 17,OS=26.5' test`
    - iosAppTests: 정상 호출, async result, unknown method, malformed/oversize, iframe·비허용 origin, Video origin, timeout, dispose
- [x] **M6-008 (P0, XL)** SwiftUI 온보딩·로그인과 WKWebView SESSION 복구 경로
  - Depends on: M6-006, M6-007
  - 작업: Android의 네이티브 온보딩·로그인 화면에 대응하는 약관 동의, ID/PW 입력, validation, loading/error 상태를 SwiftUI로 구현
  - 작업: 로그인 제출을 공통 인증 API와 KLAS 로그인 WKWebView에 연결하고 `SESSION`을 `WKHTTPCookieStore`·`SessionCoordinator`·Keychain에 동기화
  - 완료 기준: 최초 온보딩, 수동·저장 credential 로그인, 유효 세션 즉시 진입, 만료·로그아웃·재시작 복구가 하나의 startup flow로 동작
  - 검증: F-002~F-006, CAPTCHA·임시 비밀번호·네트워크·timeout, 평문 비밀번호와 SESSION의 로그·UserDefaults 비저장 확인
  - 증거: `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -destination 'platform=iOS Simulator,name=iPhone 17,OS=26.5' test -only-testing:iosAppTests/IosWebAuthDriverTests -only-testing:iosAppTests/IosAuthSecurityTests -only-testing:iosAppTests/AuthSessionControllerTests`
    - `IosWebAuthDriverTests`: CAPTCHA alert, 임시 비밀번호 재노출, hanging timeout, scheme load network failure
    - `IosAuthSecurityTests`: SecretValue/PlainPassword `[REDACTED]`, UserDefaults에 평문/`kwPWD`/`kwSESSION`/암호문/토큰 미저장, Keychain round-trip, `kwSESSION_timestamp`만 기록
    - `AuthSessionControllerTests`: 세션 만료 종료 시 SessionCoordinator 만료 후 로그인 화면 복귀
- [x] **M6-009 (P0, XL)** Home/Lecture/Board/Task WKWebView와 화면별 Bridge host
  - Depends on: M6-008
  - 작업: 화면별 URL/surface를 SwiftUI navigation에 연결하고 Bridge command를 작은 iOS host 인터페이스로 구현
  - 작업: Home·Lecture·Board·LecturePlan·Link·Settings command 중 해당 화면에 필요한 navigation/modal/reload/callback 연결
  - 완료 기준: F-007~F-016 P0 경로에서 `KlasNativeBridge.*` 호출과 Native → Web callback이 Android와 동일한 결과를 생성
  - 검증: 앱 Web + 신 iOS 조합의 탭·back·게시판·강의·과제 링크·학기 선택·modal 회귀
  - 후속: 홈 공지 `openPage(www.kw.ac.kr)`는 Link holder 인앱 http(s) 모드로 Safari 대신 WKWebView 로드. `IosFilePortsTests` in-app web, `IosHomeHostTests` `testUniversityNoticeOpenPagePushesInAppLink`
- [ ] **M6-010 (P1, L)** WKWebView 다운로드·파일 선택·외부 이동
  - Depends on: M6-009
  - 작업: WKDownload/URLSession, document/photo picker, share sheet, `UIApplication.open`을 공통 요청·URL 정책에 연결
  - 완료 기준: cookie가 필요한 다운로드, MIME·파일명, 단일/다중 선택, 취소, mailto/tel/https와 악성 scheme 거부
  - 남은 일: 파일 업로드(`UIDocumentPicker`)는 계약 테스트만 통과. 실계정 KLAS 업로드 경로 검증 후 완료 처리
- [x] **M6-011 (P1, M)** WKWebView 컨테이너의 iOS UI 환경 대응
  - Depends on: M6-009
  - 작업: SwiftUI theme, safe area, 키보드·viewport, iPhone/iPad 회전과 Dynamic Type/VoiceOver focus 처리
  - 완료 기준: Web content와 Native overlay가 compact/regular, 세로/가로, 키보드 표시 상태에서 가려지거나 중복 재생성되지 않음
  - 구현: persistent WebView layout/viewport policy, screen-level overlay host, Dynamic Type·accessibility focus, iOS UI test fixture/target을 추가

### M7 — iOS 네이티브 기능 구현

- [x] **M7-001 (P0, L)** 앱 잠금·LocalAuthentication·Keychain
  - 작업: 네이티브 UI 구현 후 WebView의 settings 페이지 내 '앱 잠금' 관련 옵션 및 버튼 상태 연동
  - 구현: `IosAppLockStore`(Keychain hash/salt, UserDefaults `a_l_e`/`b_m_e`), SwiftUI PIN(`SET`/`CHANGE`/`VERIFY`/`UNLOCK`), `scenePhase` + `AppLockPolicy`, Settings 브리지 콜백
  - 검증:
    - `./gradlew :shared:iosSimulatorArm64Test --tests 'com.icecream.kwklasplus.core.lock.IosAppLockCredentialCodecTest' --tests 'com.icecream.kwklasplus.core.web.IosWebCallbacksTest'`
    - `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -destination 'platform=iOS Simulator,name=iPhone 17,OS=26.5' test -only-testing:iosAppTests/IosAppLockStoreTests -only-testing:iosAppTests/AppLockControllerTests`
  - 남은 일: Face ID 활성화·UNLOCK은 실기기 검증
- [ ] **M7-002 (P0, L)** QR 출석 스캐너
  - Depends on: M6-009
- [ ] **M7-003 (P0, M)** iOS 온라인 강의 player·PIP 방식 결정
  - Depends on: M6-009
  - 작업: KLAS 강의 URL/진도 추출, WKWebView media와 AVPlayer 중 재생 방식, `AVPictureInPictureController` 연결 가능성을 비교
  - 작업: Android `VideoPlayerActivity`의 재생·seek·속도·진도·fullscreen·PIP command/state 계약을 iOS 구조에 매핑
  - 완료 기준: 선택 방식, 미지원/DRM·cookie 제약, fallback, PIP 복귀 상태 복원 방식을 ADR 또는 spike 문서로 확정
- [ ] **M7-004 (P0, XL)** SwiftUI 온라인 강의 player와 PIP 구현
  - Depends on: M7-003
  - 작업: Android 네이티브 VideoPlayer에 대응하는 SwiftUI 재생 화면과 play/pause·seek·속도·진도·fullscreen controls 구현
  - 작업: `KlasNativeBridge`의 `receiveVideoURL`·`receiveVideoData`·`receivePlayerStates` 및 player command를 선택한 iOS media host에 연결
  - 완료 기준: F-017·F-018의 재생, 이어보기, 진도 보고, background/foreground, PIP 시작·종료·remote action·화면 복귀 동작
  - 검증: Simulator 기본 controls + 재생 가능한 실기기에서 media/PIP/잠금 화면·중단 복구 검증
- [ ] **M7-005 (P1, M)** 도서관 QR App Group·WidgetKit 공유 정책 확정
  - Depends on: M6-008
  - 작업: 공통 도서관 QR repository 결과 중 앱·Widget에 공유할 캐시 schema, App Group, Keychain 접근 범위와 만료·잠금 정책 결정
  - 완료 기준: 개인정보 저장 위치, timeline 갱신 주기, 로그아웃/만료/오프라인 fallback, widget deep link를 문서와 fixture로 고정
- [ ] **M7-006 (P1, XL)** SwiftUI 도서관 출입증 QR 화면과 WidgetKit 구현
  - Depends on: M7-005
  - 작업: Android 네이티브 도서관 QR 화면에 대응하는 조회·설정·로딩·오류·갱신 UI와 QR 렌더링을 SwiftUI로 구현
  - 작업: App Group cache를 읽는 WidgetKit timeline과 잠금·만료·테마별 placeholder/deep link 구현
  - 완료 기준: F-020·F-021의 신규 조회, 캐시 표시, 수동 갱신, 로그아웃 삭제, 잠금 상태, light/dark widget 패리티
  - 검증: 앱 화면은 Simulator, Widget 갱신·잠금·재부팅·App Group/Keychain 동작은 실기기에서 확인

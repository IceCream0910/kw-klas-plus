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

- [ ] **M0-004 (P0, M)** Android 기준 앱을 debug/release-like로 빌드한다.
  - Depends on: M0-002, M0-003
  - Acceptance:
    - 빌드 명령과 JDK/Gradle/SDK 버전 기록
    - minify/resource shrink 조건을 포함한 빌드 성공
    - manifest merge 및 signing config 확인

- [ ] **M0-005 (P0, M)** KMP 보일러플레이트 빌드 기준을 기록한다.
  - Depends on: M0-001
  - Acceptance:
    - Android debug assemble 성공
    - `sharedLogic` Android/iOS 테스트 task 확인
    - macOS CI/개발 환경에서 iOS framework와 Xcode build 경로 문서화

- [ ] **M0-006 (P0, M)** 기준 기기/OS 매트릭스와 수동 smoke checklist를 만든다.
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
  - Depends on: M0-003, M0-004
  - 시나리오: A-001~A-012
  - Acceptance:
    - 로그인 URL/성공 redirect/SESSION cookie 형식 fixture
    - 암호화 API 성공/실패/malformed 응답 fixture
    - CAPTCHA, 임시 비밀번호, timeout을 구분
    - fixture와 test log에 실제 credential/session 없음

- [ ] **M1-002 (P0, L)** legacy Web → Native 브리지 스키마를 코드로 캡처한다.
  - Depends on: M0-003
  - Acceptance:
    - `FEATURE_PARITY_MATRIX` 5장의 모든 메서드, 인자 수/타입, return 방식 포함
    - `getAppLockSettings` 동기 반환과 `evaluteKLASScript` 철자 고정
    - 알 수 없는 메서드/잘못된 인자 거부 테스트

- [ ] **M1-003 (P0, L)** Native → Web callback과 JS 주입 fixture를 캡처한다.
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
  - Depends on: M0-006
  - Acceptance:
    - F-001~F-032의 P0/P1 수동 단계와 기대 결과
    - 화면 캡처 또는 동영상(민감정보 마스킹)
    - back/회전/백그라운드/프로세스 재생성 포함

- [ ] **M1-006 (P0, M)** 저장 데이터 migration fixture를 만든다.
  - Depends on: M0-003
  - Acceptance:
    - 일반 prefs의 구 `kwPWD`, secure prefs의 신규 `kwPWD`
    - session timestamp 정상/누락/깨진 값
    - library cache와 app lock key
    - 백업 복원 시 키 불일치 사례

- [ ] **M1-007 (P1, M)** 플랫폼 기능 특성화 테스트를 작성한다.
  - Depends on: M0-006
  - 범위: PIP event/state, Widget 진입/갱신, QR 결과, download/file chooser, biometric lifecycle
  - Acceptance: 신규 구현이 비교할 observable result 정의

## M2 — 프로젝트/모듈 기반 정렬

- [x] **M2-001 (P0, M)** ADR-001로 shared module 노출 전략을 확정한다.
  - 완료: `docs/adr/ADR-001-shared-module-strategy.md`
  - Depends on: M0-005
  - Acceptance:
    - `sharedLogic`+`sharedUI` 유지 또는 통합 근거
    - iOS에 노출되는 Kotlin framework는 하나
    - public Swift API surface 최소화

- [ ] **M2-002 (P0, M)** Kotlin/Compose/AGP/Gradle/JDK/Xcode 호환 조합을 고정한다.
  - Depends on: M0-004, M0-005
  - Acceptance:
    - legacy SDK/app version 퇴행 없음
    - Android와 iOS CI 모두 통과
    - version catalog와 문서 일치

- [ ] **M2-003 (P0, M)** `sharedUI`에 iOS device/simulator 타깃과 framework를 추가한다.
  - Depends on: M2-001, M2-002
  - Acceptance:
    - `iosArm64`, `iosSimulatorArm64` build
    - 공통 `App()`이 iOS simulator에 표시
    - framework 중복/링커 경고 없음

- [ ] **M2-004 (P0, M)** iOS SwiftUI entry가 Compose UIViewController를 호스팅하게 한다.
  - Depends on: M2-003
  - Acceptance:
    - SwiftUI wrapper와 lifecycle 전달
    - safe area/keyboard/orientation smoke test
    - 기존 `SharedLogic` 직접 import 정리

- [ ] **M2-005 (P0, M)** 공통 coroutine, serialization, Ktor 의존성을 도입한다.
  - Depends on: M2-002
  - Acceptance:
    - common core + Android OkHttp engine + iOS Darwin engine
    - timeout/redaction/error mapping 기본 설정
    - Android/iOS MockEngine 또는 fake 기반 테스트

- [ ] **M2-006 (P0, L)** 플랫폼 port와 app dependency container를 정의한다.
  - Depends on: M2-001, M2-005
  - Ports: SecureStore, PreferencesStore, WebCookieStore, WebAuthDriver, ExternalNavigator, Biometrics, Haptics, FileTransfer, QRScanner, PictureInPicture, Clock
  - Acceptance:
    - commonMain에 플랫폼 타입 없음
    - Android/iOS entry에서 명시적으로 구현 주입
    - 테스트 fake 제공

- [ ] **M2-007 (P1, M)** 공통 navigation 및 capability 모델을 정의한다.
  - Depends on: M2-003, M2-006
  - Acceptance:
    - Web route, native overlay, modal, platform feature route 구분
    - unsupported/permission-required 상태 표현
    - deep link/Intent extra가 typed route로 검증됨

- [ ] **M2-008 (P0, S)** 최소 Android/iOS OS와 폰/태블릿 정책 ADR을 확정한다.
  - Depends on: M0-006, M2-002
  - Acceptance: Store 배포 조건, Compose 지원 범위, QR/PIP/Widget API 요구사항 반영

## M3 — 공통 코어 추출

- [ ] **M3-001 (P0, M)** URL, preference key, intent payload를 typed model로 옮긴다.
  - Depends on: M1-006, M2-006
  - Acceptance:
    - legacy key reader는 유지
    - URL scheme/host 정책 테스트
    - common model에 Android Intent/Bundle 없음

- [ ] **M3-002 (P0, L)** AuthStateMachine과 LoginUseCase를 구현한다.
  - Depends on: M1-001, M2-005, M2-006
  - Acceptance:
    - A-001~A-012 공통 테스트 통과
    - network/security/user-action 오류 분리
    - 평문 password 저장/로그 없음

- [ ] **M3-003 (P0, L)** SessionCoordinator를 구현한다.
  - Depends on: M1-001, M2-006
  - Acceptance:
    - secure store와 cookie store 동기화의 단일 소유자
    - set/refresh/expire/logout 원자적 의미
    - Android legacy 1시간 instant session 동작 보존

- [ ] **M3-004 (P0, L)** KLAS API repository를 구현한다.
  - Depends on: M2-005, M3-003
  - 우선 범위: password encryption, QR check-in, attendance/semester 요청
  - Acceptance:
    - `SESSION`과 User-Agent 동작이 legacy fixture와 일치
    - JSON DTO 및 오류 매핑 테스트
    - HTTP body/header 로그 redaction

- [ ] **M3-005 (P1, L)** LibraryRepository를 공통화한다.
  - Depends on: M1-006, M2-005, M2-006
  - Acceptance:
    - XML parsing, Base64, AES 호환 fixture 통과
    - `device_gb=A`의 iOS 호환 여부 조사/ADR 기록
    - secret/authKey cache expiry와 clear 정책

- [ ] **M3-006 (P0, L)** SecureStore migration을 구현한다.
  - Depends on: M1-006, M2-006, ADR-003
  - Acceptance:
    - Android 구 일반/EncryptedSharedPreferences → 신규 store
    - write-read 검증 후 구 데이터 삭제
    - 중간 실패 재시도 가능, rollback에서 구 앱이 치명적으로 깨지지 않음
    - backup rules 테스트

- [ ] **M3-007 (P0, M)** 앱 잠금 도메인과 정책을 추출한다.
  - Depends on: M2-006, M3-006
  - Acceptance:
    - enabled/biometric/password/lifecycle 상태 모델
    - hash migration 및 검증 테스트
    - 위젯 예외 정책 명시

- [ ] **M3-008 (P0, L)** 기존 Android View UI가 신규 auth/session/repository를 사용하게 연결한다.
  - Depends on: M3-002~M3-007
  - Acceptance:
    - 아직 Compose 전환 전인 UI에서 A-001~A-012 통과
    - 신규 core 문제 시 legacy implementation으로 되돌릴 수 있음
    - Android 사용자 동작 차이 없음

## M4 — 버전형 브리지와 Android Web/Compose 전환

- [ ] **M4-001 (P0, L)** Bridge v1 command/event schema와 router를 구현한다.
  - Depends on: M1-002, M1-003, M2-006
  - Acceptance:
    - version/id/method/args/result envelope
    - allowlist, 타입/길이/origin/main-frame 검증
    - 모든 legacy 메서드가 typed command로 매핑

- [ ] **M4-002 (P0, L)** Android legacy `Android` façade를 router에 연결한다.
  - Depends on: M4-001
  - Acceptance:
    - 기존 Web commit 변경 없이 P0/P1 flow 동작
    - 동기 `getAppLockSettings` 보존
    - 외부 origin에는 façade 미노출

- [ ] **M4-003 (P0, M)** Native → JS 전달을 JSON-safe dispatcher로 교체한다.
  - Depends on: M4-001
  - Acceptance:
    - callback 문자열 직접 결합 제거
    - M1-003 특수문자/Unicode/큰 payload 통과
    - secret이 오류 로그에 포함되지 않음

- [ ] **M4-004 (P0, L)** `WebSurface` 공통 계약과 Android WebView holder를 구현한다.
  - Depends on: M2-006, M2-007, M4-001
  - Acceptance:
    - navigation/loading/error/back/reload/evaluate/cookie
    - lifecycle 재구성에서 WebView 불필요 재생성 없음
    - handler/delegate 해제 및 누수 검사

- [ ] **M4-005 (P0, L)** Compose 앱 셸과 startup/auth 화면을 구현한다.
  - Depends on: M3-008, M4-004
  - Acceptance:
    - F-001~F-006 Android parity
    - network error/CAPTCHA/timeout UI
    - 폰/태블릿/IME/접근성

- [ ] **M4-006 (P0, XL)** Home Web surface를 Compose route로 이전한다.
  - Depends on: M4-002~M4-005
  - 분할: feed/timetable/calendar/profile/settings/modal
  - Acceptance:
    - F-007~F-011, F-016, F-024 패리티
    - tab/back/modal/year-semester callbacks
    - 구 HomeActivity fallback 가능

- [ ] **M4-007 (P0, XL)** Lecture/Board/Task/Link/Plan Web surface를 이전한다.
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
  - Depends on: 해당 기능의 Android Parity
  - Acceptance:
    - 기능별 삭제, 전체 일괄 삭제 금지
    - resource shrink/minify build
    - 패리티 증거 없는 Activity는 유지

## M5 — Android 네이티브 기능 패리티

- [ ] **M5-001 (P0, L)** QR 출석을 `QRScanner`/`AttendanceRepository`에 연결한다.
  - Depends on: M3-004, M2-006
  - Acceptance: F-019와 성공/실패/취소/권한/세션 만료 통과

- [ ] **M5-002 (P0, L)** 앱 잠금/생체인식을 신규 core와 Compose UI에 연결한다.
  - Depends on: M3-007, M4-005
  - Acceptance: F-022, F-023; foreground/background race와 widget 예외 포함

- [ ] **M5-003 (P0, XL)** 비디오 플레이어와 Android PIP를 새 경계로 이전한다.
  - Depends on: M4-001, M4-007
  - Acceptance:
    - F-017, F-018 Android parity
    - 재생/정지/seek/speed/progress/PIP remote action
    - 회전/백그라운드/복귀 시 상태 보존

- [ ] **M5-004 (P1, L)** 도서관 QR UI와 AppWidget을 신규 repository/store에 연결한다.
  - Depends on: M3-005, M3-006
  - Acceptance: F-020, F-021 Android parity; 만료/테마/잠금 포함

- [ ] **M5-005 (P1, L)** download/file chooser/external navigation을 port에 연결한다.
  - Depends on: M4-004, M4-007
  - Acceptance: F-025~F-027; session cookie, MIME, filename, malicious URL 검증

- [ ] **M5-006 (P1, M)** 테마/방향/태블릿/햅틱/인앱 업데이트를 정리한다.
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
  - Depends on: M4-004, M6-001
  - Acceptance:
    - cleartext traffic 필요성 조사 후 기본 차단
    - trusted origin/main frame bridge
    - SSL 오류 우회 없음
    - exported Activity/Intent/deep link 검증

- [ ] **M8-002 (P0, M)** 민감정보 redaction과 crash attachment 정책을 적용한다.
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
3. M2-001~M2-004: iOS에서도 `sharedUI`가 실제로 뜨는 smoke slice
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

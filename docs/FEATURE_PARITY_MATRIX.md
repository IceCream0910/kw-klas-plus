# 기능·화면·브리지 패리티 매트릭스

- 기준 분석일: 2026-07-15
- Native 기준: <https://github.com/IceCream0910/kw-klas-plus> `main` 분석본
- Web 기준: <https://github.com/IceCream0910/kw-klas-plus-webview> `main` 분석본
- 주의: 구현 시작 전 두 저장소의 정확한 commit SHA를 아래 표에 고정해야 한다.

## 1. 상태 표기

- `Not started`: 구현/검증 전
- `Characterized`: 기존 동작과 계약 테스트 확보
- `Implemented`: 신규 경로 구현, 전체 패리티 검증 전
- `Parity`: 기준 시나리오 통과
- `Approved difference`: 플랫폼 차이가 승인·문서화됨
- `Blocked`: 외부 제약과 해제 조건이 기록됨

## 2. 기준선 정보

| 항목 | 고정 값 |
|---|---|
| Native commit | `76be3b50ba6f3f28ab81c58918542203c6b5933c` |
| Web commit | `870f94d13f74bf0ffc1963d39d6640658cf32cba` |
| Play 배포 기준 version | 1.2.0 / versionCode 32 (분석 시 소스) |
| Android applicationId | `com.icecream.kwklasplus` |
| Android 비교 기기/OS | API 29 phone, 최신 Android phone, Android tablet, 생체 미등록/미지원 환경 (`docs/ANDROID_REGRESSION_CHECKLIST.md`) |
| iOS 최소 버전/기기 | iOS/iPadOS 16.0, arm64, iPhone/iPad (`ADR-005`) |

## 3. 사용자 기능 패리티

| ID | 기능/화면 | 기존 기준 구현 | Android 목표 소유권 | iOS 목표 소유권 | 우선순위 | 상태 | 필수 검증 |
|---|---|---|---|---|---|---|---|
| F-001 | 콜드 스타트/네트워크 오류 | `MainActivity` | Android Compose root + 공통 startup state | SwiftUI root + reachability adapter | P0 | In progress | Compose loading root와 Activity 부착 숨김 인증 WebView 전환 완료; 오프라인/자동 로그인/timeout 문구/종료/회전·복원 실기기 회귀 대기 |
| F-002 | 최초 온보딩 | `LoginActivity` WebView `/onboarding` | Android Compose/WebView | SwiftUI/WKWebView | P1 | In progress | 온보딩 WebView를 edge-to-edge Compose `AndroidView`에 match-parent로 연결하고 적응형 시작 버튼 적용; 최초/재방문·회전·외부 약관 링크 실기기 회귀 대기 |
| F-003 | ID/PW 입력 및 동의 | `LoginActivity` XML | Android Compose login + 공통 상태 | SwiftUI login + 공통 상태 | P0 | In progress | ID/PW/동의/복구 링크를 Compose로 전환하고 동의 문구 전체 탭 토글, compact/medium 단일 열과 expanded 2열 및 반대 테마 primary/onPrimary 버튼 색상 적용; 비밀번호 비저장, IME·오류·접근성·실서버 로그인 실기기 회귀 대기 |
| F-004 | 비밀번호 서버 암호화 | `SelectScrtyPwd.do`, OkHttp | 공통 `KlasAuthApi` | 공통 `KlasAuthApi` | P0 | Implemented | 공통 PrepareCredentialUseCase/Ktor repository와 Android View 연결, 암호화 credential Keystore 저장·read-back 검증 완료; 실서버/기존 설치 실기기 로그인 미검증 |
| F-005 | Web 자동 로그인 | `MainActivity` login WebView + `appLogin.setInitial` | Android WebAuthDriver | iOS WebAuthDriver | P0 | Implemented | AndroidWebAuthDriver와 공통 URL/cookie/alert 정책 연결, JSON-safe credential 주입·15초 timeout·CAPTCHA/임시 PW 분류 구현; 실제 KLAS DOM/redirect/alert 실기기 검증 미완료 |
| F-006 | SESSION 추출/저장/복구 | `CookieManager`, `kwSESSION` | SessionCoordinator + CookieManager | SessionCoordinator + WKHTTPCookieStore | P0 | Implemented | Android 시작·cookie 관찰·로그아웃·만료를 SessionCoordinator에 연결하고 Keystore primary/legacy View 미러 적용; 실기기 신규/유효/만료/삭제/프로세스 재시작 미검증 |
| F-007 | 홈 피드/하단 탭 | `HomeActivity`, `/feed` 등 | Android Compose shell + WebView | SwiftUI shell + WKWebView | P0 | In progress | 온라인 강의·과제·팀 프로젝트 마감일 repository/날짜 정책/Web JSON을 공통화하고 Home XML root를 Compose host로 전환. WebView는 네이티브 FrameLayout 안에 유지하고 페이지 완료 시 실제 높이 변경·복원으로 Chromium viewport를 동기화하며 탭/back/reload/BottomSheet 및 실서버 날짜 fixture 실기기 검증 대기 |
| F-008 | 시간표/학기 선택 | `HomeActivity`, `/timetableTab` | 공통 bridge + Compose modal | 동일 + iOS picker | P1 | In progress | 학기/수강과목 및 시간표 repository, 저장 학기 fallback, 교시→시간/Web JSON 변환을 공통화해 Android 연결; 2026-07-18 과거 학기 누락 교수명 회귀 수정, 실기기 재검증 대기 |
| F-009 | 캘린더/날짜·시간 선택 | `HomeActivity`, `/calendar` | Material date/time adapter | iOS date/time adapter | P1 | In progress | Compose 전환 후 `75dvh` React Calendar가 IME에 의한 실제 WebView 재측정 전까지 렌더되지 않는 회귀를 확인. 네이티브 FrameLayout과 페이지 완료 시 1px 높이 변경·복원, `visualViewport` 갱신을 연결했다. 3버튼 내비게이션 모드의 Calendar Web BottomSheet + IME 조합에서는 navigation bar inset만큼 WebView를 추가 재측정해 하단 취소/확인 버튼을 키보드 위에 유지하며, 제스처 모드와 다른 탭에는 적용하지 않는다. 최초 진입 렌더링·timezone·취소·시작/종료 값 실기기 재검증 대기 |
| F-010 | 프로필/학생증 QR | `HomeActivity`, `/profile` | Web + QR value bridge | Web + QR value bridge | P1 | In progress | WebView가 URL/cookie만 발견하고 공통 repository가 허용 origin 검증·HTTP 조회·응답 파싱을 수행하도록 연결; modal/로그아웃 실기기 회귀 대기 |
| F-011 | 성적/석차/장학/KLAS AI | Web pages + session callback | Web surface | WKWebView surface | P1 | In progress | 최신 Web 기준 `3f56b56`에서 Vaul Portal의 학기 타이틀·닫기 버튼·handle은 표시되지만 `subjects.subjects.map` 스크롤 영역만 Compose 전환 후 보이지 않는 회귀를 확인. 시트 표시 후 추가 viewport pulse는 효과가 없어 롤백했으며, XML과 동일하게 Compose 단일 WebView 호스트의 네이티브 `LayoutParams`를 `MATCH_PARENT`로 고정하고 최초 유효 높이 이후 viewport를 갱신하도록 보강했다. 성적 행 표시 실기기 재검증 대기 |
| F-012 | 강의 홈 | `LectureActivity`, `/lectureHome` | Android Compose screen + WebView | SwiftUI screen + WKWebView | P0 | In progress | typed route/WebSurface/Bridge v1/다운로드·파일 picker 및 KLAS style/게시판 path script factory 연결. UI/KLAS 이중 WebView와 refresh 컨테이너를 Compose 다중 View host로 전환했으며 subj/year/session, 화면 전환, QR, refresh, back 실기기 회귀 대기 |
| F-013 | 강의계획서 | `LctPlanActivity` | 공통 Web route | 공통 Web route | P1 | In progress | typed route/WebSurface/Bridge v1과 Compose AndroidView host 연결; 검색/상세/외부 페이지 및 WebView 상태 실기기 회귀 대기 |
| F-014 | 게시판 목록/상세 | `BoardActivity` | 공통 Web route + download port | 동일 | P0 | In progress | typed route/WebSurface/Bridge v1/FileTransfer/FilePicker와 Compose refreshable AndroidView host 연결; pull refresh·첨부·mailto·fullscreen 실기기 회귀 대기 |
| F-015 | 과제/퀴즈/시험 링크 | `TaskViewActivity` | 공통 KLAS Web route | 동일 | P0 | In progress | typed Task route/WebSurface/FileTransfer/FilePicker, JSON-safe localStorage·KLAS style script와 Compose refreshable AndroidView host 연결; pull refresh·링크·영상 실기기 회귀 대기 |
| F-016 | 일반 링크 | `LinkViewActivity` | 공통 navigation + Android adapter | 공통 navigation + iOS adapter | P1 | In progress | typed Web route/WebSurface/Bridge v1과 Compose AndroidView host 연결. 공용 WebView 호스트는 시스템 바·컷아웃 inset을 먼저 소비하고 IME padding을 한 번만 적용해 단일 WebView Activity의 중복 높이 축소를 제거했다. WebView Modal과 `openCustomBottomSheet` 브리지 계약은 제거했으며 계정 복구/notice·파일·IME·전체화면 회귀 대기 |
| F-017 | 온라인 강의 재생 | `VideoPlayerActivity`, `/onlineLecture` | 전용 Android player/PIP host | iOS player host | P0 | In progress | 3개 WebSurface, metadata repository, bridge JSON/state/진도 parser, 시간 포맷과 player monitor/control script를 공통화하고 제어 UI를 Compose로 전환. 슬라이더 seek는 드래그 종료 시 한 번만 Web에 전달하며 KLAS 영상 host·재생/정지/seek/speed/진도 실기기 재검증 대기 |
| F-018 | PIP | Android `PictureInPictureParams` | Android native | AVKit/WK media spike | P1 | In progress | 공통 상태/Android port/remote action 연결. Android 12+ auto-enter로 최근 앱/제스처 전환을 보강하고 이전 버전 명시 진입 및 종료 시 Web fullscreen·phone 방향 복구를 유지하며 실기기 재검증 대기 |
| F-019 | QR 출석 | `QRScanActivity`, Google scanner | Android scanner port | AVFoundation/VisionKit port | P0 | In progress | 전처리/check-in 공통 workflow 및 공식 `AndroidQrScanner` 연결. ML Kit #1018 R8 keep, Home/Lecture single-flight 적용. Google scanner가 무스캔 종료를 일반 실패로 반환하는 경로까지 무알림 취소로 보정하고 Activity 진행 UI를 Compose로 전환했으며 취소/성공/실패 실기기 재검증 대기 |
| F-020 | 도서관 QR 조회 | `LibraryManager`, modal | 공통 API/crypto compatibility + Android UI | 공통 API + iOS UI | P1 | In progress | Ktor form gateway/workflow/XML/오류/캐시 정책 및 Android AES/Base64 adapter 완료. QR/설정 BottomSheet를 Compose로 전환하고 반대 테마 primary/onPrimary 버튼 색상, QR 원문 비보관·Bitmap 표시·밝기 복구·30초 갱신을 유지한다. 네이티브 BottomSheet는 IME 표시 중 `adjustResize`로 확보한 키보드 위 영역에서 `MATCH_PARENT` 높이로 전환하고, 설정 입력 영역만 스크롤하며 저장 버튼은 시트 최하단에 고정한다. 실기기 IME·작은 화면·실서버 재검증과 `device_gb` iOS 확인이 필요하다 |
| F-021 | 홈 화면 도서관 위젯 | `LibraryQRWidget` | AppWidgetProvider 유지/개선 | WidgetKit extension | P1 | In progress | Android 위젯 진입과 Compose QR modal 연결; FragmentManager 재생성 가능한 인자 구조, widget pin/진입 종료를 유지하며 잠금/만료/테마 실기기 재검증 대기 |
| F-022 | 앱 잠금 PIN | `LockActivity`, `AppLockManager` | 공통 policy + Android lifecycle | 공통 policy + iOS scene phase | P0 | In progress | 공통 lifecycle policy와 PIN hash/salt Keystore migration에 이어 `LockActivity`를 반응형 Compose로 전환하고 자동 제출과 중복되는 화면 확인 키를 제거. 네 가지 mode/6자리 자동 제출/물리 Enter/생체 prompt 계약을 유지했으며 기존 설치·폰/태블릿·lifecycle/위젯 예외 실기기 검증 대기 |
| F-023 | 생체인식 | `BiometricPrompt` | Android biometric port | LocalAuthentication port | P0 | In progress | Android port가 성공/취소/미등록/미지원/실패를 typed result로 반환하고 설정 활성화에 연결; 잠금 화면·실기기·iOS 미검증 |
| F-024 | 설정/테마/버전 | `SettingsActivity`, `/settings` | 공통 Web/Compose + settings repo | 동일 | P1 | In progress | WebSurface/Bridge v1/typed biometric과 Compose title/WebView host 연결; light/dark/system 재시작 persistence 및 lock modal 실기기 회귀 대기 |
| F-025 | 다운로드 | `AppDownloadManager` + DownloadManager | Android DownloadManager/SAF | URLSession/files/share | P1 | Implemented | 공통 FileTransfer 정책과 Android adapter를 4개 화면에 연결하고 진행률·파일명·취소 UI를 Compose로 전환, cookie/User-Agent/filename/MIME 보존; 성공·실패·취소 실기기 회귀 대기 |
| F-026 | 파일 선택/업로드 | 각 WebChromeClient | Activity Result adapter | document/photo picker | P1 | Implemented | 공통 FilePicker 결과와 Activity Result adapter를 4개 화면에 연결, 단일/다중/MIME/취소 구현; 실제 업로드 회귀 대기 |
| F-027 | 외부 링크/mailto/앱 | Intent | allowlist + Android Intent | allowlist + UIApplication | P1 | In progress | 공통 scheme/길이/제어문자 정책과 Android adapter 연결 완료; iOS adapter 미구현 |
| F-028 | 햅틱 | 문자열 → Android 상수 | semantic haptic adapter | UIKit feedback adapter | P2 | In progress | 14개 legacy 이름과 Android 상수 보존, 공통 semantic effect 완료; iOS adapter 미구현 |
| F-029 | Android 인앱 업데이트 | Play Core | Android 전용 capability | Approved difference | P2 | Not started | available/download/install/cancel |
| F-030 | 폰 세로/태블릿 회전 | Activity별 설정 | window policy | iOS orientation policy | P1 | In progress | ADR-005 정책에 따라 Compose compact/medium/expanded 600/840dp 경계를 구현하고 잠금 화면 1열/2열 배치 적용; 나머지 네이티브 화면과 멀티윈도우, 회전 중 WebView 상태 보존 실기기 검증 미완료 |
| F-031 | 업그레이드 데이터 이전 | 구 SharedPreferences/Encrypted prefs | 명시적 migration | 신규 설치; 향후 schema migration | P0 | In progress | credential·SESSION·PIN hash/salt 검증형 migration, legacy fallback 및 비밀 prefs backup 제외 구현; 1.1.x/1.2.0 실데이터·복원 fixture, library cache, rollback 미검증 |
| F-032 | 오류 수집/개인정보 마스킹 | Sentry Android | 공통 error taxonomy + Android Sentry | iOS Sentry/선정 도구 | P1 | In progress | 네트워크/bridge 오류 redaction과 Sentry screenshot/view hierarchy 비활성화; 알려진 키 자동 redaction/iOS 설정 미완료 |

## 4. Native → Web callback 계약

다음 이름과 인자 순서는 legacy 계약이다. 새 envelope 도입 전 삭제하거나 이름을 바꾸지 않는다.

| Callback | 인자 | 사용 화면 | 데이터 성격 |
|---|---|---|---|
| `window.receiveToken(receivedToken)` | session token | feed, calendar, grade, profile, scholarship, id card | 비밀 |
| `window.receiveDeadlineData(json)` | JSON | feed | 과제/마감 데이터 |
| `window.receiveTimetableData(data/json)` | JSON/string | feed, timetable | 시간표 |
| `window.receivedData(token, subj, yearHakgi)` | 3 strings | lecture home | token은 비밀 |
| `window.receivedData(token, subj, yearHakgi, path)` | 4 strings | board list/view | token은 비밀 |
| `window.receiveYearHakgi(value)` | string | settings/home | 일반 설정 |
| `window.receiveTheme(theme)` | `light|dark|system` | settings | 일반 설정 |
| `window.receiveVersion(version)` | string | settings | 일반 정보 |
| `window.onAppLockSettingChanged(value)` | bool 또는 settings JSON 사용처 확인 필요 | settings | 보안 설정 |
| `window.onBiometricSettingChanged(enabled)` | boolean | settings | 보안 설정 |
| `window.receiveIdCardQRValue(libraryQR, idCardQR)` | strings | profile/student ID | 민감 개인정보 |
| `window.receiveSubjList(list)` | JSON/string | agent | 강의 데이터 |
| `window.closeWebViewBottomSheet()` | 없음 | modal | UI event |

모든 callback은 신규 프로토콜에서 `KlasNativeBridge.onEvent(envelope)` 하나로 수렴시키되 legacy callback adapter를 유지한다.

## 5. Web → Native legacy 브리지 계약

구현 증거: `LegacyBridgeCatalog`에 8개 surface/64개 메서드를 고정하고 `BridgeMethodId`로 1:1 typed mapping했다. Bridge v1 JSON codec/router가 version/id/method/arguments/result/event envelope, 동기 반환, `evaluteKLASScript`, 인자 수·타입, exact origin, main-frame, UTF-8 64 KiB 제한, malformed/unknown/handler 오류를 검증·처리한다. Android는 AndroidX WebKit message listener `KlasNativeBridgeNative`를 8개 surface에 설치해 source origin/main-frame을 router에 전달하고 기존 façade로 위임한다. Web 저장소가 v1로 전환되기 전까지 `window.Android`는 병행 유지한다.

### 5.1 Home surface (`HomeActivity.JavaScriptInterface`)

| 메서드 | 의미 | 신규 command 후보 |
|---|---|---|
| `changeTab(...)` | 탭 변경 | `navigation.changeTab` |
| `evaluate(...)` | KLAS 페이지 자동화/평가 | `web.evaluateKlasPage` |
| `openPage(...)` | 인증된 KLAS/내부 페이지 | `navigation.openPage` |
| `openExternalPage(...)` | 외부 브라우저 | `platform.openExternalUrl` |
| `completePageLoad()` | 로딩 완료 | `web.pageReady` |
| `openLibraryQR()` | 도서관 QR modal | `library.openQr` |
| `openLibraryQRSettingsModal()` | 도서관 설정 | `library.openSettings` |
| `openLectureActivity(subj, name)` | 강의 홈 | `lecture.open` |
| `qrCheckIn(subj, name)` | QR 출석 | `attendance.scan` |
| `openDateTimePicker(value, isStart)` | 날짜/시간 선택 | `platform.pickDateTime` |
| `openWebViewBottomSheet(...)` | Web modal | `modal.openWeb` |
| `closeWebViewBottomSheet()` | Web modal 닫기 | `modal.close` |
| `openOptionsMenu()` | 메뉴 | `menu.open` |
| `openYearHakgiBottomSheet()` | 학기 선택 | `semester.pick` |
| `reload()` | 앱/Web 새로고침 | `web.reload` |
| `performHapticFeedback(type)` | 햅틱 | `platform.haptic` |
| `requestIdCardQRValue()` | 학생증 QR 값 요청 | `profile.requestQr` |

### 5.2 Lecture surface (`WebAppInterfaceLectureHome`)

`completePageLoad`, `openPage`, `getBoardPath`, `openBoardList`, `openBoardView`, `openExternalLink`, `evaluteKLASScript`, `openOnlineLecture`, `openLecturePlan`, `openQRScan`

`evaluteKLASScript`의 철자는 공개 legacy 계약으로 보존한다.

### 5.3 Board surface (`JavaScriptInterfaceForBoard`)

`openPage`, `openExternalLink`, `completePageLoad`

### 5.4 Lecture plan surface (`JavaScriptInterfaceLecturePlan`)

`completePageLoad`, `openPage`, `openExternalPage`

### 5.5 Link surface (`JavaScriptInterfaceForLinkView`)

`openPage`, `openLecturePlanPage`, `openWebViewBottomSheet`, `closeWebViewBottomSheet`, `completePageLoad`

### 5.6 Video surface (`VideoPlayerActivity.WebAppInterface`)

`completePageLoad`, `openExternalLink`, `openInKLAS`, `requestOnlineLecture`, `receivePlayerStates`, `receiveInitSpeed`, `receiveVideoData`, `receiveVideoURL`, `performHapticFeedback`

플레이어 state와 진도 보고 payload는 별도 fixture를 캡처해야 한다. PIP action과 웹 플레이어 event 이름도 특성 테스트 대상이다.

### 5.7 Settings surface (`JavaScriptInterfaceForSettings`)

`completePageLoad`, `changeAppTheme`, `openYearHakgiSelectModal`, `openLibraryQRSettingsModal`, `openExternalLink`, `performHapticFeedback`, `setAppLockEnabled`, `setAppLockPassword`, `setBiometricEnabled`, `getAppLockSettings`

`getAppLockSettings()`는 웹에서 동기 반환 후 즉시 `JSON.parse`한다. iOS Promise bridge로 바꿀 때 가장 먼저 깨지는 계약이므로 다음 중 하나를 선택해야 한다.

- document start에 설정 JSON을 캐시하여 legacy 동기 호출 제공
- 웹을 async `getAppLockSettings()`로 바꾸고 구 Android fallback 유지

## 6. 저장 키 호환 매트릭스

| 기존 key/file | 의미 | 신규 모델 | 이전 정책 |
|---|---|---|---|
| `com.icecream.kwklasplus/kwID` | 학번 | `AccountId` | 일반 저장소로 동일 값 이전 |
| `secure_prefs/kwPWD` | 서버 암호화 비밀번호 | `StoredCredential` | SecureStore 기록 검증 후 구값 삭제 |
| 구 일반 prefs `kwPWD` | 1.2.0 이전 비밀번호 위치 | `StoredCredential` | 구 앱과 동일하게 secure store로 우선 이전 |
| `kwSESSION` | KLAS SESSION | `Session.token` | SecureStore + WebCookieStore 동기화 |
| `kwSESSION_timestamp` | 발급/관찰 시각 | `Session.observedAt` | Long으로 파싱, 실패 시 세션 폐기 |
| `appTheme` | 테마 | `ThemePreference` | enum 검증, unknown→system |
| `yearHakgi` | 선택 학기 | `SemesterId` | 문자열 형식 검증 |
| `yearHakgiList` | 학기 목록 | `List<Semester>` | 구 직렬화 fixture 필요 |
| `library_stdNumber` | 도서관 학번 | `LibraryAccount.studentNo` | 개인정보 분류 검토 |
| `library_phone` | 전화번호 | `LibraryAccount.phone` | 개인정보 분류 검토 |
| `library_password` | 도서관 비밀번호 | `LibraryCredential` | SecureStore only |
| `library_secure_prefs/secret_*` | 도서관 암호키 | `LibrarySession.secret` | SecureStore 이전 |
| `library_secure_prefs/authKey_*` | 도서관 auth key | `LibrarySession.authKey` | SecureStore 이전/만료 정책 |
| `a_l_e`, `p_w_h`, `p_w_s`, `b_m_e` | 앱 잠금 설정 | `AppLockSettings` | legacy key reader 유지 후 schema version 기록 |

## 7. 인증 패리티 시나리오

| ID | Given | When | Then |
|---|---|---|---|
| A-001 | 저장 정보 없음 | 앱 시작 | 온보딩/수동 로그인 표시 |
| A-002 | 올바른 ID/PW | 암호화 API 성공 | 평문 미저장, 암호화 값 secure 저장, WebLogin 진입 |
| A-003 | 저장 credential, 세션 없음 | 앱 시작 | 로그인 페이지 자동 입력/제출 |
| A-004 | 1시간 내 세션 | 앱 시작 | cookie 동기화 후 홈 즉시 진입 |
| A-005 | 만료/거부 세션 | API 또는 페이지 접근 | 저장 세션 폐기 후 WebLogin |
| A-006 | 자동 로그인 성공 | SESSION cookie 생성 | secure 저장, timestamp 기록, 홈 한 번만 시작 |
| A-007 | CAPTCHA/임시 PW | 로그인 페이지 재노출/alert | 사용자 조치 안내 및 브라우저 열기 |
| A-008 | 네트워크 없음 | 앱 시작 | 명확한 오류, secret 삭제 없음 |
| A-009 | 로그인 timeout | 15초 경과 | 종료/상태 확인/재시도 제공 |
| A-010 | 앱 업그레이드 | 구 일반 prefs에 `kwPWD` 존재 | 신규 SecureStore로 무손실 이전 |
| A-011 | 로그아웃 | 사용자 확인 | Web cookie, session, 관련 localStorage 정책대로 정리 |
| A-012 | 특수문자 credential/payload | JS 전달 | 문법 오류·주입 없이 정확히 전달 |

## 8. 기능별 증거 기록 형식

행 상태를 `Parity`로 바꿀 때 아래 형식의 기록을 `TASKS.md` 해당 작업 아래 또는 PR 설명에 남긴다.

```text
Feature: F-xxx
Native baseline: <commit SHA>
Web baseline: <commit SHA>
Android build/device: <build, model, OS>
iOS build/device: <build, model, OS or N/A>
Automated tests: <commands and results>
Manual scenarios: <IDs and results>
Approved differences: <none or ADR/link>
Rollback: <flag/build/path>
```

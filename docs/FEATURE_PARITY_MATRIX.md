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
| F-001 | 콜드 스타트/네트워크 오류 | `MainActivity` | Android Compose root + 공통 startup state | SwiftUI root + reachability adapter | P0 | Not started | 오프라인, 재시도, 종료, 회전/복원 |
| F-002 | 최초 온보딩 | `LoginActivity` WebView `/onboarding` | Android Compose/WebView | SwiftUI/WKWebView | P1 | Not started | 최초/재방문, 외부 약관 링크 |
| F-003 | ID/PW 입력 및 동의 | `LoginActivity` XML | Android Compose login + 공통 상태 | SwiftUI login + 공통 상태 | P0 | Not started | 입력/IME/동의/오류/접근성 |
| F-004 | 비밀번호 서버 암호화 | `SelectScrtyPwd.do`, OkHttp | 공통 `KlasAuthApi` | 공통 `KlasAuthApi` | P0 | Implemented | 공통 PrepareCredentialUseCase/Ktor repository와 Android View 연결, 암호화 credential Keystore 저장·read-back 검증 완료; 실서버/기존 설치 실기기 로그인 미검증 |
| F-005 | Web 자동 로그인 | `MainActivity` login WebView + `appLogin.setInitial` | Android WebAuthDriver | iOS WebAuthDriver | P0 | Implemented | AndroidWebAuthDriver와 공통 URL/cookie/alert 정책 연결, JSON-safe credential 주입·15초 timeout·CAPTCHA/임시 PW 분류 구현; 실제 KLAS DOM/redirect/alert 실기기 검증 미완료 |
| F-006 | SESSION 추출/저장/복구 | `CookieManager`, `kwSESSION` | SessionCoordinator + CookieManager | SessionCoordinator + WKHTTPCookieStore | P0 | Implemented | Android 시작·cookie 관찰·로그아웃·만료를 SessionCoordinator에 연결하고 Keystore primary/legacy View 미러 적용; 실기기 신규/유효/만료/삭제/프로세스 재시작 미검증 |
| F-007 | 홈 피드/하단 탭 | `HomeActivity`, `/feed` 등 | Android Compose shell + WebView | SwiftUI shell + WKWebView | P0 | In progress | 온라인 강의·과제·팀 프로젝트 마감일 repository/날짜 정책/Web JSON을 공통화해 Android 연결; 탭 shell/back/reload 및 실서버 날짜 fixture 미검증 |
| F-008 | 시간표/학기 선택 | `HomeActivity`, `/timetableTab` | 공통 bridge + Compose modal | 동일 + iOS picker | P1 | In progress | 학기/수강과목 및 시간표 repository, 저장 학기 fallback, 교시→시간/Web JSON 변환을 공통화해 Android 연결; 2026-07-18 과거 학기 누락 교수명 회귀 수정, 실기기 재검증 대기 |
| F-009 | 캘린더/날짜·시간 선택 | `HomeActivity`, `/calendar` | Material date/time adapter | iOS date/time adapter | P1 | Not started | timezone, 취소, 시작/종료 값 |
| F-010 | 프로필/학생증 QR | `HomeActivity`, `/profile` | Web + QR value bridge | Web + QR value bridge | P1 | In progress | WebView가 URL/cookie만 발견하고 공통 repository가 허용 origin 검증·HTTP 조회·응답 파싱을 수행하도록 연결; modal/로그아웃 실기기 회귀 대기 |
| F-011 | 성적/석차/장학/KLAS AI | Web pages + session callback | Web surface | WKWebView surface | P1 | Not started | token callback, 외부/학교 페이지 이동 |
| F-012 | 강의 홈 | `LectureActivity`, `/lectureHome` | Android Compose screen + WebView | SwiftUI screen + WKWebView | P0 | In progress | typed route/WebSurface/Bridge v1/다운로드·파일 picker 및 KLAS style/게시판 path script factory 연결; subj/year/session, refresh, back 실기기 회귀 대기 |
| F-013 | 강의계획서 | `LctPlanActivity` | 공통 Web route | 공통 Web route | P1 | In progress | typed route/WebSurface/Bridge v1 연결; 검색/상세/외부 페이지 실기기 회귀 대기 |
| F-014 | 게시판 목록/상세 | `BoardActivity` | 공통 Web route + download port | 동일 | P0 | In progress | typed route/WebSurface/Bridge v1/FileTransfer/FilePicker 연결; 첨부/mailto/fullscreen 실기기 회귀 대기 |
| F-015 | 과제/퀴즈/시험 링크 | `TaskViewActivity` | 공통 KLAS Web route | 동일 | P0 | In progress | typed Task route/WebSurface/FileTransfer/FilePicker, JSON-safe localStorage와 공통 KLAS style script 연결; 링크·영상 회귀 대기 |
| F-016 | 일반 링크/Bottom sheet | `LinkViewActivity`, `WebViewModal` | 공통 navigation/modal + Android adapter | 공통 navigation/modal + iOS adapter | P1 | In progress | typed Web route/WebSurface/Bridge v1 및 계정 복구/notice/Bottom sheet script factory 연결, 외부 top-level legacy façade 제거; nested link/modal 회귀 대기 |
| F-017 | 온라인 강의 재생 | `VideoPlayerActivity`, `/onlineLecture` | 전용 Android player/PIP host | iOS player host | P0 | In progress | 3개 WebSurface, metadata repository, bridge JSON/state/진도 parser, 시간 포맷과 player monitor/control script를 공통화; KLAS 영상 host 및 실기기 재검증 대기 |
| F-018 | PIP | Android `PictureInPictureParams` | Android native | AVKit/WK media spike | P1 | In progress | 공통 상태/Android port/remote action 연결; PIP 종료 시 Web fullscreen·phone 방향 명시 복구 후 실기기 재검증 대기 |
| F-019 | QR 출석 | `QRScanActivity`, Google scanner | Android scanner port | AVFoundation/VisionKit port | P0 | Parity | 전처리/check-in 공통 workflow 및 공식 `AndroidQrScanner` 연결. ML Kit #1018 R8 keep 적용, 무스캔 종료 무알림, Home/Lecture 연속 탭 single-flight와 loading 정리까지 2026-07-19 release 실기기 검증 통과 |
| F-020 | 도서관 QR 조회 | `LibraryManager`, modal | 공통 API/crypto compatibility + Android UI | 공통 API + iOS UI | P1 | In progress | Ktor form gateway/workflow/XML/오류/캐시 정책 및 Android AES/Base64 adapter 완료; 실서버 CDATA 응답 호환 수정, 재검증과 `device_gb` iOS 확인 필요 |
| F-021 | 홈 화면 도서관 위젯 | `LibraryQRWidget` | AppWidgetProvider 유지/개선 | WidgetKit extension | P1 | In progress | Android 위젯 진입과 QR modal 연결; FragmentManager 재생성 가능한 인자 구조로 수정, 잠금/만료/테마 실기기 재검증 대기 |
| F-022 | 앱 잠금 PIN | `LockActivity`, `AppLockManager` | 공통 policy + Android lifecycle | 공통 policy + iOS scene phase | P0 | Implemented | 공통 lifecycle policy 및 Android 연결, PIN hash/salt Keystore read-through migration 완료; 기존 설치 실기기 lifecycle/위젯 예외 미검증 |
| F-023 | 생체인식 | `BiometricPrompt` | Android biometric port | LocalAuthentication port | P0 | In progress | Android port가 성공/취소/미등록/미지원/실패를 typed result로 반환하고 설정 활성화에 연결; 잠금 화면·실기기·iOS 미검증 |
| F-024 | 설정/테마/버전 | `SettingsActivity`, `/settings` | 공통 Web/Compose + settings repo | 동일 | P1 | In progress | WebSurface/Bridge v1/typed biometric 연결; light/dark/system 재시작 persistence 실기기 회귀 대기 |
| F-025 | 다운로드 | `AppDownloadManager` + DownloadManager | Android DownloadManager/SAF | URLSession/files/share | P1 | Implemented | 공통 FileTransfer 정책과 Android adapter를 4개 화면에 연결, cookie/User-Agent/filename/MIME 보존; 실패·취소 실기기 회귀 대기 |
| F-026 | 파일 선택/업로드 | 각 WebChromeClient | Activity Result adapter | document/photo picker | P1 | Implemented | 공통 FilePicker 결과와 Activity Result adapter를 4개 화면에 연결, 단일/다중/MIME/취소 구현; 실제 업로드 회귀 대기 |
| F-027 | 외부 링크/mailto/앱 | Intent | allowlist + Android Intent | allowlist + UIApplication | P1 | In progress | 공통 scheme/길이/제어문자 정책과 Android adapter 연결 완료; iOS adapter 미구현 |
| F-028 | 햅틱 | 문자열 → Android 상수 | semantic haptic adapter | UIKit feedback adapter | P2 | In progress | 14개 legacy 이름과 Android 상수 보존, 공통 semantic effect 완료; iOS adapter 미구현 |
| F-029 | Android 인앱 업데이트 | Play Core | Android 전용 capability | Approved difference | P2 | Not started | available/download/install/cancel |
| F-030 | 폰 세로/태블릿 회전 | Activity별 설정 | window policy | iOS orientation policy | P1 | In progress | ADR-005로 OS/기기/회전 정책 확정; 회전 중 WebView 상태 보존 실기기 검증 미완료 |
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
| `openCustomBottomSheet(url, cancelable)` | 커스텀 Web modal | `modal.openWeb` |
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

### 5.8 Web modal surface (`JavaScriptInterfaceForWebViewModal`)

`completePageLoad`, `closeModal`, `showToast`, `openExternalPage`, `openLibraryQR`, `openPage`

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

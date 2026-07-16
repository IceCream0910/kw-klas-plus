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
| Android 비교 기기/OS | `TBD (M0-006)` |
| iOS 최소 버전/기기 | `TBD (M2-008, ADR-007)` |

## 3. 사용자 기능 패리티

| ID | 기능/화면 | 기존 기준 구현 | Android 목표 소유권 | iOS 목표 소유권 | 우선순위 | 상태 | 필수 검증 |
|---|---|---|---|---|---|---|---|
| F-001 | 콜드 스타트/네트워크 오류 | `MainActivity` | Compose root + 공통 startup state | Compose root + reachability adapter | P0 | Not started | 오프라인, 재시도, 종료, 회전/복원 |
| F-002 | 최초 온보딩 | `LoginActivity` WebView `/onboarding` | 공통 Compose/Web surface | 공통 Compose/WKWebView | P1 | Not started | 최초/재방문, 외부 약관 링크 |
| F-003 | ID/PW 입력 및 동의 | `LoginActivity` XML | 공통 Compose login | 공통 Compose login | P0 | Not started | 입력/IME/동의/오류/접근성 |
| F-004 | 비밀번호 서버 암호화 | `SelectScrtyPwd.do`, OkHttp | 공통 `KlasAuthApi` | 공통 `KlasAuthApi` | P0 | Not started | 성공, HTTP 오류, malformed JSON, secret redaction |
| F-005 | Web 자동 로그인 | `MainActivity` login WebView + `appLogin.setInitial` | Android WebAuthDriver | iOS WebAuthDriver | P0 | Not started | DOM 완료 전후, CAPTCHA, 임시 PW, timeout |
| F-006 | SESSION 추출/저장/복구 | `CookieManager`, `kwSESSION` | SessionCoordinator + CookieManager | SessionCoordinator + WKHTTPCookieStore | P0 | Not started | 신규/유효/만료/삭제/프로세스 재시작 |
| F-007 | 홈 피드/하단 탭 | `HomeActivity`, `/feed` 등 | 공통 shell + Android WebView | 공통 shell + WKWebView | P0 | Not started | 탭, back, reload, token/data callbacks |
| F-008 | 시간표/학기 선택 | `HomeActivity`, `/timetableTab` | 공통 bridge + Compose modal | 동일 + iOS picker | P1 | Not started | 학기 persistence, 시간표 callback |
| F-009 | 캘린더/날짜·시간 선택 | `HomeActivity`, `/calendar` | Material date/time adapter | iOS date/time adapter | P1 | Not started | timezone, 취소, 시작/종료 값 |
| F-010 | 프로필/학생증 QR | `HomeActivity`, `/profile` | Web + QR value bridge | Web + QR value bridge | P1 | Not started | QR 값, modal, 로그아웃 |
| F-011 | 성적/석차/장학/KLAS AI | Web pages + session callback | Web surface | WKWebView surface | P1 | Not started | token callback, 외부/학교 페이지 이동 |
| F-012 | 강의 홈 | `LectureActivity`, `/lectureHome` | 공통 screen + Android WebView | 공통 screen + WKWebView | P0 | Not started | subj/year/session, refresh, back |
| F-013 | 강의계획서 | `LctPlanActivity` | 공통 Web route | 공통 Web route | P1 | Not started | 검색/상세/외부 페이지 |
| F-014 | 게시판 목록/상세 | `BoardActivity` | 공통 Web route + download port | 동일 | P0 | Not started | notice/pds, 첨부, mailto, 새 창 |
| F-015 | 과제/퀴즈/시험 링크 | `TaskViewActivity` | 공통 KLAS Web route | 동일 | P0 | Not started | localStorage 주입, 링크, 첨부, 영상 |
| F-016 | 일반 링크/Bottom sheet | `LinkViewActivity`, `WebViewModal` | 공통 navigation/modal + Android adapter | 공통 navigation/modal + iOS adapter | P1 | Not started | close/back, nested link, external origin |
| F-017 | 온라인 강의 재생 | `VideoPlayerActivity`, `/onlineLecture` | 전용 Android player/PIP host | iOS player host | P0 | Not started | 진도, 재생/정지, seek, 속도, 종료 보고 |
| F-018 | PIP | Android `PictureInPictureParams` | Android native | AVKit/WK media spike | P1 | Not started | 진입/복귀/remote action/진도 보존 |
| F-019 | QR 출석 | `QRScanActivity`, Google scanner | Android scanner port | AVFoundation/VisionKit port | P0 | Not started | 성공/실패/취소/권한/세션 만료 |
| F-020 | 도서관 QR 조회 | `LibraryManager`, modal | 공통 API/crypto compatibility + Android UI | 공통 API + iOS UI | P1 | Not started | secret/authKey cache, 만료, 잘못된 정보 |
| F-021 | 홈 화면 도서관 위젯 | `LibraryQRWidget` | AppWidgetProvider 유지/개선 | WidgetKit extension | P1 | Not started | 잠금/만료/갱신/테마/앱 진입 |
| F-022 | 앱 잠금 PIN | `LockActivity`, `AppLockManager` | 공통 policy + Android lifecycle | 공통 policy + iOS scene phase | P0 | Not started | 설정/변경/실패/백그라운드/위젯 예외 |
| F-023 | 생체인식 | `BiometricPrompt` | Android biometric port | LocalAuthentication port | P0 | Not started | 미등록/잠금/취소/fallback |
| F-024 | 설정/테마/버전 | `SettingsActivity`, `/settings` | 공통 Web/Compose + settings repo | 동일 | P1 | Not started | light/dark/system, restart persistence |
| F-025 | 다운로드 | `AppDownloadManager` + DownloadManager | Android DownloadManager/SAF | URLSession/files/share | P1 | Not started | cookie/auth, filename, MIME, 실패, 취소 |
| F-026 | 파일 선택/업로드 | 각 WebChromeClient | Activity Result adapter | document/photo picker | P1 | Not started | 단일/다중, 취소, 권한, MIME |
| F-027 | 외부 링크/mailto/앱 | Intent | allowlist + Android Intent | allowlist + UIApplication | P1 | Not started | http(s), mailto, 미지원 scheme |
| F-028 | 햅틱 | 문자열 → Android 상수 | semantic haptic adapter | UIKit feedback adapter | P2 | Not started | 지원/미지원 및 강도 매핑 |
| F-029 | Android 인앱 업데이트 | Play Core | Android 전용 capability | Approved difference | P2 | Not started | available/download/install/cancel |
| F-030 | 폰 세로/태블릿 회전 | Activity별 설정 | window policy | iOS orientation policy | P1 | Not started | 폰/태블릿, PIP, 회전 중 WebView 보존 |
| F-031 | 업그레이드 데이터 이전 | 구 SharedPreferences/Encrypted prefs | 명시적 migration | 신규 설치; 향후 schema migration | P0 | Not started | 1.1.x/1.2.0 fixture, rollback |
| F-032 | 오류 수집/개인정보 마스킹 | Sentry Android | 공통 error taxonomy + Android Sentry | iOS Sentry/선정 도구 | P1 | Not started | secret/token/화면 마스킹 |

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

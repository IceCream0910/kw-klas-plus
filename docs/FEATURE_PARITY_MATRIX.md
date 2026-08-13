# 기능·화면·브리지 패리티 매트릭스

## 1. 상태 표기

- `Not started`: 구현/검증 전
- `Characterized`: 기존 동작과 계약 테스트 확보
- `Implemented`: 신규 경로 구현, 전체 패리티 검증 전
- `Parity`: 기준 시나리오 통과
- `Approved difference`: 플랫폼 차이가 승인·문서화됨
- `Blocked`: 외부 제약과 해제 조건이 기록됨

## 사용자 기능 패리티

아래 상태는 **Android 패리티**를 나타낸다. iOS 구현 상태와 승인 차이는 `TASKS.md`의 M6~M8에서 추적한다. 2026-08-05 갱신에서는 Android 실기기·업그레이드·회귀 검증이 완료된 것으로 기록했다.

| ID | 기능/화면 | Android 목표 소유권 | iOS 목표 소유권 | 우선순위 | Android 상태 | 비고 |
|---|---|---|---|---|---|---|
| F-001 | 콜드 스타트/네트워크 오류 | Compose root + 공통 startup state | SwiftUI root + reachability | P0 | Parity | M5 전체 회귀 반영 |
| F-002 | 최초 온보딩 | Compose + WebView | SwiftUI + WKWebView | P1 | Parity | M5 전체 회귀 반영 |
| F-003 | ID/PW 입력 및 동의 | Compose login + 공통 상태 | SwiftUI login + 공통 상태 | P0 | Parity | 평문 비밀번호 비저장. iOS: `PlainPassword`/`SecretValue` `[REDACTED]`, UserDefaults에 평문·`kwPWD` 미저장 (`IosAuthSecurityTests`) |
| F-004 | 비밀번호 서버 암호화 | 공통 `KlasAuthApi` | 공통 `KlasAuthApi` | P0 | Parity | Android Keystore. iOS Keychain `ENCRYPTED_KLAS_PASSWORD` (`IosAuthSecurityTests` round-trip) |
| F-005 | Web 자동 로그인 | Android WebAuthDriver | iOS WebAuthDriver | P0 | Parity | iOS `IosWebAuthDriverTests`: CAPTCHA alert, 임시 비밀번호 재노출, hanging timeout, network failure |
| F-006 | SESSION 추출/저장/복구 | SessionCoordinator + CookieManager | SessionCoordinator + WKHTTPCookieStore | P0 | Parity | iOS SessionCoordinator↔Keychain↔WKHTTPCookieStore. UserDefaults에는 시각(`kwSESSION_timestamp`)만 두고 토큰(`kwSESSION`)은 저장하지 않음 (`IosAuthSecurityTests`) |
| F-007 | 홈 피드/하단 탭 | Compose shell + WebView | SwiftUI shell + WKWebView | P0 | Parity | Android 패리티 완료. iOS는 M6-006 navigation snapshot·trusted/external 분기·DEBUG back/reload. 탭 shell은 M6-009 |
| F-008 | 시간표/학기 선택 | 공통 bridge + Compose modal | 공통 bridge + iOS picker | P1 | Parity | 과거 학기 fixture 포함 |
| F-009 | 캘린더/날짜·시간 선택 | Compose/Web date-time adapter | iOS date-time adapter | P1 | Parity | IME·3버튼/제스처 내비게이션 포함 |
| F-010 | 프로필/학생증 QR | Web + QR bridge | Web + QR bridge | P1 | Parity | 허용 origin·로그아웃 포함 |
| F-011 | 성적/석차/장학/KLAS AI | WebSurface | WKWebView surface | P1 | Parity | sheet·viewport 회귀 포함 |
| F-012 | 강의 홈 | Compose + WebView | SwiftUI + WKWebView | P0 | Parity | 화면 전환·QR·refresh·back 포함 |
| F-013 | 강의계획서 | typed Web route | typed Web route | P1 | Parity | 검색·상세·외부 페이지 포함 |
| F-014 | 게시판 목록/상세 | Web route + file ports | 동일 | P0 | Parity | refresh·첨부·fullscreen 포함 |
| F-015 | 과제/퀴즈/시험 링크 | KLAS Web route | 동일 | P0 | Parity | refresh·링크·영상 포함 |
| F-016 | 일반 링크 | typed navigation + Android adapter | iOS navigation adapter | P1 | Parity | 파일·IME·전체화면 포함 |
| F-017 | 온라인 강의 재생 | Android player/PIP host | iOS player host | P0 | Parity | 재생·seek·speed·진도 포함 |
| F-018 | PIP | Android native | AVKit/WK media | P1 | Parity | remote action·상태 복구 포함 |
| F-019 | QR 출석 | Android scanner port | AVFoundation/VisionKit port | P0 | Parity | 성공·실패·취소·중복 실행 포함 |
| F-020 | 도서관 QR 조회 | 공통 API + Android UI | 공통 API + iOS UI | P1 | Parity | 캐시·밝기·IME·갱신 포함 |
| F-021 | 홈 화면 도서관 위젯 | AppWidgetProvider | WidgetKit extension | P1 | Parity | 잠금·만료·테마 포함 |
| F-022 | 앱 잠금 PIN | 공통 policy + Android lifecycle | 공통 policy + iOS scene phase | P0 | Parity | 업그레이드·위젯 예외 포함 |
| F-023 | 생체인식 | Android biometric port | LocalAuthentication port | P0 | Parity | 성공·취소·미등록·미지원 포함 |
| F-024 | 설정/테마/버전 | 공통 Web/Compose + settings | 동일 | P1 | Parity | 재시작 persistence 포함 |
| F-025 | 다운로드 | Android DownloadManager/SAF | URLSession/files/share | P1 | Parity | cookie·MIME·filename·취소 포함 |
| F-026 | 파일 선택/업로드 | Activity Result adapter | document/photo picker | P1 | Parity | 단일·다중·MIME·취소 포함 |
| F-027 | 외부 링크/mailto | allowlist + Android Intent | allowlist + UIApplication | P1 | Parity | 악성 URL 거부 포함 |
| F-028 | 햅틱 | semantic haptic adapter | UIKit feedback adapter | P2 | Parity | legacy 14개 이름 보존 |
| F-029 | Android 인앱 업데이트 | Play Core legacy 경로 유지 | Approved difference | P2 | Parity | Android 전용 기능 |
| F-030 | 폰 세로/태블릿 회전 | window size/orientation policy | iOS orientation policy | P1 | Parity | 폰·태블릿·멀티윈도우 포함 |
| F-031 | 업그레이드 데이터 이전 | 검증형 Android migration | 신규 설치/향후 schema migration | P0 | Parity | credential·SESSION·PIN·cache 포함 |
| F-032 | 오류 수집/개인정보 마스킹 | 공통 taxonomy + Android Sentry | iOS 관측 도구 | P1 | Implemented | 자동 redaction·iOS 정책은 M8-002 |

## 2. Native → Web callback 계약

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

## 3. Web → Native Bridge v1 계약

구현 증거: 최초 legacy 기준선은 8개 surface/64개 메서드로 고정했다. Compose modal 전환으로 제거된 surface를 제외한 현재 활성 계약은 `LegacyBridgeCatalog`의 7개 surface/57개 command이며 `BridgeMethodId`로 1:1 typed mapping한다. Bridge v1 JSON codec/router는 version/id/method/arguments/result/event envelope, `evaluteKLASScript`, 인자 수·타입, exact origin, main-frame, UTF-8 64 KiB 제한, malformed/unknown/handler 오류를 검증·처리한다.

Web 페이지와 Native 자동화 스크립트는 `KlasNativeBridge.*`를 호출하고 adapter가 `KlasNativeBridgeNative.postMessage` Bridge v1을 사용한다. Next.js가 없는 KLAS 페이지에는 Android·iOS가 같은 adapter를 document-start에 주입한다. iOS는 `WKScriptMessageHandlerWithReply`와 WebKit transport shim으로 Android와 동일한 `postMessage`/`onmessage` 계약을 맞춘 뒤 공통 `JsonBridgeRouter`로 검증·라우팅한다. `window.Android`는 구 Android 앱 fallback으로만 Web 저장소 adapter 내부에 남는다. 신 Android·iOS 앱은 `Android` JS 객체를 등록하지 않으며, 허용 origin과 main-frame 정보를 공통 router에 전달한다. 활성 Web 호출 이름은 Native 계약 테스트에서 카탈로그와 대조한다.

Web의 `/modal/idCard`, `/modal/agreePolicy`에 남은 `closeModal()`은 Compose 전환으로 제거된 WebView modal 전용 계약이며 현재 Native 화면에서 로드하지 않는다.

### 3.1 Home surface (`HomeBridgeDelegate`)

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

### 3.2 Lecture surface (`LectureBridgeDelegate`)

`completePageLoad`, `openPage`, `getBoardPath`, `openBoardList`, `openBoardView`, `openExternalLink`, `evaluteKLASScript`, `openOnlineLecture`, `openLecturePlan`, `openQRScan`

`evaluteKLASScript`의 철자는 공개 legacy 계약으로 보존한다.

### 3.3 Board surface (`BoardBridgeDelegate`)

`openPage`, `openExternalLink`, `completePageLoad`

### 3.4 Lecture plan surface (`LecturePlanBridgeDelegate`)

`completePageLoad`, `openPage`, `openExternalPage`

### 3.5 Link surface (`LinkBridgeDelegate`)

`openPage`, `openLecturePlanPage`, `openWebViewBottomSheet`, `closeWebViewBottomSheet`, `completePageLoad`

### 3.6 Video surface (`VideoBridgeDelegate`)

`completePageLoad`, `openExternalLink`, `openInKLAS`, `requestOnlineLecture`, `receivePlayerStates`, `receiveInitSpeed`, `receiveVideoData`, `receiveVideoURL`, `performHapticFeedback`

플레이어 state와 진도 보고 payload는 별도 fixture를 캡처해야 한다. PIP action과 웹 플레이어 event 이름도 특성 테스트 대상이다.

### 3.7 Settings surface (`SettingsBridgeDelegate`)

`completePageLoad`, `changeAppTheme`, `openYearHakgiSelectModal`, `openLibraryQRSettingsModal`, `openExternalLink`, `performHapticFeedback`, `setAppLockEnabled`, `setAppLockPassword`, `setBiometricEnabled`, `getAppLockSettings`

`getAppLockSettings()`는 Web adapter의 Promise를 기다리고 Bridge v1 result를 파싱한다. 구 Android 앱에서는 adapter가 legacy 동기 반환을 Promise 결과로 변환한다.

## 4. 저장 키 호환 매트릭스

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

## 5. 인증 패리티 시나리오

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

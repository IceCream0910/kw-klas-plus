# Android 공통화 완료 감사

## 기준과 범위

- Native 기준: `76be3b50ba6f3f28ab81c58918542203c6b5933c`
- Web 기준: `870f94d13f74bf0ffc1963d39d6640658cf32cba`
- 감사 범위: Compose UI 재구현 및 iOS 구현을 시작하기 전 `androidApp`의 네트워크, 응답 파싱, 도메인 정책, Web 자동화와 플레이어 상태 처리
- 완료 기준: Android 앱 계층이 HTTP client나 응답 parser를 직접 소유하지 않고, iOS가 동일한 repository·DTO·script factory를 재사용할 수 있어야 한다.

## 공통 모듈로 이동한 책임

| 기능 | 공통 책임 | Android 책임 |
|---|---|---|
| 인증·세션 | 인증 상태/유스케이스, KLAS 요청, 오류 모델, 세션 정책 | `shared/androidMain`: SharedPreferences/CookieManager/Keystore adapter, `androidApp`: WebView 로그인 driver |
| 학기·시간표·마감일 | 요청/DTO/파싱/Web JSON, 학기 값 검증 | 기본 User-Agent 제공, UI 상태 반영 |
| QR 출석 | 사전 조회/check-in workflow, payload codec | Google Code Scanner와 Activity result |
| 도서관 QR | Ktor form 요청, XML 파싱, workflow, 캐시 identity/만료 | `shared/androidMain`: AES/Base64와 SharedPreferences cache, `androidApp`: QR bitmap/widget |
| 학생증 QR | 허용 origin, cookie 포함 HTTP 조회, 응답 script 파싱 | 숨은 WebView로 실제 요청 URL과 cookie 발견 |
| 온라인 강의 | metadata 조회/HTML title 파싱, bridge JSON/state/진도 파싱, 시간 포맷, Web/player script factory | WebView lifecycle, media key, PIP와 화면 방향 |
| Web 자동화 | KLAS 화면 style, 게시판 path, 복구 페이지, player monitor/seek/control script | Android WebView에서 `WebScript` 실행 |
| URL·저장 키 | KLAS URL과 legacy preference key의 단일 계약 | 기존 상수 이름의 호환 facade |

`androidApp`의 직접 OkHttp, Jsoup, `org.json`, XML pull parser 의존성과 사용하지 않는 request helper는 제거했다. Ktor client와 timeout/engine 구성 및 repository/use case/storage 조립은 `shared/androidMain`의 `AndroidSharedDependencies`가 소유하며 앱에는 공통 기능과 UI 수명주기 adapter factory만 노출한다.

## Android에 남겨야 하는 경계

- `WebView.loadUrl`, `shouldInterceptRequest`: 플랫폼 WebView 탐색과 화면 수명주기
- `DownloadManager`, file picker, 외부 Intent: Android 시스템 기능
- Google Code Scanner, BiometricPrompt, PIP, AppWidget: Activity/UI 수명주기 Android SDK 기능
- `AndroidDeadlineDateParsers`: 로컬 시간대를 epoch로 변환하는 작은 플랫폼 adapter
- `evaluteKLASScript(script)`: 고정 Web 계약의 호환 API. trusted top-level surface에서만 노출하며 임의의 네이티브 문자열 결합에는 사용하지 않는다.

이 경계들은 iOS에서 동일 도메인 계약의 작은 adapter로 구현한다. HTTP 요청, XML/JSON/HTML 응답 파싱, 캐시 정책과 Web/player script를 Swift로 재작성하지 않는다.

## 자동 감사와 검증

다음 검색은 Android 앱 계층에서 직접 HTTP/응답 parser가 다시 유입되는지 확인한다.

```powershell
rg -n 'okhttp3|OkHttpClient|Request\.Builder|FormBody|RequestBody|Jsoup|org\.json|JSONObject|JSONArray|XmlPullParser|HttpURLConnection|openConnection' androidApp/src/main/kotlin androidApp/build.gradle.kts
```

직접 `evaluateJavascript`는 `WebScript` executor와 legacy `evaluteKLASScript` 호환 지점만 허용한다.

```powershell
rg -n 'evaluateJavascript\(' androidApp/src/main/kotlin -g '*.kt'
```

2026-07-19 최종 구조 재감사 결과:

- 직접 HTTP/응답 parser 검색 결과 0건
- 직접 JS 실행 3건: 공통 `WebScript` executor 2건, 공개 계약인 `evaluteKLASScript` 호환 전달 1건
- `shared/androidMain`의 `androidApp` import 0건
- `androidApp/platform` 루트 파일 0건, biometric/bridge/file/navigation/PIP/QR/web 기능별 패키지로 분리
- `:shared:testAndroidHostTest` 147개, 실패 0
- `:androidApp:testDebugUnitTest`, `:androidApp:compileDebugAndroidTestKotlin`, `:androidApp:assembleDebug`, R8 `:androidApp:assembleRelease` 통과

## 다음 단계 진입 조건

공통화 코드 기준 Android debug/release 빌드와 실기기 일괄 회귀를 통과했다. Android Compose와 iOS SwiftUI는 Android-only 구현을 직접 참조하지 않고 `shared`의 공통 상태·유스케이스·port를 통해 연결한다. WebView/WKWebView와 UI 어댑터는 각 플랫폼 앱이 소유한다.

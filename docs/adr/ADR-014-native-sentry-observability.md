# ADR-014: Native Sentry 오류·운영 로그의 수집 경계

- 상태: Accepted; 실기기 crash 및 배포 심볼 업로드 검증 별도
- 날짜: 2026-10-09

## 결정

Android의 `sentry-android:8.20.0`과 `kw-klas-plus-android` 프로젝트를 유지한다. iOS 앱에 Swift Package `sentry-cocoa:9.30.1`의 `Sentry` 제품과 `kw-klas-plus-ios` 프로젝트를 연결한다. SDK 최소 iOS 15는 앱의 iOS 16 최소 버전 범위 안에 있다. Cocoa SDK는 Swift 6 도구 체인을 사용하므로 Xcode CI 조합에서 검증해야 한다. 위젯 확장은 연결하지 않는다. Swift Package 버전은 `Package.resolved`로 고정하고 최종 Archive의 SDK 개인정보 Manifest를 확인한다.

초기화는 Android Application과 SwiftUI App이 각각 소유한다. DSN은 공개 이벤트 수신 주소이며 인증 토큰은 저장소에 넣지 않는다. 개발·운영 환경과 `bundle-id@version+build` 릴리스 식별자를 구분한다. Errors와 구조화 Logs를 사용한다. Tracing·Profiling·Session Replay나 콘솔/OSLog/Logcat 자동 전달은 추가하지 않는다.

## 데이터 경계

일반 문자열 logger를 앱 코드에 공개하지 않고 고정 enum을 사용한다. `beforeSendLog`는 허용된 본문만 통과시키고 모든 원래 속성을 버린 뒤 `app.event`, `app.platform`, `sentry.release`, `sentry.environment`만 구성한다. 사용자 식별자·URL·쿠키·학생 정보·비밀번호·토큰·브리지 payload·예외 문자열은 받지 않는다.

`beforeSend`는 사용자·요청·message·extra·tags·breadcrumb·임의 context를 제거하고 예외 값은 `[redacted]`로 바꾼다. 아래의 내부 진단 이벤트는 별도의 안전한 요약을 구성한다. 예외 타입과 stack trace, SDK가 제공하는 app/device/os/runtime 진단은 유지한다. 스크린샷·view hierarchy는 꺼 두고 iOS 자동 breadcrumb·네트워크 추적·실패 요청 수집도 끈다. Android breadcrumb는 전송 전에 버린다. 자동 오류 수집을 로그로 중복 보고하지 않는다.

## 로그 예제

```kotlin
SentryTelemetry.log(TelemetryEvent.REMINDER_SCHEDULE_REJECTED)
SentryTelemetry.log(TelemetryEvent.REMINDER_REFRESH_FAILED)
```

```swift
SentryTelemetry.log(.reminderScheduleRejected)
SentryTelemetry.log(.reminderRefreshExpired)
```

- `app.initialized` (info): 환경·릴리스별로 SDK 초기화가 완료된 앱 실행을 확인한다. 프로세스 초기화 시 한 번 기록한다.
- `reminder.schedule_rejected` (warn): OS가 백그라운드 알림 작업 등록을 받아들이지 않아 이후 갱신이 실행되지 않는 이유를 찾는다.
- `reminder.refresh_failed` (error): OS가 시작한 갱신 작업의 실패를 찾는다. 예상된 취소를 실패로 기록하지 않는다.
- `reminder.refresh_expired` (warn, iOS): OS 실행 시간 만료로 갱신이 취소되는 빈도를 확인한다.

매 화면 렌더링·재생 tick·API payload를 로그로 남기지 않는다. 새 이벤트는 실제 운영 질문과 고정 이름·심각도를 정하고 양 플랫폼 허용 목록과 필터 테스트를 갱신한다.

## 검증 및 배포

Android JVM 및 iOS host 테스트는 임의 본문 차단, scope 속성 제거, 오류의 민감 context 제거를 검증한다. XCTest host는 SDK 초기화를 건너뛰어 테스트에서 실제 전송하지 않는다. iOS 별도 Debug 앱을 `-sentry-smoke-test` 인자로 실행하면 비민감 검증 오류와 초기화 로그를 확인할 수 있다. 이 경로는 Release에서 컴파일되지 않는다. 기존 로그인 데이터가 없는 전용 기기를 사용한다.

시뮬레이터·빌드 성공만으로 실기기 crash 수집이나 심볼 해석을 검증했다고 간주하지 않는다. 배포 전 dSYM/Android mapping 업로드와 App Privacy·개인정보처리방침의 Sentry 오류/로그 수집 설명을 확인한다. 서버 스크러빙은 클라이언트 필터를 대체하지 않는다.

### 2026-10-09 검증 결과

- Android `:androidApp:testDebugUnitTest :androidApp:assembleDebug --no-configuration-cache`: JVM 41개(신규 필터 3개 포함)·빌드 통과.
- iOS `iosAppUnitTests/SentryTelemetryTests`: 3개 통과. Debug·Release 시뮬레이터 빌드 통과.
- iOS 앱·위젯·Sentry SDK의 개인정보 Manifest 포함 및 Release에서 검증 오류 인자 제외 확인.
- 같은 Debug 시뮬레이터 설정의 앱 debug dylib는 SDK 추가 전 26.93 MiB에서 32.03 MiB로 증가했다(+5.10 MiB). 실제 App Store 다운로드 크기는 별도 확인이 필요하다.
- iOS 전용 시뮬레이터에서 검증 오류와 `app.initialized` 로그 수신 확인. 오류 값은 `[redacted]`, 사용자 0명·첨부 0개이며 릴리스는 `2.0.0 (1)`이다.
- Android는 컴파일된 앱 logger·필터를 SDK core JVM 실행으로 호출해 `app.initialized` 로그 수신 확인. 실제 Android Application 초기화·실기기 crash는 미검증이다.
- 양 플랫폼 수신 로그의 사용자 속성 제거와 플랫폼·릴리스·환경 속성 확인.
- iOS 검증 오류의 debug dylib dSYM 부재가 Sentry에 보고됐다. 같은 UUID의 dSYM은 로컬 생성했으나 업로드 인증 토큰이 없어 업로드하지 않았다. 배포 시 해당 Archive의 dSYM을 올려 심볼 해석을 검증해야 한다.

배포마다 실제 배포 바이너리와 일치하는 심볼을 다음 목적지에 등록한다. 이전 빌드의 심볼이나 다시 빌드한 파일로 대체하지 않는다.

| 플랫폼 | 파일 | 목적지 |
|---|---|---|
| Android | R8/ProGuard `mapping.txt` | Sentry `kw-klas-plus-android`, Google Play Console |
| Android | 포함된 native `.so`의 debug symbols | Sentry, Google Play Console; 해당 라이브러리의 심볼 제공 여부 확인 |
| iOS | 앱·확장 및 필요한 framework의 dSYM | Sentry `kw-klas-plus-ios`, App Store Connect 배포 시 심볼 포함 |

iOS 심볼은 Google Play Console에 올리지 않는다. Android AAB의 mapping/native symbols 자동 포함 여부를 배포 산출물에서 확인하며, 자동 포함되지 않은 파일은 해당 버전에 맞춰 따로 업로드한다. 업로드 완료 후 새 오류의 함수명·파일·줄 번호 해석을 확인한다.

### Release 빌드의 자동 심볼 업로드 (#115)

- 추적: [#115](https://github.com/IceCream0910/kw-klas-plus/issues/115)
- 기준 Native: `5533dffc4b6b906641ef41c5de4b36a0779a606b`
- 호환 WebView: 로컬 `ef68c0f51b00d118449060f1331e6f54b9b8d652`. 인증·저장 키·브리지 변경이 없어 기존 호환 버전을 유지한다.

Android는 기존 Sentry Gradle 플러그인을 사용하며 `assembleRelease`·`bundleRelease`에서 R8/ProGuard 매핑과 native symbols를 자동 업로드한다. ProGuard UUID 생성·앱 메타데이터 삽입과 소스 미포함 정책은 유지한다. Debug는 난독화하지 않으며 업로드 대상에서 제외한다. `.so`에 공급되지 않은 디버그 정보는 업로드 설정으로 복구되지 않는다.

iOS는 device Release 빌드의 마지막 `Upload Sentry Symbols` 단계에서 `iosApp/scripts/upload-sentry-symbols.sh`를 실행한다. Release dSYM을 입력 의존성으로 선언해 생성 완료를 기다리고, 동일 빌드의 `DWARF_DSYM_FOLDER_PATH`를 재귀 검색해 앱·위젯·포함된 framework의 가용 dSYM을 `kw-klas-plus-ios`로 업로드한다. Debug와 시뮬레이터는 생략하며 소스 코드는 포함하지 않는다. dSYM 생성 설정과 script sandbox 비활성화 설정은 기존 값을 사용한다.

양 플랫폼의 로컬 빌드는 프로젝트 루트의 `sentry.properties`에 `auth.token=발급받은_토큰`을 설정해 사용한다. Android Gradle 플러그인은 이 파일을 자동 탐색하며 iOS 스크립트는 같은 파일을 `SENTRY_PROPERTIES`로 전달하므로 Android Studio·Xcode GUI에서도 셸 환경 변수 없이 동작한다. 이 파일은 Git에서 제외하고 권한을 `600`으로 유지한다. 빌드 환경의 `SENTRY_AUTH_TOKEN`도 사용할 수 있다. iOS 로컬 빌드에는 `brew install getsentry/tools/sentry-cli`가 필요하다. 토큰 값은 저장소·명령 인자·로그에 넣지 않는다.

자동 업로드는 로컬 빌드에서만 사용한다. `.github/workflows/release.yml`은 Android 업로드를 기존처럼 비활성화하고 iOS archive에 `SENTRY_AUTO_UPLOAD=false`를 전달한다. GitHub Actions에 Sentry 토큰이나 CLI 설치는 필요 없다. 로컬 iOS 업로드 스크립트는 자격증명 설정 파일·환경 변수 부재, CLI·앱 dSYM 누락 또는 업로드 실패를 빌드 실패로 전달한다. 파일에 토큰이 비어 있거나 잘못된 경우에는 CLI 인증 단계에서 실패한다.

업로드 없는 검증 빌드는 Android `-PsentryAutoUpload=false`, iOS `SENTRY_AUTO_UPLOAD=false`를 사용한다. iOS는 Xcode build setting 인자 또는 빌드 환경 변수로 전달할 수 있다. Android의 UUID·매핑 생성과 iOS dSYM 생성은 생략하지 않는다. 롤백은 자동 업로드 관련 변경을 되돌리거나 이 옵션으로 업로드를 비활성화하며, 보관한 해당 빌드 산출물로 수동 업로드한다.

로컬 검증: `./gradlew :androidApp:assembleRelease :androidApp:bundleRelease --dry-run --offline --no-daemon` 성공 및 양 업로드 task 연결 확인. `plutil -lint`, `sh -n`, `git diff --check` 통과. 임시 mock CLI로 Debug/시뮬레이터/명시적 생략, 토큰·dSYM 누락, 정상 호출(공백 포함 경로), 업로드 실패 전파와 로컬 properties 파일 전달, Xcode 단계 순서·Release dSYM 입력 의존성을 확인했다. 실제 업로드 검증 결과는 아래에 별도로 기록한다.

2026-11-08 로컬 실제 업로드 검증:

- `./gradlew :androidApp:assembleRelease :androidApp:bundleRelease --offline --no-daemon --console=plain` 성공. 두 업로드 task가 자동 실행됐고 Sentry CLI가 ProGuard 매핑 1개(`ca09f0b6-9036-310b-9fb6-946ea14f70e9`)와 native debug information 12개를 `UPLOADED`로 확인했다. APK·AAB의 ProGuard UUID도 업로드 매핑과 일치한다.
- 최신 로컬 iOS `2.0.1(3)` Archive의 `dSYMs`를 대상으로 동일 업로드 스크립트를 실행해 앱(`b8ec823c-70bb-3a21-ae38-3f49f67b277d`)·위젯(`9de2e84a-5169-34da-95ad-c20b1b9f49e1`) arm64 dSYM 2개의 `UPLOADED`와 종료 코드 0을 확인했다. 새 Xcode Archive 빌드 전체를 실행한 검증은 아니다.
- 양 플랫폼이 루트 `sentry.properties`를 읽어 업로드했으며 토큰 값은 출력하지 않았다. 새 실기기 crash의 symbolication과 Xcode build phase를 포함한 새 Archive 검증은 별도다.

필요하면 해당 배포 Archive를 다음과 같이 수동 업로드한다.

```sh
SENTRY_ORG=yun-taein SENTRY_PROJECT=kw-klas-plus-ios \
  sentry-cli debug-files upload /path/to/archive.xcarchive/dSYMs
```

## 롤백

이번 SDK·초기화·계측·문서 변경을 되돌린다. Logs만 중지하려면 Android `options.logs.isEnabled`, iOS `options.enableLogs`를 false로 설정한다. Android 자동 초기화를 다시 사용할 때는 Application의 수동 초기화를 함께 제거해 이중 초기화를 피한다. 인증·저장 키·브리지 계약과 기존 사용자 데이터는 변경하지 않는다.

### KLAS 작업 실패 진단 (#120)

`klas.operation_failed`는 요청·선택 학기 과목 파싱·할 일 응답 및 날짜 변환·WebView 전달·암호화·로그인·세션 저장/복원/연장 중 실패한 단계를 기록한다. 학사 Native API는 고정된 endpoint 이름을 사용하고, 임의 경로를 받는 Native API 브리지는 GET/POST 종류만 기록한다. HTTP 상태, 경과 시간, 할 일 조회 성공·실패 과목 수는 허용 범위의 정수로만 보낸다. 세션 만료·자격증명 오류·CAPTCHA·추가 인증은 info, 나머지 진단은 warn이다. 취소와 오래된 요청의 폐기는 실패로 수집하지 않는다.

동일 단계·실패·endpoint·HTTP 상태는 프로세스별 60초에 한 번만 남기며, 같은 기간 최대 100개 키를 추적한다. 양 플랫폼의 로그 전송 필터가 고정 식별자와 단계별 필드만 다시 검증한다. 요청·응답 원문, URL, 학번, 토큰, 과목 식별자·이름, 예외 문자열은 이 이벤트의 입력이 아니다. 기존 오류 이벤트의 필터와 SDK 자동 수집 정책은 유지한다. 로그의 롤백은 계측과 이 이벤트의 로그 필터를 되돌린다.

### 오류 이벤트에 표시하는 안전한 요약 (#120)

알려진 운영 실패는 같은 진단 필드로 Sentry 오류 이벤트에도 기록한다. 예를 들어 `KLAS DEADLINE_DATE [TASK_DEADLINES]: MALFORMED` 또는 `KLAS LOGIN_CONFIRM: TIMEOUT`으로 표시한다. `beforeSend`는 내부 이벤트 표식과 허용 목록을 확인하고, 요약·태그·그룹 키를 다시 만든다. HTTP 상태는 요약과 태그에 표시하되 그룹 키에는 넣지 않는다. 예외 원문은 이 경로에서도 복원하지 않으며, 일반 오류의 예외 값은 계속 `[redacted]`로 남긴다.

세션 만료·잘못된 자격증명·CAPTCHA·추가 인증 및 할 일 조회의 단순 집계 결과는 Logs에만 남긴다. 저장소 오류를 제외한 할 일 집계 이벤트는 개별 API·파싱 실패를 중복 보고하지 않는다. 신규 오류 이벤트와 로그는 기존 60초 수집 제한을 공유한다. 이 변경만 되돌려도 기존 Logs는 유지된다.

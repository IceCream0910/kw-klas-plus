# ADR-014: Native Sentry 오류·운영 로그의 수집 경계

- 상태: Accepted; 실기기 crash 및 배포 심볼 업로드 검증 별도
- 날짜: 2026-10-09

## 결정

Android의 `sentry-android:8.20.0`과 `kw-klas-plus-android` 프로젝트를 유지한다. iOS 앱에 Swift Package `sentry-cocoa:9.30.1`의 `Sentry` 제품과 `kw-klas-plus-ios` 프로젝트를 연결한다. SDK 최소 iOS 15는 앱의 iOS 16 최소 버전 범위 안에 있다. Cocoa SDK는 Swift 6 도구 체인을 사용하므로 Xcode CI 조합에서 검증해야 한다. 위젯 확장은 연결하지 않는다. Swift Package 버전은 `Package.resolved`로 고정하고 최종 Archive의 SDK 개인정보 Manifest를 확인한다.

초기화는 Android Application과 SwiftUI App이 각각 소유한다. DSN은 공개 이벤트 수신 주소이며 인증 토큰은 저장소에 넣지 않는다. 개발·운영 환경과 `bundle-id@version+build` 릴리스 식별자를 구분한다. Errors와 구조화 Logs를 사용한다. Tracing·Profiling·Session Replay나 콘솔/OSLog/Logcat 자동 전달은 추가하지 않는다.

## 데이터 경계

일반 문자열 logger를 앱 코드에 공개하지 않고 고정 enum을 사용한다. `beforeSendLog`는 허용된 본문만 통과시키고 모든 원래 속성을 버린 뒤 `app.event`, `app.platform`, `sentry.release`, `sentry.environment`만 구성한다. 사용자 식별자·URL·쿠키·학생 정보·비밀번호·토큰·브리지 payload·예외 문자열은 받지 않는다.

`beforeSend`는 사용자·요청·message·extra·tags·breadcrumb·임의 context를 제거하고 예외 값은 `[redacted]`로 바꾼다. 예외 타입과 stack trace, SDK가 제공하는 app/device/os/runtime 진단은 유지한다. 스크린샷·view hierarchy는 꺼 두고 iOS 자동 breadcrumb·네트워크 추적·실패 요청 수집도 끈다. Android breadcrumb는 전송 전에 버린다. 자동 오류 수집을 로그로 중복 보고하지 않는다.

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

심볼 업로드는 빌드별로 다음 명령을 실행하며 인증 토큰은 로컬 환경 또는 CI secret으로만 제공한다. 현재 Xcode/GitHub CI에 자동 업로드 단계는 추가하지 않았다.

```sh
SENTRY_ORG=yun-taein SENTRY_PROJECT=kw-klas-plus-ios \
  sentry-cli debug-files upload /path/to/archive.xcarchive/dSYMs
```

## 롤백

이번 SDK·초기화·계측·문서 변경을 되돌린다. Logs만 중지하려면 Android `options.logs.isEnabled`, iOS `options.enableLogs`를 false로 설정한다. Android 자동 초기화를 다시 사용할 때는 Application의 수동 초기화를 함께 제거해 이중 초기화를 피한다. 인증·저장 키·브리지 계약과 기존 사용자 데이터는 변경하지 않는다.

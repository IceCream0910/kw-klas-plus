# 테스트와 release 승격

## GitHub 검사

| 시점 | 검사 | 실패하면 |
| --- | --- | --- |
| 브랜치 push, PR, merge queue | JDK 21/Linux에서 `:androidApp:testDebugUnitTest`와 `:shared:testAndroidHostTest` | 기존 main 필수 검사에서 병합 차단 |
| main push 또는 수동 실행 | Android 에뮬레이터와 iPhone 시뮬레이터에서 Tester Army, Android instrumentation, Kotlin iOS·Swift·XCTest UI | release 승격 차단 |
| 위 릴리스 검증과 병렬 | Android Release APK/AAB·R8/Sentry UUID, unsigned iOS archive·위젯·dSYM | release 승격 차단 |

main ruleset **22331611**의 필수 검사 이름 `Android build and unit tests`, `iOS build and unit tests`를 유지한다. 두 번째 이름은 기존 규칙과의 호환용이며 실행 내용은 공통 KMP와 Android adapter JVM 테스트다. release ruleset **25774154**에서 요구하는 `Android release build`, `iOS release build`도 유지한다. 시뮬레이터 검사까지 통과한 해당 SHA만 승격 작업에서 다룬다.

승격은 `main`에서만 실행한다. 실행 중 main이 갱신되면 이전 실행은 push하지 않고 최신 실행에 맡긴다. 검증한 SHA가 release에서 fast-forward 되는 경우에만 일반 push한다. 승격 job에만 `contents: write`가 필요하며, GitHub Actions 토큰은 브랜치 규칙을 우회하지 않는다. PR 브랜치에 변경을 올린 뒤 Actions의 **Release validation → Run workflow**에서 그 브랜치를 선택하면 승격 없이 GitHub 실행 환경을 먼저 검증할 수 있다. release가 성공한 뒤에도 스토어 서명·제출은 별도 절차다.

## 테스트 정리 기준

기준 Native: `343abf53cd09978d54468d62700bd1f2c9fb7e79`. WebView 기준 소스: 원격 main `8f33e1241f142868d509ac52ed4ea5ccf654f627`, 로컬 동반 변경 `5834c25`. E2E는 앱이 실제 접속하는 배포 WebView를 테스트하며 해당 서비스의 배포 커밋을 고정하지 않는다.

- Android/JVM과 KMP: 인증 실패·저장소 이전·세션 만료·브리지 스키마와 권한·도메인 로직을 유지한다. 브랜치 검사에는 OS 기기, 외부 KLAS 계정, 배포 WebView를 넣지 않는다. Gradle 의존성은 다운로드할 수 있어야 한다.
- Android instrumentation: Compose의 키보드·레이아웃·설정 상태와 Keystore·위젯은 실제 OS 및 컴포넌트에 맞닿아 있어 유지한다. 샘플 산술·패키지명 테스트는 삭제했다.
- iOS XCTest: Swift와 Kotlin의 플랫폼 테스트는 시뮬레이터 쪽에 둔다. 앱의 WebKit·Keychain·WidgetKit·회전·Dynamic Type 검증은 유지한다. 원격 영상은 이미 명시적으로 켤 때만 테스트한다. 비동기 작업은 고정 시간 후 결과를 단정하는 대신 화면 상태·이벤트나 테스트가 제어하는 완료 신호를 기다린다.
- Reviewer 데모의 로그인은 기존 설계대로 일반 계정의 로그인 후 설정·약관 흐름을 생략한다. 해당 퍼널의 분기·동의·건너뛰기는 기존 플랫폼 테스트에서 별도로 확인한다.

## 로컬에서 E2E 실행

Node 26과 테스트 전용 기기를 준비하고 Debug 앱을 설치한다. 시나리오 시작 시 앱 데이터를 초기화하므로 개인 계정이 들어 있는 기기에서 실행하지 않는다.

```sh
npm ci --prefix e2e
E2E_ANDROID_DEVICE=emulator-5554 npm test --prefix e2e -- --target android
E2E_IOS_DEVICE=<simulator-udid> npm test --prefix e2e -- --target ios
```

E2E는 최초 소개 화면, 데모 비밀번호 오류, 성공 로그인, WebView 피드에 나타나는 가상 과목, 재실행 후 인증 복원, Android 로그아웃 취소 및 양 플랫폼 로그아웃 확정 및 다음 실행에서도 로그아웃 유지 여부를 확인한다. 심사용 가상 데이터만 사용한다. AI 모델이나 개인 학번·비밀번호가 필요하지 않다. UI에서 관찰한 상태를 기준으로 진행하며 테스트 전체를 자동 재시도하지 않는다. 실패하면 `e2e/.e2e`의 JUnit과 화면 추적을 확인한다.

배포 WebView 자체가 응답하지 않으면 main 병합은 가능하지만 release 승격은 멈춘다. 실계정 로그인, 추가 인증, 권한·위젯·PiP 실기기 검증 및 스토어 배포는 별도다. 복구가 필요하면 먼저 release workflow의 승격 job을 비활성화하거나 관련 PR을 되돌린다. release를 이미 승격했다면 강제 push 대신 수정 커밋을 main에 반영하고 검증 후 다시 승격한다.

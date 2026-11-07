# #113 데모 모드 검증 기록

기준 Native: `5533dffc4b6b906641ef41c5de4b36a0779a606b`.
호환 WebView: `IceCream0910/kw-klas-plus-webview@ef68c0f51b00d118449060f1331e6f54b9b8d652`.

## 자동 검증

```sh
./gradlew :shared:allTests :androidApp:testDebugUnitTest :androidApp:compileDebugAndroidTestKotlin --offline
```

최종 공통 Android 339개/iOS simulator 369개, Android JVM 45개 통과. Android instrumentation 컴파일 통과. 추가 Repository 회귀 테스트 이후 `./gradlew :shared:allTests --offline`도 통과했다. 한 실행에서 기존 `KlasApiCoreTest.queuedRequestsCannotExecuteOnAnotherAccount`가 대기해 해당 worker를 종료했고 전체 재실행은 통과했다. 기존 동시요청 테스트 코드는 변경하지 않았다.

검증 대상:

- 예약 계정의 실제 암호화·인증 차단, 일반 세션/cookie 무접근, 세션 복원·로그아웃.
- 실제 HTTP 호출 없이 fixture 응답, 일반 engine 위임, 미지원 endpoint·종료된 가상 토큰 차단.
- Native 브리지 요청→chunk 응답 재조립, 강의 홈 필드 계약, Native 동작 전달, 학교 링크 차단.
- 가상 캘린더 CRUD·과제 제출·강의 진행률·재로그인 초기화.
- 같은 세션의 조회 시각을 2025·2030·2100년으로 이동한 시작/마감일 생성과 강의 홈 응답 일관성. 2030·2100년에도 실제 DeadlineRepository에서 강의/과제/팀과제가 기한이 남은 항목으로 반환됨.
- iOS 별도 설정·dependencies 복원, 기존 사용자 설정 보존, 차단 안내 뒤 WebView 클릭 복원.

초기 구현 단계에서 Android Debug APK 및 공통/플랫폼 의존 경계 검사, iOS 앱 전체 테스트 254개가 통과했다. 이는 후속 보완 전 결과이며 최종 플레이어 검증과 구분한다.

## 최종 iOS 플레이어 검증

```sh
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosAppUnitTests \
  -destination "platform=iOS Simulator,id=$SIMULATOR_ID" \
  -parallel-testing-enabled NO \
  -only-testing:iosAppTests/IosVideoHostTests CODE_SIGN_IDENTITY=- test
```

iPhone Air 시뮬레이터 한 대에서 실행했다. 로컬 fixture 테스트는 배포 페이지의 전역 선언을 유지한 HTML과 테스트 번들의 120초 MP4를 사용한다. Native 버튼 명령으로 재생/일시정지·`00:24 → 00:34 → 00:24` 이동·UI/데모 진행률 콜백을 검증한다.

실제 서버 테스트 `testLiveDemoPlayerControlsThroughNavigationAndBridge`는 테스트 호스트 환경에 `KLAS_RUN_LIVE_PLAYER=1`을 명시한 경우에만 실행하고 기본 실행에서는 skip한다. 해당 값을 `.xctestrun`의 테스트 환경에 지정한 뒤 다음 명령으로 실행했다.

```sh
xcodebuild -xctestrun "$DEMO_XCTESTRUN" \
  -destination "platform=iOS Simulator,id=$SIMULATOR_ID" \
  -parallel-testing-enabled NO \
  -only-testing:iosAppTests/IosVideoHostTests test-without-building
```

실배포 테스트를 포함한 33개 모두 통과했다. 실제 `https://klasplus.yuntae.in/demo/native-player`와 MP4를 열어 navigation callback→브리지→UI 갱신을 검증했으며 navigation 완료를 수동 호출하지 않았다. 학교 서버는 사용하지 않았다.

실배포에서 확인하고 수정한 원인:

- `.html?start=0`의 307 응답이 `/demo/native-player?start=0`로 이동해 기존 정확한 `.html` 검사에서 상태 모니터 설치가 누락됨.
- 배포 페이지의 전역 `const player`와 10초 이동 명령의 전역 `var player`가 충돌함.

초기 로컬 테스트는 페이지 전역 선언을 제거하고 `.html` 주소를 전달해 위 두 조건을 놓쳤다. 최종 회귀 테스트는 두 조건을 보존한다.

## WebView 호환 검증

별도 웹 저장소에서 Native 중심 동작 위임과 표시 컴포넌트를 반영했다. 참조 작업에서 호환 버전의 웹 테스트 56개와 OpenNext 빌드가 통과했다. 이번 플레이어 URL·명령 및 날짜 보완에는 웹 추가 수정·배포가 없다.

## 사용자 확인 및 미검증 범위

사용자가 최종 변경 후 “모두 잘 작동해”라고 확인했다. 구체적인 확인 기기·OS·기능별 목록은 수집하지 않았으므로 모든 기기 조합의 검증을 의미하지 않는다.

- 연결된 Android 기기가 없어 추가한 instrumentation 실행과 Android 실제 영상 재생은 에이전트가 검증하지 못했다.
- 실제 기기 PiP·Keychain/생체인식·OS 알림·WidgetKit·카메라 전체 조합은 별도 확인이 필요하다.
- 실제 학교 인증·학교 영상·실제 파일 제출·실제 QR 출석/출입·App Store 심사 통과를 검증한 것은 아니다.

세션은 명시적 로그아웃까지 복원되며 fixture 데이터는 프로세스 메모리 수명이다. 앱 잠금은 실제 기기 설정으로 유지한다. Native 재빌드가 필요하고 기존 release APK/AAB 및 iOS 버전 변경은 이 기능 PR에 포함하지 않는다. 보안·저장소 경계와 롤백은 [ADR-016](adr/ADR-016-review-demo-mode.md)을 따른다.

## PR 검토 후 저장소·리스너 수명 보완

- Android 공통 의존성의 계정 preferences와 위젯의 변경 관찰 대상을 `AppPrefs.MAIN`으로 고정했다. 화면용 preferences만 데모 여부에 따라 선택한다. 위젯 시작은 중복 실행을 막고 데모 종료 시 일반 계정 identity를 다시 적용한다.
- `DemoPreferenceLifetimeTest`는 복원된 데모에서 만든 실제 `AndroidAppDependencies`를 유지한 채 로그아웃하고, 일반 계정 자격증명을 저장·재생성 후 복원한다. 실제 학번·학기 변경에 따른 위젯 snapshot 갱신과 리스너 등록 대상도 확인한다.
- 읽기 전용 `Resizable_Experimental` 에뮬레이터에 Debug/AndroidTest APK를 설치하고 `adb shell am instrument -w -r -e class com.icecream.kwklasplus.DemoPreferenceLifetimeTest com.icecream.kwklasplus.test/androidx.test.runner.AndroidJUnitRunner` 실행: 2개 통과. 테스트 preferences/위젯 파일은 별도 이름공간을 사용한다.
- iOS 위젯 인스턴스별 전역 종료 리스너를 제거하고 앱 시작 시 한 번 등록한다. `IosDemoWidgetLifecycleTest`는 반복 설치·위젯 재생성 후에도 종료/만료당 앱 timeline 갱신이 한 번이고 임시 위젯 콜백은 실행되지 않음을 검증한다.
- `./gradlew :shared:allTests :androidApp:testDebugUnitTest :androidApp:assembleDebug :androidApp:assembleDebugAndroidTest --offline`: 공통 Android 339개/iOS 370개 및 Android JVM 45개 통과, 앱·instrumentation 빌드 통과.

이전 CI의 PiP 테스트는 WebKit 프로세스 시작이 지연되며 60초 실행 제한을 넘었다. 로그에는 같은 시점의 네트워크 프로세스 시작 지연(66초)과 뒤이은 CAPTCHA 테스트 타임아웃도 있었다. 상태 전이 단위 테스트에서 실제 학교 영상 URL/제목 요청을 시작하지 않고 재생 상태를 직접 준비하도록 바꿨다. 각 테스트의 WebView를 명시적으로 정리하고 PiP 복원은 단일 `Task.yield()` 대신 실제 경로 변경을 기다린다. 실제 영상 제어는 별도 MP4 fixture/명시적 실배포 테스트로 계속 검증한다. 플레이어 모델 해제 시 영상 WebView도 정리한다.

CI와 같은 단일 시뮬레이터와 60/120초 실행 제한으로 iOS 앱 전체 테스트 실행: 258개, 1개 skip, 실패 0개. PiP와 CAPTCHA 테스트 모두 통과했다. 로그 `/private/tmp/demo-review-ios-full.log` 및 `/private/tmp/demo-review-instrumentation.log`.

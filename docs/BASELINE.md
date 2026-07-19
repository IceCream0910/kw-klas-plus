# 마이그레이션 기준선

## 저장소

- 마이그레이션 작업 브랜치: `migration-main`
- 마이그레이션 저장소 origin: 아직 연결하지 않음
- Native 기준 remote: `legacy-native` (push 비활성화)
- Web 기준 remote: `legacy-web` (push 비활성화)

## 고정 커밋

- Native: `76be3b50ba6f3f28ab81c58918542203c6b5933c`
- Web: `870f94d13f74bf0ffc1963d39d6640658cf32cba`

기준 커밋을 변경할 때는 두 저장소의 변경 내역을 검토하고 브리지 계약 fixture와 기능 패리티 매트릭스를 함께 갱신한다.

## 모듈 전략

- `legacyAndroidApp`: Native 기준 커밋의 `app` 모듈을 보존하는 비교 앱
- `androidApp`: 운영 applicationId/version을 유지하는 Android 앱. 레거시 View 실행 경로를 복원하고 Compose와 공통 모듈을 병행 활성화
- debug 비교 앱 id: `com.icecream.kwklasplus.legacy`
- release-like legacy 앱 id: `com.icecream.kwklasplus`

`legacyAndroidApp`에 적용한 통합 변경은 다음으로 제한한다.

1. debug 빌드에 `.legacy` application id와 version name suffix 추가
2. AGP 9 내장 Kotlin을 사용하기 위해 `org.jetbrains.kotlin.android` 제거
3. AGP 9 새 DSL과 호환되지 않는 Sentry 5.9.0 Gradle 플러그인과 mapping 자동 업로드 설정 제거

Sentry Android 런타임 의존성과 Manifest 설정은 유지한다.

## 툴체인

| 항목 | 고정 값 |
|---|---|
| JDK | 21 |
| Gradle | 9.4.1 |
| AGP | 9.2.1 |
| Kotlin | 2.4.0 |
| Compose compiler plugin | Kotlin 2.4.0 동기화 |
| Ktor | 3.5.0 |
| kotlinx.serialization | 1.11.0 |
| kotlinx.coroutines | 1.10.2 |
| Android compile SDK | 신규/legacy 37 |
| Android min SDK | 신규/legacy 29 |

현재 Windows 환경의 전역 `JAVA_HOME`은 유효하지 않은 Android Studio JRE를 가리킨다. CLI 검증에서는 설치된 JDK 21 경로를 명시한다. 저장소에는 로컬 JDK 절대 경로를 기록하지 않는다.

## 검증 기록

- 2026-07-16: 사용자가 Android 빌드 성공을 수동 확인함
- 2026-07-16: 당시 `:sharedLogic:testAndroidHostTest` 성공. 2026-07-19 모듈명을 `shared`로 변경
- 2026-07-16: `./gradlew :androidApp:assembleDebug --no-configuration-cache` 성공
- 2026-07-16: 당시 공통 host test, Android instrumentation Kotlin 컴파일, debug APK 빌드 성공
- 2026-07-16: SESSION/User-Agent/HTML 세션 만료 계약을 포함한 공통 host test 44개 통과
- 2026-07-16: QR 출석 3단계 전처리/check-in/error mapping을 포함한 공통 host test 50개 통과 및 Android debug build 성공
- 2026-07-17: 사용자가 마이그레이션 Android 앱의 실제 빌드·실행 정상 동작을 수동 확인함
- 2026-07-17: 학기/수강과목 DTO, 선택 fallback, 오류 mapping을 포함한 공통 host test 55개 통과 및 Android debug build 성공
- 2026-07-17: 시간표 동적 요일 필드, 교시 span, Web JSON codec을 포함한 공통 host test 59개 통과 및 Android debug build 성공
- 2026-07-17: 마감일 필터/남은 시간/Web JSON, 1시간 미만 정수 절삭 호환 및 Android 날짜 parser 형식을 포함한 host test 66개 통과, Android debug build 성공
- 2026-07-17: Keystore primary SESSION/legacy 미러, timestamp 누락 폐기, fallback과 SecureStore 전환 테스트를 포함한 host test 72개 통과. Android 앱 및 계측 테스트 Kotlin 컴파일 성공
- 2026-07-17: WebAuth URL/host/cookie/alert 정책, credential 준비 및 저장 credential resume/timeout 테스트를 포함한 host test 82개 통과. Android WebAuthDriver 및 기존 View 로그인 경로 컴파일 성공
- 2026-07-17: Bridge v1 codec/router, 64개 typed command mapping, 13개 event ID, UTF-8 payload/origin/main-frame/sync/error redaction 테스트를 포함한 host test 93개 통과. Android debug build 성공
- 2026-07-17: typed route, FileTransfer/FilePicker, exact trusted origin, WebSurface 전 화면 연결, JSON-safe player payload를 포함한 공통 host test 114개와 Android unit test 3개 통과. Android instrumentation Kotlin 컴파일, debug APK, R8 minify release unsigned APK 빌드 성공. production signing 연결은 M8-004에서 추적
- 2026-07-18: 실기기에서 과거 학기 시간표, QR 출석 진입, 도서관 QR/위젯, 영상 상태/PIP 복구 회귀를 확인. CDATA XML, 누락 교수명, KLAS 영상 host/state callback, Fragment 복원, scanner 초기화 예외 및 PIP 종료 복구를 수정하고 공통 host test 121개, Android unit test, instrumentation Kotlin 컴파일, debug APK 및 R8 release APK 빌드 성공. 수정 빌드 실기기 재검증은 대기
- 2026-07-18: QR 출석 재검증에서 Google scanner 화면 전 Task 실패 확인. `barcode_ui` application metadata와 ML Kit 오류 분류를 추가했으나 ModuleInstallClient 사전 확인이 동기 초기화 실패를 유발해 제거. 레거시 startScan 경로와 모듈 다운로드 중 제한 재시도를 복원하고 Android debug build/instrumentation Kotlin 컴파일 성공. 실기기 재검증 대기
- 2026-07-18: QR scanner `getClient(Activity)` 내부 telemetry 초기화에서 기기별 `NullPointerException` 지속 확인. application context와 레거시 QR 옵션으로 생성하도록 변경하고 Google 내부 첫 stack frame을 오류 reason에 추가. Android debug build/instrumentation Kotlin 컴파일 성공, 실기기 재검증 대기
- 2026-07-18: application context scanner에서 delegate 화면 없이 Task가 미완료되는 회귀 확인. QR scanner 시작을 고정 레거시의 QRScanActivity Activity context/Task listener 방식으로 복원하고 공통 AttendanceRepository 체크인은 유지. Android debug build/instrumentation Kotlin 컴파일 성공, 실기기 재검증 대기
- 2026-07-18: Google scanner UI가 표시되지 않은 상태임을 재확인. QR 진입 구성 전체를 고정 레거시와 동일한 Activity context/Task listener/Activity `barcode_ui` metadata/ML Kit 자동 초기화 provider로 복원하고 수동 초기화 및 provider 제거 경로를 제외했다. provider/metadata 계측 테스트 컴파일, debug APK 빌드 및 병합 Manifest 확인 성공. 실기기 재검증 대기
- 2026-07-19: release R8 빌드에서 keep 적용 후 실제 실패 위치가 Google Code Scanner `zze` 생성자의 telemetry 초기화임을 확인. 설치된 Google barcode UI action을 Activity Result로 직접 실행해 생성자를 우회하고 SDK와 동일한 SafeParcelable 결과를 해석하는 Android fallback, 결과 DTO keep, package visibility를 추가했다. debug/release APK와 instrumentation Kotlin 컴파일 성공, 실기기 재검증 대기
- 2026-07-19: googlesamples/mlkit#1018과 동일한 AGP 9 R8 full-mode 내부 생성자 제거 문제임을 확인. 사용자 추가 ML Kit 생성자 규칙에 `com.google.android.gms.internal.mlkit_code_scanner.**` keep을 보완하고 직접 Intent/내부 Parcelable 우회를 제거했다. `QRScanActivity`를 공통 `QrScanner` port의 `AndroidQrScanner`에 재연결하고 Activity context의 공식 API 호출로 복원. release mapping/seeds에서 `zzny` 원형 보존, release APK 및 instrumentation Kotlin 컴파일 성공
- 2026-07-19: Compose/iOS 전 Android 공통화 감사를 완료했다. 학생증·도서관·강의 metadata HTTP와 JSON/XML/HTML/player 파싱 및 KLAS/Web/player script를 공통 KMP 모듈로 이동하고 Android 직접 OkHttp/Jsoup/parser 의존성을 제거했다. 공통 host test 136개, Android unit test, instrumentation Kotlin 컴파일, debug APK와 R8 release APK 빌드 통과. 세부 경계는 `docs/ANDROID_COMMONIZATION_AUDIT.md`에 기록했다.
- 2026-07-19: 사용자 실기기 일괄 회귀 체크리스트 전체 통과. QR scanner 무스캔 종료의 취소 Toast를 제거하고 Home/Lecture QR 출석 연속 탭을 Activity Result 수명까지 single-flight로 제한해 scanner 중복 표시와 loading dialog 잔류를 수정했다. 공통 host test 137개, Android unit test, instrumentation Kotlin 컴파일, debug/R8 release APK 빌드 통과. F-019 Android parity와 M5-001 완료를 기록했다.
- 2026-07-19: 최종 UI 전략을 Android Compose/iOS SwiftUI로 확정하고 `sharedLogic`을 `shared`로 변경했다. `sharedUI`와 Compose Multiplatform UI 의존성을 제거하고 Android WebView 어댑터와 Kotlin 소스 54개를 `androidApp/src/main/kotlin`으로 이전했다. `Shared.framework`와 Xcode embed script를 `:shared`로 정렬하고 `commonMain` 플랫폼 import 검증을 추가했다. 공통 host test와 Android Kotlin 컴파일 강제 재실행, Android unit/instrumentation Kotlin/debug/R8 release 빌드 통과.
- 2026-07-19: 실기기 패리티 이후 KMP 경계를 재감사해 credential/session/cookie/legacy migration/app lock secret·codec/library crypto·cache/external navigation/haptics와 공통 repository/use case 조립을 `shared/androidMain`으로 이동했다. `AndroidSharedDependencies`를 도입하고 앱 UI adapter를 기능별 패키지로 분리했으며 source-set 역참조 검사를 추가했다. 공통 Android host test 147개, Android unit, instrumentation Kotlin, debug APK와 R8 release APK 빌드 통과.
- Android Keystore 계측 테스트는 컴파일 완료, 연결된 실기기/에뮬레이터 실행은 미검증
- 2026-07-19: `sharedLogic`을 `shared`로 변경하고 `sharedUI`를 제거했다. Android UI/WebView 어댑터는 `androidApp`, iOS UI/WKWebView 어댑터는 `iosApp`, 공통 API·모델·엔티티·유스케이스·상태와 플랫폼별 API 구현은 `shared` source set이 소유한다.
- `androidApp`은 versionCode 33/versionName 2.0.0-alpha1과 레거시 View 진입점을 유지하며 Android Compose 전환 기반과 `shared`를 링크함

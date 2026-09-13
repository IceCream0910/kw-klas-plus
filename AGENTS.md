# KLAS+ KMP 프로젝트 작업 규칙

사용자 지시에 따라 KMP 기반 Android·iOS 앱의 개선·기능 추가·버그 수정을 진행한다. Android UI는 Compose, iOS UI는 SwiftUI이며 기존 Android 사용자 동작·데이터와 양 플랫폼의 보안·브리지 계약을 구조 정리보다 우선한다.

## 작업 전 필수 확인

1. `docs/ARCHITECTURE.md`의 모듈·보안 경계를 확인한다.
2. 계약에 영향을 주면 `docs/ARCHITECTURE.md`의 인증·저장 키·브리지 항목을 코드·계약 테스트와 대조하고 함께 갱신한다. 구현 목표로 표시된 Android SESSION 단일 저장 경로는 아직 완료된 동작으로 간주하지 않는다. `docs/adr/README.md`의 결정도 확인한다. `docs/kmp-migration/feature_parity_matrix.md`는 과거 상태 기록이다.
3. 변경 대상 기준 커밋과 별도 [WebView 저장소](https://github.com/IceCream0910/kw-klas-plus-webview)의 호환 버전을 PR에 기록한다. 과거 작업 이력은 `docs/kmp-migration/kmp_migration_tasks.md`에 보존한다.

## 불변 우선순위

1. Android 기능 및 사용자 데이터 호환성
2. 로그인·세션·보안 저장소의 정확성
3. WebView ↔ Native 브리지 호환성
4. 회귀 테스트와 롤백 가능성
5. 양 플랫폼 기능 개선
6. 구조 개선과 중복 제거

## 아키텍처 경계

- `shared/commonMain`
  - API 네트워크 통신, 직렬화 데이터 모델, 엔티티, 인증·세션 정책, 유스케이스, 플랫폼 중립 ViewModel/상태, 저장소 인터페이스, 브리지 명령/이벤트 모델을 둔다.
  - Android `Context`, `WebView`, `Activity`, iOS `UIViewController`, `WKWebView`를 참조하지 않는다.
- `shared/androidMain`, `shared/iosMain`
  - 공통 인터페이스에 필요한 플랫폼별 API, 네트워크 엔진, 저장소와 작은 어댑터 구현을 둔다.
  - 큰 플랫폼 기능은 생성자 주입 가능한 인터페이스 구현으로 분리한다. 복잡한 기능을 무조건 `expect/actual` 클래스로 만들지 않는다.
  - 플랫폼 SDK를 사용하더라도 Activity/View/WebView/UIViewController/WKWebView 수명주기나 앱 화면을 몰라도 되는 저장소·암호화·세션·캐시·작은 OS 어댑터는 플랫폼 source set이 소유한다.
- `androidApp`
  - Compose UI, Android 진입점, Manifest, WebView, 위젯, PIP Activity, QR 스캐너, 다운로드/파일 선택, 생체인식과 앱별 preference 생성을 소유한다.
  - 공통 port 구현 중 Activity Result, FragmentActivity, View/WebView, 앱 리소스 또는 앱의 특정 Activity에 결합된 구현만 소유한다.
- `iosApp`
  - SwiftUI, WKWebView, iOS 진입점, entitlements, WidgetKit extension, LocalAuthentication, Keychain, AVKit 및 앱 수명주기 연결을 소유한다.

의존 방향은 `androidApp`/`iosApp` → `shared`이다. `shared`가 플랫폼 앱을 참조하거나 Compose·SwiftUI·Android View·UIKit 타입을 공개 API에 노출하면 안 된다.

## 호환성 규칙

- Android 패키지명 `com.icecream.kwklasplus`, 기존 서명/배포 트랙, 버전 코드의 연속성을 보존한다.
- 기존 SharedPreferences 키 이름과 의미를 임의로 변경하지 않는다. 변경이 필요하면 읽기-이전-검증-구키 삭제 순서의 명시적 마이그레이션을 작성한다.
- 기존 `SESSION` 쿠키 이름, 도메인, 네이티브 HTTP의 인증 헤더 동작을 특성 테스트로 먼저 고정한다.
- 신 앱은 `KlasNativeBridgeNative` Bridge v1을 사용하며 구 Android용 `Android` fallback은 웹 adapter에만 있다. 기존 메서드명, 인자 순서, 콜백명을 호환 경로 없이 삭제하거나 변경하지 않는다.
- 오타처럼 보이는 `evaluteKLASScript`도 공개 계약이므로 호환 별칭 없이 수정하지 않는다.
- 브리지 변경은 최소 한 릴리스 동안 구버전과 신버전을 함께 지원하고, WebView 저장소의 계약 테스트와 함께 배포한다.
- WebView URL, User-Agent, DOM/localStorage 키, CookieStore 동기화 순서를 동작 계약으로 취급한다.
- 실제 사용 중인 Android Widget `RemoteViews` XML은 유지한다. View/Compose 공존 자산을 정리할 때는 사용처와 롤백 경로를 확인한다.
- iOS 구현 때문에 Android 동작을 공통 최저 수준으로 낮추지 않는다. 공통 의미를 정의하고 플랫폼 능력 차이는 어댑터가 처리한다.

## 인증 및 보안 규칙

- 평문 KLAS 비밀번호는 암호화 API 호출 순간에만 메모리에 존재하게 하고 저장·로그·분석·크래시 첨부에 포함하지 않는다.
- 서버가 반환한 암호화 비밀번호, 세션 토큰, 도서관 비밀번호/키, 앱 잠금 해시는 모두 `SecureStore`로 분류한다.
- Android 신규 보안 저장소는 Android Keystore에 의해 보호되는 구현을 사용한다. 현재 `EncryptedSharedPreferences` 데이터는 파괴하지 않고 이전 경로를 제공한다.
- iOS 비밀 값은 Keychain에 저장한다. 일반 설정은 UserDefaults 계열 저장소와 분리한다.
- WebView 브리지는 허용된 origin과 top-level frame에서만 활성화하며, 메서드 allowlist·인자 검증·최대 payload 크기를 둔다.
- JS 문자열을 직접 이어 붙이지 않는다. 모든 주입 값은 JSON 직렬화 후 전달한다.
- 외부 URL에는 브리지 객체를 노출하지 않는다. 다운로드 URL, Intent extra, 딥링크는 사용 전에 검증한다.
- 인증 정보와 브리지 payload를 Sentry breadcrumb, 로그, 화면 캡처에 남기지 않는다.
- 보안 관련 동작을 바꿀 때는 `docs/ARCHITECTURE.md`의 위협 항목과 테스트를 함께 갱신한다.

## 구현 방식

- 새 작업은 이슈/PR로 추적한다. `docs/kmp-migration/kmp_migration_tasks.md`의 M1~M7 ID는 과거 이력으로 유지한다.
- 코드 주석은 최소화한다. 코드만으로 의도나 제약을 충분히 표현할 수 없을 때만 작성하며, 꼭 필요한 주석은 한국어로 작성한다.
- PR에 기준 커밋, 테스트 명령/결과, 수동 검증과 미검증 범위를 남긴다.
- 기능 이동 전 특성 테스트를 추가한다. 테스트 없이 기존 코드를 삭제하지 않는다.
- 공통 모델은 `org.json.JSONObject`, Android `Bundle`, Swift Dictionary 대신 직렬화 가능한 Kotlin 타입을 사용한다.
- 시간, 난수, HTTP, 저장소, 외부 URL 실행, 생체인식은 인터페이스로 주입해 공통 테스트에서 대체할 수 있게 한다.
- 네트워크 오류, 세션 만료, CAPTCHA/임시 비밀번호 요구, 사용자 취소를 서로 다른 결과 타입으로 표현한다.
- 새 의존성은 Android와 iOS 지원, Kotlin/Compose 호환 버전, 유지보수 상태, 바이너리 크기를 확인한 뒤 추가한다.
- 버전 카탈로그의 Kotlin/Compose/AGP/Xcode 조합을 한 단위로 검증한다. 자동으로 최신 버전만 올리지 않는다.

## 검증 기준

변경 범위에 맞는 최소 검증을 모두 수행한다.

- 공통 로직: `commonTest` 및 Android/iOS 타깃 테스트
- Android UI/플랫폼: JVM 테스트 + instrumentation + 실기기 수동 검증
- iOS UI/플랫폼: simulator 테스트 + Keychain/생체인식/PIP/Widget이 필요한 실기기 검증
- 브리지: Native 메서드 스키마 테스트 + Web 저장소의 호출/콜백 계약 테스트
- 인증: 신규 로그인, 저장 자격증명 로그인, 유효 세션 즉시 진입, 만료 세션 재로그인, CAPTCHA/임시 비밀번호, 로그아웃, 앱 데이터 업그레이드
- 계약·플랫폼 차이 변경: Native·Web 계약 테스트와 관련 ADR을 갱신하고 PR에 검증 증거를 기록

빌드 성공만으로 기능 완료로 간주하지 않는다.

## 완료 정의

작업은 다음 조건을 모두 만족할 때만 완료한다.

- Android 기존 동작이 기준 앱과 동일하거나 승인된 차이가 문서화됨
- 실패/취소/세션 만료 경로 포함 테스트 통과
- 민감정보 로그 및 불필요한 브리지 노출 없음
- 이슈/PR의 상태와 변경된 계약·ADR 문서 갱신
- 롤백 방법 또는 구 구현 fallback이 존재
- 관련 문서와 실제 코드가 일치

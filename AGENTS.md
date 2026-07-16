# KLAS+ 마이그레이션 작업 규칙

이 저장소의 최우선 목표는 기존 Android 앱의 모든 동작을 보존하면서 KMP + Compose Multiplatform 구조로 점진적으로 이전하고, 그 위에 iOS 지원을 추가하는 것이다. 새 구조의 미관이나 코드 정리보다 Android 회귀 방지가 우선한다.

## 작업 전 필수 확인

1. `TASKS.md`에서 현재 단계와 선행 작업을 확인한다.
2. `docs/MIGRATION_ARCHITECTURE.md`의 경계와 의존성 규칙을 읽는다.
3. `docs/FEATURE_PARITY_MATRIX.md`에서 변경 대상 기능과 브리지 계약을 확인한다.
4. 원본 Android 기준 저장소와 WebView 저장소의 기준 커밋을 기록한다.
   - Native: <https://github.com/IceCream0910/kw-klas-plus>
   - Web: <https://github.com/IceCream0910/kw-klas-plus-webview>
5. 기준 커밋이 문서에 고정되기 전에는 원본의 최신 `main`을 곧바로 구현 기준으로 간주하지 않는다.

## 불변 우선순위

1. Android 기능 및 사용자 데이터 호환성
2. 로그인·세션·보안 저장소의 정확성
3. WebView ↔ Native 브리지 호환성
4. 회귀 테스트와 롤백 가능성
5. iOS 기능 확장
6. 구조 개선과 중복 제거

## 아키텍처 경계

- `sharedLogic/commonMain`
  - 인증 상태 머신, 세션 정책, DTO, 유스케이스, 저장소 인터페이스, 브리지 명령/이벤트 모델을 둔다.
  - Android `Context`, `WebView`, `Activity`, iOS `UIViewController`, `WKWebView`를 참조하지 않는다.
- `sharedLogic/androidMain`, `sharedLogic/iosMain`
  - 공통 인터페이스에 필요한 작은 플랫폼 어댑터 또는 플랫폼별 네트워크 엔진 구성을 둔다.
  - 큰 플랫폼 기능은 생성자 주입 가능한 인터페이스 구현으로 분리한다. 복잡한 기능을 무조건 `expect/actual` 클래스로 만들지 않는다.
- `sharedUI/commonMain`
  - Compose 화면, 내비게이션 상태, 프레젠테이션 모델을 둔다.
  - WebView 자체가 아니라 `WebSurface`와 `PlatformCapabilities` 같은 추상 계약에 의존한다.
- `sharedUI/androidMain`, `sharedUI/iosMain`
  - `AndroidView(WebView)` 및 UIKit/WKWebView 상호운용 같은 UI 어댑터를 둔다.
- `androidApp`
  - Android 진입점, Manifest, 위젯, PIP Activity, QR 스캐너, 다운로드/파일 선택, 생체인식 및 Keystore 구현을 소유한다.
- `iosApp`
  - iOS 진입점, entitlements, WidgetKit extension, LocalAuthentication, Keychain, AVKit 및 앱 수명주기 연결을 소유한다.

의존 방향은 플랫폼 앱 → `sharedUI` → `sharedLogic`이다. `sharedLogic`이 `sharedUI`나 플랫폼 앱을 참조하면 안 된다.

## 호환성 규칙

- Android 패키지명 `com.icecream.kwklasplus`, 기존 서명/배포 트랙, 버전 코드의 연속성을 보존한다.
- 기존 SharedPreferences 키 이름과 의미를 임의로 변경하지 않는다. 변경이 필요하면 읽기-이전-검증-구키 삭제 순서의 명시적 마이그레이션을 작성한다.
- 기존 `SESSION` 쿠키 이름, 도메인, 네이티브 HTTP의 인증 헤더 동작을 특성 테스트로 먼저 고정한다.
- JavaScript 브리지의 기존 객체명 `Android`, 메서드명, 인자 순서, 콜백명을 Android 패리티 완료 전에는 삭제하거나 변경하지 않는다.
- 오타처럼 보이는 `evaluteKLASScript`도 공개 계약이므로 호환 별칭 없이 수정하지 않는다.
- 브리지 변경은 최소 한 릴리스 동안 구버전과 신버전을 함께 지원하고, WebView 저장소의 계약 테스트와 함께 배포한다.
- WebView URL, User-Agent, DOM/localStorage 키, CookieStore 동기화 순서를 동작 계약으로 취급한다.
- Compose 전환 중에는 View와 Compose의 공존을 허용한다. 한 번에 전체 Activity를 재작성하지 않는다.
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
- 보안 관련 동작을 바꿀 때는 `docs/MIGRATION_ARCHITECTURE.md`의 위협 항목과 테스트를 함께 갱신한다.

## 구현 방식

- 한 작업은 가능한 한 `TASKS.md`의 한 ID에 대응시킨다.
- 코드 주석은 최소화한다. 코드만으로 의도나 제약을 충분히 표현할 수 없을 때만 작성하며, 꼭 필요한 주석은 한국어로 작성한다.
- 작업 시작 시 체크박스 아래에 담당/브랜치/기준 커밋을 기록할 수 있다. 완료 시 증거가 되는 테스트 명령이나 수동 검증 결과를 남긴다.
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
- 릴리스: `docs/FEATURE_PARITY_MATRIX.md`의 해당 행을 증거와 함께 갱신

빌드 성공만으로 기능 완료로 간주하지 않는다.

## 완료 정의

작업은 다음 조건을 모두 만족할 때만 완료한다.

- Android 기존 동작이 기준 앱과 동일하거나 승인된 차이가 문서화됨
- 실패/취소/세션 만료 경로 포함 테스트 통과
- 민감정보 로그 및 불필요한 브리지 노출 없음
- `TASKS.md` 상태와 기능 패리티 매트릭스 갱신
- 롤백 방법 또는 구 구현 fallback이 존재
- 관련 문서와 실제 코드가 일치

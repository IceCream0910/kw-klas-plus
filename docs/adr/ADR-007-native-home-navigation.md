# ADR-007: 네이티브 홈 하단 탐색

상태: Accepted (웹 저장소 동시 배포 대기)

홈의 네 탭은 Android Compose와 iOS의 UIKit 시스템 `UITabBar`가 표시한다. iOS 26 이상에서 시스템 Liquid Glass 탭 바를 받는다. `WKWebView`는 한 `HomeView`에 고정하고 탭 선택만 바꾼다. `TabView`마다 WebView를 재부착하면 검은 화면과 세션 흐름 훼손이 발생하므로 사용하지 않는다. Android는 앱 Material 테마 색상의 반투명 플로팅 캡슐을 웹의 기존 gradual blur 위에 표시한다. 별도 캡처 blur를 실행하지 않는다. Android 탭 아이콘·라벨은 iOS의 피드·시간표·캘린더·전체와 맞추고, 선택된 아이콘·라벨에는 `primary`, 선택 캡슐에는 `surfaceContainer` 계열 색상을 사용한다. 선택 캡슐은 위치를 애니메이션한다. 탭을 실제로 바꿀 때만 각 플랫폼의 기존 `CLOCK_TICK` 햅틱을 실행한다.

네이티브 탭 선택은 공통 `NativeHomeTabScripts`가 고정된 탭 ID와 KLAS+ fallback URL만 받아 JS로 전달한다. 새 웹의 `window.klasNativeNavigate(tab)`은 Next.js `router.push`를 실행한다. 미배포 웹에서는 기존 URL 로딩으로 돌아간다. iOS는 JS 함수 존재 여부를 확인하고 없으면 기존 `WebViewHolder.load` 경로로 이동하여 navigation delegate 수명주기를 유지한다. 웹에서 시작한 탭 이동은 기존 Bridge v1 `changeTab`을 재사용하며, 이미 이동한 URL이면 네이티브 선택만 동기화한다. 새 Web→Native 메서드는 추가하지 않는다. 웹이 라우트 이동 후 `completePageLoad`를 호출해야 Native 화면 데이터가 다시 주입된다.

iOS의 기존 `reload` 브리지 호출은 데이터를 다시 가져온 뒤 현재 탭 URL을 강제로 다시 연다. 현재 URL의 탭이 같아도 이동을 생략하면 페이지 완료 신호가 없어서 로딩이 끝나지 않는다. 데이터 요청과 페이지 로드 중에는 기존 `WKWebView` 위에 반투명 로딩 오버레이를 표시하고, WebKit의 로드 완료·실패 또는 웹의 `completePageLoad`에서 해제한다. 초기 진입 로딩과 구분해 탭 바와 WebView를 다시 만들지 않는다.

네이티브 탭 fallback URL 검증은 설정된 `KlasUrls.KLAS_PLUS_BASE`와 탭별 경로를 사용한다. 로컬 HTTP 테스트 서버를 지정하면 정확한 origin만 허용하고, Android/iOS의 평문 웹 로드 예외는 Debug 구성에만 둔다. URL을 운영 도메인으로 되돌리면 테스트 서버 origin은 허용 목록에서 빠진다.

iOS 홈의 초기 WebKit 로드가 완료되면 웹의 `completePageLoad`가 늦거나 누락돼도 로딩 화면을 내리고 기존 데이터 주입을 실행한다. 웹 콜백이 뒤이어 오면 같은 주입 경로를 다시 실행한다. 로드 실패는 기존 실패 화면과 재시도 경로로 간다.

iOS 피드의 pull-to-refresh 표시는 웹 앱이 소유한다. 네이티브 홈 `WKWebView`의 피드 탭에서는 `UIScrollView`의 bounce를 끄고 다른 탭으로 이동하면 되돌려, 웹 새로고침 표시와 시스템 스크롤 반동이 겹치지 않게 한다.

롤백은 네이티브 바와 새 JS 호출을 제거하고 기존 `switchToTab` URL 로딩을 다시 사용하는 것이다. 웹의 기존 하단 탐색은 구 앱 사용자용으로 유지한다. Native와 웹이 따로 배포되므로 웹 배포 및 구 앱 표시 정책은 별도 저장소에서 검증해야 한다.

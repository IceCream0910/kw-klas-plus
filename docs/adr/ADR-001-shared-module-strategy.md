# ADR-001: 단일 shared 코어와 플랫폼별 UI

- 상태: 승인
- 일자: 2026-07-16
- 최종 수정: 2026-07-19

## 결정

Kotlin Multiplatform 코드는 `shared` 모듈 하나로 노출하고, 기존 Android 앱은 `legacyAndroidApp` 비교 모듈로 보존한다. Android UI는 `androidApp`의 Compose로, iOS UI는 `iosApp`의 SwiftUI로 각각 구현한다.

의존 방향은 다음으로 제한한다.

```text
androidApp -> shared
iosApp -> shared
legacyAndroidApp (격리된 비교 기준선)
```

`shared/commonMain`은 API 네트워크 통신, 모델, 엔티티, 유스케이스, 플랫폼 중립 상태/ViewModel과 port를 소유한다. `shared/androidMain`과 `shared/iosMain`은 공통 계약에 필요한 플랫폼별 API·엔진·저장소 구현을 소유한다. Activity, WebView, Compose UI, UIViewController, WKWebView, SwiftUI는 플랫폼 앱이 소유한다.

## 이유

- 두 플랫폼의 화면 설계와 시스템 UI 차이를 각 네이티브 UI 프레임워크에서 직접 처리할 수 있다.
- 네트워크·파싱·업무 규칙·상태 전이를 재사용해 iOS 네이티브 코드 중복을 최소화한다.
- iOS에는 `Shared.framework` 하나만 노출해 Kotlin framework 중복과 Swift API surface를 줄인다.
- Android 레거시 View와 Compose를 공존시키면서 기능별로 점진 교체할 수 있다.

## 결과

- `sharedUI` 모듈과 Compose Multiplatform UI 의존성을 제거한다.
- 플랫폼 UI가 필요로 하는 상태와 이벤트는 직렬화 가능하거나 Swift에서 소비 가능한 공통 타입으로 제공한다.
- 기능별 패리티 완료 전 legacy Activity를 삭제하지 않는다.
- iOS 기능 구현은 Android Compose 전환과 공통 코어 안정화 이후 진행한다.

# ADR-003: min Android/iOS 버전과 태블릿/회전 정책

- 상태: Accepted (M6-001 정책 고정 분량)
- 날짜: 2026-08-05

## 결정

| 플랫폼 | 최소 OS | 기기 | 비고 |
|---|---|---|---|
| Android | API 29 (minSdk) | 폰·태블릿 | compileSdk/targetSdk 37. 반응형 기준은 [아키텍처의 플랫폼 기능 항목](../ARCHITECTURE.md#플랫폼-기능과-adr) 참고 |
| iOS / iPadOS | 16.0 | iPhone·iPad (`TARGETED_DEVICE_FAMILY = 1,2`) | `iosApp/Configuration/Config.xcconfig`의 `IPHONEOS_DEPLOYMENT_TARGET` |

태블릿·회전·safe area의 UI 세부 정책은 플랫폼별 구현이 소유한다. 이 ADR은 최소 OS와 지원 기기 범위만 고정한다.

## 근거

- Android minSdk 29는 M2-008에서 확정된 운영 기준이다.
- iOS 16은 SwiftUI·WKWebView·Keychain·LocalAuthentication 경로를 추가 폴리필 없이 사용할 수 있는 하한으로, Xcode 프로젝트 초안 값과 동일하게 둔다.
- 버전 카탈로그(`gradle/libs.versions.toml`)와 iOS xcconfig·문서 표를 한 조합으로 유지한다. 최신 SDK만 단독으로 올리지 않는다.

## 로컬 서명

- `TEAM_ID`와 개인 서명 설정은 `iosApp/Configuration/Config.local.xcconfig`에만 둔다.
- 커밋되는 `Config.xcconfig`의 `TEAM_ID`는 비운다. `Info.plist`에는 비밀·Team ID를 넣지 않는다.
- App Store 등록 앱 ID는 `com.icecream.kwklasplus`, 위젯 확장은 `com.icecream.kwklasplus.libraryqr`로 고정하며 Team ID를 붙이지 않는다. 앱과 확장은 같은 로컬 `TEAM_ID`로 서명한다. Apple Developer에서 두 ID에 App Group `group.com.icecream.kwklasplus.Q2C928H69W`를 연결하고 공유 Keychain entitlement가 포함된 프로파일을 사용한다. 이전 개발용 번들 ID와는 별도 앱이므로 기존 개발 설치의 기본 Keychain 그룹 데이터는 자동 이전되지 않는다.
- 2026-10-08 배포팀에서 기존 `group.com.icecream.kwklasplus` 등록이 불가능하여 팀 전용 그룹으로 변경했다. 기존 개발 앱의 그룹 파일·설정은 삭제하지 않으며 새 앱에서는 위젯 표시 데이터를 다시 생성한다. 그룹에 SESSION 원문을 저장하지 않는 정책과 공유 Keychain 그룹은 유지한다. 롤백 시 앱·확장 entitlement와 Kotlin·Swift의 그룹 참조를 함께 되돌린다.

## 결과

- iOS 빌드 설정의 deployment target 단일 출처는 `Config.xcconfig`다.
- 기여자 환경 표는 `CONTRIBUTING.md`가 `libs.versions.toml`·wrapper·xcconfig와 일치해야 한다.
- CI의 iOS 빌드 기준은 `.github/workflows/ci.yml`의 Xcode 26.5다. iOS 27 SDK에만 있는 API는 `#if compiler(>=6.4)`로 감싸 Xcode 26.5에서도 빌드되게 하고, Xcode 27 동작은 로컬에서 검증한다. CI를 Xcode 27로 올릴 때는 GitHub 러너 정식 이미지 제공 여부와 Kotlin/Native 조합을 함께 검증한다.

# ADR-007: min Android/iOS 버전과 태블릿/회전 정책

- 상태: Accepted (M6-001 정책 고정 분량)
- 날짜: 2026-08-05

## 결정

| 플랫폼 | 최소 OS | 기기 | 비고 |
|---|---|---|---|
| Android | API 29 (minSdk) | 폰·태블릿 | compileSdk/targetSdk 37. 반응형 기준은 `docs/MIGRATION_ARCHITECTURE.md` §6.3 |
| iOS / iPadOS | 16.0 | iPhone·iPad (`TARGETED_DEVICE_FAMILY = 1,2`) | `iosApp/Configuration/Config.xcconfig`의 `IPHONEOS_DEPLOYMENT_TARGET` |

태블릿·회전·safe area의 UI 세부 패리티는 Android는 기존 정책, iOS는 M6-011에서 구현한다. 이 ADR은 최소 OS와 지원 기기 범위만 고정한다.

## 근거

- Android minSdk 29는 M2-008에서 확정된 운영 기준이다.
- iOS 16은 SwiftUI·WKWebView·Keychain·LocalAuthentication 경로를 추가 폴리필 없이 사용할 수 있는 하한으로, Xcode 프로젝트 초안 값과 동일하게 둔다.
- 버전 카탈로그(`gradle/libs.versions.toml`)와 iOS xcconfig·문서 표를 한 조합으로 유지한다. 최신 SDK만 단독으로 올리지 않는다.

## 로컬 서명

- `TEAM_ID`와 개인 서명 설정은 `iosApp/Configuration/Config.local.xcconfig`에만 둔다.
- 커밋되는 `Config.xcconfig`의 `TEAM_ID`는 비운다. `Info.plist`에는 비밀·Team ID를 넣지 않는다.

## 결과

- iOS 빌드 설정의 deployment target 단일 출처는 `Config.xcconfig`다.
- 기여자 환경 표는 `CONTRIBUTING.md`가 `libs.versions.toml`·wrapper·xcconfig와 일치해야 한다.

# ADR-005: 최소 OS와 폰·태블릿 window 정책

- 상태: 승인
- 기준일: 2026-07-17

## 지원 범위

- Android: API 29 이상, compile/target API 37
- iOS/iPadOS: 16.0 이상
- 기기: Android phone/tablet, iPhone/iPad
- Apple simulator/architecture: arm64만 지원

앱의 WidgetKit, LocalAuthentication, WKWebView, AVKit PIP 구현과 Swift 동시성 기반 어댑터의 유지보수 범위를 고려해 제품 하한을 iOS/iPadOS 16으로 둔다. Xcode 보일러플레이트의 18.2 deployment target은 16.0으로 낮춘다.

## 화면과 회전

- phone/iPhone의 일반 화면은 세로를 기본으로 한다.
- Android tablet과 iPad는 회전 및 창 크기 변경을 지원한다.
- 강의 PIP 진입을 위해 재생 화면은 플랫폼이 요구하는 회전·resize 동작을 허용한다.
- 화면 크기 변경 시 WebView/WKWebView 세션과 재생 상태를 보존하며 단순 configuration change로 공통 인증 상태를 재생성하지 않는다.
- 멀티윈도우와 split view에서 capability가 unavailable이면 명시적 상태를 반환하고 앱을 종료하지 않는다.

## 검증

- Android API 29 실기기 또는 emulator와 최신 target API에서 smoke/regression을 수행한다.
- iOS 16 simulator, 최신 iOS simulator, iPhone/iPad 실기기에서 WebView·생체인식·PIP·Widget을 구분해 검증한다.
- OS 하한 변경은 Kotlin/Compose/AGP/Xcode 조합 검증과 함께 별도 결정으로 다룬다.

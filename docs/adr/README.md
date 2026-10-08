# ADR 목록

왜 이 방식을 선택했는지 알고 싶을 때 아래 기록을 참고해 주세요. 파일명은 `ADR-NNN-topic.md`로 통일합니다. 상태가 `Proposed`인 문서는 아직 구현 결정이 아닙니다.

| 문서 | 결정 | 상태 |
|---|---|---|
| [ADR-001](ADR-001-ios-player-pip.md) | iOS 온라인 강의 WKWebView/PIP | Accepted; 실기기 검증 별도 |
| [ADR-002](ADR-002-ios-library-qr-widget.md) | 개인정보를 공유하지 않는 도서관 QR 위젯 | Accepted |
| [ADR-003](ADR-003-min-platform-versions.md) | Android/iOS 최소 지원 OS | Accepted |
| [ADR-004](ADR-004-android-academic-widgets.md) | Android 학사 시간표·캘린더 위젯 | 구현 기준; 실기기 검증 대기 |
| [ADR-005](ADR-005-ios-academic-widgets.md) | iOS 학사 WidgetKit 확장 | Accepted; 후속 새로고침은 [ADR-006](ADR-006-ios-widget-interactive-refresh.md) |
| [ADR-006](ADR-006-ios-widget-interactive-refresh.md) | iOS 17 캘린더 위젯 AppIntent 새로고침 | Accepted; 실기기 검증 별도 |
| [ADR-007](ADR-007-native-home-navigation.md) | 네이티브 홈 하단 탐색과 웹 라우터 연결 | Accepted; 웹 저장소 동시 배포 대기 |
| [ADR-008](ADR-008-native-onboarding.md) | 네이티브 최초 온보딩 | Accepted; 실기기 검증 대기 |
| [ADR-009](ADR-009-login-funnel.md) | 인증 후 설정을 포함한 로그인 퍼널 | Accepted; 약관 전문 및 실기기 검증 대기 |
| [ADR-010](ADR-010-ios-liquid-glass.md) | iOS 26/27 Liquid Glass 적용 범위 | Accepted; 실기기 검증 대기 |
| [ADR-011](ADR-011-deadline-notifications.md) | DEADLINE 최신 조회 알림·웹 토글/네이티브 권한 시트 | Accepted; DEADLINE 전용 구현·자동 검증, 실기기/배포 검증 대기 |
| [ADR-012](ADR-012-deadline-notifications-native-implementation.md) | DEADLINE 알림 구현 구조 및 검증 기록 | ADR-011 구현 기록; 실기기/배포 검증 별도 |
| [ADR-013](ADR-013-deadline-notifications-webview-contract.md) | DEADLINE WebView 토글·Native 권한 시트 계약 | ADR-011 구현 계약; 웹 호환 배포 검증 별도 |

현재 코드의 모듈 경계는 [아키텍처](../ARCHITECTURE.md), M1~M7 당시 진행 상황은 [마이그레이션 기록](../kmp-migration/kmp_migration_tasks.md)에 있어요.

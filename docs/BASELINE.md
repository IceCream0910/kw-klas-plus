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
- `androidApp`: KMP + Compose 기반 신규 Android 앱
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
| Compose Multiplatform | 1.11.1 |
| Android compile SDK | 신규 앱 36, legacy 37 |
| Android min SDK | 신규 앱 24, legacy 29 |

현재 Windows 환경의 전역 `JAVA_HOME`은 유효하지 않은 Android Studio JRE를 가리킨다. CLI 검증에서는 설치된 JDK 21 경로를 명시한다. 저장소에는 로컬 JDK 절대 경로를 기록하지 않는다.

## 검증 기록

- 2026-07-16: 사용자가 Android 빌드 성공을 수동 확인함
- 자동 빌드 로그의 최종 성공 여부는 사용자 확인으로 대체함
- 실기기 기능 패리티는 아직 검증 전


# KLAS+ 기여 가이드

버그나 기능 제안은 [이슈](https://github.com/IceCream0910/kw-klas-plus/issues)에 남겨 주세요. 본인 계정의 세션 등 개인정보가 포함된 로그나 보안 취약점은 [이메일](mailto:hey@yuntae.in)로 보내주세요.

## 작업 범위

새 작업은 GitHub Issue와 PR에서 추적해요. 문서를 찾을 때는 다음처럼 나눠 보세요.

- **구조와 인증·저장 키·브리지 계약:** [아키텍처](docs/ARCHITECTURE.md).
- **왜 이렇게 만들었는지:** [ADR 목록](docs/adr/README.md)
- **KMP 마이그레이션 기록:** [작업표](docs/kmp-migration/kmp_migration_tasks.md)와 [패리티 표](docs/kmp-migration/feature_parity_matrix.md). 새 작업의 백로그로 사용하지 않아요.

기존 동작·설정·서명·버전 코드를 지켜주세요. 웹 화면은 [별도 저장소](https://github.com/IceCream0910/kw-klas-plus-webview)에서 배포하므로 브리지를 바꿀 때는 양쪽 테스트와 구버전 호환 경로가 필요합니다. `commonMain`에는 UI 타입을 넣지 마세요.

인증·저장 키·브리지 계약을 바꾸면 아키텍처 문서도 실제 구현과 함께 갱신해 주세요.

## 개발 환경

버전은 [카탈로그](gradle/libs.versions.toml), [Gradle wrapper](gradle/wrapper/gradle-wrapper.properties), [iOS 설정](iosApp/Configuration/Config.xcconfig)을 기준으로 재확인해 주세요.

| 항목 | 기준                                |
|---|-----------------------------------|
| JDK / Gradle | 21 / 9.6.1                        |
| Kotlin / AGP / Ktor | 2.4.0 / 9.3.2 / 3.5.0             |
| Android | minSdk 29, compileSdk/targetSdk 37 |
| iOS/iPadOS | 16.0 이상, macOS와 Xcode 필요          |

Android 빌드와 공통/Android JVM 테스트:

```sh
./gradlew :androidApp:assembleDebug
./gradlew :shared:testAndroidHostTest :androidApp:testDebugUnitTest
```

Windows에서는 `.\gradlew.bat`를 쓰세요. iOS는 macOS에서 [`iosApp.xcodeproj`](iosApp/iosApp.xcodeproj)를 열고 `:shared:iosSimulatorArm64Test`와 Xcode 테스트를 실행합니다. 기기 서명에는 [예제 설정](iosApp/Configuration/Config.local.xcconfig.example)을 개인 `Config.local.xcconfig`로 복사해 `TEAM_ID`를 넣으세요. 로컬 설정·서명 키·비밀값은 커밋하지 마세요.

## 작업 절차와 검증

1. 작업할 항목을 [GitHub Issue](https://github.com/IceCream0910/kw-klas-plus/issues)에 먼저 등록해 주세요. 제목은 아래의 커밋 메시지 컨벤션을 따르고, 본문에는 목표나 재현 절차, 기대 동작, Android·iOS·Web 영향 등을 자유롭게 적어 주세요. label은 플랫폼(Android, iOS, Web)으로 구분하여 설정해주세요.
2. 메인테이너가 이슈를 검토해 [GitHub Project 백로그 보드](https://github.com/users/IceCream0910/projects/5)에 올립니다.
3. 작업을 마치면 이슈를 연결한 PR을 열어 주세요. 변경 이유, 테스트 결과, 호환성·보안 영향과 롤백 방법을 남기고, 설계가 바뀌었다면 문서도 갱신해야 합니다.

두 플랫폼을 따로 검증할 수 있다면 PR도 나눠 주세요. 코드 주석은 꼭 필요할 경우에 한해 한국어로 작성해주세요.

## 브랜치와 커밋

PR 기준 브랜치는 이슈/릴리스에서 확인해 주세요. 이슈 제목·커밋 메시지·PR 제목은 모두 `type(scope): 한국어 요약` 형식을 따라 주세요. scope는 `android`, `ios`, `shared`로 구분하며,  `docs`와 같이 플랫폼 범위가 불명확한 type에서는 생략해도 좋아요.

| type | 사용 시점                | 예시 |
|---|----------------------|---|
| `feat` | 사용자 기능 추가            | `feat(android): 시간표 필터 추가` |
| `fix` | 버그/이슈 수정             | `fix(ios): 세션 만료 후 로그인 화면 복귀` |
| `refactor` | 동작 변화 없는 코드 구조 개선    | `refactor(shared): 세션 상태 분리` |
| `chore` | 다른 유형에 속하지 않는 유지보수 작업 | `chore: 미사용 개발 설정 정리` |
| `test` | 테스트 추가·수정            | `test(shared): 쿠키 만료 경계 추가` |
| `docs` | 문서 변경                | `docs: 기여 가이드 갱신` |
| `ci` | CI 워크플로              | `ci: iOS 테스트 작업 보강` |

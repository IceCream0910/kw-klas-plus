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
| Xcode | CI 26.5, iOS 27 SDK 기능 확인은 27     |

Android 빌드와 공통/Android JVM 테스트:

```sh
./gradlew :androidApp:assembleDebug
./gradlew :shared:testAndroidHostTest :androidApp:testDebugUnitTest
```

Windows에서는 `.\gradlew.bat`를 쓰세요. iOS는 macOS에서 [`iosApp.xcodeproj`](iosApp/iosApp.xcodeproj)를 열고 `:shared:iosSimulatorArm64Test`와 Xcode `iosAppUnitTests` 스킴의 테스트를 실행합니다.

### iOS 로컬 서명

먼저 [예제 설정](iosApp/Configuration/Config.local.xcconfig.example)을 복사하세요. `Config.local.xcconfig`는 Git에서 제외되며, 이 파일과 서명 키·비밀값은 커밋하지 마세요.

```sh
cp iosApp/Configuration/Config.local.xcconfig.example iosApp/Configuration/Config.local.xcconfig
```

서명할 Apple 팀에 따라 설정이 달라요.

| 상황 | `Config.local.xcconfig` | 설치되는 앱 |
|---|---|---|
| 시뮬레이터만 사용 | 수정 불필요 | 배포 식별자 그대로 |
| 배포 팀 멤버 | `TEAM_ID`만 배포 팀 ID로 입력 | 배포 앱과 같은 식별자 |
| 자기 팀(Personal Team 포함)으로 실기기 테스트 | `TEAM_ID`, `APP_BUNDLE_IDENTIFIER`, `APP_GROUP_IDENTIFIER` 입력 | 배포 앱과 별도 앱 |

서명 에러가 나도 Xcode의 Signing & Capabilities 화면에서 Team을 직접 고르지 마세요. 고른 Team ID가 `project.pbxproj`에 기록되어 다른 작업자의 `TEAM_ID` 설정을 덮어씁니다. 이런 변경이 생기면 커밋하지 말고 되돌리세요.

배포용 Bundle ID는 앱 `com.icecream.kwklasplus`, 위젯 `com.icecream.kwklasplus.libraryqr`이고 App Group은 `group.com.icecream.kwklasplus.Q2C928H69W`입니다. 이 식별자는 배포 팀에 등록되어 있어 다른 팀에서는 `TEAM_ID`만 바꿔서 사용할 수 없습니다.

자기 팀으로 실기기 테스트할 때는 Xcode → Settings → Accounts에 계정을 추가하고, 그 팀이 등록할 수 있는 고유한 값을 넣으세요. Bundle ID와 App Group은 Apple 전체에서 한 팀만 등록할 수 있으므로 자신의 이름 등으로 구분하세요.

```
TEAM_ID = ABCDE12345
APP_BUNDLE_IDENTIFIER = com.yourname.kwklasplus
APP_GROUP_IDENTIFIER = group.com.yourname.kwklasplus
```

위젯·테스트 Bundle ID(`.libraryqr`, `.tests`, `.uitests`), 양 타깃 entitlement의 App Group, 앱·위젯 `Info.plist`의 `KLASAppGroupIdentifier`가 함께 바뀌고 Kotlin·Swift 저장소는 이 값을 읽어요. 공유 Keychain 그룹 `academic-session`에는 서명한 팀의 접두사가 자동으로 붙어요. 이렇게 설치한 앱은 배포 앱과 Keychain·App Group 데이터를 공유하지 않으며, Personal Team 프로파일은 Apple 정책에 따라 7일마다 만료되므로 Xcode에서 다시 실행해 갱신하세요.

서명 없는 컴파일 확인이나 시뮬레이터 테스트 결과만으로 실기기의 Keychain·App Group 동작을 검증했다고 간주하지 마세요. 상세 계약은 [ADR-003의 로컬 서명 항목](docs/adr/ADR-003-min-platform-versions.md#로컬-서명)을 확인하세요.

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

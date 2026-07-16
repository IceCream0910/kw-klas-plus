This is a Kotlin Multiplatform project targeting Android, iOS.

> 이 저장소는 기존 Android View 앱을 KMP + Compose Multiplatform으로 점진 이전하기 위한 작업 공간입니다. Android 기능 패리티가 iOS 확장보다 우선합니다.

### Migration documents

- [작업 및 호환성 규칙](./AGENTS.md)
- [단계별 구현 백로그](./TASKS.md)
- [목표 아키텍처와 마이그레이션 전략](./docs/MIGRATION_ARCHITECTURE.md)
- [기능·화면·브리지 패리티 매트릭스](./docs/FEATURE_PARITY_MATRIX.md)
- [고정 기준선과 툴체인](./docs/BASELINE.md)

* [/iosApp](./iosApp/iosApp) contains an iOS application. Even if you’re sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

* [/sharedLogic](./sharedLogic/src) is for the code that will be shared between app targets in the project.
  The most important subfolder is [commonMain](./sharedLogic/src/commonMain/kotlin). If preferred, you
  can add code to the platform-specific folders here too.

* [/sharedUI](./sharedUI/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - [commonMain](./sharedUI/src/commonMain/kotlin) is for code that’s common for all targets.
  - `androidMain` is currently the only platform source set configured in this module.
  - Adding `iosMain` and iOS targets is a required migration task; see `M2-003` in [TASKS.md](./TASKS.md).

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and options:

- Android app: `./gradlew :androidApp:assembleDebug`
- iOS app: open the [/iosApp](./iosApp) directory in Xcode and run it from there.

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- Android tests: `./gradlew :sharedUI:testAndroidHostTest :sharedLogic:testAndroidHostTest`
- iOS tests: `./gradlew :sharedLogic:iosSimulatorArm64Test`

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…

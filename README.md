This is a Kotlin Multiplatform project targeting Android and iOS.

> 이 저장소는 기존 Android View 앱을 KMP 공통 코어 + Android Compose + iOS SwiftUI 구조로 점진 이전하기 위한 작업 공간입니다. Android 기능 패리티가 iOS 확장보다 우선합니다.

### Migration documents

- [작업 및 호환성 규칙](./AGENTS.md)
- [단계별 구현 백로그](./TASKS.md)
- [목표 아키텍처와 마이그레이션 전략](./docs/MIGRATION_ARCHITECTURE.md)
- [기능·화면·브리지 패리티 매트릭스](./docs/FEATURE_PARITY_MATRIX.md)
- [고정 기준선과 툴체인](./docs/BASELINE.md)

* [/androidApp](./androidApp/src/main) contains Android entry points, Compose UI, WebView adapters, and Android system integrations.

* [/iosApp](./iosApp/iosApp) contains the SwiftUI application, WKWebView adapters, and iOS system integrations.

* [/shared](./shared/src) contains Kotlin code shared by both applications.
  - [commonMain](./shared/src/commonMain/kotlin) owns network APIs, models, entities, use cases, platform-neutral state/ViewModels, and ports.
  - [androidMain](./shared/src/androidMain/kotlin) owns Android-specific implementations of shared APIs, including the Ktor OkHttp engine.
  - [iosMain](./shared/src/iosMain/kotlin) owns iOS-specific implementations of shared APIs, including the Ktor Darwin engine.

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and options:

- Android app: `./gradlew :androidApp:assembleDebug`
- iOS app: open the [/iosApp](./iosApp) directory in Xcode and run it from there.

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- Android tests: `./gradlew :shared:testAndroidHostTest :androidApp:testDebugUnitTest`
- iOS tests: `./gradlew :shared:iosSimulatorArm64Test`

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…

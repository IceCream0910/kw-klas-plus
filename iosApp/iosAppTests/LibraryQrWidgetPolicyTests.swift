import Foundation
import Shared
import XCTest
@testable import kw_klas_plus

final class LibraryQrWidgetPolicyTests: XCTestCase {

    func testDeepLinkSpecifications() {
        let qrUrl = LibraryQrWidgetFixtures.deepLinkQr
        XCTAssertEqual(qrUrl.scheme, "kwklasplus")
        XCTAssertEqual(qrUrl.host, "library-qr")
        XCTAssertEqual(qrUrl.path, "")
    }

    func testConfiguredWidgetOpensQrDirectly() {
        XCTAssertEqual(
            LibraryQrWidgetFixtures.resolveRoute(
                url: LibraryQrWidgetFixtures.deepLinkQr,
                hasConfiguredCredentials: true
            ),
            .openQrDirectly
        )
    }

    func testUnconfiguredWidgetRoutesToSettings() {
        XCTAssertEqual(
            LibraryQrWidgetFixtures.resolveRoute(
                url: LibraryQrWidgetFixtures.deepLinkQr,
                hasConfiguredCredentials: false
            ),
            .routeToSettings
        )
    }

    func testUnknownPathIsIgnored() {
        let settingsUrl = URL(string: "kwklasplus://library-qr/settings")!
        XCTAssertFalse(LibraryQrRouter.isLibraryQrURL(settingsUrl))
        XCTAssertNil(
            LibraryQrWidgetFixtures.resolveRoute(
                url: settingsUrl,
                hasConfiguredCredentials: true
            )
        )
    }

    func testSettingsCanSaveRequiresEveryField() {
        XCTAssertFalse(LibraryQrSettingsUiState(studentNumber: "", password: "password", phone: "010").canSave)
        XCTAssertFalse(LibraryQrSettingsUiState(studentNumber: "2026000001", password: "", phone: "010").canSave)
        XCTAssertFalse(LibraryQrSettingsUiState(studentNumber: "2026000001", password: "password", phone: "").canSave)
        XCTAssertTrue(LibraryQrSettingsUiState(studentNumber: "2026000001", password: "password", phone: "010").canSave)
    }

    func testUiStateErrorDefaults() {
        let state = LibraryQrUiState()
        XCTAssertFalse(state.isError)
        XCTAssertEqual(state.errorMessage, "")
    }

    @MainActor
    func testResignActiveRestoresBrightnessWhenQrPresented() async {
        let suite = "com.icecream.kwklasplus.test.libraryqr.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }
        let keychain = IosKeychainSecureStore(service: suite)
        let authRuntime = IosAuthRuntime.companion.createForTests(defaults: defaults, secureStore: keychain)
        let lockController = AppLockController(store: authRuntime.dependencies.appLockStore)
        let controller = LibraryQrController(
            service: authRuntime.dependencies.libraryService,
            appLock: lockController
        )

        await withCheckedContinuation { continuation in
            authRuntime.dependencies.libraryService.saveCredentials(
                studentNumber: "2026000001",
                phoneNumber: "01012345678",
                password: "password",
                onDone: { continuation.resume() }
            )
        }

        controller.presentQrFromApp()
        XCTAssertEqual(controller.presentedSheet, .qr(isWidgetEntry: false))
        XCTAssertNotNil(controller.originalBrightness)

        controller.handleAppWillResignActive()
        XCTAssertNil(controller.originalBrightness)
    }

    @MainActor
    func testResignActiveDoesNothingWhenQrNotPresented() {
        let suite = "com.icecream.kwklasplus.test.libraryqr.nosheet.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }
        let keychain = IosKeychainSecureStore(service: suite)
        let authRuntime = IosAuthRuntime.companion.createForTests(defaults: defaults, secureStore: keychain)
        let lockController = AppLockController(store: authRuntime.dependencies.appLockStore)
        let controller = LibraryQrController(
            service: authRuntime.dependencies.libraryService,
            appLock: lockController
        )

        controller.handleAppWillResignActive()
        XCTAssertNil(controller.originalBrightness)
    }

    @MainActor
    func testUnconfiguredWidgetOpensSettingsDirectlyWhenAppLockDisabled() {
        let suite = "com.icecream.kwklasplus.test.libraryqr.unconfig.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }
        let keychain = IosKeychainSecureStore(service: suite)
        let authRuntime = IosAuthRuntime.companion.createForTests(defaults: defaults, secureStore: keychain)
        let lockController = AppLockController(store: authRuntime.dependencies.appLockStore)
        let controller = LibraryQrController(
            service: authRuntime.dependencies.libraryService,
            appLock: lockController
        )

        let handled = controller.handleOpenURL(LibraryQrWidgetFixtures.deepLinkQr)
        XCTAssertTrue(handled)
        XCTAssertEqual(controller.presentedSheet, .settings)
    }

    @MainActor
    func testUnconfiguredWidgetRequiresUnlockWhenAppLockEnabled() {
        let suite = "com.icecream.kwklasplus.test.libraryqr.unconfig.lock.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }
        let keychain = IosKeychainSecureStore(service: suite)
        let authRuntime = IosAuthRuntime.companion.createForTests(defaults: defaults, secureStore: keychain)
        let lockController = AppLockController(store: authRuntime.dependencies.appLockStore)
        let controller = LibraryQrController(
            service: authRuntime.dependencies.libraryService,
            appLock: lockController
        )

        lockController.store.savePassword(password: "123456")
        lockController.store.setEnabled(enabled: true)
        lockController.store.isUnlocked = false

        let handled = controller.handleOpenURL(LibraryQrWidgetFixtures.deepLinkQr)
        XCTAssertTrue(handled)

        XCTAssertNil(controller.presentedSheet)
        XCTAssertEqual(lockController.mode, .unlock)

        [1, 2, 3, 4, 5, 6].forEach { lockController.appendDigit($0) }

        XCTAssertEqual(controller.presentedSheet, .settings)
    }

    @MainActor
    func testLoggedOutWidgetShowsNoticeAndDoesNotOpenSettings() {
        let suite = "com.icecream.kwklasplus.test.libraryqr.loggedout.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }
        let keychain = IosKeychainSecureStore(service: suite)
        let authRuntime = IosAuthRuntime.companion.createForTests(defaults: defaults, secureStore: keychain)
        let lockController = AppLockController(store: authRuntime.dependencies.appLockStore)
        let controller = LibraryQrController(
            service: authRuntime.dependencies.libraryService,
            appLock: lockController,
            isSessionAuthenticated: { false }
        )

        let handled = controller.handleOpenURL(LibraryQrWidgetFixtures.deepLinkQr)
        XCTAssertTrue(handled)
        XCTAssertNil(controller.presentedSheet)
    }

    @MainActor
    func testFetchErrorKeepsSheetOpenAndShowsErrorUI() async {
        let suite = "com.icecream.kwklasplus.test.libraryqr.fetcherror.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }
        let keychain = IosKeychainSecureStore(service: suite)
        let authRuntime = IosAuthRuntime.companion.createForTests(defaults: defaults, secureStore: keychain)
        let lockController = AppLockController(store: authRuntime.dependencies.appLockStore)
        let controller = LibraryQrController(
            service: authRuntime.dependencies.libraryService,
            appLock: lockController,
            fetchQrData: { completion in
                completion(LibraryQrResultInvalidResponse())
            }
        )

        await withCheckedContinuation { continuation in
            authRuntime.dependencies.libraryService.saveCredentials(
                studentNumber: "2026000001",
                phoneNumber: "01012345678",
                password: "password",
                onDone: { continuation.resume() }
            )
        }

        controller.presentQrFromApp()
        XCTAssertEqual(controller.presentedSheet, .qr(isWidgetEntry: false))

        await Task.yield()

        XCTAssertFalse(controller.qrState.loading)
        XCTAssertTrue(controller.qrState.isError)
        XCTAssertFalse(controller.isTimerRunning)
        XCTAssertNotNil(controller.originalBrightness)
        XCTAssertEqual(controller.presentedSheet, .qr(isWidgetEntry: false))

        controller.presentedSheet = nil
        controller.onSheetDismissed()
        XCTAssertNil(controller.originalBrightness)
    }

    @MainActor
    func testFetchSuccessStartsTimerAndRetainsBrightness() async {
        let suite = "com.icecream.kwklasplus.test.libraryqr.success.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }
        let keychain = IosKeychainSecureStore(service: suite)
        let authRuntime = IosAuthRuntime.companion.createForTests(defaults: defaults, secureStore: keychain)
        let lockController = AppLockController(store: authRuntime.dependencies.appLockStore)
        let controller = LibraryQrController(
            service: authRuntime.dependencies.libraryService,
            appLock: lockController,
            fetchQrData: { completion in
                completion(LibraryQrResultSuccess(data: LibraryQrData(values: [
                    "qr_code": "12345678",
                    "user_name": "홍길동",
                    "user_code": "2026000001",
                    "user_deptName": "컴퓨터정보공학부",
                    "user_patName": "학부생"
                ])))
            }
        )

        await withCheckedContinuation { continuation in
            authRuntime.dependencies.libraryService.saveCredentials(
                studentNumber: "2026000001",
                phoneNumber: "01012345678",
                password: "password",
                onDone: { continuation.resume() }
            )
        }

        controller.presentQrFromApp()
        await Task.yield()

        XCTAssertFalse(controller.qrState.loading)
        XCTAssertFalse(controller.qrState.isError)
        XCTAssertNotNil(controller.qrState.image)
        XCTAssertTrue(controller.isTimerRunning)
        XCTAssertNotNil(controller.originalBrightness)
    }

    @MainActor
    func testRetryAfterErrorStartsTimerOnSuccess() async {
        let suite = "com.icecream.kwklasplus.test.libraryqr.retry.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }
        let keychain = IosKeychainSecureStore(service: suite)
        let authRuntime = IosAuthRuntime.companion.createForTests(defaults: defaults, secureStore: keychain)
        let lockController = AppLockController(store: authRuntime.dependencies.appLockStore)
        var shouldSucceed = false
        let controller = LibraryQrController(
            service: authRuntime.dependencies.libraryService,
            appLock: lockController,
            fetchQrData: { completion in
                if shouldSucceed {
                    completion(LibraryQrResultSuccess(data: LibraryQrData(values: [
                        "qr_code": "12345678",
                        "user_name": "홍길동",
                        "user_code": "2026000001",
                        "user_deptName": "컴퓨터정보공학부",
                        "user_patName": "학부생"
                    ])))
                } else {
                    completion(LibraryQrResultInvalidResponse())
                }
            }
        )

        await withCheckedContinuation { continuation in
            authRuntime.dependencies.libraryService.saveCredentials(
                studentNumber: "2026000001",
                phoneNumber: "01012345678",
                password: "password",
                onDone: { continuation.resume() }
            )
        }

        controller.presentQrFromApp()
        await Task.yield()

        XCTAssertTrue(controller.qrState.isError)
        XCTAssertFalse(controller.isTimerRunning)
        XCTAssertNotNil(controller.originalBrightness)

        shouldSucceed = true
        controller.refreshQr()
        XCTAssertNotNil(controller.originalBrightness)

        await Task.yield()
        XCTAssertFalse(controller.qrState.isError)
        XCTAssertTrue(controller.isTimerRunning)
    }

    @MainActor
    func testFetchErrorResetSettingsRequiresUnlockWhenAppLockEnabled() async {
        let suite = "com.icecream.kwklasplus.test.libraryqr.errorlock.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }
        let keychain = IosKeychainSecureStore(service: suite)
        let authRuntime = IosAuthRuntime.companion.createForTests(defaults: defaults, secureStore: keychain)
        let lockController = AppLockController(store: authRuntime.dependencies.appLockStore)
        let controller = LibraryQrController(
            service: authRuntime.dependencies.libraryService,
            appLock: lockController,
            fetchQrData: { completion in
                completion(LibraryQrResultInvalidResponse())
            }
        )

        lockController.store.savePassword(password: "123456")
        lockController.store.setEnabled(enabled: true)
        lockController.store.isUnlocked = false

        await withCheckedContinuation { continuation in
            authRuntime.dependencies.libraryService.saveCredentials(
                studentNumber: "2026000001",
                phoneNumber: "01012345678",
                password: "password",
                onDone: { continuation.resume() }
            )
        }

        _ = controller.handleOpenURL(LibraryQrWidgetFixtures.deepLinkQr)
        XCTAssertEqual(controller.presentedSheet, .qr(isWidgetEntry: true))

        await Task.yield()
        XCTAssertTrue(controller.qrState.isError)

        controller.presentSettingsFromQrError()
        XCTAssertEqual(lockController.mode, .unlock)
        XCTAssertNil(controller.presentedSheet)
        XCTAssertFalse(controller.isQrBypassActive)

        [1, 2, 3, 4, 5, 6].forEach { lockController.appendDigit($0) }
        XCTAssertEqual(controller.presentedSheet, .settings)
    }

    @MainActor
    func testResetSettingsFromFetchErrorThenSaveAndDismissRestoresBrightness() async {
        let suite = "com.icecream.kwklasplus.test.libraryqr.resetsave.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }
        let keychain = IosKeychainSecureStore(service: suite)
        let authRuntime = IosAuthRuntime.companion.createForTests(defaults: defaults, secureStore: keychain)
        let lockController = AppLockController(store: authRuntime.dependencies.appLockStore)
        var shouldSucceed = false
        let controller = LibraryQrController(
            service: authRuntime.dependencies.libraryService,
            appLock: lockController,
            fetchQrData: { completion in
                if shouldSucceed {
                    completion(LibraryQrResultSuccess(data: LibraryQrData(values: [
                        "qr_code": "12345678",
                        "user_name": "홍길동",
                        "user_code": "2026000001",
                        "user_deptName": "컴퓨터정보공학부",
                        "user_patName": "학부생"
                    ])))
                } else {
                    completion(LibraryQrResultInvalidResponse())
                }
            }
        )

        await withCheckedContinuation { continuation in
            authRuntime.dependencies.libraryService.saveCredentials(
                studentNumber: "2026000001",
                phoneNumber: "01012345678",
                password: "wrong_password",
                onDone: { continuation.resume() }
            )
        }

        // 1. fetch 실패
        controller.presentQrFromApp()
        XCTAssertEqual(controller.presentedSheet, .qr(isWidgetEntry: false))
        await Task.yield()
        XCTAssertTrue(controller.qrState.isError)
        XCTAssertNotNil(controller.originalBrightness)

        // 2. 출입증 재설정 클릭
        controller.presentSettingsFromQrError()
        XCTAssertEqual(controller.presentedSheet, .settings)

        // 3. 올바른 정보 입력 후 저장
        controller.settingsState = LibraryQrSettingsUiState(
            studentNumber: "2026000001",
            password: "correct_password",
            phone: "01012345678"
        )
        shouldSucceed = true
        controller.saveSettings()

        for _ in 0..<30 {
            if controller.presentedSheet == .qr(isWidgetEntry: false) && !controller.qrState.isError { break }
            try? await Task.sleep(nanoseconds: 10_000_000)
        }

        XCTAssertEqual(controller.presentedSheet, .qr(isWidgetEntry: false))
        XCTAssertFalse(controller.qrState.isError)
        XCTAssertTrue(controller.isTimerRunning)
        XCTAssertNotNil(controller.originalBrightness)

        // 4. sheet를 아래로 스와이프하여 내림
        controller.presentedSheet = nil
        controller.onSheetDismissed()

        // 5. 밝기가 정상 복원됨
        XCTAssertNil(controller.originalBrightness)
        XCTAssertFalse(controller.isTimerRunning)
    }

    @MainActor
    func testSettingsFromAppSettingsDismissesWithoutOpeningQr() async {
        let suite = "com.icecream.kwklasplus.test.libraryqr.appsettings.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }
        let keychain = IosKeychainSecureStore(service: suite)
        let authRuntime = IosAuthRuntime.companion.createForTests(defaults: defaults, secureStore: keychain)
        let lockController = AppLockController(store: authRuntime.dependencies.appLockStore)
        let controller = LibraryQrController(
            service: authRuntime.dependencies.libraryService,
            appLock: lockController
        )

        // 앱 설정 화면에서 출입증 설정 진입
        controller.presentSettingsFromApp()
        XCTAssertEqual(controller.presentedSheet, .settings)

        controller.settingsState = LibraryQrSettingsUiState(
            studentNumber: "2026000001",
            password: "test_password",
            phone: "01012345678"
        )
        controller.saveSettings()

        // 저장 후 QR 화면이 열리지 않고 sheet가 닫혀야 함
        try? await Task.sleep(nanoseconds: 50_000_000)
        XCTAssertNil(controller.presentedSheet)
        XCTAssertNil(controller.originalBrightness)
    }
}

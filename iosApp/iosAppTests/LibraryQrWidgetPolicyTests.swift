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

    func testUnconfiguredWidgetShowsNoticeOnly() {
        XCTAssertEqual(
            LibraryQrWidgetFixtures.resolveRoute(
                url: LibraryQrWidgetFixtures.deepLinkQr,
                hasConfiguredCredentials: false
            ),
            .showUnconfiguredNotice
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

    func testFetchErrorShowsSheetAlertInAppAndToastThenLockForWidget() {
        XCTAssertEqual(
            LibraryQrFetchErrorPolicy.action(isWidgetEntry: false),
            .showSheetAlert
        )
        XCTAssertEqual(
            LibraryQrFetchErrorPolicy.action(isWidgetEntry: true),
            .toastThenRestoreLock
        )
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
}

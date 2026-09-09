import Foundation
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
}

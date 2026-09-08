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

    func testConfiguredAppLockBypassPolicy() {
        // 설정이 완료된 상태에서는 앱 잠금이 켜져 있어도 잠금 예외로 즉시 QR 시트 팝업
        let decisionLocked = LibraryQrWidgetFixtures.resolveRoute(
            url: LibraryQrWidgetFixtures.deepLinkQr,
            hasConfiguredCredentials: true,
            isAppLockActive: true
        )
        XCTAssertEqual(decisionLocked, .openQrDirectly(isAppLockBypassed: true))

        // 앱 잠금이 꺼져 있는 경우 일반적인 즉시 팝업
        let decisionUnlocked = LibraryQrWidgetFixtures.resolveRoute(
            url: LibraryQrWidgetFixtures.deepLinkQr,
            hasConfiguredCredentials: true,
            isAppLockActive: false
        )
        XCTAssertEqual(decisionUnlocked, .openQrDirectly(isAppLockBypassed: false))
    }

    func testUnconfiguredWidgetShowsNoticeOnly() {
        let locked = LibraryQrWidgetFixtures.resolveRoute(
            url: LibraryQrWidgetFixtures.deepLinkQr,
            hasConfiguredCredentials: false,
            isAppLockActive: true
        )
        XCTAssertEqual(locked, .showUnconfiguredNotice)

        let unlocked = LibraryQrWidgetFixtures.resolveRoute(
            url: LibraryQrWidgetFixtures.deepLinkQr,
            hasConfiguredCredentials: false,
            isAppLockActive: false
        )
        XCTAssertEqual(unlocked, .showUnconfiguredNotice)
    }

    func testUnknownPathIsIgnored() {
        let settingsUrl = URL(string: "kwklasplus://library-qr/settings")!
        XCTAssertFalse(LibraryQrRouter.isLibraryQrURL(settingsUrl))
        XCTAssertNil(
            LibraryQrWidgetFixtures.resolveRoute(
                url: settingsUrl,
                hasConfiguredCredentials: true,
                isAppLockActive: true
            )
        )
    }

    func testPendingDeepLinkWaitsForKlasLogin() {
        var gate = LibraryQrPendingGate()
        gate.remember(LibraryQrRouter.qrURL)
        XCTAssertEqual(gate.pendingURL, LibraryQrRouter.qrURL)
        XCTAssertEqual(gate.consume(), LibraryQrRouter.qrURL)
        XCTAssertNil(gate.pendingURL)
    }

    func testAuthenticatedDeepLinkIsReturnedImmediately() {
        var gate = LibraryQrPendingGate()
        XCTAssertEqual(
            gate.receive(url: LibraryQrRouter.qrURL, isAuthenticated: true),
            LibraryQrRouter.qrURL
        )
        XCTAssertNil(gate.pendingURL)
    }

    func testPendingUrlSurvivesUntilConsumed() {
        var gate = LibraryQrPendingGate()
        gate.remember(LibraryQrRouter.qrURL)
        gate.remember(LibraryQrRouter.qrURL)
        XCTAssertEqual(gate.pendingURL, LibraryQrRouter.qrURL)
        XCTAssertNil(gate.takePending(isAuthenticated: false))
        XCTAssertEqual(gate.pendingURL, LibraryQrRouter.qrURL)
        XCTAssertEqual(gate.takePending(isAuthenticated: true), LibraryQrRouter.qrURL)
        XCTAssertNil(gate.pendingURL)
    }

    func testSettingsCanSaveRequiresEveryField() {
        XCTAssertFalse(LibraryQrSettingsUiState(studentNumber: "", password: "password", phone: "010").canSave)
        XCTAssertFalse(LibraryQrSettingsUiState(studentNumber: "2026000001", password: "", phone: "010").canSave)
        XCTAssertFalse(LibraryQrSettingsUiState(studentNumber: "2026000001", password: "password", phone: "").canSave)
        XCTAssertTrue(LibraryQrSettingsUiState(studentNumber: "2026000001", password: "password", phone: "010").canSave)
    }
}

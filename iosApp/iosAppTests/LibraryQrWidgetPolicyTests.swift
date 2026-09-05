import Foundation
import XCTest

final class LibraryQrWidgetPolicyTests: XCTestCase {

    func testDeepLinkSpecifications() {
        let qrUrl = LibraryQrWidgetFixtures.deepLinkQr
        XCTAssertEqual(qrUrl.scheme, "kwklasplus")
        XCTAssertEqual(qrUrl.host, "library-qr")
        XCTAssertEqual(qrUrl.path, "")

        let settingsUrl = LibraryQrWidgetFixtures.deepLinkSettings
        XCTAssertEqual(settingsUrl.scheme, "kwklasplus")
        XCTAssertEqual(settingsUrl.host, "library-qr")
        XCTAssertEqual(settingsUrl.path, "/settings")
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

    func testUnconfiguredRequiresUnlockThenSettings() {
        // 도서관 자격증명이 미설정된 경우, 인증 없이 설정 화면을 열지 않고 잠금 해제를 거치도록 분기
        let decision = LibraryQrWidgetFixtures.resolveRoute(
            url: LibraryQrWidgetFixtures.deepLinkQr,
            hasConfiguredCredentials: false,
            isAppLockActive: true
        )
        XCTAssertEqual(decision, .requireUnlockThenOpenSettings)
    }

    func testExplicitSettingsDeepLinkRequiresUnlock() {
        // settings 딥링크로의 직접 접근은 항상 잠금 해제를 거쳐야 함
        let decision = LibraryQrWidgetFixtures.resolveRoute(
            url: LibraryQrWidgetFixtures.deepLinkSettings,
            hasConfiguredCredentials: true,
            isAppLockActive: true
        )
        XCTAssertEqual(decision, .requireUnlockThenOpenSettings)
    }

    func testSessionLifecycleCleansUpMemoryAndMaintainsLock() {
        var state = LibraryQrSessionState(
            screenBrightness: 0.4,
            isAppLocked: true
        )

        // 1. QR 바텀시트 진입: 밝기 1.0 및 QR/개인정보 로드
        state.enterSheet(
            qrData: "KW_LIB_QR_SAMPLE_DATA_12345",
            userName: "홍길동",
            userCode: "2020202020"
        )
        XCTAssertEqual(state.screenBrightness, 1.0)
        XCTAssertEqual(state.qrData, "KW_LIB_QR_SAMPLE_DATA_12345")
        XCTAssertEqual(state.userName, "홍길동")
        XCTAssertTrue(state.isSheetVisible)
        XCTAssertTrue(state.isAppLocked) // 전체 잠금은 유지

        // 2. 바텀시트 퇴장 (닫힘 또는 백그라운드 전환): 메모리 파기 및 밝기 복원, 전체 앱 잠금 유지
        state.dismissSheet(originalBrightness: 0.4)
        XCTAssertEqual(state.screenBrightness, 0.4)
        XCTAssertNil(state.qrData)
        XCTAssertNil(state.userName)
        XCTAssertNil(state.userCode)
        XCTAssertFalse(state.isSheetVisible)
        XCTAssertTrue(state.isAppLocked) // 시트가 닫혀도 앱 잠금 상태는 안전하게 보존됨
    }
}

import XCTest
@testable import kw_klas_plus

final class HomeSystemTabBarMetricsTests: XCTestCase {
    func testOverlayHeightStaysInSafeArea() {
        XCTAssertEqual(HomeSystemTabBarMetrics.overlayHeight, 60)
    }

    func testFillsHomeIndicatorMatchesSystemTabBarStyle() {
        if #available(iOS 26.0, *) {
            XCTAssertFalse(HomeSystemTabBarMetrics.fillsHomeIndicator)
        } else {
            XCTAssertTrue(HomeSystemTabBarMetrics.fillsHomeIndicator)
        }
    }
}

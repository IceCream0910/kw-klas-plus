import XCTest
@testable import kw_klas_plus

final class HomeSystemTabBarMetricsTests: XCTestCase {
    func testFillsHomeIndicatorMatchesSystemTabBarStyle() {
        if #available(iOS 26.0, *) {
            XCTAssertFalse(HomeSystemTabBarMetrics.fillsHomeIndicator)
        } else {
            XCTAssertTrue(HomeSystemTabBarMetrics.fillsHomeIndicator)
        }
    }
}

import CoreGraphics
import XCTest
@testable import kw_klas_plus

final class SelectionSheetMetricsTests: XCTestCase {
    func testUnknownWindowDoesNotCapContentHeight() {
        XCTAssertEqual(SelectionSheetMetrics.maxDetent(windowSize: nil), .infinity)
        XCTAssertEqual(SelectionSheetMetrics.maxDetent(windowSize: .zero), .infinity)
        XCTAssertEqual(
            SelectionSheetMetrics.detentHeight(contentHeight: 300, windowSize: nil),
            300 + SelectionSheetMetrics.grabberAllowance
        )
    }

    func testDetentFitsShortContent() {
        let window = CGSize(width: 390, height: 844)

        XCTAssertEqual(
            SelectionSheetMetrics.detentHeight(contentHeight: 200, windowSize: window),
            220
        )
    }

    func testDetentIsCappedByShorterWindowSide() {
        XCTAssertEqual(
            SelectionSheetMetrics.detentHeight(
                contentHeight: 900,
                windowSize: CGSize(width: 390, height: 844)
            ),
            351,
            accuracy: 0.001
        )
        XCTAssertEqual(
            SelectionSheetMetrics.detentHeight(
                contentHeight: 900,
                windowSize: CGSize(width: 844, height: 390)
            ),
            351,
            accuracy: 0.001
        )
    }

    func testDetentFollowsResizedWindowInsteadOfScreen() {
        XCTAssertEqual(
            SelectionSheetMetrics.detentHeight(
                contentHeight: 900,
                windowSize: CGSize(width: 320, height: 480)
            ),
            288,
            accuracy: 0.001
        )
    }

    func testDetentHasMinimumHeight() {
        XCTAssertEqual(
            SelectionSheetMetrics.detentHeight(
                contentHeight: -100,
                windowSize: CGSize(width: 390, height: 844)
            ),
            1
        )
    }
}

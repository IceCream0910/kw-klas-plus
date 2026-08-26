import XCTest
@testable import kw_klas_plus

final class LectureBoardPathsTests: XCTestCase {
    func testEachBoardPathBecomesAvailableIndependently() {
        var paths = LectureBoardPaths()

        paths.update(notice: "notice", pds: "")

        XCTAssertEqual(paths.path(for: "notice"), "notice")
        XCTAssertNil(paths.path(for: "pds"))
    }

    func testPartialUpdatesDoNotEraseResolvedPath() {
        var paths = LectureBoardPaths()

        paths.update(notice: "notice", pds: "")
        paths.update(notice: "", pds: "pds")

        XCTAssertEqual(paths.path(for: "notice"), "notice")
        XCTAssertEqual(paths.path(for: "pds"), "pds")
    }

    func testUnknownBoardTypeIsRejected() {
        let paths = LectureBoardPaths()

        XCTAssertTrue(paths.isSupported(type: "notice"))
        XCTAssertTrue(paths.isSupported(type: "pds"))
        XCTAssertFalse(paths.isSupported(type: "unknown"))
        XCTAssertNil(paths.path(for: "unknown"))
    }
}

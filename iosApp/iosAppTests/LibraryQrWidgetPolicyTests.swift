import Foundation
import XCTest

final class LibraryQrWidgetPolicyTests: XCTestCase {

    func testSnapshotCodableRoundTrip() throws {
        let original = LibraryQrWidgetFixtures.normalConfiguredSnapshot
        let encoder = JSONEncoder()
        let data = try encoder.encode(original)

        let decoder = JSONDecoder()
        let decoded = try decoder.decode(LibraryWidgetSnapshot.self, from: data)

        XCTAssertEqual(decoded, original)
        XCTAssertEqual(decoded.studentNumber, "2020202020")
        XCTAssertEqual(decoded.userName, "홍길동")
        XCTAssertEqual(decoded.department, "소프트웨어학부")
        XCTAssertEqual(decoded.userCategory, "학부생")
        XCTAssertTrue(decoded.isConfigured)
        XCTAssertFalse(decoded.isAppLockEnabled)
    }

    func testAdr006JsonFixtureParsing() throws {
        let jsonData = try XCTUnwrap(LibraryQrWidgetFixtures.validSnapshotJson.data(using: .utf8))
        let decoded = try JSONDecoder().decode(LibraryWidgetSnapshot.self, from: jsonData)

        XCTAssertEqual(decoded, LibraryQrWidgetFixtures.normalConfiguredSnapshot)
    }

    func testAppLockMaskingPolicy() {
        let normal = LibraryQrWidgetFixtures.normalConfiguredSnapshot
        XCTAssertEqual(normal.maskedUserName, "홍길동")
        XCTAssertEqual(normal.maskedStudentNumber, "2020202020")

        let locked = LibraryQrWidgetFixtures.lockedSnapshot
        XCTAssertEqual(locked.maskedUserName, "홍*동")
        XCTAssertEqual(locked.maskedStudentNumber, "2020******")

        let twoCharName = LibraryWidgetSnapshot(
            studentNumber: "2021123456",
            userName: "이산",
            department: "전자공학과",
            userCategory: "학부생",
            isConfigured: true,
            isAppLockEnabled: true,
            updatedAtMillis: 1000
        )
        XCTAssertEqual(twoCharName.maskedUserName, "이*")
        XCTAssertEqual(twoCharName.maskedStudentNumber, "2021******")
    }

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

    func testFallbackSnapshots() {
        let unconfigured = LibraryQrWidgetFixtures.unconfiguredSnapshot
        XCTAssertFalse(unconfigured.isConfigured)
        XCTAssertTrue(unconfigured.studentNumber.isEmpty)

        let loggedOut = LibraryQrWidgetFixtures.loggedOutFallbackSnapshot
        XCTAssertFalse(loggedOut.isConfigured)
        XCTAssertTrue(loggedOut.userName.isEmpty)
    }
}

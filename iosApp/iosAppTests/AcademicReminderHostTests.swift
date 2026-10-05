import Foundation
import Shared
import XCTest
@testable import kw_klas_plus

final class AcademicReminderHostTests: XCTestCase {
    func testLedgerReadDistinguishesMissingUnreadableAndMalformedContent() throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: root) }
        let host = AcademicReminderHost(root: root)
        let missing = host.readLedger()
        XCTAssertTrue(missing.success)
        XCTAssertNil(missing.value)
        let ledger = root.appendingPathComponent("ledger.json")
        try "not-json".write(to: ledger, atomically: true, encoding: .utf8)
        let malformed = host.readLedger()
        XCTAssertTrue(malformed.success)
        XCTAssertEqual(malformed.value, "not-json")
        try FileManager.default.removeItem(at: ledger)
        try FileManager.default.createDirectory(at: ledger, withIntermediateDirectories: true)
        let unreadable = host.readLedger()
        XCTAssertFalse(unreadable.success)
        XCTAssertNil(unreadable.value)
    }
}

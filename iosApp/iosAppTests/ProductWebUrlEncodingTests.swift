import Foundation
import Shared
import XCTest
@testable import kw_klas_plus

final class ProductWebUrlEncodingTests: XCTestCase {
    private let titles = ["강의 공지사항", "강의 자료실", "Q&A", "C#", "1+1", "100%"]

    func testBoardListUrlParsesWithIos16Parser() throws {
        guard #available(iOS 17.0, *) else { throw XCTSkip("iOS 17 이상 런타임에서만 iOS 16 파서를 재현할 수 있다") }
        for title in titles {
            let url = ProductWebUrls.shared.boardList(title: title)
            XCTAssertNotNil(URL(string: url, encodingInvalidCharacters: false), "title=\(title)")
        }
    }

    func testBoardListTitleRoundTripsThroughQuery() throws {
        for title in titles {
            let url = try XCTUnwrap(URL(string: ProductWebUrls.shared.boardList(title: title)))
            let components = URLComponents(url: url, resolvingAgainstBaseURL: false)
            XCTAssertEqual(components?.queryItems?.first { $0.name == "title" }?.value, title, "title=\(title)")
        }
    }

    func testBoardViewQueryValuesRoundTrip() throws {
        let url = try XCTUnwrap(URL(string: ProductWebUrls.shared.boardView(boardNumber: "1&2", masterNumber: "a#b")))
        let items = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems
        XCTAssertEqual(items?.first { $0.name == "boardNo" }?.value, "1&2")
        XCTAssertEqual(items?.first { $0.name == "masterNo" }?.value, "a#b")
    }

    @MainActor
    func testUnparseableUrlMarksLoadFailed() {
        let holder = WebViewHolder()
        defer { holder.dispose() }
        holder.load("https://[invalid")
        XCTAssertEqual(
            holder.navigationState.loadPhase,
            .failed(url: "https://[invalid", category: .unknown)
        )
    }
}

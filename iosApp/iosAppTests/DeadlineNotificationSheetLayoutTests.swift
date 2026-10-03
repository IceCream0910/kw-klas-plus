import SwiftUI
import UIKit
import XCTest
@testable import kw_klas_plus

@MainActor
final class DeadlineNotificationSheetLayoutTests: XCTestCase {
    func testSmallViewportMeasuresExpandedContentWithoutCollapsingScrollArea() async throws {
        let state = SheetContentState()
        var heights: [CGFloat] = []
        let host = UIHostingController(rootView:
            DeadlineNotificationSheetLayout(showsActions: true, onHeightChange: { heights.append($0) }) {
                ChangingSheetContent(state: state)
            } actions: {
                Button("닫기") {}.frame(maxWidth: .infinity).buttonStyle(KlasInverseButtonStyle(enabled: true))
            }
        )
        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 402, height: 256))
        window.rootViewController = host
        window.makeKeyAndVisible()
        defer { window.isHidden = true; window.rootViewController = nil }
        await settle(host)
        state.hidden = true
        await settle(host)
        state.hidden = false
        state.expanded = true
        await settle(host)

        XCTAssertGreaterThan(try XCTUnwrap(heights.last), 500)
        let scroll = try XCTUnwrap(findScrollView(host.view))
        XCTAssertGreaterThan(scroll.bounds.height, 0)
        XCTAssertGreaterThan(scroll.contentSize.height, 400)
        XCTAssertTrue(heights.allSatisfy { $0.isFinite && $0 >= 200 })
        window.frame.size.height = try XCTUnwrap(heights.last)
        await settle(host)
        let image = UIGraphicsImageRenderer(bounds: host.view.bounds).image { _ in
            host.view.drawHierarchy(in: host.view.bounds, afterScreenUpdates: true)
        }
        try image.pngData()?.write(to: FileManager.default.temporaryDirectory.appendingPathComponent("deadline-sheet-layout-regression.png"))
    }

    func testLargeTextFitsUIKitDetentAndKeepsScrollableContent() async throws {
        var height: CGFloat = 256
        let layout = DeadlineNotificationSheetLayout(showsActions: true, onHeightChange: { height = $0 }) {
            VStack(alignment: .leading, spacing: 14) {
                Text("알림 설정 완료!").font(.title2.bold())
                Text("앱을 사용하고 있지 않을 때, 주기적으로 24시간 이내에 마감되는 할 일이 있는지 확인해서 알림을 보내줄게요.").font(.subheadline)
                DeadlineNotificationPreview()
            }
        } actions: {
            Button("닫기") {}.frame(maxWidth: .infinity).buttonStyle(KlasInverseButtonStyle(enabled: true))
        }
        let presenter = UIViewController()
        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 402, height: 874))
        window.rootViewController = presenter
        window.makeKeyAndVisible()
        let host = UIHostingController(rootView: layout.environment(\.dynamicTypeSize, .accessibility3))
        host.modalPresentationStyle = .pageSheet
        host.sheetPresentationController?.detents = [.custom { context in min(height, context.maximumDetentValue) }]
        presenter.present(host, animated: false)
        defer { presenter.dismiss(animated: false); window.isHidden = true; window.rootViewController = nil }
        await settle(host)
        host.sheetPresentationController?.invalidateDetents()
        await settle(host)

        XCTAssertGreaterThan(height, 600)
        let scroll = try XCTUnwrap(findScrollView(host.view))
        XCTAssertGreaterThan(scroll.bounds.height, 0)
        XCTAssertGreaterThan(scroll.contentSize.height, 400)
    }

    private func settle(_ host: UIViewController) async {
        for _ in 0..<12 {
            host.view.setNeedsLayout()
            host.view.layoutIfNeeded()
            try? await Task.sleep(nanoseconds: 30_000_000)
        }
    }

    private func findScrollView(_ view: UIView) -> UIScrollView? {
        if let scroll = view as? UIScrollView { return scroll }
        return view.subviews.lazy.compactMap(findScrollView).first
    }
}

private final class SheetContentState: ObservableObject {
    @Published var expanded = false
    @Published var hidden = false
}

private struct ChangingSheetContent: View {
    @ObservedObject var state: SheetContentState
    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            if !state.hidden {
                Text(state.expanded ? "알림 설정 완료!" : "알림 설정을 확인하고 있어요.")
                if state.expanded {
                    Text("앱을 사용하지 않을 때, 하루 안에 마감되는 할 일을 알려드려요.")
                    DeadlineNotificationPreview()
                }
            }
        }
    }
}

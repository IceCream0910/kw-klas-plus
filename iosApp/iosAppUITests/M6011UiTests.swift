import XCTest

final class M6011UiTests: XCTestCase {
    private var app: XCUIApplication!
    private let uiTimeout: TimeInterval = 15

    override func setUp() {
        super.setUp()
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments = ["-m6011-ui-test"]
        if name.contains("RemainAccessibleAtLargeType") {
            app.launchEnvironment["M6011_DYNAMIC_TYPE"] =
                ProcessInfo.processInfo.environment["M6011_DYNAMIC_TYPE"] ?? "accessibility3"
        } else if let dynamicType = ProcessInfo.processInfo.environment["M6011_DYNAMIC_TYPE"] {
            app.launchEnvironment["M6011_DYNAMIC_TYPE"] = dynamicType
        }
        app.launch()
    }

    override func tearDown() {
        XCUIDevice.shared.orientation = .portrait
        app = nil
        super.tearDown()
    }

    func testKeyboardInputUsesResizedWebSurface() {
        let input = app.textFields["웹 입력"]
        waitUntilHittable(input)
        XCTAssertTrue(app.staticTexts["웹 하단 내비게이션"].waitForExistence(timeout: uiTimeout))
        input.tap()
        XCTAssertTrue(app.keyboards.firstMatch.waitForExistence(timeout: uiTimeout))
        waitUntilHittable(input)
        waitUntilHittable(app.descendants(matching: .any)["m6011_native_controls"])
        input.typeText("KLAS")
        XCTAssertEqual(input.value as? String, "KLAS")
    }

    func testNativeOverlaysArePresentedOnceAndRemainAccessibleAtLargeType() {
        tapButton("m6011_sheet_button")
        let sheet = app.descendants(matching: .any)["selection_bottom_sheet"]
        XCTAssertTrue(app.staticTexts["환경 확인"].waitForExistence(timeout: uiTimeout))
        let firstOption = app.buttons["selection_option_0"]
        waitUntilHittable(firstOption)
        waitUntilHittable(app.buttons["selection_option_1"])
        XCTAssertEqual(app.buttons.matching(identifier: "selection_option_0").count, 1)
        firstOption.coordinate(withNormalizedOffset: CGVector(dx: 0.95, dy: 0.5)).tap()
        waitUntilGone(sheet)

        tapButton("m6011_alert_button")
        let alert = app.alerts["환경 확인"]
        let confirm = alert.buttons["확인"]
        waitUntilHittable(confirm)
        confirm.tap()
        waitUntilGone(alert)

        tapButton("m6011_download_button")
        let downloadOverlay = app.descendants(matching: .any)["download_progress_overlay"]
        let cancel = app.buttons["취소"]
        waitUntilHittable(cancel)
        XCTAssertTrue(downloadOverlay.exists)
        XCTAssertEqual(app.buttons.matching(NSPredicate(format: "label == %@", "취소")).count, 1)
        cancel.tap()
        waitUntilGone(downloadOverlay)
    }

    private func tapButton(_ identifier: String, file: StaticString = #filePath, line: UInt = #line) {
        let button = app.buttons[identifier]
        waitUntilHittable(button, file: file, line: line)
        button.tap()
    }

    private func waitUntilHittable(_ element: XCUIElement, file: StaticString = #filePath, line: UInt = #line) {
        let predicate = NSPredicate { _, _ in element.exists && element.isHittable }
        let expectation = XCTNSPredicateExpectation(predicate: predicate, object: nil)
        XCTAssertEqual(XCTWaiter().wait(for: [expectation], timeout: uiTimeout), .completed,
                       "상호작용할 수 있는 상태가 되지 않음: \(element)", file: file, line: line)
    }

    private func waitUntilGone(_ element: XCUIElement, file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertTrue(element.waitForNonExistence(timeout: uiTimeout),
                      "화면에서 사라지지 않음: \(element)", file: file, line: line)
    }
}

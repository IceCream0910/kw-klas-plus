import XCTest

final class M6011UiTests: XCTestCase {
    private var app: XCUIApplication!

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

    func testWebSurfaceAndNativeControlsRemainAvailableAfterRotation() {
        let webSurface = app.descendants(matching: .any)["m6011_web_surface"]
        XCTAssertTrue(webSurface.waitForExistence(timeout: 5))
        XCTAssertTrue(waitForButton("m6011_sheet_button").isHittable)

        XCUIDevice.shared.orientation = .landscapeLeft
        XCTAssertTrue(webSurface.waitForExistence(timeout: 5))
        XCTAssertTrue(waitForButton("m6011_alert_button").isHittable)

        XCUIDevice.shared.orientation = .portrait
        XCTAssertTrue(webSurface.waitForExistence(timeout: 5))
        XCTAssertTrue(waitForButton("m6011_download_button").isHittable)
    }

    func testKeyboardInputUsesResizedWebSurface() {
        let input = app.textFields["웹 입력"]
        XCTAssertTrue(input.waitForExistence(timeout: 5))
        input.tap()
        XCTAssertTrue(input.isHittable)
        let nativeControls = app.descendants(matching: .any)["m6011_native_controls"]
        XCTAssertTrue(nativeControls.waitForExistence(timeout: 5))
        XCTAssertTrue(nativeControls.isHittable)
    }

    func testNativeOverlaysArePresentedOnceAndRemainAccessibleAtLargeType() {
        waitForButton("m6011_sheet_button").tap()
        let sheet = app.descendants(matching: .any)["selection_bottom_sheet"]
        XCTAssertTrue(sheet.waitForExistence(timeout: 3))
        XCTAssertTrue(app.staticTexts["환경 확인"].waitForExistence(timeout: 2))
        let firstOption = app.buttons["selection_option_0"]
        XCTAssertTrue(firstOption.waitForExistence(timeout: 2))
        XCTAssertEqual(app.buttons.matching(identifier: "selection_option_0").count, 1)
        XCTAssertTrue(firstOption.isHittable)
        XCTAssertTrue(app.buttons["selection_option_1"].isHittable)
        firstOption.tap()
        waitUntilGone(firstOption)

        waitForButton("m6011_alert_button").tap()
        let alert = app.alerts["환경 확인"]
        XCTAssertTrue(alert.waitForExistence(timeout: 3))
        XCTAssertTrue(alert.buttons["확인"].isHittable)
        alert.buttons["확인"].tap()
        waitUntilGone(alert)

        waitForButton("m6011_download_button").tap()
        let downloadOverlay = app.descendants(matching: .any)["download_progress_overlay"]
        XCTAssertTrue(downloadOverlay.waitForExistence(timeout: 3))
        let cancel = app.buttons["취소"]
        XCTAssertTrue(cancel.waitForExistence(timeout: 2))
        XCTAssertEqual(app.buttons.matching(NSPredicate(format: "label == %@", "취소")).count, 1)
        XCTAssertTrue(cancel.isHittable)
        cancel.tap()
        waitUntilGone(downloadOverlay)
    }

    func testWebInputAndControlsExposeAccessibilityLabels() {
        XCTAssertTrue(app.textFields["웹 입력"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["웹 하단 내비게이션"].exists)
        XCTAssertTrue(waitForButton("m6011_sheet_button").isHittable)
        XCTAssertTrue(waitForButton("m6011_alert_button").isHittable)
        XCTAssertTrue(waitForButton("m6011_download_button").isHittable)
    }

    private func waitForButton(_ identifier: String) -> XCUIElement {
        let button = app.buttons[identifier]
        XCTAssertTrue(button.waitForExistence(timeout: 5))
        return button
    }

    private func waitUntilGone(_ element: XCUIElement, timeout: TimeInterval = 3) {
        let expectation = XCTNSPredicateExpectation(
            predicate: NSPredicate(format: "exists == false"),
            object: element
        )
        XCTAssertEqual(XCTWaiter().wait(for: [expectation], timeout: timeout), .completed)
    }
}

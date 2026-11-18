import XCTest

final class DemoJourneyTests: XCTestCase {
    private var app: XCUIApplication!
    private let timeout: TimeInterval = 90

    override func setUp() {
        super.setUp()
        continueAfterFailure = false
        app = XCUIApplication()
        app.launch()
    }

    override func tearDown() {
        if (testRun?.failureCount ?? 0) > 0, !app.secureTextFields.firstMatch.exists {
            let screenshot = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
            screenshot.name = "실패 화면"
            screenshot.lifetime = .keepAlways
            add(screenshot)
        }
        XCUIDevice.shared.orientation = .portrait
        app.terminate()
        super.tearDown()
    }

    func testLoginInvalidPasswordSessionRestoreAndLogout() {
        openLogin()
        let studentId = app.textFields["login_student_id"]
        studentId.tap()
        studentId.typeText("000000000")
        XCTAssertFalse(app.buttons["다음"].isEnabled)
        studentId.typeText("0")
        let password = app.secureTextFields["login_password"]
        XCTAssertTrue(password.waitForExistence(timeout: timeout))
        password.tap()
        let invalid = "InvalidDemoPassword"
        password.typeText(invalid)
        app.buttons["로그인하기"].tap()
        XCTAssertTrue(app.staticTexts["학번 또는 비밀번호를 확인해 주세요."].waitForExistence(timeout: timeout))
        password.tap()
        password.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: invalid.count))
        password.typeText("KlasplusDemo!123")
        app.buttons["로그인하기"].tap()
        waitForHome()
        app.terminate()
        app.launch()
        waitForHome()
        logout()
        XCTAssertTrue(studentId.waitForExistence(timeout: timeout))
        app.terminate()
        app.launch()
        XCTAssertTrue(app.buttons["로그인"].waitForExistence(timeout: timeout))
    }

    func testLoginHelpSheet() {
        openLogin()
        let studentId = app.textFields["login_student_id"]
        let help = app.buttons["로그인은 어떻게 처리되나요?"]
        XCTAssertTrue(help.waitForExistence(timeout: timeout))
        help.tap()
        let content = app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", "학번과 비밀번호는 이 기기에서 학교 KLAS 서버로 직접 전달돼요")).firstMatch
        XCTAssertTrue(content.waitForExistence(timeout: timeout))
        let close = app.buttons["닫기"]
        close.tap()
        XCTAssertTrue(close.waitForNonExistence(timeout: timeout))
        XCTAssertTrue(studentId.waitForExistence(timeout: timeout))
    }

    func testTabsRotationAndSemesterSelection() {
        signIn()
        let timetable = app.buttons["시간표"]
        let feed = app.buttons["피드"]
        XCUIDevice.shared.orientation = .landscapeLeft
        let home = app.otherElements["home_view"]
        let landscape = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in home.exists && home.frame.width > home.frame.height }, object: nil)
        XCTAssertEqual(XCTWaiter().wait(for: [landscape], timeout: timeout), .completed)
        XCTAssertTrue(timetable.waitForExistence(timeout: timeout))
        XCTAssertTrue(app.buttons["메뉴 열기"].exists)
        XCUIDevice.shared.orientation = .portrait
        let portrait = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in home.exists && home.frame.height > home.frame.width }, object: nil)
        XCTAssertEqual(XCTWaiter().wait(for: [portrait], timeout: timeout), .completed)
        timetable.tap()
        let course = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "컴퓨터프로그래밍,")).firstMatch
        XCTAssertTrue(course.waitForExistence(timeout: timeout))
        feed.tap()
        XCTAssertTrue(course.waitForNonExistence(timeout: timeout))
        XCTAssertTrue(app.staticTexts["컴퓨터프로그래밍"].waitForExistence(timeout: timeout))
        timetable.tap()
        let term = app.buttons.matching(NSPredicate(format: "label MATCHES %@", "[0-9]{4}(년도|년)?[[:space:]]*[12]학기")).firstMatch
        XCTAssertTrue(term.waitForExistence(timeout: timeout))
        let digits = term.label.components(separatedBy: CharacterSet.decimalDigits.inverted).filter { !$0.isEmpty }.compactMap(Int.init)
        XCTAssertEqual(digits.count, 2)
        let year = digits[1] == 2 ? digits[0] : digits[0] - 1
        let semester = digits[1] == 2 ? 1 : 2
        term.tap()
        XCTAssertTrue(app.staticTexts["학기 선택"].waitForExistence(timeout: timeout))
        let previousPattern = "\(year)(년도|년)?[[:space:]]*\(semester)학기"
        let previous = app.buttons.matching(NSPredicate(format: "label MATCHES %@", previousPattern)).firstMatch
        XCTAssertTrue(previous.waitForExistence(timeout: timeout))
        previous.tap()
        XCTAssertTrue(app.staticTexts["학기 선택"].waitForNonExistence(timeout: timeout))
        XCTAssertTrue(app.buttons.matching(NSPredicate(format: "label MATCHES %@", previousPattern)).firstMatch.waitForExistence(timeout: timeout))
    }

    func testLiveMediaPlaybackAndSeek() throws {
        signIn()
        app.staticTexts["온라인 강의"].firstMatch.tap()
        let lecture = app.staticTexts["프로그래밍 시작하기"].firstMatch
        XCTAssertTrue(lecture.waitForExistence(timeout: timeout))
        lecture.tap()
        XCTAssertTrue(app.staticTexts["02:00"].waitForExistence(timeout: timeout))
        let play = app.buttons["재생"]
        let pause = app.buttons["일시정지"]
        if pause.exists { pause.tap() }
        XCTAssertTrue(play.waitForExistence(timeout: timeout))
        play.tap()
        XCTAssertTrue(pause.waitForExistence(timeout: timeout))
        let times = app.staticTexts.matching(NSPredicate(format: "label MATCHES %@", "[0-9]{2}:[0-5][0-9]"))
        func elapsed() -> Int? {
            for index in 0..<times.count {
                let parts = times.element(boundBy: index).label.split(separator: ":").compactMap { Int($0) }
                if parts.count == 2, parts[0] * 60 + parts[1] != 120 { return parts[0] * 60 + parts[1] }
            }
            return nil
        }
        let played = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in (elapsed() ?? 0) >= 12 }, object: nil)
        XCTAssertEqual(XCTWaiter().wait(for: [played], timeout: timeout), .completed)
        pause.tap()
        XCTAssertTrue(play.waitForExistence(timeout: timeout))
        let before = try XCTUnwrap(elapsed())
        app.buttons["10초 뒤로"].tap()
        let seeked = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in guard let value = elapsed() else { return false }; return value >= before - 11 && value <= before - 9 }, object: nil)
        XCTAssertEqual(XCTWaiter().wait(for: [seeked], timeout: timeout), .completed)
        app.buttons["학습 종료"].tap()
        XCTAssertTrue(app.staticTexts["정말 강의 수강을 종료할까요?"].waitForExistence(timeout: timeout))
        app.buttons["확인"].tap()
        XCTAssertTrue(lecture.waitForExistence(timeout: timeout))
    }

    private func openLogin() {
        waitForStartup()
        let studentId = app.textFields["login_student_id"]
        if app.buttons["메뉴 열기"].exists {
            logout()
            XCTAssertTrue(studentId.waitForExistence(timeout: timeout))
            return
        }
        if studentId.exists { return }
        let login = app.buttons["로그인"]
        XCTAssertTrue(login.waitForExistence(timeout: timeout))
        login.tap()
        XCTAssertTrue(studentId.waitForExistence(timeout: timeout))
    }

    private func signIn() {
        waitForStartup()
        if app.buttons["메뉴 열기"].exists { waitForHome(); return }
        openLogin()
        let studentId = app.textFields["login_student_id"]
        studentId.tap()
        studentId.typeText("0000000000")
        let password = app.secureTextFields["login_password"]
        XCTAssertTrue(password.waitForExistence(timeout: timeout))
        password.tap()
        password.typeText("KlasplusDemo!123")
        app.buttons["로그인하기"].tap()
        waitForHome()
    }

    private func waitForStartup() {
        let ready = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
            self.app.buttons["로그인"].exists || self.app.textFields["login_student_id"].exists || self.app.buttons["메뉴 열기"].exists
        }, object: nil)
        XCTAssertEqual(XCTWaiter().wait(for: [ready], timeout: timeout), .completed)
    }

    private func waitForHome() {
        let menu = app.buttons["메뉴 열기"]
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        addUIInterruptionMonitor(withDescription: "비밀번호 저장 안내") { alert in
            for label in ["지금 안 함", "Not Now", "Don't Save", "Don’t Save"] where alert.buttons[label].exists {
                alert.buttons[label].tap()
                return true
            }
            return false
        }
        if springboard.buttons["Not Now"].exists { springboard.buttons["Not Now"].tap() }
        XCTAssertTrue(menu.waitForExistence(timeout: timeout))
        XCTAssertTrue(app.webViews.firstMatch.waitForExistence(timeout: timeout))
    }

    private func logout() {
        app.buttons["메뉴 열기"].tap()
        let logout = app.buttons["로그아웃"]
        XCTAssertTrue(logout.waitForExistence(timeout: timeout))
        logout.tap()
        let confirm = app.buttons["확인"]
        XCTAssertTrue(confirm.waitForExistence(timeout: timeout))
        confirm.tap()
    }
}

import Foundation
import Shared
import XCTest
@testable import kw_klas_plus

final class AcademicWidgetContractTests: XCTestCase {
    private let validJSON = """
    {"schemaVersion":1,"owner":"abc123","term":"2026,2","month":"2026-09","classes":[{"title":"자료구조","day":0,"startTime":"9:0","endTime":"10:0","info":"새빛관 301/교수","color":{"palette":"default-v1","slot":0,"light":{"background":"#E0F4E8","text":"#16633D"},"dark":{"background":"#103822","text":"#54E995"}}}],"timetableFetchedAt":10,"events":[{"id":"1","title":"회의","start":"2026-09-10T12:00","end":"2026-09-10T13:00","color":"#ff0000"}],"calendarFetchedAt":20,"calendarStatus":"READY"}
    """

    func testAllowlistAcceptsOnlyWidgetHostAndPath() {
        XCTAssertEqual(AcademicWidgetRouter.tab(from: AcademicWidgetRouter.timetableURL), "timetable")
        XCTAssertEqual(AcademicWidgetRouter.tab(from: AcademicWidgetRouter.calendarURL), "calendar")
        XCTAssertNil(AcademicWidgetRouter.tab(from: URL(string: "kwklasplus://widget/timetable?x=1")!))
        XCTAssertNil(AcademicWidgetRouter.tab(from: URL(string: "kwklasplus://widget/calendar#frag")!))
        XCTAssertNil(AcademicWidgetRouter.tab(from: URL(string: "kwklasplus://library-qr")!))
        XCTAssertNil(AcademicWidgetRouter.tab(from: URL(string: "klasplus://widget/calendar")!))
        XCTAssertNil(AcademicWidgetRouter.tab(from: URL(string: "kwklasplus://other/calendar")!))
        XCTAssertNil(LibraryQrRouter.resolveRoute(url: AcademicWidgetRouter.calendarURL, hasConfiguredCredentials: true))
        XCTAssertFalse(LibraryQrRouter.isLibraryQrURL(AcademicWidgetRouter.calendarURL))
    }

    func testSwiftDecodeRejectsCorruptVersionMismatchBlankOwnerAndOwnerMismatch() {
        XCTAssertEqual(AcademicWidgetDisplayReader.decode(validJSON, ownerMarker: "abc123")?.term, "2026,2")
        XCTAssertEqual(AcademicWidgetDisplayReader.decode(validJSON, ownerMarker: "abc123")?.classes?.first?.title, "자료구조")
        XCTAssertNil(AcademicWidgetDisplayReader.decode("{", ownerMarker: "abc123"))
        XCTAssertNil(AcademicWidgetDisplayReader.decode(
            """
            {"schemaVersion":2,"owner":"abc123","term":"2026,2","month":"","classes":null,"timetableFetchedAt":0,"events":null,"calendarFetchedAt":0,"calendarStatus":"READY"}
            """,
            ownerMarker: "abc123"
        ))
        XCTAssertNil(AcademicWidgetDisplayReader.decode(
            """
            {"schemaVersion":1,"owner":"","term":"2026,2","month":"","classes":null,"timetableFetchedAt":0,"events":null,"calendarFetchedAt":0,"calendarStatus":"READY"}
            """,
            ownerMarker: "abc123"
        ))
        XCTAssertNil(AcademicWidgetDisplayReader.decode(validJSON, ownerMarker: "other-owner"))
        XCTAssertNil(AcademicWidgetDisplayReader.decode(validJSON, ownerMarker: ""))
    }

    @MainActor
    func testLoginHopKeepsDestinationUntilAuthenticated() {
        let env = makeLockEnvironment(enabled: false)
        defer { env.tearDown() }
        let opener = AcademicWidgetOpenController()
        var opened: String?
        opener.attach { opened = $0 }

        XCTAssertTrue(opener.handleOpenURL(
            AcademicWidgetRouter.calendarURL,
            isAuthenticated: false,
            appLock: env.lock
        ))
        XCTAssertEqual(opener.pendingTab, "calendar")
        XCTAssertNil(opened)

        opener.consumeIfPossible(isAuthenticated: true, appLock: env.lock)
        XCTAssertEqual(opened, "calendar")
        XCTAssertNil(opener.pendingTab)
    }

    @MainActor
    func testUnlockCancelDoesNotOpenProtectedTabAndDoesNotUseQrBypass() {
        let env = makeLockEnvironment(enabled: true)
        defer { env.tearDown() }
        let opener = AcademicWidgetOpenController()
        var opened: String?
        opener.attach { opened = $0 }

        XCTAssertTrue(opener.handleOpenURL(
            AcademicWidgetRouter.timetableURL,
            isAuthenticated: true,
            appLock: env.lock
        ))
        XCTAssertEqual(env.lock.mode, .unlock)
        XCTAssertNil(opened)
        XCTAssertEqual(opener.pendingTab, "timetable")
        XCTAssertFalse(env.libraryQr.isQrBypassActive)
        XCTAssertEqual(
            AppLockCoverPolicy.coverMode(
                isSessionAuthenticated: true,
                isQrBypassActive: env.libraryQr.isQrBypassActive,
                mode: env.lock.mode
            ),
            .unlock
        )
    }

    @MainActor
    func testUnlockSuccessOpensTab() {
        let env = makeLockEnvironment(enabled: true)
        defer { env.tearDown() }
        let opener = AcademicWidgetOpenController()
        var opened: String?
        opener.attach { opened = $0 }

        _ = opener.handleOpenURL(
            AcademicWidgetRouter.calendarURL,
            isAuthenticated: true,
            appLock: env.lock
        )
        [1, 2, 3, 4, 5, 6].forEach(env.lock.appendDigit)
        XCTAssertEqual(opened, "calendar")
        XCTAssertNil(opener.pendingTab)
        XCTAssertFalse(env.libraryQr.isQrBypassActive)
    }

    @MainActor
    func testBootstrapKeepsPendingWidgetTab() {
        let coordinator = makeHomeCoordinator()
        defer { coordinator.dispose() }
        coordinator.openWidgetTab("calendar")
        coordinator.handleBootstrap(HomeBootstrapResultReady(
            sessionToken: SecretValue.companion.of(value: "session"),
            yearHakgi: "2026,1",
            yearHakgiListJoined: "2026,1",
            timetableJson: "{}",
            deadlineJson: "[]",
            promptYearHakgiChange: false
        ))
        XCTAssertEqual(coordinator.currentTab, "calendar")
    }

    @MainActor
    private func makeHomeCoordinator() -> HomeCoordinator {
        let suite = "com.icecream.kwklasplus.test.academicwidget.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defaults.removePersistentDomain(forName: suite)
        return HomeCoordinator(
            authRuntime: IosAuthRuntime.companion.create(defaults: defaults),
            onLogout: {}
        )
    }

    @MainActor
    private func makeLockEnvironment(enabled: Bool) -> LockEnv {
        let suite = "com.icecream.kwklasplus.test.academicwidget.lock.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defaults.removePersistentDomain(forName: suite)
        let keychain = IosKeychainSecureStore(service: suite)
        let authRuntime = IosAuthRuntime.companion.createForTests(defaults: defaults, secureStore: keychain)
        let lock = AppLockController(store: authRuntime.dependencies.appLockStore, canUseBiometrics: { false })
        if enabled {
            lock.store.savePassword(password: "123456")
            lock.store.setEnabled(enabled: true)
            lock.store.isUnlocked = false
        }
        let libraryQr = LibraryQrController(
            service: authRuntime.dependencies.libraryService,
            appLock: lock
        )
        return LockEnv(suite: suite, defaults: defaults, lock: lock, libraryQr: libraryQr)
    }
}

@MainActor
private struct LockEnv {
    let suite: String
    let defaults: UserDefaults
    let lock: AppLockController
    let libraryQr: LibraryQrController

    func tearDown() {
        defaults.removePersistentDomain(forName: suite)
    }
}

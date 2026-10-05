import Foundation
import Shared
import XCTest
@testable import kw_klas_plus

@MainActor
final class AuthSessionControllerTests: XCTestCase {
    func testRetainedPinAfterLogoutSkipsRecommendationAndStillRequiresAgreements() {
        let store = FakeFunnelStatusStore(value: "complete")
        let controller = makeController(networkPath: FakeNetworkPathChecker(satisfied: true), funnelStatusStore: store, hasAppLockPassword: { true })
        controller.handleHomeLogout()
        XCTAssertEqual(controller.phase, .needsCredentials)
        XCTAssertTrue(store.write("authenticating"))
        controller.enterAuthenticated(initialDelayMillis: 0)
        controller.skipLibrary()
        XCTAssertEqual(controller.loginState.step, .notifications)
        XCTAssertEqual(controller.phase, .setup)
        XCTAssertEqual(store.read(), "setup")
        XCTAssertFalse(controller.loginState.privacyAccepted)
        controller.backFunnel()
        XCTAssertEqual(controller.loginState.step, .library)
        controller.skipLibrary()
        controller.continueFunnel()
        XCTAssertEqual(controller.loginState.step, .agreements)
        controller.finishFunnel()
        XCTAssertEqual(store.read(), "setup")
    }

    func testSavedLibrarySkipsRetainedPinWithoutChangingItsSetting() async {
        let store = FakeFunnelStatusStore(value: "authenticating")
        let controller = makeController(networkPath: FakeNetworkPathChecker(satisfied: true), funnelStatusStore: store, saveLibraryCredentials: { _, _, _, done in done(true) }, hasAppLockPassword: { true })
        controller.enterAuthenticated(initialDelayMillis: 0)
        controller.loginState.libraryPhone = "01012345678"
        controller.loginState.libraryPassword = "fixture"
        controller.saveLibrary()
        await waitUntil { controller.loginState.step == .notifications }
        XCTAssertEqual(controller.phase, .setup)
        XCTAssertEqual(store.read(), "setup")
    }

    func testBackFunnelUsesAvailablePreviousStep() {
        let controller = makeController(networkPath: FakeNetworkPathChecker(satisfied: true))
        controller.loginState.step = .complete
        controller.backFunnel()
        XCTAssertEqual(controller.loginState.step, .agreements)
        controller.backFunnel()
        XCTAssertEqual(controller.loginState.step, .notifications)
        controller.backFunnel()
        XCTAssertEqual(controller.loginState.step, .appLock)
        controller.backFunnel()
        XCTAssertEqual(controller.loginState.step, .library)
        controller.backFunnel()
        XCTAssertEqual(controller.loginState.step, .library)

        controller.loginState.step = .studentId
        controller.beginFunnelFromOnboarding()
        controller.backFunnel()
        XCTAssertTrue(controller.loginState.onboardingVisible)
    }

    func testLockAndNotificationsCanBeSkippedBeforeRequiredAgreements() {
        let store = FakeFunnelStatusStore(value: "authenticating")
        let controller = makeController(networkPath: FakeNetworkPathChecker(satisfied: true), funnelStatusStore: store)
        controller.enterAuthenticated(initialDelayMillis: 0)
        controller.skipLibrary()
        XCTAssertEqual(controller.loginState.step, .appLock)
        XCTAssertFalse(controller.loginState.privacyAccepted)
        controller.continueFunnel()
        XCTAssertEqual(controller.loginState.step, .notifications)
        XCTAssertFalse(controller.loginState.privacyAccepted)
        controller.continueFunnel()
        XCTAssertEqual(controller.loginState.step, .agreements)
        XCTAssertEqual(store.read(), "setup")
        controller.finishFunnel()
        XCTAssertEqual(store.read(), "setup")
        controller.continueFunnel()
        XCTAssertEqual(controller.loginState.step, .complete)
        XCTAssertEqual(store.read(), "setup")
        controller.finishFunnel()
        XCTAssertEqual(store.read(), "complete")
    }

    func testCompletedStudentIdDoesNotAutoAdvanceAgainAfterBack() {
        let controller = makeController(networkPath: FakeNetworkPathChecker(satisfied: true))
        controller.loginState.onboardingVisible = false
        controller.updateStudentId("202012345")
        controller.updateStudentId("2020123456")
        XCTAssertEqual(controller.loginState.step, .password)
        XCTAssertNil(controller.loginState.error)

        controller.backFunnel()
        controller.updateStudentId("2020123456")
        XCTAssertEqual(controller.loginState.step, .studentId)
        XCTAssertNil(controller.loginState.error)
    }

    func testInterruptedManualLoginReturnsToPasswordBeforeHome() async {
        let suite = "com.icecream.kwklasplus.test.auth.funnel.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defaults.set("authenticating", forKey: "login_funnel_status")
        defer { defaults.removePersistentDomain(forName: suite) }
        let controller = AuthSessionController(
            authRuntime: IosAuthRuntime.companion.create(defaults: defaults),
            networkPath: FakeNetworkPathChecker(satisfied: true)
        )

        controller.start()
        await waitUntil { controller.phase == .needsCredentials }

        XCTAssertEqual(controller.loginState.step, .password)
        XCTAssertFalse(controller.loginState.onboardingVisible)
    }

    func testFunnelCannotFinishBeforeAuthentication() {
        let controller = makeController(networkPath: FakeNetworkPathChecker(satisfied: true))
        controller.loginState.step = .complete
        controller.loginState.privacyAccepted = true

        controller.finishFunnel()

        XCTAssertEqual(controller.phase, .checkingNetwork)
        XCTAssertNotNil(controller.toastMessage)
    }

    func testFailedLibraryStorageKeepsSetupStepAndShowsError() async {
        let store = FakeFunnelStatusStore(value: "authenticating")
        let controller = makeController(
            networkPath: FakeNetworkPathChecker(satisfied: true),
            funnelStatusStore: store,
            saveLibraryCredentials: { _, _, _, onResult in onResult(false) }
        )
        controller.enterAuthenticated(initialDelayMillis: 0)
        controller.loginState.studentId = "2020123456"
        controller.loginState.libraryPhone = "01012345678"
        controller.loginState.libraryPassword = "lib-pass"

        controller.saveLibrary()
        await waitUntil { controller.loginState.error != nil }

        XCTAssertEqual(controller.phase, .setup)
        XCTAssertEqual(controller.loginState.step, .library)
        XCTAssertEqual(controller.loginState.libraryPassword, "lib-pass")
        XCTAssertEqual(store.read(), "setup")
    }

    func testFailedAuthenticatingStatusWriteKeepsLoginBeforeCredentialPreparation() {
        let store = FakeFunnelStatusStore(rejectedValues: ["authenticating"])
        let controller = makeController(
            networkPath: FakeNetworkPathChecker(satisfied: true),
            funnelStatusStore: store
        )
        controller.loginState.studentId = "2020123456"
        controller.loginState.password = "secret"
        controller.loginState.step = .password

        controller.submitLogin()

        XCTAssertEqual(controller.phase, .checkingNetwork)
        XCTAssertEqual(controller.loginState.step, .password)
        XCTAssertEqual(controller.loginState.password, "secret")
        XCTAssertNotNil(controller.loginState.error)
        XCTAssertNil(store.read())
    }

    func testFailedSetupStatusWriteBlocksHome() {
        let store = FakeFunnelStatusStore(value: "authenticating", rejectedValues: ["setup"])
        let controller = makeController(
            networkPath: FakeNetworkPathChecker(satisfied: true),
            funnelStatusStore: store
        )

        controller.enterAuthenticated(initialDelayMillis: 0)

        XCTAssertEqual(controller.phase, .blocked(.storageFailure))
        XCTAssertEqual(store.read(), "authenticating")
    }

    func testFailedCompleteStatusWriteKeepsSetupScreen() {
        let store = FakeFunnelStatusStore(value: "authenticating", rejectedValues: ["complete"])
        let controller = makeController(
            networkPath: FakeNetworkPathChecker(satisfied: true),
            funnelStatusStore: store
        )
        controller.enterAuthenticated(initialDelayMillis: 0)
        XCTAssertEqual(controller.phase, .setup)
        controller.loginState.step = .complete
        controller.loginState.privacyAccepted = true
        controller.loginState.termsAccepted = true

        controller.finishFunnel()

        XCTAssertEqual(controller.phase, .setup)
        XCTAssertEqual(store.read(), "setup")
        XCTAssertNotNil(controller.toastMessage)
    }

    func testStaleSetupStatusBlocksCompletionWithFeedback() {
        let store = FakeFunnelStatusStore(value: "authenticating")
        let controller = makeController(
            networkPath: FakeNetworkPathChecker(satisfied: true),
            funnelStatusStore: store
        )
        controller.enterAuthenticated(initialDelayMillis: 0)
        controller.loginState.step = .complete
        controller.loginState.privacyAccepted = true
        controller.loginState.termsAccepted = true
        XCTAssertTrue(store.write("not_started"))

        controller.finishFunnel()

        XCTAssertEqual(controller.phase, .setup)
        XCTAssertEqual(store.read(), "not_started")
        XCTAssertNotNil(controller.toastMessage)
    }

    func testFailedLegacyMigrationStatusWriteBlocksHome() {
        let store = FakeFunnelStatusStore(rejectedValues: ["complete"])
        let controller = makeController(
            networkPath: FakeNetworkPathChecker(satisfied: true),
            funnelStatusStore: store
        )

        controller.enterAuthenticated(initialDelayMillis: 0)

        XCTAssertEqual(controller.phase, .blocked(.storageFailure))
    }

    func testPersistedStatusAdvancesFromAuthenticationThroughCompletion() {
        let suite = "com.icecream.kwklasplus.test.auth.status.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defaults.removePersistentDomain(forName: suite)
        defer { defaults.removePersistentDomain(forName: suite) }
        defaults.set("authenticating", forKey: "login_funnel_status")
        let controller = AuthSessionController(
            authRuntime: IosAuthRuntime.companion.create(defaults: defaults),
            networkPath: FakeNetworkPathChecker(satisfied: true)
        )

        controller.enterAuthenticated(initialDelayMillis: 0)
        XCTAssertEqual(controller.phase, .setup)
        XCTAssertEqual(defaults.string(forKey: "login_funnel_status"), "setup")

        controller.loginState.step = .complete
        controller.loginState.privacyAccepted = true
        controller.loginState.termsAccepted = true
        controller.finishFunnel()

        XCTAssertEqual(controller.phase, .authenticated)
        XCTAssertEqual(defaults.string(forKey: "login_funnel_status"), "complete")
    }

    func testStartReturnsImmediatelyWhileNetworkProbeIsInFlight() {
        let checker = FakeNetworkPathChecker(satisfied: false, delayNanos: 200_000_000)
        let controller = makeController(networkPath: checker)

        let started = Date()
        controller.start()
        let elapsed = Date().timeIntervalSince(started)

        XCTAssertLessThan(elapsed, 0.05)
        XCTAssertEqual(controller.phase, .checkingNetwork)
    }

    func testStartBlocksWhenNetworkUnsatisfied() async {
        let checker = FakeNetworkPathChecker(satisfied: false)
        let controller = makeController(networkPath: checker)

        controller.start()
        await waitUntil { controller.phase == .blocked(.noNetwork) }

        XCTAssertEqual(controller.phase, .blocked(.noNetwork))
    }

    func testStartBootstrapsWhenNetworkSatisfied() async {
        let checker = FakeNetworkPathChecker(satisfied: true)
        let controller = makeController(networkPath: checker)

        controller.start()
        await waitUntil {
            switch controller.phase {
            case .needsCredentials, .authenticating, .authenticated:
                return true
            default:
                return false
            }
        }

        if case .blocked = controller.phase {
            XCTFail("phase=\(controller.phase)")
        }
    }

    func testStartPrefillsRetainedAccountIdWithoutPassword() async {
        let suite = "com.icecream.kwklasplus.test.auth.prefill.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defaults.removePersistentDomain(forName: suite)
        defaults.set("2020123456", forKey: "kwID")
        defer { defaults.removePersistentDomain(forName: suite) }

        let keychain = IosKeychainSecureStore(
            service: "com.icecream.kwklasplus.test.auth.prefill.keychain.\(UUID().uuidString)"
        )
        let controller = AuthSessionController(
            authRuntime: IosAuthRuntime.companion.createForTests(defaults: defaults, secureStore: keychain),
            networkPath: FakeNetworkPathChecker(satisfied: true)
        )

        controller.start()
        await waitUntil { controller.phase == .needsCredentials }

        XCTAssertEqual(controller.phase, .needsCredentials)
        XCTAssertEqual(controller.loginState.studentId, "2020123456")
        XCTAssertEqual(controller.loginState.password, "")
        XCTAssertFalse(controller.loginState.onboardingVisible)
    }

    func testHomeSessionExpiredClearsSessionBeforeReturningToLogin() async {
        let suite = "com.icecream.kwklasplus.test.auth.expired.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defaults.removePersistentDomain(forName: suite)
        defer { defaults.removePersistentDomain(forName: suite) }
        let keychain = IosKeychainSecureStore(
            service: "com.icecream.kwklasplus.test.auth.expired.keychain.\(UUID().uuidString)"
        )
        let runtime = IosAuthRuntime.companion.createForTests(defaults: defaults, secureStore: keychain)
        let observed = expectation(description: "session observed")
        runtime.observeSessionToken(token: "session-expired") { result in
            XCTAssertTrue(result is SessionResultActive)
            observed.fulfill()
        }
        await fulfillment(of: [observed], timeout: 5)

        let controller = AuthSessionController(
            authRuntime: runtime,
            networkPath: FakeNetworkPathChecker(satisfied: false)
        )
        controller.handleHomeSessionExpired()
        await waitUntil { controller.phase == .needsCredentials }

        XCTAssertEqual(controller.phase, .needsCredentials)
        let restored = expectation(description: "session expired")
        runtime.restoreSession { result in
            XCTAssertFalse(result is SessionResultActive)
            restored.fulfill()
        }
        await fulfillment(of: [restored], timeout: 5)
        let removed = expectation(description: "remove test session")
        keychain.remove(key: SecureKey.sessionToken) { error in
            XCTAssertNil(error)
            removed.fulfill()
        }
        await fulfillment(of: [removed], timeout: 5)
    }

    private func makeController(
        networkPath: NetworkPathChecking,
        funnelStatusStore: FunnelStatusStoring? = nil,
        saveLibraryCredentials: ((String, String, String, @escaping (Bool) -> Void) -> Void)? = nil,
        hasAppLockPassword: @escaping () -> Bool = { false }
    ) -> AuthSessionController {
        let suite = "com.icecream.kwklasplus.test.auth.network.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defaults.removePersistentDomain(forName: suite)
        return AuthSessionController(
            authRuntime: IosAuthRuntime.companion.create(defaults: defaults),
            networkPath: networkPath,
            funnelStatusStore: funnelStatusStore,
            saveLibraryCredentials: saveLibraryCredentials,
            hasAppLockPassword: hasAppLockPassword
        )
    }

    private func waitUntil(
        timeout: TimeInterval = 3,
        predicate: @escaping () -> Bool,
        file: StaticString = #filePath,
        line: UInt = #line
    ) async {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if predicate() { return }
            try? await Task.sleep(nanoseconds: 20_000_000)
        }
        XCTAssertTrue(predicate(), "waitUntil timed out", file: file, line: line)
    }
}

private final class FakeFunnelStatusStore: FunnelStatusStoring {
    private var value: String?
    private let rejectedValues: Set<String>

    init(value: String? = nil, rejectedValues: Set<String> = []) {
        self.value = value
        self.rejectedValues = rejectedValues
    }

    func read() -> String? { value }

    func write(_ value: String) -> Bool {
        guard !rejectedValues.contains(value) else { return false }
        self.value = value
        return read() == value
    }
}

private final class FakeNetworkPathChecker: NetworkPathChecking, @unchecked Sendable {
    var satisfied: Bool
    var delayNanos: UInt64

    init(satisfied: Bool, delayNanos: UInt64 = 0) {
        self.satisfied = satisfied
        self.delayNanos = delayNanos
    }

    func isSatisfied() async -> Bool {
        if delayNanos > 0 {
            try? await Task.sleep(nanoseconds: delayNanos)
        }
        return satisfied
    }
}

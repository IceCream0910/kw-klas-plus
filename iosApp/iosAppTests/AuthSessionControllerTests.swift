import Foundation
import Shared
import XCTest
@testable import kw_klas_plus

@MainActor
final class AuthSessionControllerTests: XCTestCase {
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
        let runtime = IosAuthRuntime.companion.create(defaults: defaults)
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
        defaults.removePersistentDomain(forName: suite)
    }

    private func makeController(networkPath: NetworkPathChecking) -> AuthSessionController {
        let suite = "com.icecream.kwklasplus.test.auth.network.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defaults.removePersistentDomain(forName: suite)
        return AuthSessionController(
            authRuntime: IosAuthRuntime.companion.create(defaults: defaults),
            networkPath: networkPath
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

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
        predicate: @escaping () -> Bool
    ) async {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if predicate() { return }
            try? await Task.sleep(nanoseconds: 20_000_000)
        }
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

import Foundation
import Shared
import XCTest
@testable import kw_klas_plus

final class IosAppLockStoreTests: XCTestCase {
    func testSaveVerifyAndDisableClearsSecretsFromUserDefaults() {
        let env = LockTestEnvironment()
        defer { env.tearDown() }

        XCTAssertFalse(env.store.isEnabled())
        XCTAssertFalse(env.store.hasPassword())
        XCTAssertEqual(
            env.store.currentSettings().toLegacyJson(),
            "{\"enabled\":false,\"biometric\":false,\"hasPassword\":false}"
        )

        env.store.savePassword(password: "123456")
        env.store.setEnabled(enabled: true)
        env.store.setBiometricEnabled(enabled: true)

        XCTAssertTrue(env.store.isEnabled())
        XCTAssertTrue(env.store.hasPassword())
        XCTAssertTrue(env.store.verifyPassword(input: "123456"))
        XCTAssertFalse(env.store.verifyPassword(input: "000000"))
        XCTAssertEqual(
            env.store.currentSettings().toLegacyJson(),
            "{\"enabled\":true,\"biometric\":true,\"hasPassword\":true}"
        )
        XCTAssertTrue(env.defaults.bool(forKey: "a_l_e"))
        XCTAssertTrue(env.defaults.bool(forKey: "b_m_e"))
        assertUserDefaults(env.defaults, doesNotContain: "123456")

        env.store.setEnabled(enabled: false)

        XCTAssertFalse(env.store.isEnabled())
        XCTAssertFalse(env.store.hasPassword())
        XCTAssertFalse(env.store.isBiometricEnabled())
        XCTAssertFalse(env.store.verifyPassword(input: "123456"))
        XCTAssertFalse(env.defaults.bool(forKey: "a_l_e"))
        XCTAssertFalse(env.defaults.bool(forKey: "b_m_e"))
        XCTAssertNil(env.defaults.string(forKey: "p_w_h"))
        XCTAssertNil(env.defaults.string(forKey: "p_w_s"))
    }

    func testHomeRuntimeJsonReadsLiveStore() {
        let env = LockTestEnvironment()
        defer { env.tearDown() }
        env.store.savePassword(password: "654321")
        env.store.setEnabled(enabled: true)

        let runtime = IosHomeRuntime.companion.create(dependencies: env.dependencies)
        XCTAssertEqual(
            runtime.defaultAppLockSettingsJson(),
            "{\"enabled\":true,\"biometric\":false,\"hasPassword\":true}"
        )
    }

    private func assertUserDefaults(
        _ defaults: UserDefaults,
        doesNotContain secret: String,
        file: StaticString = #filePath,
        line: UInt = #line
    ) {
        for (key, value) in defaults.dictionaryRepresentation() {
            XCTAssertFalse(
                String(describing: value).contains(secret),
                "UserDefaults[\(key)] leaked secret",
                file: file,
                line: line
            )
        }
    }
}

@MainActor
final class AppLockControllerTests: XCTestCase {
    func testSetPinThenBackgroundRequestsUnlock() async {
        let env = LockTestEnvironment()
        defer { env.tearDown() }
        let controller = AppLockController(
            store: env.store,
            canUseBiometrics: { false },
            isAppInBackground: { true },
            backgroundLockDelayNanos: 0
        )
        let finished = expectation(description: "set pin")
        var succeeded = false

        controller.presentPasswordSetup { success in
            succeeded = success
            finished.fulfill()
        }
        XCTAssertEqual(controller.mode, .set)
        [1, 2, 3, 4, 5, 6].forEach(controller.appendDigit)
        [1, 2, 3, 4, 5, 6].forEach(controller.appendDigit)

        await fulfillment(of: [finished], timeout: 2)
        XCTAssertTrue(succeeded)
        XCTAssertTrue(env.store.isEnabled())
        XCTAssertTrue(env.store.verifyPassword(input: "123456"))
        XCTAssertTrue(env.store.isUnlocked)
        XCTAssertNil(controller.mode)

        controller.handleScenePhase(.background)
        await waitUntil { !env.store.isUnlocked }
        XCTAssertFalse(env.store.isUnlocked)
        controller.handleScenePhase(.active)
        XCTAssertEqual(controller.mode, .unlock)
    }

    func testCancelRestoreDoesNotEnableLock() {
        let env = LockTestEnvironment()
        defer { env.tearDown() }
        let controller = AppLockController(store: env.store, canUseBiometrics: { false })
        var succeeded: Bool?
        controller.presentPasswordSetup { success in
            succeeded = success
        }
        controller.cancel()
        XCTAssertEqual(succeeded, false)
        XCTAssertFalse(env.store.isEnabled())
        XCTAssertNil(controller.mode)
        XCTAssertFalse(env.store.hasPassword())
    }

    func testVerifyDisableClearsPassword() async {
        let env = LockTestEnvironment()
        defer { env.tearDown() }
        env.store.savePassword(password: "123456")
        env.store.setEnabled(enabled: true)
        let controller = AppLockController(store: env.store, canUseBiometrics: { false })
        let finished = expectation(description: "disable")
        var succeeded = false

        controller.presentVerifyToDisable { success in
            succeeded = success
            finished.fulfill()
        }
        [1, 2, 3, 4, 5, 6].forEach(controller.appendDigit)

        await fulfillment(of: [finished], timeout: 2)
        XCTAssertTrue(succeeded)
        XCTAssertFalse(env.store.isEnabled())
        XCTAssertFalse(env.store.hasPassword())
    }

    func testSettingsHostReadsStoreJson() {
        let env = LockTestEnvironment()
        defer { env.tearDown() }
        env.store.savePassword(password: "112233")
        env.store.setEnabled(enabled: true)
        env.store.setBiometricEnabled(enabled: true)

        let authRuntime = IosAuthRuntime.companion.create(dependencies: env.dependencies)
        let coordinator = HomeCoordinator(authRuntime: authRuntime, onLogout: {})
        let appLock = AppLockController(store: env.store, canUseBiometrics: { false })
        let model = SettingsScreenModel(coordinator: coordinator, appLock: appLock)

        XCTAssertEqual(
            model.currentLockSettingsJson(),
            "{\"enabled\":true,\"biometric\":true,\"hasPassword\":true}"
        )
        model.holder.dispose()
    }

    func testLandscapeUsesTwoPaneLikeAndroid() {
        XCTAssertFalse(LockScreenMetrics.useTwoPane(width: 393, height: 852))
        XCTAssertTrue(LockScreenMetrics.useTwoPane(width: 852, height: 393))
        XCTAssertTrue(LockScreenMetrics.useTwoPane(width: 932, height: 430))
        XCTAssertFalse(LockScreenMetrics.useTwoPane(width: 834, height: 1194))
        XCTAssertTrue(LockScreenMetrics.useTwoPane(width: 1194, height: 834))
    }

    func testEnableBiometricsDoesNotOpenUnlockScreen() async {
        let env = LockTestEnvironment()
        defer { env.tearDown() }
        env.store.savePassword(password: "123456")
        env.store.setEnabled(enabled: true)
        env.store.isUnlocked = true

        let box = ControllerBox()
        let controller = AppLockController(
            store: env.store,
            canUseBiometrics: { true },
            isAppInBackground: { true },
            authenticateBiometrics: { _, _ in
                box.controller?.handleScenePhase(.background)
                box.controller?.handleScenePhase(.active)
                return PlatformActionResultSuccess()
            }
        )
        box.controller = controller

        let result = await controller.authenticateEnableBiometrics()
        XCTAssertTrue(result is PlatformActionResultSuccess)
        XCTAssertTrue(env.store.isUnlocked)
        XCTAssertNil(controller.mode)
    }

    func testSetPinBiometricPromptDoesNotOpenUnlock() async {
        let env = LockTestEnvironment()
        defer { env.tearDown() }
        let box = ControllerBox()
        let finished = expectation(description: "set pin")
        let controller = AppLockController(
            store: env.store,
            canUseBiometrics: { true },
            isAppInBackground: { true },
            authenticateBiometrics: { _, _ in
                box.controller?.handleScenePhase(.background)
                box.controller?.handleScenePhase(.active)
                return PlatformActionResultSuccess()
            }
        )
        box.controller = controller
        var succeeded = false
        controller.presentPasswordSetup { success in
            succeeded = success
            finished.fulfill()
        }
        [1, 2, 3, 4, 5, 6].forEach(controller.appendDigit)
        [1, 2, 3, 4, 5, 6].forEach(controller.appendDigit)

        await fulfillment(of: [finished], timeout: 2)
        XCTAssertTrue(succeeded)
        XCTAssertTrue(env.store.isUnlocked)
        XCTAssertTrue(env.store.isBiometricEnabled())
        XCTAssertNil(controller.mode)
    }

    func testFaceIdInactiveDoesNotLockWhenAppStillForeground() {
        let env = LockTestEnvironment()
        defer { env.tearDown() }
        env.store.savePassword(password: "123456")
        env.store.setEnabled(enabled: true)
        env.store.isUnlocked = true
        let controller = AppLockController(
            store: env.store,
            canUseBiometrics: { false },
            isAppInBackground: { false }
        )

        controller.handleScenePhase(.background)
        controller.handleScenePhase(.active)

        XCTAssertTrue(env.store.isUnlocked)
        XCTAssertNil(controller.mode)
    }

    func testQuickForegroundReturnDoesNotLock() async {
        let env = LockTestEnvironment()
        defer { env.tearDown() }
        env.store.savePassword(password: "123456")
        env.store.setEnabled(enabled: true)
        env.store.isUnlocked = true
        let controller = AppLockController(
            store: env.store,
            canUseBiometrics: { false },
            isAppInBackground: { true },
            backgroundLockDelayNanos: 5_000_000_000
        )

        controller.handleScenePhase(.background)
        controller.handleScenePhase(.active)

        XCTAssertTrue(env.store.isUnlocked)
        XCTAssertNil(controller.mode)
    }

    private func waitUntil(
        timeout: TimeInterval = 1,
        _ predicate: @escaping () -> Bool
    ) async {
        let deadline = Date().addingTimeInterval(timeout)
        while !predicate(), Date() < deadline {
            await Task.yield()
        }
    }
}

private final class ControllerBox {
    var controller: AppLockController?
}

private final class LockTestEnvironment {
    let suite: String
    let defaults: UserDefaults
    let keychain: IosKeychainSecureStore
    let store: IosAppLockStore
    let dependencies: IosSharedDependencies

    init() {
        suite = "com.icecream.kwklasplus.test.applock.\(UUID().uuidString)"
        defaults = UserDefaults(suiteName: suite)!
        defaults.removePersistentDomain(forName: suite)
        keychain = IosKeychainSecureStore(
            service: "com.icecream.kwklasplus.test.applock.keychain.\(UUID().uuidString)"
        )
        dependencies = IosSharedDependencies.companion.create(
            defaults: defaults,
            secureStore: keychain,
            cookieStore: nil
        )
        store = dependencies.appLockStore
    }

    func tearDown() {
        store.setEnabled(enabled: false)
        defaults.removePersistentDomain(forName: suite)
    }
}

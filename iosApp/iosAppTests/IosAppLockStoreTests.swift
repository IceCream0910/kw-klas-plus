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
        let controller = AppLockController(store: env.store, canUseBiometrics: { false })
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
        XCTAssertNil(controller.mode)

        env.store.isUnlocked = true
        controller.handleScenePhase(.background)
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

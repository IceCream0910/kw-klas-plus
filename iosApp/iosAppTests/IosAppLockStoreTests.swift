import Foundation
import SwiftUI
import UIKit
import Shared
import XCTest
@testable import kw_klas_plus

final class IosAppLockStoreTests: XCTestCase {
    @MainActor
    func testOnboardingCoverAllowsOnlyExplicitPasswordSetup() {
        for mode in [AppLockController.Mode.set, .change] {
            XCTAssertEqual(AppLockCoverPolicy.coverMode(isSessionAuthenticated: false, isQrBypassActive: false, mode: mode, allowsOnboardingSetup: true), mode)
            XCTAssertNil(AppLockCoverPolicy.coverMode(isSessionAuthenticated: false, isQrBypassActive: false, mode: mode))
        }
        for mode in [AppLockController.Mode.unlock, .verify] {
            XCTAssertNil(AppLockCoverPolicy.coverMode(isSessionAuthenticated: false, isQrBypassActive: false, mode: mode, allowsOnboardingSetup: true))
        }
        XCTAssertNil(AppLockCoverPolicy.coverMode(isSessionAuthenticated: false, isQrBypassActive: true, mode: .set, allowsOnboardingSetup: true))
    }

    @MainActor
    func testPrivacyCoverConcealsHomePixelsAndKeepsUnlockedControlVisible() throws {
        let sensitive = Color.red.frame(width: 120, height: 120)
        let hidden = ImageRenderer(content: sensitive.protectedHomeContent(isHidden: true))
        let visible = ImageRenderer(content: sensitive.protectedHomeContent(isHidden: false))
        let expected = ImageRenderer(content: Color(uiColor: .systemBackground).frame(width: 120, height: 120))
        let hiddenPixels = try XCTUnwrap(hidden.uiImage?.pngData())
        let visiblePixels = try XCTUnwrap(visible.uiImage?.pngData())
        XCTAssertEqual(hiddenPixels, try XCTUnwrap(expected.uiImage?.pngData()))
        XCTAssertNotEqual(hiddenPixels, visiblePixels)
    }

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

    func testOnboardingNewPinAdvancesAfterSaveEvenWhenBiometricsCancelled() async {
        let env = LockTestEnvironment()
        defer { env.tearDown() }
        let lock = AppLockController(store: env.store, canUseBiometrics: { true },
            authenticateBiometrics: { _, _ in PlatformActionResultCancelled() })
        let auth = AuthSessionController(authRuntime: IosAuthRuntime.companion.create(dependencies: env.dependencies))
        env.defaults.set("authenticating", forKey: "login_funnel_status")
        auth.enterAuthenticated(initialDelayMillis: 0)
        auth.skipLibrary()
        let finished = expectation(description: "advance onboarding")
        lock.presentOnboardingPasswordSetup { success in
            if success { auth.continueFunnel() }
            finished.fulfill()
        }
        for _ in 0..<2 { [1, 2, 3, 4, 5, 6].forEach(lock.appendDigit) }
        await fulfillment(of: [finished], timeout: 2)
        XCTAssertEqual(auth.loginState.step, .notifications)
        XCTAssertEqual(env.defaults.string(forKey: "login_funnel_status"), "setup")
        XCTAssertTrue(env.store.isEnabled())
        XCTAssertNil(lock.mode)
    }

    func testOnboardingExistingPinEnablesBeforeCompletion() async {
        let env = LockTestEnvironment()
        defer { env.tearDown() }
        env.store.savePassword(password: "123456")
        let lock = AppLockController(store: env.store, canUseBiometrics: { false })
        let finished = expectation(description: "existing pin enabled")
        var succeeded = false
        lock.presentOnboardingPasswordSetup { success in
            succeeded = success && env.store.isEnabled()
            finished.fulfill()
        }
        XCTAssertEqual(lock.mode, .change)
        [1, 2, 3, 4, 5, 6].forEach(lock.appendDigit)
        for _ in 0..<2 { [6, 5, 4, 3, 2, 1].forEach(lock.appendDigit) }
        await fulfillment(of: [finished], timeout: 2)
        XCTAssertTrue(succeeded)
        XCTAssertTrue(env.store.verifyPassword(input: "654321"))
        XCTAssertTrue(env.store.isUnlocked)
    }

    func testCancelledOnboardingExistingPinDoesNotEnable() {
        let env = LockTestEnvironment()
        defer { env.tearDown() }
        env.store.savePassword(password: "123456")
        let lock = AppLockController(store: env.store, canUseBiometrics: { false })
        var succeeded: Bool?
        lock.presentOnboardingPasswordSetup { succeeded = $0 }
        lock.cancel()
        XCTAssertEqual(succeeded, false)
        XCTAssertFalse(env.store.isEnabled())
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

    func testLibraryQrExemptSkipsUnlockPrompt() {
        let env = LockTestEnvironment()
        defer { env.tearDown() }
        env.store.savePassword(password: "123456")
        env.store.setEnabled(enabled: true)
        env.store.isUnlocked = false
        let controller = AppLockController(store: env.store, canUseBiometrics: { false })
        controller.isLibraryQrExempt = true
        controller.handleScenePhase(.active)
        XCTAssertNil(controller.mode)
        XCTAssertFalse(env.store.isUnlocked)
    }

    func testQrBypassHidesUnlockCoverWithoutClearingMode() {
        XCTAssertEqual(
            AppLockCoverPolicy.coverMode(
                isSessionAuthenticated: true,
                isQrBypassActive: true,
                mode: .unlock
            ),
            nil
        )
        XCTAssertEqual(
            AppLockCoverPolicy.assignedMode(
                isQrBypassActive: true,
                current: .unlock,
                proposed: nil
            ),
            .unlock
        )
        XCTAssertEqual(
            AppLockCoverPolicy.coverMode(
                isSessionAuthenticated: true,
                isQrBypassActive: false,
                mode: .unlock
            ),
            .unlock
        )
    }

    func testPresentUnlockCompletesWhenAlreadyUnlocked() {
        let env = LockTestEnvironment()
        defer { env.tearDown() }
        env.store.savePassword(password: "123456")
        env.store.setEnabled(enabled: true)
        env.store.isUnlocked = true
        let controller = AppLockController(store: env.store, canUseBiometrics: { false })
        var succeeded: Bool?
        controller.presentUnlock { succeeded = $0 }
        XCTAssertEqual(succeeded, true)
        XCTAssertNil(controller.mode)
    }

    func testPresentUnlockSuccessCallsCompletion() {
        let env = LockTestEnvironment()
        defer { env.tearDown() }
        env.store.savePassword(password: "123456")
        env.store.setEnabled(enabled: true)
        env.store.isUnlocked = false
        let controller = AppLockController(store: env.store, canUseBiometrics: { false })
        var succeeded: Bool?
        controller.presentUnlock { succeeded = $0 }
        XCTAssertEqual(controller.mode, .unlock)
        [1, 2, 3, 4, 5, 6].forEach(controller.appendDigit)
        XCTAssertEqual(succeeded, true)
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

import Shared
import SwiftUI
import UIKit

@MainActor
final class AppLockController: ObservableObject {
    enum Mode: String, Identifiable {
        case unlock
        case set
        case change
        case verify

        var id: String { rawValue }
    }

    @Published var mode: Mode?
    @Published private(set) var title = ""
    @Published private(set) var description = ""
    @Published private(set) var enteredDigits = 0
    @Published private(set) var biometricVisible = false
    @Published var toastMessage: String?

    let store: IosAppLockStore

    private let policy = AppLockPolicy()
    private let biometrics = IosBiometrics()
    private let canUseBiometrics: () -> Bool
    private var input = ""
    private var firstNewPassword: String?
    private var oldPassword: String?
    private var disabling = false
    private var settingsCompletion: ((Bool) -> Void)?
    private var toastTask: Task<Void, Never>?
    private var didAutoPromptBiometric = false

    init(
        store: IosAppLockStore,
        canUseBiometrics: @escaping () -> Bool = { IosBiometricAvailability.canAuthenticate() }
    ) {
        self.store = store
        self.canUseBiometrics = canUseBiometrics
    }

    var canCancel: Bool { mode != .unlock }

    func handleScenePhase(_ phase: ScenePhase) {
        switch phase {
        case .background:
            let reduced = policy.reduce(
                state: store.currentState(),
                event: AppLockEventEnteredBackground.shared
            )
            store.isUnlocked = reduced.unlocked
        case .active:
            requestUnlockIfNeeded()
        default:
            break
        }
    }

    func requestUnlockIfNeeded() {
        let exempt = mode != nil
        if policy.shouldRequestUnlock(state: store.currentState(), isExemptHost: exempt) {
            present(.unlock, disabling: false, completion: nil)
        }
    }

    func presentPasswordSetup(completion: @escaping (Bool) -> Void) {
        present(store.hasPassword() ? .change : .set, disabling: false, completion: completion)
    }

    func presentVerifyToDisable(completion: @escaping (Bool) -> Void) {
        present(.verify, disabling: true, completion: completion)
    }

    func cancel() {
        guard canCancel else { return }
        finishSettings(success: false)
    }

    func appendDigit(_ digit: Int) {
        guard input.count < LockScreenMetrics.pinLength else { return }
        input.append(String(digit))
        enteredDigits = input.count
        if input.count == LockScreenMetrics.pinLength {
            confirmInput()
        }
    }

    func deleteDigit() {
        guard !input.isEmpty else { return }
        input.removeLast()
        enteredDigits = input.count
    }

    func promptUnlockBiometricIfNeeded() {
        guard mode == .unlock, biometricVisible, !didAutoPromptBiometric else { return }
        didAutoPromptBiometric = true
        Task { await authenticateUnlock() }
    }

    func requestUnlockBiometric() {
        Task { await authenticateUnlock() }
    }

    func authenticateEnableBiometrics() async -> PlatformActionResult {
        await biometrics.authenticate(purpose: .enableBiometrics)
    }

    private func present(_ mode: Mode, disabling: Bool, completion: ((Bool) -> Void)?) {
        self.disabling = disabling
        settingsCompletion = completion
        firstNewPassword = nil
        oldPassword = nil
        didAutoPromptBiometric = false
        self.mode = mode
        refreshChrome()
    }

    private func refreshChrome() {
        switch mode {
        case .unlock:
            title = "앱 잠금"
            description = "비밀번호 6자리를 입력해주세요."
            biometricVisible = store.isBiometricEnabled() && canUseBiometrics()
        case .set:
            title = "비밀번호 설정"
            description = firstNewPassword == nil
                ? "새로운 비밀번호 6자리를 입력해주세요."
                : "다시 한번 입력해주세요."
            biometricVisible = false
        case .change:
            title = "비밀번호 변경"
            if oldPassword == nil {
                description = "현재 비밀번호를 입력해주세요."
            } else if firstNewPassword == nil {
                description = "새로운 비밀번호 6자리를 입력해주세요."
            } else {
                description = "다시 한번 입력해주세요."
            }
            biometricVisible = false
        case .verify:
            title = "비밀번호 확인"
            description = "기존 비밀번호를 입력해주세요."
            biometricVisible = false
        case nil:
            title = ""
            description = ""
            biometricVisible = false
        }
        clearInput()
    }

    private func confirmInput() {
        guard input.count == LockScreenMetrics.pinLength else {
            showToast("비밀번호 6자리를 입력해주세요.")
            return
        }
        switch mode {
        case .unlock:
            verifyUnlock(input)
        case .set:
            setPassword(input)
        case .change:
            changePassword(input)
        case .verify:
            verifyExisting(input)
        case nil:
            break
        }
    }

    private func verifyUnlock(_ value: String) {
        if store.verifyPassword(input: value) {
            unlockSuccess()
        } else {
            showToast("비밀번호가 일치하지 않습니다.")
            clearInput()
        }
    }

    private func setPassword(_ value: String) {
        if firstNewPassword == nil {
            firstNewPassword = value
            refreshChrome()
            return
        }
        if value != firstNewPassword {
            showToast("비밀번호가 일치하지 않습니다. 처음부터 다시 시도해주세요.")
            firstNewPassword = nil
            refreshChrome()
            return
        }
        store.savePassword(password: value)
        store.setEnabled(enabled: true)
        showToast("비밀번호가 설정되었습니다.")
        Task { await completePasswordUpdate() }
    }

    private func changePassword(_ value: String) {
        if oldPassword == nil {
            if store.verifyPassword(input: value) {
                oldPassword = value
                refreshChrome()
            } else {
                showToast("현재 비밀번호가 일치하지 않습니다.")
                clearInput()
            }
            return
        }
        if firstNewPassword == nil {
            firstNewPassword = value
            refreshChrome()
            return
        }
        if value != firstNewPassword {
            showToast("비밀번호가 일치하지 않습니다. 새로운 비밀번호부터 다시 입력해주세요.")
            firstNewPassword = nil
            refreshChrome()
            return
        }
        store.savePassword(password: value)
        showToast("비밀번호가 변경되었습니다.")
        Task { await completePasswordUpdate() }
    }

    private func verifyExisting(_ value: String) {
        if store.verifyPassword(input: value) {
            if disabling {
                store.setEnabled(enabled: false)
            }
            finishSettings(success: true)
        } else {
            showToast("비밀번호가 일치하지 않습니다.")
            clearInput()
        }
    }

    private func completePasswordUpdate() async {
        if canUseBiometrics() {
            let result = await biometrics.authenticate(
                purpose: .enableBiometrics,
                localizedReason: "생체인증을 사용하려면 인증이 필요합니다."
            )
            if result is PlatformActionResultSuccess {
                store.setBiometricEnabled(enabled: true)
                showToast("생체인증이 활성화되었습니다.")
            }
        }
        finishSettings(success: true)
    }

    private func authenticateUnlock() async {
        let result = await biometrics.authenticate(purpose: .unlockApp)
        if result is PlatformActionResultSuccess {
            unlockSuccess()
        }
    }

    private func unlockSuccess() {
        store.isUnlocked = true
        mode = nil
        settingsCompletion = nil
        disabling = false
    }

    private func finishSettings(success: Bool) {
        let completion = settingsCompletion
        settingsCompletion = nil
        disabling = false
        mode = nil
        completion?(success)
    }

    private func clearInput() {
        input = ""
        enteredDigits = 0
    }

    func showToast(_ message: String) {
        toastMessage = message
        UIAccessibility.post(notification: .announcement, argument: message)
        toastTask?.cancel()
        toastTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: 2_500_000_000)
            if toastMessage == message {
                toastMessage = nil
            }
        }
    }
}

enum LockScreenMetrics {
    static let pinLength = 6
}

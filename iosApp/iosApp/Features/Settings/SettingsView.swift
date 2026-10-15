import Shared
import SwiftUI

@MainActor
final class SettingsScreenModel: ObservableObject {
    let holder: WebViewHolder
    let coordinator: HomeCoordinator
    let appLock: AppLockController
    private var host: SettingsHostAdapter

    init(coordinator: HomeCoordinator, appLock: AppLockController) {
        self.coordinator = coordinator
        self.appLock = appLock
        let host = SettingsHostAdapter()
        self.host = host
        self.holder = WebViewHolder.withLegacyBridge(
            surface: .settings,
            handler: IosSettingsLegacyBridgeCommandHandler(host: host),
            synchronousHandler: IosSettingsLegacySynchronousBridgeCommandHandler(host: host)
        )
        host.model = self
        host.store = appLock.store
        coordinator.attachSettingsWebView(holder)
        holder.load(KlasUrls.shared.SETTINGS)
    }

    deinit { holder.dispose() }

    func completePageLoad() {
        holder.evaluate(IosWebCallbacks.shared.receiveTheme(theme: coordinator.theme))
        holder.evaluate(IosWebCallbacks.shared.receiveYearHakgi(value: coordinator.yearHakgi))
        holder.evaluate(IosWebCallbacks.shared.receiveVersion(version: coordinator.appVersion))
        notifyLockSettingsChanged()
    }

    func currentLockSettingsJson() -> String {
        appLock.store.currentSettings().toLegacyJson()
    }

    func configureContextualAppLock() {
        appLock.presentContextualPasswordSetup { [weak self] success in
            guard let self else { return }
            self.handleLockSetupResult(success: success, disabling: false)
            if success { self.coordinator.showToast("앱 잠금이 설정되었어요.") }
        }
    }

    func setAppLockEnabled(enabled: Bool) {
        if enabled {
            appLock.presentPasswordSetup { [weak self] success in
                self?.handleLockSetupResult(success: success, disabling: false)
            }
            return
        }
        if !appLock.store.hasPassword() {
            notifyLockSettingsChanged()
            return
        }
        appLock.presentVerifyToDisable { [weak self] success in
            self?.handleLockSetupResult(success: success, disabling: true)
        }
    }

    func setAppLockPassword() {
        appLock.presentPasswordSetup { [weak self] success in
            self?.handleLockSetupResult(success: success, disabling: false)
        }
    }

    func setBiometricEnabled(enabled: Bool) {
        if enabled {
            if let message = IosBiometricAvailability.errorMessage() {
                coordinator.showToast(message)
                holder.evaluate(IosWebCallbacks.shared.biometricSettingChanged(enabled: false))
                return
            }
            Task {
                let result = await appLock.authenticateEnableBiometrics()
                if result is PlatformActionResultSuccess {
                    appLock.store.setBiometricEnabled(enabled: true)
                    coordinator.showToast("생체인증이 활성화되었습니다.")
                    holder.evaluate(IosWebCallbacks.shared.biometricSettingChanged(enabled: true))
                } else {
                    holder.evaluate(IosWebCallbacks.shared.biometricSettingChanged(enabled: false))
                }
            }
        } else {
            appLock.store.setBiometricEnabled(enabled: false)
            coordinator.showToast("생체인증이 비활성화되었습니다.")
            holder.evaluate(IosWebCallbacks.shared.biometricSettingChanged(enabled: false))
        }
    }

    private func handleLockSetupResult(success: Bool, disabling: Bool) {
        if !success {
            notifyLockSettingsChanged()
            coordinator.showToast("인증이 취소되었습니다.")
            return
        }
        if disabling {
            coordinator.showToast("앱 잠금이 비활성화되고 비밀번호가 초기화되었습니다.")
        }
        notifyLockSettingsChanged()
    }

    private func notifyLockSettingsChanged() {
        holder.evaluate(
            IosWebCallbacks.shared.appLockSettingChanged(settings: appLock.store.currentSettings())
        )
        holder.evaluate(IosWebCallbacks.shared.requestSettingsReload())
    }
}

final class SettingsHostAdapter: SettingsBridgeHost {
    weak var model: SettingsScreenModel?
    var store: IosAppLockStore?

    func completePageLoad() {
        Task { @MainActor in model?.completePageLoad() }
    }

    func changeAppTheme(type: String) {
        Task { @MainActor in model?.coordinator.applyTheme(type) }
    }

    func openYearHakgiSelectModal() {
        Task { @MainActor in model?.coordinator.presentYearHakgiPicker() }
    }

    func openLibraryQRSettingsModal() {
        Task { @MainActor in model?.coordinator.libraryQr?.presentSettingsFromApp() }
    }

    func openExternalLink(url: String) {
        Task { @MainActor in model?.coordinator.openExternal(url: url) }
    }

    func performHapticFeedback(type: String) {
        Task { @MainActor in model?.coordinator.performHaptic(type) }
    }

    func setAppLockEnabled(enabled: Bool) {
        Task { @MainActor in model?.setAppLockEnabled(enabled: enabled) }
    }

    func setAppLockPassword() {
        Task { @MainActor in model?.setAppLockPassword() }
    }

    func setBiometricEnabled(enabled: Bool) {
        Task { @MainActor in model?.setBiometricEnabled(enabled: enabled) }
    }

    func getAppLockSettings() -> String {
        store?.currentSettings().toLegacyJson()
            ?? "{\"enabled\":false,\"biometric\":false,\"hasPassword\":false}"
    }
}

struct SettingsView: View {
    @StateObject private var model: SettingsScreenModel
    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var scenePhase

    init(coordinator: HomeCoordinator, appLock: AppLockController) {
        _model = StateObject(
            wrappedValue: SettingsScreenModel(coordinator: coordinator, appLock: appLock)
        )
    }

    var body: some View {
        PushedWebStack(holder: model.holder) {
            if model.holder.goBack() { return }
            dismiss()
        }
        .overlay(alignment: .bottom) {
            SettingsLockSuggestion(setup: model.coordinator.contextualSetup, appLock: model.appLock,
                isActive: scenePhase == .active, onAction: model.configureContextualAppLock)
                .padding(.bottom, 16)
        }
        .accessibilityIdentifier("settings_view")
    }
}

struct SettingsLockSuggestion: View {
    @ObservedObject var setup: ContextualSetup
    @ObservedObject var appLock: AppLockController
    let isActive: Bool
    let onAction: () -> Void

    var body: some View {
        Group {
            if setup.suggestion == .appLock, !appLock.store.isEnabled(), appLock.mode == nil, isActive {
                SetupSuggestionCard(kind: .appLock, onClose: setup.dismiss) {
                    setup.dismiss()
                    onAction()
                }
                .onAppear { setup.displayed(.appLock) }
            }
        }
        .onAppear { setup.offer(.appLock, configured: appLock.store.isEnabled(), replaceCurrent: true) }
    }
}

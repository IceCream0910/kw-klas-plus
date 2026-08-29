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
        holder.load(KlasUrls.shared.SETTINGS)
    }

    deinit { holder.dispose() }

    func completePageLoad() {
        holder.evaluate(IosWebCallbacks.shared.receiveTheme(theme: coordinator.theme))
        holder.evaluate(IosWebCallbacks.shared.receiveYearHakgi(value: coordinator.yearHakgi))
        holder.evaluate(IosWebCallbacks.shared.receiveVersion(version: coordinator.appVersion))
    }

    func currentLockSettingsJson() -> String {
        appLock.store.currentSettings().toLegacyJson()
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
        Task { @MainActor in model?.coordinator.presentUnavailable() }
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
        .accessibilityIdentifier("settings_view")
    }
}

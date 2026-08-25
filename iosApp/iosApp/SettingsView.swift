import Shared
import SwiftUI

@MainActor
final class SettingsScreenModel: ObservableObject {
    let holder: WebViewHolder
    let coordinator: HomeCoordinator
    private var host: SettingsHostAdapter
    let lockSettingsJson: String

    init(coordinator: HomeCoordinator) {
        self.coordinator = coordinator
        self.lockSettingsJson = coordinator.homeRuntime.defaultAppLockSettingsJson()
        let host = SettingsHostAdapter()
        self.host = host
        self.holder = WebViewHolder.withLegacyBridge(
            surface: .settings,
            handler: IosSettingsLegacyBridgeCommandHandler(host: host),
            synchronousHandler: IosSettingsLegacySynchronousBridgeCommandHandler(host: host)
        )
        host.model = self
        holder.load(KlasUrls.shared.SETTINGS)
    }

    deinit { holder.dispose() }

    func completePageLoad() {
        holder.evaluate(IosWebCallbacks.shared.receiveTheme(theme: coordinator.theme))
        holder.evaluate(IosWebCallbacks.shared.receiveYearHakgi(value: coordinator.yearHakgi))
        holder.evaluate(IosWebCallbacks.shared.receiveVersion(version: coordinator.appVersion))
    }
}

final class SettingsHostAdapter: SettingsBridgeHost {
    weak var model: SettingsScreenModel?

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
        Task { @MainActor in model?.coordinator.presentUnavailable() }
    }

    func setAppLockPassword() {
        Task { @MainActor in model?.coordinator.presentUnavailable() }
    }

    func setBiometricEnabled(enabled: Bool) {
        Task { @MainActor in model?.coordinator.presentUnavailable() }
    }

    func getAppLockSettings() -> String {
        model?.lockSettingsJson
            ?? "{\"enabled\":false,\"biometric\":false,\"hasPassword\":false}"
    }
}

struct SettingsView: View {
    @StateObject private var model: SettingsScreenModel
    @Environment(\.dismiss) private var dismiss

    init(coordinator: HomeCoordinator) {
        _model = StateObject(wrappedValue: SettingsScreenModel(coordinator: coordinator))
    }

    var body: some View {
        PushedWebStack(holder: model.holder) {
            if model.holder.goBack() { return }
            dismiss()
        }
        .accessibilityIdentifier("settings_view")
    }
}

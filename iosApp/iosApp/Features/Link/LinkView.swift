import Shared
import SwiftUI

@MainActor
final class LinkScreenModel: ObservableObject {
    let holder: WebViewHolder
    let sessionToken: SecretValue?
    weak var coordinator: HomeCoordinator?
    private var host: LinkHostAdapter
    var isWebBottomSheetOpen = false

    init(url: String, sessionToken: SecretValue?, coordinator: HomeCoordinator) {
        self.sessionToken = sessionToken
        self.coordinator = coordinator
        let host = LinkHostAdapter()
        self.host = host
        self.holder = WebViewHolder.withLegacyBridge(
            surface: .linkView,
            handler: IosLinkLegacyBridgeCommandHandler(host: host)
        )
        host.model = self
        holder.load(url)
    }

    deinit { holder.dispose() }

    func completePageLoad() {
        guard let token = sessionToken else { return }
        holder.evaluate(IosWebCallbacks.shared.receiveToken(token: token.reveal()))
    }

    func handleBack(dismiss: () -> Void) {
        if isWebBottomSheetOpen {
            holder.evaluate(KlasWebAutomationScripts.shared.closeBottomSheet())
            isWebBottomSheetOpen = false
            return
        }
        if holder.goBack() { return }
        dismiss()
    }
}

final class LinkHostAdapter: LinkBridgeHost {
    weak var model: LinkScreenModel?

    func openPage(url: String) {
        Task { @MainActor in model?.coordinator?.openWeb(url: url) }
    }

    func openLecturePlanPage(id: String) {
        Task { @MainActor in model?.coordinator?.openLecturePlan(subjectId: id) }
    }

    func openWebViewBottomSheet() {
        Task { @MainActor in model?.isWebBottomSheetOpen = true }
    }

    func closeWebViewBottomSheet() {
        Task { @MainActor in model?.isWebBottomSheetOpen = false }
    }

    func completePageLoad() {
        Task { @MainActor in model?.completePageLoad() }
    }
}

struct LinkView: View {
    @StateObject private var model: LinkScreenModel
    @Environment(\.dismiss) private var dismiss

    init(url: String, sessionToken: SecretValue?, coordinator: HomeCoordinator) {
        _model = StateObject(
            wrappedValue: LinkScreenModel(
                url: url,
                sessionToken: sessionToken,
                coordinator: coordinator
            )
        )
    }

    var body: some View {
        PushedWebStack(holder: model.holder) {
            model.handleBack(dismiss: { dismiss() })
        }
        .accessibilityIdentifier("link_view")
    }
}

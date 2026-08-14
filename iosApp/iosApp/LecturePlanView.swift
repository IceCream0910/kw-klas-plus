import Shared
import SwiftUI

@MainActor
final class LecturePlanScreenModel: ObservableObject {
    let holder: WebViewHolder
    let subjectId: String
    let sessionToken: SecretValue?
    weak var coordinator: HomeCoordinator?
    private var host: LecturePlanHostAdapter

    init(subjectId: String, sessionToken: SecretValue?, coordinator: HomeCoordinator) {
        self.subjectId = subjectId
        self.sessionToken = sessionToken
        self.coordinator = coordinator
        let host = LecturePlanHostAdapter()
        self.host = host
        self.holder = WebViewHolder.withLegacyBridge(
            surface: .lecturePlan,
            handler: IosLecturePlanLegacyBridgeCommandHandler(host: host)
        )
        host.model = self
        holder.load(KlasUrls.shared.LECTURE_PLAN)
    }

    deinit { holder.dispose() }

    func completePageLoad() {
        guard let token = sessionToken else { return }
        holder.evaluate(IosWebCallbacks.shared.receivedData(token: token.reveal(), subjectId: subjectId))
    }
}

final class LecturePlanHostAdapter: LecturePlanBridgeHost {
    weak var model: LecturePlanScreenModel?

    func completePageLoad() {
        Task { @MainActor in model?.completePageLoad() }
    }

    func openPage(url: String) {
        Task { @MainActor in model?.coordinator?.openWeb(url: url) }
    }

    func openExternalPage(url: String) {
        Task { @MainActor in model?.coordinator?.openExternal(url: url) }
    }
}

struct LecturePlanView: View {
    @StateObject private var model: LecturePlanScreenModel
    @Environment(\.dismiss) private var dismiss

    init(subjectId: String, sessionToken: SecretValue?, coordinator: HomeCoordinator) {
        _model = StateObject(
            wrappedValue: LecturePlanScreenModel(
                subjectId: subjectId,
                sessionToken: sessionToken,
                coordinator: coordinator
            )
        )
    }

    var body: some View {
        PushedWebStack(holder: model.holder, isLoading: model.holder.isLoading) {
            if model.holder.goBack() { return }
            dismiss()
        }
        .accessibilityIdentifier("lecture_plan_view")
    }
}

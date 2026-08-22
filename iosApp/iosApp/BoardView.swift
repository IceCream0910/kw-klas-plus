import Shared
import SwiftUI

enum BoardScreenMode {
    case list(title: String)
    case view(boardNumber: String, masterNumber: String)
}

@MainActor
final class BoardScreenModel: ObservableObject {
    let holder: WebViewHolder
    let path: String
    let subjectId: String
    let yearSemester: String
    let sessionToken: SecretValue?
    weak var coordinator: HomeCoordinator?
    private var host: BoardHostAdapter

    init(
        mode: BoardScreenMode,
        path: String,
        subjectId: String,
        yearSemester: String,
        sessionToken: SecretValue?,
        coordinator: HomeCoordinator
    ) {
        self.path = path
        self.subjectId = subjectId
        self.yearSemester = yearSemester
        self.sessionToken = sessionToken
        self.coordinator = coordinator
        let host = BoardHostAdapter()
        self.host = host
        self.holder = WebViewHolder.withLegacyBridge(
            surface: .board,
            handler: IosBoardLegacyBridgeCommandHandler(host: host)
        )
        host.model = self
        switch mode {
        case let .list(title):
            holder.load(ProductWebUrls.shared.boardList(title: title))
        case let .view(boardNumber, masterNumber):
            holder.load(ProductWebUrls.shared.boardView(boardNumber: boardNumber, masterNumber: masterNumber))
        }
    }

    deinit { holder.dispose() }

    func completePageLoad() {
        guard let token = sessionToken else { return }
        holder.evaluate(
            IosWebCallbacks.shared.receivedData(
                token: token.reveal(),
                subjectId: subjectId,
                yearHakgi: yearSemester,
                path: path
            )
        )
    }
}

final class BoardHostAdapter: BoardBridgeHost {
    weak var model: BoardScreenModel?

    func openPage(url: String) {
        Task { @MainActor in model?.coordinator?.openWeb(url: url) }
    }

    func openExternalLink(url: String) {
        Task { @MainActor in model?.coordinator?.openExternal(url: url) }
    }

    func completePageLoad() {
        Task { @MainActor in model?.completePageLoad() }
    }
}

struct BoardView: View {
    @StateObject private var model: BoardScreenModel
    @Environment(\.dismiss) private var dismiss

    init(
        mode: BoardScreenMode,
        path: String,
        subjectId: String,
        yearSemester: String,
        sessionToken: SecretValue?,
        coordinator: HomeCoordinator
    ) {
        _model = StateObject(
            wrappedValue: BoardScreenModel(
                mode: mode,
                path: path,
                subjectId: subjectId,
                yearSemester: yearSemester,
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
        .accessibilityIdentifier("board_view")
    }
}

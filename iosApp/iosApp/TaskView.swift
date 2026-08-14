import Shared
import SwiftUI

@MainActor
final class TaskScreenModel: ObservableObject {
    let holder: WebViewHolder
    private var didInject = false
    let subjectId: String
    let yearSemester: String
    weak var coordinator: HomeCoordinator?

    init(
        path: String,
        subjectId: String,
        yearSemester: String,
        sessionToken: SecretValue?,
        coordinator: HomeCoordinator
    ) {
        self.subjectId = subjectId
        self.yearSemester = yearSemester
        self.coordinator = coordinator
        self.holder = WebViewHolder()
        let url = ProductWebUrls.shared.task(path: path)
        if url.contains("OnlineCntntsStdPage.do") {
            coordinator.presentUnavailable()
        }
        holder.load(url)
    }

    deinit { holder.dispose() }

    func handleNavigation(_ state: WebNavigationState) {
        guard case let .ready(url) = state.loadPhase else { return }
        holder.evaluate(KlasWebAutomationScripts.shared.styleContentPage(hideSubjectHeader: true))
        if !didInject {
            holder.evaluate(IosWebCallbacks.shared.setLocalStorage(key: "selectYearhakgi", value: yearSemester))
            holder.evaluate(IosWebCallbacks.shared.setLocalStorage(key: "selectSubj", value: subjectId))
            holder.reload()
            didInject = true
            return
        }
        if url.contains("OnlineCntntsStdPage.do") {
            coordinator?.presentUnavailable()
        }
    }
}

struct TaskView: View {
    @StateObject private var model: TaskScreenModel
    @Environment(\.dismiss) private var dismiss

    init(
        path: String,
        subjectId: String,
        yearSemester: String,
        sessionToken: SecretValue?,
        coordinator: HomeCoordinator
    ) {
        _model = StateObject(
            wrappedValue: TaskScreenModel(
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
        .onChange(of: model.holder.navigationState) { state in
            model.handleNavigation(state)
        }
        .accessibilityIdentifier("task_view")
    }
}

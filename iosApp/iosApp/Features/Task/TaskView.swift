import Shared
import SwiftUI

@MainActor
final class TaskScreenModel: ObservableObject {
    let holder: WebViewHolder
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
            coordinator.openOnlineLectureList(subjectId: subjectId, yearSemester: yearSemester, replacingCurrent: true)
            return
        }
        holder.addDocumentStartScript(LegacyWebScripts.shared.selectAcademicContext(yearSemester: yearSemester, subjectId: subjectId))
        holder.load(url)
    }

    deinit { holder.dispose() }

    func handleNavigation(_ state: WebNavigationState) {
        guard case let .ready(url) = state.loadPhase else { return }
        holder.evaluate(KlasWebAutomationScripts.shared.styleContentPage(hideSubjectHeader: true))
        if url.contains("OnlineCntntsStdPage.do") {
            coordinator?.openOnlineLectureList(
                subjectId: subjectId,
                yearSemester: yearSemester,
                replacingCurrent: true
            )
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
        PushedWebStack(holder: model.holder) {
            if model.holder.goBack() { return }
            dismiss()
        }
        .onChange(of: model.holder.navigationState) { state in
            model.handleNavigation(state)
        }
        .accessibilityIdentifier("task_view")
    }
}

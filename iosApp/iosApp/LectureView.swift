import Shared
import SwiftUI

@MainActor
final class LectureScreenModel: ObservableObject {
    let uiHolder: WebViewHolder
    let klasHolder: WebViewHolder
    @Published var showingKlas = false
    @Published var isLoading = true

    let subjectId: String
    let subjectName: String
    let yearSemester: String
    let sessionToken: SecretValue?
    weak var coordinator: HomeCoordinator?

    private var host: LectureHostAdapter
    private var boardNoticePath = ""
    private var boardPdsPath = ""

    init(
        subjectId: String,
        subjectName: String,
        yearSemester: String,
        sessionToken: SecretValue?,
        coordinator: HomeCoordinator
    ) {
        self.subjectId = subjectId
        self.subjectName = subjectName
        self.yearSemester = yearSemester
        self.sessionToken = sessionToken
        self.coordinator = coordinator
        let host = LectureHostAdapter()
        self.host = host
        self.uiHolder = WebViewHolder.withLegacyBridge(
            surface: .lecture,
            handler: IosLectureLegacyBridgeCommandHandler(host: host)
        )
        self.klasHolder = WebViewHolder.withLegacyBridge(
            surface: .lecture,
            handler: IosLectureLegacyBridgeCommandHandler(host: host)
        )
        host.model = self
        uiHolder.load(KlasUrls.shared.LECTURE_HOME)
        klasHolder.load(KlasUrls.shared.KLAS_FRAME)
    }

    deinit {
        uiHolder.dispose()
        klasHolder.dispose()
    }

    func completePageLoad() {
        guard let token = sessionToken else { return }
        uiHolder.evaluate(
            IosWebCallbacks.shared.receivedData(
                token: token.reveal(),
                subjectId: subjectId,
                yearHakgi: yearSemester
            )
        )
        isLoading = false
    }

    func storeBoardPaths(notice: String, pds: String) {
        boardNoticePath = notice
        boardPdsPath = pds
    }

    func openBoardList(type: String, title: String) {
        guard let path = boardPath(for: type) else {
            coordinator?.showToast("아직 정보를 불러오지 못했어요. 몇 초 후에 다시 시도해주세요.")
            return
        }
        coordinator?.openBoardList(
            path: path,
            title: title,
            subjectId: subjectId,
            yearSemester: yearSemester
        )
    }

    func openBoardView(type: String, boardNo: String, masterNo: String) {
        guard let path = boardPath(for: type) else {
            coordinator?.showToast("아직 정보를 불러오지 못했어요. 몇 초 후에 다시 시도해주세요.")
            return
        }
        coordinator?.openBoardView(
            path: path,
            boardNumber: boardNo,
            masterNumber: masterNo,
            subjectId: subjectId,
            yearSemester: yearSemester
        )
    }

    func evaluteKLASScript(_ script: String) {
        klasHolder.evaluateRaw(script)
        showingKlas = true
    }

    func handleKlasNavigation(_ state: WebNavigationState) {
        guard case let .ready(url) = state.loadPhase else { return }
        klasHolder.evaluate(KlasWebAutomationScripts.shared.styleContentPage(hideSubjectHeader: true))
        if url.contains("OnlineCntntsStdPage.do") {
            klasHolder.evaluate(IosWebCallbacks.shared.setLocalStorage(key: "selectYearhakgi", value: yearSemester))
            klasHolder.evaluate(IosWebCallbacks.shared.setLocalStorage(key: "selectSubj", value: subjectId))
            coordinator?.presentUnavailable()
        }
        if url.contains("Frame.do") {
            klasHolder.evaluate(
                KlasWebAutomationScripts.shared.openLecture(
                    yearSemester: yearSemester,
                    subjectId: subjectId
                )
            )
            showingKlas = false
            isLoading = false
        }
        if url.contains("LctrumHomeStdPage.do") {
            klasHolder.evaluate(KlasWebAutomationScripts.shared.collectLectureBoardPaths())
            showingKlas = false
        }
    }

    func handleBack(dismiss: () -> Void) {
        if showingKlas {
            if !klasHolder.goBack() {
                showingKlas = false
            }
            return
        }
        if uiHolder.goBack() { return }
        dismiss()
    }

    private func boardPath(for type: String) -> String? {
        if boardNoticePath.isEmpty || boardPdsPath.isEmpty { return nil }
        switch type {
        case "notice": return boardNoticePath
        case "pds": return boardPdsPath
        default: return ""
        }
    }
}

final class LectureHostAdapter: LectureBridgeHost {
    weak var model: LectureScreenModel?

    func completePageLoad() {
        Task { @MainActor in model?.completePageLoad() }
    }

    func openPage(url: String) {
        Task { @MainActor in model?.coordinator?.openWeb(url: url) }
    }

    func getBoardPath(noticePath: String, pdsPath: String) {
        Task { @MainActor in model?.storeBoardPaths(notice: noticePath, pds: pdsPath) }
    }

    func openBoardList(type: String, title: String) {
        Task { @MainActor in model?.openBoardList(type: type, title: title) }
    }

    func openBoardView(type: String, boardNo: String, masterNo: String) {
        Task { @MainActor in model?.openBoardView(type: type, boardNo: boardNo, masterNo: masterNo) }
    }

    func openExternalLink(url: String) {
        Task { @MainActor in model?.coordinator?.openExternal(url: url) }
    }

    func evaluteKLASScript(script: String) {
        Task { @MainActor in model?.evaluteKLASScript(script) }
    }

    func openOnlineLecture() {
        Task { @MainActor in model?.coordinator?.presentUnavailable() }
    }

    func openLecturePlan() {
        Task { @MainActor in
            guard let model else { return }
            model.coordinator?.openLecturePlan(subjectId: model.subjectId)
        }
    }

    func openQRScan() {
        Task { @MainActor in model?.coordinator?.presentUnavailable() }
    }
}

struct LectureView: View {
    @StateObject private var model: LectureScreenModel
    @Environment(\.dismiss) private var dismiss

    init(
        subjectId: String,
        subjectName: String,
        yearSemester: String,
        sessionToken: SecretValue?,
        coordinator: HomeCoordinator
    ) {
        _model = StateObject(
            wrappedValue: LectureScreenModel(
                subjectId: subjectId,
                subjectName: subjectName,
                yearSemester: yearSemester,
                sessionToken: sessionToken,
                coordinator: coordinator
            )
        )
    }

    var body: some View {
        ZStack {
            WebViewContainer(webView: model.uiHolder.webView)
            WebViewContainer(webView: model.klasHolder.webView)
                .opacity(model.showingKlas ? 1 : 0)
                .allowsHitTesting(model.showingKlas)
            if model.isLoading {
                KlasLoadingView(message: "불러오는 중")
            }
        }
        .ignoresSafeArea(edges: .bottom)
        .navigationTitle(model.subjectName)
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden(true)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Button {
                    model.handleBack(dismiss: { dismiss() })
                } label: {
                    Image(systemName: "chevron.left")
                }
            }
        }
        .webJavaScriptAlert(model.uiHolder)
        .webJavaScriptAlert(model.klasHolder)
        .onChange(of: model.klasHolder.navigationState) { state in
            model.handleKlasNavigation(state)
        }
        .accessibilityIdentifier("lecture_view")
    }
}

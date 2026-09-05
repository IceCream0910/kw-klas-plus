import Shared
import SwiftUI

enum LectureBootstrap: Equatable {
    case idle
    case opening
    case finished
}

struct LectureBoardPaths {
    private var noticePath = ""
    private var pdsPath = ""

    mutating func update(notice: String, pds: String) {
        if !notice.isEmpty { noticePath = notice }
        if !pds.isEmpty { pdsPath = pds }
    }

    func isSupported(type: String) -> Bool {
        type == "notice" || type == "pds"
    }

    func path(for type: String) -> String? {
        let path: String
        switch type {
        case "notice": path = noticePath
        case "pds": path = pdsPath
        default: return nil
        }
        return path.isEmpty ? nil : path
    }
}

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
    private var boardPaths = LectureBoardPaths()
    private(set) var bootstrap = LectureBootstrap.idle
    private var pendingBoardNavigation: PendingBoardNavigation?
    private var boardPathTimeoutTask: Task<Void, Never>?
    private var openLectureRetryTask: Task<Void, Never>?
    private var openLectureWindowTask: Task<Void, Never>?

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
        klasHolder.onWebContentProcessDidTerminate = { [weak self] in
            self?.prepareLectureBootstrapAfterWebContentTermination()
        }
        uiHolder.load(KlasUrls.shared.LECTURE_HOME)
        klasHolder.load(KlasUrls.shared.KLAS_FRAME)
    }

    deinit {
        boardPathTimeoutTask?.cancel()
        openLectureRetryTask?.cancel()
        openLectureWindowTask?.cancel()
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
        boardPaths.update(notice: notice, pds: pds)
        resumePendingBoardNavigation()
    }

    func openBoardList(type: String, title: String) {
        openOrCollectBoardPath(.list(type: type, title: title))
    }

    func openBoardView(type: String, boardNo: String, masterNo: String) {
        openOrCollectBoardPath(.view(type: type, boardNo: boardNo, masterNo: masterNo))
    }

    func evaluteKLASScript(_ script: String) {
        klasHolder.evaluateRaw(script)
        showingKlas = true
    }

    /// Android `LectureActivity`의 `WebViewClient.onPageFinished` 패리티.
    /// Android는 페이지 로드 완료 콜백에서 URL별로 분기하지만, iOS는 `WebNavigationState`의
    /// `.ready(url)` 이벤트로 같은 분기를 수행한다.
    func handleKlasNavigation(_ state: WebNavigationState) {
        guard case let .ready(url) = state.loadPhase else { return }
        klasHolder.evaluate(KlasWebAutomationScripts.shared.styleContentPage(hideSubjectHeader: true))
        if url.contains("OnlineCntntsStdPage.do") {
            klasHolder.evaluate(IosWebCallbacks.shared.setLocalStorage(key: "selectYearhakgi", value: yearSemester))
            klasHolder.evaluate(IosWebCallbacks.shared.setLocalStorage(key: "selectSubj", value: subjectId))
            klasHolder.load(KlasUrls.shared.KLAS_LECTURE_HOME)
            coordinator?.openOnlineLectureList(subjectId: subjectId, yearSemester: yearSemester)
        }
        if url.contains("Frame.do") {
            openLectureIfNeeded()
            if showingKlas { showingKlas = false }
            if isLoading { isLoading = false }
        }
        if url.contains("LctrumHomeStdPage.do") {
            finishOpeningLecture(success: true)
            klasHolder.evaluate(KlasWebAutomationScripts.shared.collectLectureBoardPaths(maxRetries: 20, intervalMs: 250))
            if showingKlas { showingKlas = false }
        }
    }

    /// Android는 `Frame.do` 로드 시 `KlasWebAutomationScripts.openLecture(...)`를 즉시 한 번만 호출한다.
    /// WKWebView `didFinish`는 `goLctrum` 내부 상태가 덜 준비된 시점에 올 수 있고, 그때 호출하면
    /// KLAS가 비동기로 `오류가 발생하였습니다.` alert를 띄운다. JS에서 `window.alert`를 덮어쓰면
    /// 그 비동기 alert를 놓치므로, 함수가 생길 때까지만 기다린 뒤 네이티브에서 부트스트랩 오류를 삼킨다.
    /// 강의 홈으로 진입하거나 대기 시간이 끝나면 억제를 해제한다. `bootstrap == .finished`면 주입은 1회다.
    /// 타임아웃은 `openLectureWhenReady`가 이미 `goLctrum`을 호출한 뒤에도 두 번째 호출을 보내지 않는다.
    private func openLectureIfNeeded() {
        guard bootstrap == .idle else { return }
        bootstrap = .opening
        klasHolder.suppressJavaScriptAlertContaining = Self.bootstrapLectureErrorMarker
        klasHolder.onSuppressedJavaScriptAlert = { [weak self] in
            Task { @MainActor in self?.scheduleOpenLectureRetry() }
        }
        klasHolder.evaluate(
            KlasWebAutomationScripts.shared.openLectureWhenReady(
                yearSemester: yearSemester,
                subjectId: subjectId,
                maxRetries: 20,
                intervalMs: 250,
            )
        )
        openLectureWindowTask?.cancel()
        openLectureWindowTask = Task { @MainActor [weak self] in
            try? await Task.sleep(nanoseconds: 5_000_000_000)
            guard let self, !Task.isCancelled else { return }
            self.handleOpenLectureWindowExpired()
        }
    }

    func handleOpenLectureWindowExpired() {
        guard bootstrap == .opening else { return }
        finishOpeningLecture(success: false)
    }

    func prepareLectureBootstrapAfterWebContentTermination() {
        guard bootstrap == .opening else { return }
        clearOpeningWork(next: .idle)
    }

    private func scheduleOpenLectureRetry() {
        openLectureRetryTask?.cancel()
        openLectureRetryTask = Task { @MainActor [weak self] in
            try? await Task.sleep(nanoseconds: 250_000_000)
            guard let self, !Task.isCancelled, self.bootstrap == .opening else { return }
            self.klasHolder.evaluate(
                KlasWebAutomationScripts.shared.openLecture(
                    yearSemester: self.yearSemester,
                    subjectId: self.subjectId
                )
            )
        }
    }

    private func finishOpeningLecture(success: Bool) {
        clearOpeningWork(next: bootstrap == .opening ? .finished : bootstrap)
        if success, let message = klasHolder.javaScriptAlertMessage, Self.isBootstrapLectureError(message) {
            klasHolder.confirmJavaScriptAlert()
        }
    }

    private func clearOpeningWork(next: LectureBootstrap) {
        openLectureRetryTask?.cancel()
        openLectureWindowTask?.cancel()
        klasHolder.suppressJavaScriptAlertContaining = nil
        klasHolder.onSuppressedJavaScriptAlert = nil
        bootstrap = next
    }

    nonisolated static let bootstrapLectureErrorMarker = "오류가 발생"

    nonisolated static func isBootstrapLectureError(_ message: String) -> Bool {
        message.contains(bootstrapLectureErrorMarker)
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

    private func openOrCollectBoardPath(_ navigation: PendingBoardNavigation) {
        guard boardPaths.isSupported(type: navigation.type) else {
            coordinator?.showToast("지원하지 않는 게시판입니다.")
            return
        }
        if let path = boardPaths.path(for: navigation.type) {
            openBoard(navigation, path: path)
            return
        }

        let wasWaiting = pendingBoardNavigation != nil
        pendingBoardNavigation = navigation
        boardPathTimeoutTask?.cancel()
        boardPathTimeoutTask = Task { @MainActor [weak self] in
            try? await Task.sleep(nanoseconds: 6_000_000_000)
            guard !Task.isCancelled, let self, self.pendingBoardNavigation != nil else { return }
            self.pendingBoardNavigation = nil
            self.coordinator?.showToast("게시판 정보를 불러오지 못했어요. 강의 화면을 새로고침한 뒤 다시 시도해주세요.")
        }
        klasHolder.evaluate(KlasWebAutomationScripts.shared.collectLectureBoardPaths(maxRetries: 20, intervalMs: 250))
        if !wasWaiting {
            coordinator?.showToast("게시판 정보를 불러오는 중이에요.")
        }
    }

    private func resumePendingBoardNavigation() {
        guard let navigation = pendingBoardNavigation,
              let path = boardPaths.path(for: navigation.type) else { return }
        pendingBoardNavigation = nil
        boardPathTimeoutTask?.cancel()
        openBoard(navigation, path: path)
    }

    private func openBoard(_ navigation: PendingBoardNavigation, path: String) {
        switch navigation {
        case let .list(_, title):
            coordinator?.openBoardList(
                path: path,
                title: title,
                subjectId: subjectId,
                yearSemester: yearSemester
            )
        case let .view(_, boardNo, masterNo):
            coordinator?.openBoardView(
                path: path,
                boardNumber: boardNo,
                masterNumber: masterNo,
                subjectId: subjectId,
                yearSemester: yearSemester
            )
        }
    }

    private enum PendingBoardNavigation {
        case list(type: String, title: String)
        case view(type: String, boardNo: String, masterNo: String)

        var type: String {
            switch self {
            case let .list(type, _), let .view(type, _, _): return type
            }
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
        Task { @MainActor in
            guard let model else { return }
            model.coordinator?.openOnlineLectureList(subjectId: model.subjectId, yearSemester: model.yearSemester)
        }
    }

    func openLecturePlan() {
        Task { @MainActor in
            guard let model else { return }
            model.coordinator?.openLecturePlan(subjectId: model.subjectId)
        }
    }

    func openQRScan() {
        Task { @MainActor in
            guard let model else { return }
            model.coordinator?.startQrCheckIn(
                subjectId: model.subjectId,
                subjectName: model.subjectName,
                yearHakgi: model.yearSemester,
                requireParsedTerm: true
            )
        }
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
                .webSurfaceLayout()
                .accessibilityHidden(hidesWebForOverlay || model.showingKlas)
            WebViewContainer(webView: model.klasHolder.webView)
                .webSurfaceLayout()
                .opacity(model.showingKlas ? 1 : 0)
                .allowsHitTesting(model.showingKlas)
                .accessibilityHidden(hidesWebForOverlay || !model.showingKlas)
            if model.isLoading {
                KlasLoadingView(message: "불러오는 중")
            }
        }
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
            ToolbarItem(placement: .topBarTrailing) {
                if model.uiHolder.shareableFileURL != nil || model.klasHolder.shareableFileURL != nil {
                    Button {
                        if model.klasHolder.shareableFileURL != nil {
                            model.klasHolder.shareCurrentFile()
                        } else {
                            model.uiHolder.shareCurrentFile()
                        }
                    } label: {
                        Image(systemName: "square.and.arrow.up")
                    }
                    .accessibilityIdentifier("pdf_share_button")
                    .accessibilityLabel("공유")
                }
            }
        }
        .webJavaScriptAlert(model.uiHolder, model.klasHolder, secondaryEnabled: model.showingKlas)
        .webDownloadOverlay(model.uiHolder)
        .webDownloadOverlay(model.klasHolder)
        .onReceive(model.klasHolder.$navigationState) { state in
            model.handleKlasNavigation(state)
        }
        .accessibilityIdentifier("lecture_view")
    }

    private var hidesWebForOverlay: Bool {
        model.isLoading
            || model.uiHolder.javaScriptAlertMessage != nil
            || model.klasHolder.javaScriptAlertMessage != nil
            || model.uiHolder.downloadProgress != nil
            || model.klasHolder.downloadProgress != nil
    }
}

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
    private var didOpenLecture = false
    private var isOpeningLecture = false
    private var boardPathCollectTask: Task<Void, Never>?
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
        uiHolder.load(KlasUrls.shared.LECTURE_HOME)
        klasHolder.load(KlasUrls.shared.KLAS_FRAME)
    }

    deinit {
        boardPathCollectTask?.cancel()
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

    /// Android `LectureActivity`의 `WebViewClient.onPageFinished` 패리티.
    /// Android는 페이지 로드 완료 콜백에서 URL별로 분기하지만, iOS는 `WebNavigationState`의
    /// `.ready(url)` 이벤트로 같은 분기를 수행한다.
    func handleKlasNavigation(_ state: WebNavigationState) {
        guard case let .ready(url) = state.loadPhase else { return }
        klasHolder.evaluate(KlasWebAutomationScripts.shared.styleContentPage(hideSubjectHeader: true))
        if url.contains("OnlineCntntsStdPage.do") {
            klasHolder.evaluate(IosWebCallbacks.shared.setLocalStorage(key: "selectYearhakgi", value: yearSemester))
            klasHolder.evaluate(IosWebCallbacks.shared.setLocalStorage(key: "selectSubj", value: subjectId))
            coordinator?.presentUnavailable()
        }
        if url.contains("Frame.do") {
            openLectureIfNeeded()
            if showingKlas { showingKlas = false }
            if isLoading { isLoading = false }
        }
        if url.contains("LctrumHomeStdPage.do") {
            finishOpeningLecture(success: true)
            collectBoardPathsUntilReady()
            if showingKlas { showingKlas = false }
        }
    }

    /// Android는 `Frame.do` 로드 시 `KlasWebAutomationScripts.openLecture(...)`를 즉시 한 번만 호출한다.
    /// WKWebView `didFinish`는 `goLctrum` 내부 상태가 덜 준비된 시점에 올 수 있고, 그때 호출하면
    /// KLAS가 비동기로 `오류가 발생하였습니다.` alert를 띄운다. JS에서 `window.alert`를 덮어쓰면
    /// 그 비동기 alert를 놓치므로, 함수가 생길 때까지만 기다린 뒤 네이티브에서 부트스트랩 오류를 삼킨다.
    /// 강의 홈으로 진입하거나 대기 시간이 끝나면 억제를 해제한다. `didOpenLecture`로 주입은 1회다.
    private func openLectureIfNeeded() {
        guard !didOpenLecture else { return }
        didOpenLecture = true
        isOpeningLecture = true
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
            guard let self, !Task.isCancelled, self.isOpeningLecture else { return }
            self.finishOpeningLecture(success: false)
            self.klasHolder.evaluate(
                KlasWebAutomationScripts.shared.openLecture(
                    yearSemester: self.yearSemester,
                    subjectId: self.subjectId
                )
            )
        }
    }

    private func scheduleOpenLectureRetry() {
        openLectureRetryTask?.cancel()
        openLectureRetryTask = Task { @MainActor [weak self] in
            try? await Task.sleep(nanoseconds: 250_000_000)
            guard let self, !Task.isCancelled, self.isOpeningLecture else { return }
            self.klasHolder.evaluate(
                KlasWebAutomationScripts.shared.openLecture(
                    yearSemester: self.yearSemester,
                    subjectId: self.subjectId
                )
            )
        }
    }

    private func finishOpeningLecture(success: Bool) {
        isOpeningLecture = false
        openLectureRetryTask?.cancel()
        openLectureWindowTask?.cancel()
        klasHolder.suppressJavaScriptAlertContaining = nil
        klasHolder.onSuppressedJavaScriptAlert = nil
        if success, let message = klasHolder.javaScriptAlertMessage, Self.isBootstrapLectureError(message) {
            klasHolder.confirmJavaScriptAlert()
        }
    }

    nonisolated static let bootstrapLectureErrorMarker = "오류가 발생"

    nonisolated static func isBootstrapLectureError(_ message: String) -> Bool {
        message.contains(bootstrapLectureErrorMarker)
    }

    /// Android는 `LctrumHomeStdPage.do` 로드 시 `collectLectureBoardPaths()`를 한 번만 실행한다.
    /// iOS에서는 강의 홈 DOM(공지/자료실 링크)이 로드 완료 직후 아직 렌더링되지 않았을 수 있어,
    /// 브릿지 콜백(`getBoardPath` → `storeBoardPaths`)으로 경로가 채워질 때까지 네이티브에서 폴링한다.
    /// 최대 10회(약 4초) 시도하며, 경로가 모두 확보되거나 화면 이탈로 Task가 취소되면 중단한다.
    private func collectBoardPathsUntilReady() {
        boardPathCollectTask?.cancel()
        boardPathCollectTask = Task { @MainActor [weak self] in
            guard let self else { return }
            for _ in 0..<10 {
                if Task.isCancelled { return }
                if !self.boardNoticePath.isEmpty && !self.boardPdsPath.isEmpty { return }
                self.klasHolder.evaluate(KlasWebAutomationScripts.shared.collectLectureBoardPaths())
                try? await Task.sleep(nanoseconds: 400_000_000)
            }
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
                .webSurfaceLayout()
                .accessibilityHidden(
                    model.isLoading
                        || model.showingKlas
                        || model.uiHolder.javaScriptAlertMessage != nil
                        || model.klasHolder.javaScriptAlertMessage != nil
                        || model.uiHolder.downloadProgress != nil
                        || model.klasHolder.downloadProgress != nil
                )
            WebViewContainer(webView: model.klasHolder.webView)
                .webSurfaceLayout()
                .opacity(model.showingKlas ? 1 : 0)
                .allowsHitTesting(model.showingKlas)
                .accessibilityHidden(
                    model.isLoading
                        || !model.showingKlas
                        || model.uiHolder.javaScriptAlertMessage != nil
                        || model.klasHolder.javaScriptAlertMessage != nil
                        || model.uiHolder.downloadProgress != nil
                        || model.klasHolder.downloadProgress != nil
                )
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
        .webJavaScriptAlert(model.uiHolder, model.klasHolder)
        .webDownloadOverlay(model.uiHolder, model.klasHolder)
        .onReceive(model.klasHolder.$navigationState) { state in
            model.handleKlasNavigation(state)
        }
        .accessibilityIdentifier("lecture_view")
    }
}

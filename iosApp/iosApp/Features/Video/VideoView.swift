import Shared
import SwiftUI

@MainActor
final class VideoScreenModel: ObservableObject {
    let listHolder: WebViewHolder
    let klasHolder: WebViewHolder
    let videoHolder: WebViewHolder
    let subjectId: String
    let yearSemester: String
    let sessionToken: SecretValue?
    let coordinator: HomeCoordinator

    @Published var uiState = VideoPlayerUiState()
    @Published var isPlayerVisible = false
    @Published var showingKlas = false
    @Published var isInPictureInPicture = false
    @Published var isPictureInPictureSupported = true
    @Published var showSpeedSheet = false
    @Published var showCloseConfirm = false
    @Published var showLectureChangeConfirm = false
    private var pendingLectureChangeJson: String?

    private(set) var lastVideoScriptSource: String?
    private(set) var lastKlasScriptSource: String?

    private var host: VideoHostAdapter
    private let codec = PlayerBridgeCodec.companion.create()
    private let originPolicy = KlasContentOriginPolicy()
    private let haptics = IosHaptics()
    private var didInjectKlasLocalStorage = false
    private var isKlasLoaded = false
    private var lastPlaytime: Float = 0
    private var duration: Float = 0
    private var isFullscreen = false
    private var titleFetchTask: Task<Void, Never>?
    private(set) var didStart = false
    private var lastOnlineContentRequest: PlayerWebScripts.OnlineContentRequest?
    private var pendingOnlineContentScript: WebScript?
    private var isKlasOnViewerPage = false
    var onDismissRequested: (() -> Void)?

    static let speedOptions: [Double] = [0.25, 0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0]

    init(
        subjectId: String,
        yearSemester: String,
        sessionToken: SecretValue?,
        coordinator: HomeCoordinator
    ) {
        self.subjectId = subjectId
        self.yearSemester = yearSemester
        self.sessionToken = sessionToken
        self.coordinator = coordinator
        PiPRestoreTracker.shared.installIfNeeded()
        let host = VideoHostAdapter()
        self.host = host
        self.listHolder = WebViewHolder.withLegacyBridge(
            surface: .video,
            handler: IosVideoLegacyBridgeCommandHandler(host: host)
        )
        self.klasHolder = WebViewHolder.withLegacyBridge(
            surface: .video,
            handler: IosVideoLegacyBridgeCommandHandler(host: host)
        )
        self.videoHolder = WebViewHolder.withLegacyBridge(
            surface: .video,
            handler: IosVideoLegacyBridgeCommandHandler(host: host)
        )
        host.model = self
        listHolder.autoDismissJavaScriptAlerts = true
        listHolder.onJavaScriptAlertReceived = { [weak self] message in
            guard let self, !message.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
            self.coordinator.showToast(message)
        }
        klasHolder.autoDismissJavaScriptAlerts = true
        klasHolder.onJavaScriptAlertReceived = { [weak self] message in
            guard let self, !message.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
            self.coordinator.showToast(message)

            // KLAS 본인인증 완료 후 자동 전환
            // Android는 알림창(AlertDialog)의 '확인'을 누르면 KLAS 웹페이지가 알아서 다음 영상 화면으로 넘어갑니다.
            // 반면 iOS는 알림창으로 멈추지 않고 백그라운드에서 자동 처리(토스트 안내)하므로,
            // "인증 완료" 메시지가 오면 앱이 직접 영상 화면 여는 스크립트(openOnlineContentViewer)를 실행해 주어야 합니다.
            if (message.contains("인증") && (message.contains("되었습니다") || message.contains("완료"))),
               let lastReq = self.lastOnlineContentRequest {
                let viewerScript = PlayerWebScripts.shared.openOnlineContentViewer(request: lastReq)
                self.evaluateKlas(viewerScript)
            }
        }
        videoHolder.autoDismissJavaScriptAlerts = true
        videoHolder.onJavaScriptAlertReceived = { [weak self] message in
            guard let self, !message.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
            self.coordinator.showToast(message)
        }
        klasHolder.addDocumentStartScript(
            IosWebCallbacks.shared.setLocalStorage(key: "selectYearhakgi", value: yearSemester)
        )
        klasHolder.addDocumentStartScript(
            IosWebCallbacks.shared.setLocalStorage(key: "selectSubj", value: subjectId)
        )
        klasHolder.addDocumentStartScript(
            KlasWebAutomationScripts.shared.redirectWindowOpenToSameFrame()
        )
        klasHolder.onNavigationStateChange = { [weak self] state in
            self?.handleKlasNavigation(state)
        }
        videoHolder.onNavigationStateChange = { [weak self] state in
            self?.handleVideoNavigation(state)
        }
    }

    /// 화면이 실제로 나타난 뒤(.onAppear) 강의 목록을 불러옵니다.
    /// 화면에 뜨기 전에 미리 불러오면 자바스크립트가 실행되지 않아 강의 목록이 무한 로딩에 빠집니다.
    func start() {
        guard !didStart else { return }
        didStart = true
        listHolder.load(KlasUrls.shared.ONLINE_LECTURE)
        klasHolder.load(KlasUrls.shared.KLAS_ONLINE_CONTENTS)
    }

    deinit {
        titleFetchTask?.cancel()
        listHolder.dispose()
        klasHolder.dispose()
        IosPlaybackAudioSession.deactivate()
    }

    func completePageLoad() {
        guard let token = sessionToken else { return }
        listHolder.evaluate(
            IosWebCallbacks.shared.receivedData(
                token: token.reveal(),
                subjectId: subjectId,
                yearHakgi: yearSemester
            )
        )
    }

    func openInKLAS() {
        isPlayerVisible = false
        showingKlas = true
    }

    func requestOnlineLecture(json: String) {
        if !isKlasLoaded {
            coordinator.showToast("아직 강의 정보를 불러오는 중이에요. 몇 초 후에 다시 시도해주세요.")
            return
        }
        let decoded = codec.decodeOnlineContent(value: json)
        guard let success = decoded as? OnlineContentDecodeResultSuccess else {
            coordinator.showToast("강의를 불러오는 중 오류가 발생했습니다.")
            return
        }
        let req = success.request

        if isSameLecture(as: req) && (isInPictureInPicture || isPlayerVisible) {
            isPlayerVisible = true
            showingKlas = false
            return
        }

        if isInPictureInPicture {
            pendingLectureChangeJson = json
            showLectureChangeConfirm = true
            return
        }

        loadOnlineLecture(req: req)
    }

    func confirmLectureChange() {
        showLectureChangeConfirm = false
        guard let json = pendingLectureChangeJson else { return }
        pendingLectureChangeJson = nil

        evaluateVideo(PlayerWebScripts.shared.playback(command: .pause))
        videoHolder.evaluateRaw("(function(){var v=document.querySelector('video');if(v&&v.webkitSetPresentationMode){try{v.webkitSetPresentationMode('inline');}catch(e){}}if(document.exitPictureInPicture){try{document.exitPictureInPicture();}catch(e){}}})();")
        videoHolder.load("about:blank")
        isInPictureInPicture = false
        IosPlayerOrientation.lockPortraitOnPhone()

        let decoded = codec.decodeOnlineContent(value: json)
        guard let success = decoded as? OnlineContentDecodeResultSuccess else { return }
        loadOnlineLecture(req: success.request)
    }

    func cancelLectureChange() {
        showLectureChangeConfirm = false
        pendingLectureChangeJson = nil
    }

    private func loadOnlineLecture(req: PlayerWebScripts.OnlineContentRequest) {
        self.lastOnlineContentRequest = req
        let script = PlayerWebScripts.shared.openOnlineContent(request: req)
        uiState = VideoPlayerUiState()
        isPlayerVisible = false
        showingKlas = true

        if isKlasOnViewerPage {
            pendingOnlineContentScript = script
            isKlasLoaded = false
            klasHolder.load(KlasUrls.shared.KLAS_ONLINE_CONTENTS)
        } else {
            evaluateKlas(script)
        }
    }

    func isSameLecture(as req: PlayerWebScripts.OnlineContentRequest) -> Bool {
        guard let last = lastOnlineContentRequest else { return false }
        return last.subjectId == req.subjectId &&
            last.year == req.year &&
            last.semester == req.semester &&
            last.classNumber == req.classNumber &&
            last.module == req.module &&
            last.lesson == req.lesson &&
            last.objectId == req.objectId &&
            last.weeklySequence == req.weeklySequence
    }

    func receivePlayerStates(
        currentTime: String,
        duration: String,
        isMuted: String,
        isPlaying: String,
        isFullscreen: String
    ) {
        let state = codec.playerState(
            currentTime: currentTime,
            duration: duration,
            isMuted: isMuted,
            isPlaying: isPlaying,
            isFullscreen: isFullscreen
        )
        self.duration = state.durationSeconds
        self.isFullscreen = state.isFullscreen
        if !state.isFullscreen {
            hideController()
        }
        uiState.progress = state.progressFraction
        uiState.currentTime = codec.formatTime(seconds: state.currentSeconds)
        uiState.totalTime = codec.formatTime(seconds: state.durationSeconds)
        uiState.isPlaying = state.isPlaying
        uiState.isMuted = state.isMuted
        refreshPictureInPictureMode()
    }

    func receiveInitSpeed(currentSpeed: String) {
        if currentSpeed.isEmpty {
            uiState.speedText = "1.0x"
        } else {
            uiState.speedText = "\(currentSpeed)x"
        }
    }

    func receiveVideoData(progress: String, time: String) {
        guard let parsed = codec.lectureProgress(progressHtml: progress, timeHtml: time) else { return }
        lastPlaytime = Float(parsed.playedSeconds)
        uiState.lectureTime = parsed.displayText
    }

    func receiveVideoURL(_ videoURL: String) {
        guard originPolicy.isTrustedVideoUrl(url: videoURL) else {
            coordinator.showToast("강의 영상 주소를 확인하지 못했습니다.")
            return
        }
        videoHolder.load(videoURL)
        isPlayerVisible = true
        showingKlas = false
        fetchTitle(videoURL)
    }

    func openExternalLink(url: String) {
        coordinator.openExternal(url: url)
    }

    func performHapticFeedback(type: String) {
        _ = haptics.performLegacy(contractName: type)
    }

    func handleKlasNavigation(_ state: WebNavigationState) {
        guard case let .ready(url) = state.loadPhase else { return }
        evaluateKlas(KlasWebAutomationScripts.shared.styleOnlineContentsPage())
        if !didInjectKlasLocalStorage {
            klasHolder.evaluate(IosWebCallbacks.shared.setLocalStorage(key: "selectYearhakgi", value: yearSemester))
            klasHolder.evaluate(IosWebCallbacks.shared.setLocalStorage(key: "selectSubj", value: subjectId))
            klasHolder.reload()
            didInjectKlasLocalStorage = true
            return
        }
        isKlasLoaded = true

        if url.contains("OnlineCntntsStdPage") {
            isKlasOnViewerPage = false
            if let pending = pendingOnlineContentScript {
                pendingOnlineContentScript = nil
                evaluateKlas(pending)
                return
            }
        }

        if !url.contains("OnlineCntntsStdPage") && !url.contains("Certi") {
            isKlasOnViewerPage = true
            evaluateKlas(KlasWebAutomationScripts.shared.styleViewerPage())
            evaluateKlas(KlasWebAutomationScripts.shared.monitorLectureProgress())
            evaluateKlas(KlasWebAutomationScripts.shared.reportViewerVideoUrl())
        } else {
            if !url.contains("Certi") {
                isPlayerVisible = false
            }
        }
    }

    func handleVideoNavigation(_ state: WebNavigationState) {
        guard case let .ready(url) = state.loadPhase else { return }
        guard originPolicy.isTrustedVideoUrl(url: url) else { return }
        IosPlaybackAudioSession.activate()
        evaluateVideo(PlayerWebScripts.shared.monitorState())
        hideController()
        refreshPictureInPictureSupport()
    }

    func seekToProgress(_ progress: Float) {
        let seconds = Double(progress) * Double(duration)
        guard seconds.isFinite, seconds >= 0 else { return }
        evaluateVideo(PlayerWebScripts.shared.seekTo(seconds: seconds))
    }

    func seekToLastPlaytime() {
        guard lastPlaytime.isFinite, lastPlaytime >= 0 else { return }
        evaluateVideo(PlayerWebScripts.shared.seekTo(seconds: Double(lastPlaytime)))
    }

    func playPause() {
        evaluateVideo(
            PlayerWebScripts.shared.playback(
                command: uiState.isPlaying ? .pause : .play
            )
        )
    }

    func move(_ direction: PlayerSeekDirection) {
        evaluateVideo(PlayerWebScripts.shared.move(direction: direction))
    }

    func toggleMute() {
        let nextMuted = !uiState.isMuted
        uiState.isMuted = nextMuted
        evaluateVideo(PlayerWebScripts.shared.mute(muted: nextMuted))
    }

    func toggleFullscreen() {
        if isFullscreen {
            evaluateVideo(PlayerWebScripts.shared.closeFullScreenIfAvailable())
        } else {
            evaluateVideo(PlayerWebScripts.shared.openFullScreenIfAvailable())
            evaluateVideo(PlayerWebScripts.shared.setControllerVisible(visible: true))
        }
    }

    func selectSpeed(_ speed: Double) {
        uiState.speedText = "\(speed)x"
        evaluateVideo(PlayerWebScripts.shared.changePlaybackRate(speed: speed))
        showSpeedSheet = false
    }

    func startPictureInPicture(dismiss: (() -> Void)? = nil) {
        guard isPlayerVisible else { return }
        if isInPictureInPicture {
            return
        }
        if !isPictureInPictureSupported {
            coordinator.showToast("이 기기에서는 PIP를 사용할 수 없습니다.")
            return
        }
        PiPRestoreTracker.shared.reset()
        hideController()
        isInPictureInPicture = true
        isPlayerVisible = false
        evaluateVideo(PlayerWebScripts.shared.enterPictureInPicture())
        dismiss?()
    }

    func handleScenePhase(_ phase: ScenePhase) {
        if phase == .active {
            refreshPictureInPictureMode()
        }
    }

    func handleBack(dismiss: () -> Void) {
        if isInPictureInPicture {
            if isPlayerVisible || showingKlas {
                isPlayerVisible = false
                showingKlas = false
                return
            }
            dismiss()
            return
        }
        if isPlayerVisible || showingKlas {
            resetToLectureList()
            return
        }
        coordinator.clearActiveVideoModelIfIdle()
        dismiss()
    }

    func confirmClose() {
        showCloseConfirm = false
        isInPictureInPicture = false
        hideController()
        IosPlayerOrientation.lockPortraitOnPhone()
        resetToLectureList()
        coordinator.clearActiveVideoModelIfIdle()
    }

    func showPlayerFromPip() {
        guard isInPictureInPicture else { return }
        isPlayerVisible = true
        showingKlas = false
    }

    private func resetToLectureList() {
        evaluateVideo(PlayerWebScripts.shared.playback(command: .pause))
        videoHolder.load("about:blank")
        isPlayerVisible = false
        showingKlas = false
        uiState = VideoPlayerUiState()
        lastPlaytime = 0
        duration = 0
        lastOnlineContentRequest = nil
        pendingOnlineContentScript = nil
        isKlasLoaded = false
        klasHolder.load(KlasUrls.shared.KLAS_ONLINE_CONTENTS)
    }

    func restorePlayerAfterPictureInPicture() {
        guard isInPictureInPicture else { return }
        isInPictureInPicture = false
        isPlayerVisible = true
        hideController()
        IosPlayerOrientation.lockPortraitOnPhone()
        Task { @MainActor in
            if !self.coordinator.isVideoScreenPresented {
                self.coordinator.openVideo(subjectId: self.subjectId, yearSemester: self.yearSemester)
            }
        }
    }

    func handlePictureInPictureClosedBySystem() {
        guard isInPictureInPicture else { return }
        isInPictureInPicture = false
        isPlayerVisible = false
        hideController()
        IosPlayerOrientation.lockPortraitOnPhone()
        resetToLectureList()
        coordinator.clearActiveVideoModelIfIdle()
    }

    func refreshPictureInPictureMode() {
        videoHolder.evaluate(PlayerWebScripts.shared.pictureInPicturePresentationMode()) { [weak self] result in
            Task { @MainActor in
                guard let self else { return }
                let raw = (result as? String) ?? "inline:playing"
                let parts = raw.split(separator: ":")
                let mode = parts.first.map(String.init) ?? "inline"
                let isPaused = parts.count > 1 ? (parts[1] == "paused") : false
                let pip = (mode == "picture-in-picture")
                if pip {
                    let wasInPip = self.isInPictureInPicture
                    let wasPlayerVisible = self.isPlayerVisible
                    self.isInPictureInPicture = true
                    if !wasInPip {
                        self.isPlayerVisible = false
                        if wasPlayerVisible && self.coordinator.isVideoScreenPresented {
                            self.onDismissRequested?()
                        }
                    }
                } else if self.isInPictureInPicture {
                    let isRestoreRequested = PiPRestoreTracker.shared.isRestoreRequested
                    PiPRestoreTracker.shared.reset()

                    let shouldRestore = isRestoreRequested || (mode == "fullscreen")

                    if shouldRestore {
                        self.restorePlayerAfterPictureInPicture()
                    } else {
                        self.handlePictureInPictureClosedBySystem()
                    }
                }
            }
        }
    }

    private func refreshPictureInPictureSupport() {
        videoHolder.evaluate(PlayerWebScripts.shared.isPictureInPictureSupported()) { [weak self] result in
            Task { @MainActor in
                guard let self else { return }
                if let value = result as? String {
                    self.isPictureInPictureSupported = value == "true"
                } else if let value = result as? Bool {
                    self.isPictureInPictureSupported = value
                }
            }
        }
    }

    private func hideController() {
        evaluateVideo(PlayerWebScripts.shared.setControllerVisible(visible: false))
    }

    private func fetchTitle(_ url: String) {
        let repository = coordinator.mediaMetadataRepository
        titleFetchTask?.cancel()
        titleFetchTask = Task { @MainActor [weak self] in
            let result = await withCheckedContinuation { continuation in
                repository.fetchTitle(url: url) { value, _ in
                    continuation.resume(returning: value)
                }
            }
            guard !Task.isCancelled, let self else { return }
            if let success = result as? MediaMetadataResultSuccess {
                self.uiState.lectureName = success.title
            }
        }
    }

    private func evaluateVideo(_ script: WebScript) {
        lastVideoScriptSource = script.reveal()
        videoHolder.evaluate(script)
    }

    private func evaluateKlas(_ script: WebScript) {
        lastKlasScriptSource = script.reveal()
        klasHolder.evaluate(script)
    }
}

final class VideoHostAdapter: VideoBridgeHost {
    weak var model: VideoScreenModel?

    func completePageLoad() {
        Task { @MainActor in model?.completePageLoad() }
    }

    func openExternalLink(url: String) {
        Task { @MainActor in model?.openExternalLink(url: url) }
    }

    func openInKLAS() {
        Task { @MainActor in model?.openInKLAS() }
    }

    func requestOnlineLecture(json: String) {
        Task { @MainActor in model?.requestOnlineLecture(json: json) }
    }

    func receivePlayerStates(
        currentTime: String,
        duration: String,
        isMuted: String,
        isPlaying: String,
        isFullscreen: String
    ) {
        Task { @MainActor in
            model?.receivePlayerStates(
                currentTime: currentTime,
                duration: duration,
                isMuted: isMuted,
                isPlaying: isPlaying,
                isFullscreen: isFullscreen
            )
        }
    }

    func receiveInitSpeed(currentSpeed: String) {
        Task { @MainActor in model?.receiveInitSpeed(currentSpeed: currentSpeed) }
    }

    func receiveVideoData(progress: String, time: String) {
        Task { @MainActor in model?.receiveVideoData(progress: progress, time: time) }
    }

    func receiveVideoURL(videoURL: String) {
        Task { @MainActor in model?.receiveVideoURL(videoURL) }
    }

    func performHapticFeedback(type: String) {
        Task { @MainActor in model?.performHapticFeedback(type: type) }
    }
}

struct VideoView: View {
    @StateObject private var model: VideoScreenModel
    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var scenePhase

    init(
        subjectId: String,
        yearSemester: String,
        sessionToken: SecretValue?,
        coordinator: HomeCoordinator
    ) {
        let videoModel = coordinator.videoModel(
            subjectId: subjectId,
            yearSemester: yearSemester
        )
        _model = StateObject(wrappedValue: videoModel)
    }

    var body: some View {
        ZStack(alignment: .bottom) {
            WebViewContainer(webView: model.klasHolder.webView)
                .webSurfaceLayout()
                .opacity(model.isPlayerVisible ? 0 : 1)
                .allowsHitTesting(showsKlas)
                .accessibilityHidden(!showsKlas)
            WebViewContainer(webView: model.listHolder.webView)
                .webSurfaceLayout()
                .background(Color(.systemBackground))
                .opacity(showsList ? 1 : 0)
                .allowsHitTesting(showsList)
                .accessibilityHidden(!showsList)
            if model.isPlayerVisible {
                VideoPlayerOverlay(
                    state: model.uiState,
                    isPictureInPictureSupported: model.isPictureInPictureSupported && !model.isInPictureInPicture,
                    onSeek: { model.seekToProgress($0) },
                    onPlayPauseClick: { model.playPause() },
                    onBackwardClick: { model.move(.backward) },
                    onForwardClick: { model.move(.forward) },
                    onMuteClick: { model.toggleMute() },
                    onFullscreenClick: { model.toggleFullscreen() },
                    onPictureInPictureClick: { model.startPictureInPicture(dismiss: { dismiss() }) },
                    onSpeedClick: { model.showSpeedSheet = true },
                    onCloseClick: { model.showCloseConfirm = true },
                    onLectureTimeClick: { model.seekToLastPlaytime() }
                ) {
                    WebViewContainer(webView: model.videoHolder.webView)
                        .id(model.videoHolder.creationID)
                }
            } else {
                WebViewContainer(webView: model.videoHolder.webView)
                    .id(model.videoHolder.creationID)
                    .opacity(0)
                    .allowsHitTesting(false)
                    .accessibilityHidden(true)
            }
        }
        .navigationTitle("온라인 강의")
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
        .webJavaScriptAlert(model.listHolder, model.klasHolder, secondaryEnabled: model.showingKlas)
        .webJavaScriptAlert(model.videoHolder, enabled: model.isPlayerVisible)
        .webDownloadOverlay(model.listHolder)
        .webDownloadOverlay(model.klasHolder)
        .onAppear {
            model.videoHolder.webView.alpha = 1
            model.start()
            model.coordinator.isVideoScreenPresented = true
            model.onDismissRequested = {
                dismiss()
            }
        }
        .onDisappear {
            model.videoHolder.webView.alpha = 0
            model.coordinator.isVideoScreenPresented = false
            model.onDismissRequested = nil
        }
        .onChange(of: scenePhase) { phase in
            model.handleScenePhase(phase)
        }
        .sheet(isPresented: $model.showSpeedSheet) {
            SelectionBottomSheet(
                title: "재생 속도",
                options: VideoScreenModel.speedOptions.map { speed in
                    SelectionOptionRow(
                        title: "\(speed)x",
                        isSelected: model.uiState.speedText == "\(speed)x"
                    ) {
                        model.selectSpeed(speed)
                    }
                }
            )
        }
        .confirmationDialog("강의 종료", isPresented: $model.showCloseConfirm, titleVisibility: .visible) {
            Button("확인", role: .destructive) { model.confirmClose() }
            Button("취소", role: .cancel) {}
        } message: {
            Text("정말 강의 수강을 종료할까요?")
        }
        .confirmationDialog("강의 전환", isPresented: $model.showLectureChangeConfirm, titleVisibility: .visible) {
            Button("확인", role: .destructive) { model.confirmLectureChange() }
            Button("취소", role: .cancel) { model.cancelLectureChange() }
        } message: {
            Text("지금 재생 중인 강의를 종료하고 선택한 강의를 재생할까요?")
        }
        .accessibilityIdentifier("video_view")
    }

    private var showsList: Bool {
        !model.isPlayerVisible && !model.showingKlas
    }

    private var showsKlas: Bool {
        !model.isPlayerVisible && model.showingKlas
    }
}


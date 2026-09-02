import Foundation
import Shared
import SwiftUI
import UIKit
import WebKit

enum HomeDestination: Hashable {
    case lecture(subjectId: String, subjectName: String, yearSemester: String)
    case boardList(path: String, title: String, subjectId: String, yearSemester: String)
    case boardView(
        path: String,
        boardNumber: String,
        masterNumber: String,
        subjectId: String,
        yearSemester: String
    )
    case task(path: String, subjectId: String, yearSemester: String)
    case lecturePlan(subjectId: String)
    case video(subjectId: String, yearSemester: String)
    case link(url: String)
    case settings
}

enum HomeBootstrapPhase: Equatable {
    case loading
    case ready
    case emptyTerms
    case sessionExpired
    case failed(String)
}

enum QrAttendancePhase: Equatable {
    case idle
    case preparing
    case authenticating
    case result(title: String, message: String)
}

@MainActor
final class HomeCoordinator: ObservableObject {
    @Published var path = NavigationPath()
    @Published private(set) var bootstrapPhase: HomeBootstrapPhase = .loading
    @Published private(set) var homeHolder: WebViewHolder?
    @Published var isPageLoading = true
    @Published var isWebBottomSheetOpen = false
    @Published var toastMessage: String?
    @Published var showYearHakgiPicker = false
    @Published var yearHakgiPickerIsUpdate = false
    @Published var showOptionsMenu = false
    @Published var showDatePicker = false
    @Published var datePickerDate = Date()
    @Published var datePickerIsStart = true
    @Published var showLogoutConfirm = false
    @Published var theme = "system"
    @Published var qrPhase: QrAttendancePhase = .idle
    @Published var isPresentingQrScanner = false
    var isVideoScreenPresented = false

    let homeRuntime: IosHomeRuntime
    let mediaMetadataRepository: MediaMetadataRepository
    let onLogout: () -> Void

    private(set) var sessionToken: SecretValue?
    private(set) var yearHakgi = ""
    private(set) var yearHakgiList: [String] = []
    private(set) var timetableJson = ""
    private(set) var deadlineJson = ""
    private(set) var currentTab = ""

    private let routeFactory = AppRouteFactory(
        webPolicy: ExternalNavigationPolicy(maximumLength: 2048)
    )
    private let navigator = IosExternalNavigator.companion.system()
    private static let dateTimeFormatter: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "yyyy-MM-dd'T'HH:mm"
        return f
    }()

    private let haptics = IosHaptics()
    private let qrScanner: IosQrScanner?
    private let attendanceRepository: AttendanceRepository
    private var qrScanCompletion: ((QrScanResult) -> Void)?
    private let prepareCheckInOverride: ((QrPreparationRequest, SecretValue) async -> QrPreparationResult)?
    private let checkInOverride: ((QrAttendancePayload, SecretValue, SecretValue) async -> QrCheckInResult)?
    private let qrScanLaunchGuard = QrScanLaunchGuard()
    private var homeHost: HomeBridgeHostAdapter?
    private var toastTask: Task<Void, Never>?
    private var qrTask: Task<Void, Never>?
    private var didStart = false
    private var releaseQrGuardOnDismiss = false
    private weak var settingsWebHolder: WebViewHolder?

    var colorScheme: ColorScheme? {
        switch theme {
        case "dark": return .dark
        case "light": return .light
        default: return nil
        }
    }

    var appVersion: String {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? ""
    }

    init(
        authRuntime: IosAuthRuntime,
        onLogout: @escaping () -> Void,
        qrScanner: IosQrScanner? = nil,
        attendanceRepository: AttendanceRepository? = nil,
        prepareCheckIn: ((QrPreparationRequest, SecretValue) async -> QrPreparationResult)? = nil,
        checkIn: ((QrAttendancePayload, SecretValue, SecretValue) async -> QrCheckInResult)? = nil
    ) {
        let dependencies = authRuntime.dependencies
        self.homeRuntime = IosHomeRuntime.companion.create(dependencies: dependencies)
        self.mediaMetadataRepository = dependencies.mediaMetadataRepository
        self.onLogout = onLogout
        self.theme = homeRuntime.currentTheme()
        self.qrScanner = qrScanner
        self.attendanceRepository = attendanceRepository ?? dependencies.attendanceRepository
        self.prepareCheckInOverride = prepareCheckIn
        self.checkInOverride = checkIn
    }

    func start() {
        guard !didStart else { return }
        didStart = true
        bootstrapPhase = .loading
        homeRuntime.bootstrapHome(userAgent: Self.platformUserAgent()) { [weak self] result in
            Task { @MainActor in
                self?.handleBootstrap(result)
            }
        }
    }

    func switchToTab(_ tab: String) {
        if currentTab == tab && !currentTab.isEmpty { return }
        currentTab = tab
        isPageLoading = true
        let url = ProductWebUrls.shared.homeTab(tab: tab, yearHakgi: yearHakgi)
        homeHolder?.load(url)
        if !yearHakgi.isEmpty {
            homeHolder?.evaluate(IosWebCallbacks.shared.setLocalStorage(key: "currentYearHakgi", value: yearHakgi))
        }
        _ = haptics.performLegacy(contractName: "CLOCK_TICK")
    }

    /// `switchToTab`은 같은 탭이면 return하므로, 학기 변경·로드 실패 재시도는 가드를 비운 뒤 URL을 다시 만든다.
    func reloadCurrentTab() {
        let tab = currentTab.isEmpty ? "feed" : currentTab
        currentTab = ""
        switchToTab(tab)
    }

    func handleHomeNavigation(_ state: WebNavigationState) {
        guard case .failed = state.loadPhase else { return }
        isPageLoading = false
    }

    static func pageLoadFailureMessage(for category: WebNavFailureCategory) -> String {
        switch category {
        case .network:
            return "네트워크 연결을 확인해 주세요."
        case .tls:
            return "보안 연결에 실패했습니다."
        case .http, .cancelled, .unknown:
            return "페이지를 불러오지 못했습니다."
        }
    }

    func injectHomePageLoad() {
        guard let holder = homeHolder, let token = sessionToken else { return }
        currentTab = Self.homeTab(fromUrl: holder.webView.url?.absoluteString ?? "", fallback: currentTab)
        holder.evaluate(IosWebCallbacks.shared.receiveToken(token: token.reveal()))
        injectHomeTabData()
        isPageLoading = false
    }

    func injectHomeTabData() {
        guard let holder = homeHolder else { return }
        holder.evaluate(IosWebCallbacks.shared.setLocalStorage(key: "currentYearHakgi", value: yearHakgi))
        switch currentTab {
        case "feed":
            holder.evaluate(IosWebCallbacks.shared.receiveDeadline(json: deadlineJson))
            holder.evaluate(IosWebCallbacks.shared.receiveTimetable(json: timetableJson))
            if let token = sessionToken {
                holder.evaluate(IosWebCallbacks.shared.setLocalStorage(key: "klasSessionToken", value: token.reveal()))
            }
        case "timetable":
            holder.evaluate(
                IosWebCallbacks.shared.updateYearHakgiButtonText(
                    value: homeRuntime.yearHakgiButtonText(value: yearHakgi)
                )
            )
            if timetableJson.isEmpty {
                showToast("시간표를 불러오는데 실패했습니다.")
            } else {
                holder.evaluate(IosWebCallbacks.shared.receiveTimetable(json: timetableJson))
            }
        default:
            break
        }
    }

    /// Android `HomeActivity.getCurrentTab()` 패리티. 웹 `router.push`는 Native `changeTab`을 부르지 않는다.
    static func homeTab(fromUrl url: String, fallback: String = "") -> String {
        if url.contains("timetable") { return "timetable" }
        if url.contains("calendar") { return "calendar" }
        if url.contains("profile") { return "menu" }
        if url.contains("feed") { return "feed" }
        return fallback
    }

    func openLecture(subjectId: String, subjectName: String) {
        guard let token = sessionToken else { return }
        let resolution = routeFactory.lecture(
            subjectId: subjectId,
            subjectName: subjectName,
            yearSemester: yearHakgi,
            session: token
        )
        if resolution is AppRouteResolutionAccepted {
            path.append(HomeDestination.lecture(
                subjectId: subjectId,
                subjectName: subjectName,
                yearSemester: yearHakgi
            ))
        }
    }

    func openTask(path: String, subjectId: String, yearSemester: String) {
        if path.contains("OnlineCntntsStdPage.do") {
            openOnlineLectureList(subjectId: subjectId, yearSemester: yearSemester)
            return
        }
        guard let token = sessionToken else { return }
        let resolution = routeFactory.task(
            path: path,
            subjectId: subjectId,
            yearSemester: yearSemester,
            session: token
        )
        if resolution is AppRouteResolutionAccepted {
            self.path.append(HomeDestination.task(
                path: path,
                subjectId: subjectId,
                yearSemester: yearSemester
            ))
        }
    }

    private(set) var activeVideoModel: VideoScreenModel?

    func videoModel(subjectId: String, yearSemester: String) -> VideoScreenModel {
        if let existing = activeVideoModel,
           existing.subjectId == subjectId && existing.yearSemester == yearSemester {
            return existing
        }
        let newModel = VideoScreenModel(
            subjectId: subjectId,
            yearSemester: yearSemester,
            sessionToken: sessionToken,
            coordinator: self
        )
        activeVideoModel = newModel
        return newModel
    }

    func clearActiveVideoModelIfIdle() {
        if let active = activeVideoModel, !active.isInPictureInPicture {
            activeVideoModel = nil
        }
    }

    func openVideo(subjectId: String, yearSemester: String, replacingCurrent: Bool = false) {
        guard let token = sessionToken else { return }
        let resolution = routeFactory.video(
            subjectId: subjectId,
            yearSemester: yearSemester,
            session: token
        )
        guard resolution is AppRouteResolutionAccepted else { return }
        if replacingCurrent, path.count > 0 {
            path.removeLast()
        }
        path.append(HomeDestination.video(subjectId: subjectId, yearSemester: yearSemester))
    }

    func openOnlineLectureList(subjectId: String, yearSemester: String, replacingCurrent: Bool = false) {
        if let active = activeVideoModel, !active.isInPictureInPicture {
            activeVideoModel = nil
        }
        openVideo(subjectId: subjectId, yearSemester: yearSemester, replacingCurrent: replacingCurrent)
    }

    func openWeb(url: String) {
        let resolution = routeFactory.web(url: url, session: sessionToken)
        if resolution is AppRouteResolutionAccepted {
            path.append(HomeDestination.link(url: url))
        }
    }

    func openExternal(url: String) {
        _ = navigator.openValidated(rawValue: url)
    }

    func openBoardList(path: String, title: String, subjectId: String, yearSemester: String) {
        guard let token = sessionToken else { return }
        let resolution = routeFactory.boardList(
            path: path,
            title: title,
            subjectId: subjectId,
            yearSemester: yearSemester,
            session: token
        )
        if resolution is AppRouteResolutionAccepted {
            self.path.append(HomeDestination.boardList(
                path: path,
                title: title,
                subjectId: subjectId,
                yearSemester: yearSemester
            ))
        }
    }

    func openBoardView(
        path: String,
        boardNumber: String,
        masterNumber: String,
        subjectId: String,
        yearSemester: String
    ) {
        guard let token = sessionToken else { return }
        let resolution = routeFactory.boardView(
            path: path,
            boardNumber: boardNumber,
            masterNumber: masterNumber,
            subjectId: subjectId,
            yearSemester: yearSemester,
            session: token
        )
        if resolution is AppRouteResolutionAccepted {
            self.path.append(HomeDestination.boardView(
                path: path,
                boardNumber: boardNumber,
                masterNumber: masterNumber,
                subjectId: subjectId,
                yearSemester: yearSemester
            ))
        }
    }

    func openLecturePlan(subjectId: String) {
        guard let token = sessionToken else { return }
        let resolution = routeFactory.lecturePlan(subjectId: subjectId, session: token)
        if resolution is AppRouteResolutionAccepted {
            path.append(HomeDestination.lecturePlan(subjectId: subjectId))
        }
    }

    func openSettings() {
        showOptionsMenu = false
        path.append(HomeDestination.settings)
    }

    func attachSettingsWebView(_ holder: WebViewHolder) {
        settingsWebHolder = holder
    }

    func selectYearHakgi(_ value: String) {
        yearHakgi = value
        homeRuntime.saveYearHakgi(value: value)
        showYearHakgiPicker = false
        reloadHome()
        let script = IosWebCallbacks.shared.receiveYearHakgi(value: value)
        homeHolder?.evaluate(script)
        settingsWebHolder?.evaluate(script)
    }

    func applyTheme(_ type: String) {
        guard ["light", "dark", "system"].contains(type) else { return }
        theme = type
        homeRuntime.saveTheme(value: type)
    }

    func reloadHome() {
        isPageLoading = true
        homeRuntime.refreshHome(yearHakgi: yearHakgi, userAgent: Self.platformUserAgent()) { [weak self] result in
            Task { @MainActor in
                self?.handleRefresh(result)
            }
        }
    }

    func confirmDatePicker() {
        let value = Self.dateTimeFormatter.string(from: datePickerDate)
        homeHolder?.evaluate(IosWebCallbacks.shared.setDateTime(value: value, isStart: datePickerIsStart))
        showDatePicker = false
    }

    func presentUnavailable() {
        showToast("곧 지원 예정입니다.")
    }

    func startQrCheckIn(
        subjectId: String,
        subjectName: String,
        yearHakgi: String,
        requireParsedTerm: Bool
    ) {
        guard qrScanLaunchGuard.tryAcquire() else { return }
        guard let session = sessionToken,
              !subjectId.isEmpty,
              !subjectName.isEmpty else {
            qrScanLaunchGuard.release()
            showToast(
                requireParsedTerm
                    ? "QR출석을 위한 정보를 불러오지 못했어요. 다시 시도해주세요."
                    : "출석 정보를 확인하지 못했습니다."
            )
            return
        }
        guard let term = yearAndSemester(from: yearHakgi, requireParsedTerm: requireParsedTerm) else {
            qrScanLaunchGuard.release()
            showToast("QR출석을 위한 정보를 불러오지 못했어요. 다시 시도해주세요.")
            return
        }
        qrPhase = .preparing
        qrTask = Task { @MainActor in
            var scannerLaunched = false
            defer {
                if !scannerLaunched {
                    if qrPhase == .preparing { qrPhase = .idle }
                    qrScanLaunchGuard.release()
                }
            }
            let request = QrPreparationRequest(
                year: term.year,
                semester: term.semester,
                subjectId: subjectId,
                subjectName: subjectName
            )
            let prepared = await prepareAttendance(request, session: session)
            guard !Task.isCancelled else { return }
            if let success = prepared as? QrPreparationResultSuccess {
                qrPhase = .idle
                scannerLaunched = true
                await scanAndCheckIn(payload: success.payload, session: session)
                return
            }
            if prepared is QrPreparationResultUnsupportedSubject {
                showToast("QR출석이 지원되지 않는 강의입니다.")
                return
            }
            if prepared is QrPreparationResultSessionExpired {
                presentQrSessionExpired()
                return
            }
            showToast("출석 정보를 불러오지 못했습니다. 잠시 후 다시 시도해주세요.")
        }
    }

    func dismissQrAlert() {
        qrPhase = .idle
        if releaseQrGuardOnDismiss {
            qrScanLaunchGuard.release()
            releaseQrGuardOnDismiss = false
        }
    }

    var usesOverlayQrScanner: Bool { qrScanner == nil }

    func finishQrScan(_ result: QrScanResult) {
        guard qrScanCompletion != nil || isPresentingQrScanner else { return }
        isPresentingQrScanner = false
        let completion = qrScanCompletion
        qrScanCompletion = nil
        completion?(result)
    }

    var qrAlertTitle: String {
        if case let .result(title, _) = qrPhase { return title }
        return ""
    }

    var qrAlertMessage: String {
        if case let .result(_, message) = qrPhase { return message }
        return ""
    }

    var isQrAlertPresented: Bool {
        if case .result = qrPhase { return true }
        return false
    }

    func dispose() {
        qrTask?.cancel()
        qrTask = nil
        qrScanLaunchGuard.release()
        qrPhase = .idle
        finishQrScan(QrScanResultCancelled())
        let holder = homeHolder
        homeHolder = nil
        holder?.dispose()
        activeVideoModel = nil
    }

    func presentLogoutConfirm() {
        showOptionsMenu = false
        Task { @MainActor in
            showLogoutConfirm = true
        }
    }

    func confirmLogout() {
        homeRuntime.logout { [weak self] in
            Task { @MainActor in
                self?.dispose()
                self?.onLogout()
            }
        }
    }

    func openOfficialAppStore() {
        openAppStore(id: "1510521632")
    }

    func openLibraryAppStore() {
        openAppStore(id: "1192646132")
    }

    private func openAppStore(id: String) {
        if let url = URL(string: "itms-apps://itunes.apple.com/app/id\(id)") {
            UIApplication.shared.open(url)
        }
        showOptionsMenu = false
    }

    func showToast(_ message: String) {
        withAnimation(.easeInOut(duration: 0.2)) {
            toastMessage = message
        }
        ToastBanner.show(message)
        UIAccessibility.post(notification: .announcement, argument: message)
        toastTask?.cancel()
        toastTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: 2_500_000_000)
            withAnimation(.easeInOut(duration: 0.2)) {
                if toastMessage == message {
                    toastMessage = nil
                }
            }
        }
    }

    func performHaptic(_ type: String) {
        _ = haptics.performLegacy(contractName: type)
    }

    func presentYearHakgiPicker(isUpdate: Bool = false) {
        yearHakgiPickerIsUpdate = isUpdate
        showYearHakgiPicker = true
    }

    func openDateTimePicker(currentDateTime: String?, isStart: Bool) {
        datePickerIsStart = isStart
        if let currentDateTime, !currentDateTime.isEmpty {
            if let parsed = Self.dateTimeFormatter.date(from: currentDateTime) {
                datePickerDate = parsed
            }
        } else {
            datePickerDate = Date()
        }
        showDatePicker = true
    }

    func handleBootstrap(_ result: HomeBootstrapResult) {
        if let ready = result as? HomeBootstrapResultReady {
            applyReady(ready)
            attachHomeHolder()
            switchToTab("feed")
            bootstrapPhase = .ready
            if ready.promptYearHakgiChange {
                presentYearHakgiPicker(isUpdate: true)
            }
            return
        }
        if let empty = result as? HomeBootstrapResultEmptyTerms {
            sessionToken = empty.sessionToken
            bootstrapPhase = .emptyTerms
            return
        }
        if result is HomeBootstrapResultSessionExpired {
            bootstrapPhase = .sessionExpired
            return
        }
        if let failure = result as? HomeBootstrapResultFailure {
            bootstrapPhase = .failed(failure.message)
        }
    }

    func handleRefresh(_ result: HomeBootstrapResult) {
        if let ready = result as? HomeBootstrapResultReady {
            applyReady(ready)
            // 초기 bootstrap 실패 후 재시도에서는 아직 Home holder가 없으므로 새로 연결.
            if homeHolder == nil {
                attachHomeHolder()
                currentTab = ""
                switchToTab("feed")
            } else {
                reloadCurrentTab()
            }
            bootstrapPhase = .ready
            return
        }
        if let empty = result as? HomeBootstrapResultEmptyTerms {
            sessionToken = empty.sessionToken
            bootstrapPhase = .emptyTerms
            return
        }
        if result is HomeBootstrapResultSessionExpired {
            bootstrapPhase = .sessionExpired
            return
        }
        isPageLoading = false
        if let failure = result as? HomeBootstrapResultFailure {
            showToast(failure.message)
        }
    }

    private func applyReady(_ ready: HomeBootstrapResultReady) {
        sessionToken = ready.sessionToken
        yearHakgi = ready.yearHakgi
        yearHakgiList = ready.yearHakgiListJoined.split(separator: "&").map(String.init)
        timetableJson = ready.timetableJson
        deadlineJson = ready.deadlineJson
    }

    private func attachHomeHolder() {
        homeHolder?.dispose()
        let adapter = HomeBridgeHostAdapter(coordinator: self)
        homeHost = adapter
        let holder = WebViewHolder.withLegacyBridge(
            surface: .home,
            handler: IosHomeLegacyBridgeCommandHandler(host: adapter)
        )
        homeHolder = holder
    }

    static func platformUserAgent() -> String {
        (WKWebView().value(forKey: "userAgent") as? String)?
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .nilIfEmpty ?? "Mozilla/5.0"
    }

    private func scanAndCheckIn(payload: QrAttendancePayload, session: SecretValue) async {
        let scan = await performScan()
        guard !Task.isCancelled else {
            qrScanLaunchGuard.release()
            return
        }
        if scan is QrScanResultCancelled {
            qrScanLaunchGuard.release()
            return
        }
        if scan is QrScanResultPermissionRequired {
            presentQrResult(
                title: "QR 스캔 실패",
                message: "카메라 권한을 허용해주세요."
            )
            return
        }
        if let failed = scan as? QrScanResultFailed {
            presentQrResult(
                title: "QR 스캔 실패",
                message: scannerFailureMessage(failed.reason)
            )
            return
        }
        guard let success = scan as? QrScanResultSuccess else {
            qrScanLaunchGuard.release()
            return
        }
        qrPhase = .authenticating
        let checked = await submitCheckIn(payload: payload, session: session, scanned: SecretValue.companion.of(value: success.value))
        guard !Task.isCancelled else {
            qrScanLaunchGuard.release()
            qrPhase = .idle
            return
        }
        if checked is QrCheckInResultSuccess {
            _ = haptics.performLegacy(contractName: "CONFIRM")
            presentQrResult(title: "출석 체크 성공", message: "정상적으로 출석 처리 되었습니다.")
            return
        }
        if let rejected = checked as? QrCheckInResultRejected {
            _ = haptics.performLegacy(contractName: "REJECT")
            presentQrResult(
                title: "출석 체크 실패",
                message: rejectedMessages(rejected)
            )
            return
        }
        presentQrResult(title: "오류 발생", message: "출석 체크 중 오류가 발생했습니다. \(checkInErrorMessage(checked))")
    }

    private func performScan() async -> QrScanResult {
        if let qrScanner {
            isPresentingQrScanner = true
            defer { isPresentingQrScanner = false }
            return await qrScanner.scan()
        }
        return await withCheckedContinuation { continuation in
            qrScanCompletion = { continuation.resume(returning: $0) }
            isPresentingQrScanner = true
        }
    }

    private func presentQrResult(title: String, message: String) {
        releaseQrGuardOnDismiss = true
        qrPhase = .result(title: title, message: message)
    }

    private func presentQrSessionExpired() {
        path = NavigationPath()
        qrPhase = .idle
        bootstrapPhase = .sessionExpired
    }

    private func prepareAttendance(
        _ request: QrPreparationRequest,
        session: SecretValue
    ) async -> QrPreparationResult {
        if let prepareCheckInOverride {
            return await prepareCheckInOverride(request, session)
        }
        return await withCheckedContinuation { continuation in
            attendanceRepository.prepareCheckIn(
                session: session,
                userAgent: klasUserAgent(),
                request: request,
                completionHandler: { result, _ in
                    continuation.resume(returning: result ?? QrPreparationResultNetworkFailure.shared)
                }
            )
        }
    }

    private func submitCheckIn(
        payload: QrAttendancePayload,
        session: SecretValue,
        scanned: SecretValue
    ) async -> QrCheckInResult {
        if let checkInOverride {
            return await checkInOverride(payload, session, scanned)
        }
        return await withCheckedContinuation { continuation in
            attendanceRepository.checkIn(
                session: session,
                userAgent: klasUserAgent(),
                payload: payload,
                scannedCode: scanned,
                completionHandler: { result, _ in
                    continuation.resume(returning: result ?? QrCheckInResultNetworkFailure.shared)
                }
            )
        }
    }

    private func klasUserAgent() -> KlasUserAgent {
        KlasUserAgent.companion.fromPlatform(value: Self.platformUserAgent())
    }

    private func yearAndSemester(
        from yearHakgi: String,
        requireParsedTerm: Bool
    ) -> (year: String, semester: String)? {
        if let term = AcademicTermKey.companion.parse(value: yearHakgi) {
            return (term.year, term.semester)
        }
        if requireParsedTerm { return nil }
        let now = Date()
        let calendar = Calendar.current
        let year = String(calendar.component(.year, from: now))
        let month = calendar.component(.month, from: now)
        let semester = month < 8 ? "1" : "2"
        return (year, semester)
    }

    private func scannerFailureMessage(_ reason: String) -> String {
        switch reason {
        case "scanner_camera_permission_required":
            return "카메라 권한을 허용해주세요."
        case "scanner_unavailable":
            return "이 기기에서는 카메라를 사용할 수 없습니다."
        default:
            return "QR 스캔 중 오류가 발생했습니다: \(reason)"
        }
    }

    private func checkInErrorMessage(_ result: QrCheckInResult) -> String {
        if result is QrCheckInResultSessionExpired {
            return "로그인 세션이 만료되었습니다. 앱을 재시작한 후 다시 시도해보세요."
        }
        if result is QrCheckInResultTimeout {
            return "서버 응답 시간이 초과되었습니다."
        }
        if result is QrCheckInResultNetworkFailure {
            return "네트워크 연결을 확인해주세요."
        }
        if let http = result as? QrCheckInResultHttpFailure {
            return "서버 오류: \(http.statusCode)"
        }
        if result is QrCheckInResultEmptyResponse {
            return "응답 내용이 비어있습니다."
        }
        return "서버 응답을 처리하지 못했습니다."
    }

    private func rejectedMessages(_ rejected: QrCheckInResultRejected) -> String {
        let values = rejected.messages as? [String] ?? (rejected.messages as? NSArray as? [String]) ?? []
        return values.joined(separator: " ")
    }
}

private extension String {
    var nilIfEmpty: String? { isEmpty ? nil : self }
}

final class HomeBridgeHostAdapter: HomeBridgeHost {
    weak var coordinator: HomeCoordinator?

    init(coordinator: HomeCoordinator) {
        self.coordinator = coordinator
    }

    func changeTab(tab: String) {
        Task { @MainActor in coordinator?.switchToTab(tab) }
    }

    func evaluate(url: String, yearHakgi: String, subj: String) {
        Task { @MainActor in coordinator?.openTask(path: url, subjectId: subj, yearSemester: yearHakgi) }
    }

    func openPage(url: String) {
        Task { @MainActor in coordinator?.openWeb(url: url) }
    }

    func openExternalPage(url: String) {
        Task { @MainActor in coordinator?.openExternal(url: url) }
    }

    func completePageLoad() {
        Task { @MainActor in coordinator?.injectHomePageLoad() }
    }

    func openLibraryQR() {
        Task { @MainActor in coordinator?.presentUnavailable() }
    }

    func openLibraryQRSettingsModal() {
        Task { @MainActor in coordinator?.presentUnavailable() }
    }

    func openLectureActivity(subj: String, subjName: String) {
        Task { @MainActor in coordinator?.openLecture(subjectId: subj, subjectName: subjName) }
    }

    func qrCheckIn(subjId: String, subjName: String) {
        Task { @MainActor in
            coordinator?.startQrCheckIn(
                subjectId: subjId,
                subjectName: subjName,
                yearHakgi: coordinator?.yearHakgi ?? "",
                requireParsedTerm: false
            )
        }
    }

    func openDateTimePicker(currentDateTime: String?, isStart: Bool) {
        Task { @MainActor in coordinator?.openDateTimePicker(currentDateTime: currentDateTime, isStart: isStart) }
    }

    func openWebViewBottomSheet() {
        Task { @MainActor in coordinator?.isWebBottomSheetOpen = true }
    }

    func closeWebViewBottomSheet() {
        Task { @MainActor in coordinator?.isWebBottomSheetOpen = false }
    }

    func openOptionsMenu() {
        Task { @MainActor in coordinator?.showOptionsMenu = true }
    }

    func openYearHakgiBottomSheet() {
        Task { @MainActor in coordinator?.presentYearHakgiPicker() }
    }

    func reload() {
        Task { @MainActor in coordinator?.reloadHome() }
    }

    func performHapticFeedback(type: String) {
        Task { @MainActor in coordinator?.performHaptic(type) }
    }

    func requestIdCardQRValue() {
        Task { @MainActor in coordinator?.presentUnavailable() }
    }
}

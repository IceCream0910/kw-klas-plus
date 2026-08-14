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

    let homeRuntime: IosHomeRuntime
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
    private let externalPolicy = ExternalNavigationPolicy(maximumLength: 2048)
    private let haptics = IosHaptics()
    private var homeHost: HomeBridgeHostAdapter?
    private var toastTask: Task<Void, Never>?
    private var backPressedAt: Date = .distantPast
    private var didStart = false

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

    init(authRuntime: IosAuthRuntime, onLogout: @escaping () -> Void) {
        self.homeRuntime = IosHomeRuntime.companion.create(dependencies: authRuntime.dependencies)
        self.onLogout = onLogout
        self.theme = homeRuntime.currentTheme()
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

    func openWeb(url: String) {
        let resolution = routeFactory.web(url: url, session: sessionToken)
        if resolution is AppRouteResolutionAccepted {
            path.append(HomeDestination.link(url: url))
        }
    }

    func openExternal(url: String) {
        let resolution = externalPolicy.resolve(rawValue: url)
        if let allowed = resolution as? ExternalNavigationResolutionAllowed {
            openDestination(allowed.destination)
        }
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
        path.append(HomeDestination.settings)
    }

    func selectYearHakgi(_ value: String) {
        yearHakgi = value
        homeRuntime.saveYearHakgi(value: value)
        showYearHakgiPicker = false
        reloadHome()
        homeHolder?.evaluate(IosWebCallbacks.shared.receiveYearHakgi(value: value))
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

    func handleHomeBack() {
        if isWebBottomSheetOpen {
            homeHolder?.evaluate(KlasWebAutomationScripts.shared.closeBottomSheet())
            isWebBottomSheetOpen = false
            return
        }
        let now = Date()
        if now.timeIntervalSince(backPressedAt) < 2 {
            UIApplication.shared.perform(NSSelectorFromString("suspend"))
        } else {
            backPressedAt = now
            showToast("한 번 더 뒤로 가면 앱이 꺼져요.")
        }
    }

    func confirmDatePicker() {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd'T'HH:mm"
        let value = formatter.string(from: datePickerDate)
        homeHolder?.evaluate(IosWebCallbacks.shared.setDateTime(value: value, isStart: datePickerIsStart))
        showDatePicker = false
    }

    func presentUnavailable() {
        showToast("곧 지원 예정입니다.")
    }

    func dispose() {
        homeHolder?.dispose()
        homeHolder = nil
    }

    func confirmLogout() {
        homeRuntime.logout { [weak self] in
            Task { @MainActor in
                self?.dispose()
                self?.onLogout()
            }
        }
    }

    func openExternalAppSearch(_ query: String) {
        let encoded = query.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? query
        if let url = URL(string: "https://apps.apple.com/kr/search?term=\(encoded)") {
            UIApplication.shared.open(url)
        }
        showOptionsMenu = false
    }

    func showToast(_ message: String) {
        toastMessage = message
        toastTask?.cancel()
        toastTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: 2_500_000_000)
            if toastMessage == message {
                toastMessage = nil
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
            let formatter = DateFormatter()
            formatter.locale = Locale(identifier: "en_US_POSIX")
            formatter.dateFormat = "yyyy-MM-dd'T'HH:mm"
            if let parsed = formatter.date(from: currentDateTime) {
                datePickerDate = parsed
            }
        } else {
            datePickerDate = Date()
        }
        showDatePicker = true
    }

    private func handleBootstrap(_ result: HomeBootstrapResult) {
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
        if result is HomeBootstrapResultEmptyTerms {
            bootstrapPhase = .emptyTerms
            sessionToken = nil
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

    private func handleRefresh(_ result: HomeBootstrapResult) {
        if let ready = result as? HomeBootstrapResultReady {
            applyReady(ready)
            homeHolder?.reload()
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

    private func openDestination(_ destination: ExternalDestination) {
        let raw: String
        if let web = destination as? ExternalDestinationWeb {
            raw = web.url
        } else if let email = destination as? ExternalDestinationEmail {
            raw = "mailto:\(email.address)"
        } else if let tel = destination as? ExternalDestinationTelephone {
            raw = "tel:\(tel.number)"
        } else if let platform = destination as? ExternalDestinationPlatformUri {
            raw = platform.uri
        } else {
            return
        }
        guard let url = URL(string: raw) else { return }
        UIApplication.shared.open(url)
    }

    static func platformUserAgent() -> String {
        (WKWebView().value(forKey: "userAgent") as? String)?
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .nilIfEmpty ?? "Mozilla/5.0"
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
        Task { @MainActor in coordinator?.presentUnavailable() }
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

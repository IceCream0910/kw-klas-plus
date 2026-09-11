import Foundation
import Shared
import SwiftUI
import UIKit
import WidgetKit

enum LibraryQrPresentedSheet: Identifiable, Equatable {
    case qr(isWidgetEntry: Bool)
    case settings

    var id: String {
        switch self {
        case .qr(let isWidgetEntry):
            return isWidgetEntry ? "qr-widget" : "qr"
        case .settings:
            return "settings"
        }
    }
}

@MainActor
final class LibraryQrController: ObservableObject {
    static let widgetKind = "LibraryQRWidget"

    @Published var presentedSheet: LibraryQrPresentedSheet?
    @Published var qrState = LibraryQrUiState()
    @Published var settingsState = LibraryQrSettingsUiState(studentNumber: "", password: "", phone: "")
    @Published var addWidgetAlertPresented = false
    @Published var isQrBypassActive = false

    let errorMessage = "중앙도서관 출입증 정보를 가져올 수 없습니다.\n설정에서 입력한 정보가 올바른지 확인한 후 다시 시도해주세요."
    let setupNoticeMessage = "먼저 앱에서 중앙도서관 출입증 설정을 완료해주세요."
    let addWidgetMessage = "홈 화면을 길게 누른 뒤 위젯 추가에서 KLAS+ '중앙도서관 출입증'을 선택해주세요."

    private let service: IosLibraryService
    private let appLock: AppLockController
    private let isSessionAuthenticated: () -> Bool
    private let colorScheme: () -> ColorScheme?
    private let widgetInstalled: (@escaping (Bool) -> Void) -> Void
    private let fetchQrData: (@escaping (LibraryQrResult) -> Void) -> Void
    private(set) var originalBrightness: CGFloat?
    private var resignActiveObserver: NSObjectProtocol?
    private var refreshTask: Task<Void, Never>?
    private var fetchGeneration = 0
    private var isRetry = false
    private var refreshWebAfterSettings = false
    var onWebIdCardRefreshNeeded: (() -> Void)?
    var isTimerRunning: Bool { refreshTask != nil }

    init(
        service: IosLibraryService,
        appLock: AppLockController,
        isSessionAuthenticated: @escaping () -> Bool = { true },
        colorScheme: @escaping () -> ColorScheme? = { nil },
        widgetInstalled: ((@escaping (Bool) -> Void) -> Void)? = nil,
        fetchQrData: ((@escaping (LibraryQrResult) -> Void) -> Void)? = nil
    ) {
        self.service = service
        self.appLock = appLock
        self.isSessionAuthenticated = isSessionAuthenticated
        self.colorScheme = colorScheme
        self.fetchQrData = fetchQrData ?? { [service] completion in
            service.getLibraryQrData(onResult: completion)
        }
        self.widgetInstalled = widgetInstalled ?? { completion in
            WidgetCenter.shared.getCurrentConfigurations { result in
                let installed = (try? result.get().contains { $0.kind == LibraryQrController.widgetKind }) ?? false
                DispatchQueue.main.async { completion(installed) }
            }
        }
        self.resignActiveObserver = NotificationCenter.default.addObserver(
            forName: UIApplication.willResignActiveNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            Task { @MainActor in
                self?.handleAppWillResignActive()
            }
        }
    }

    deinit {
        if let resignActiveObserver {
            NotificationCenter.default.removeObserver(resignActiveObserver)
        }
    }

    func handleAppWillResignActive() {
        guard case .qr = presentedSheet else { return }
        restoreBrightness()
    }

    func handleOpenURL(_ url: URL) -> Bool {
        switch LibraryQrRouter.resolveRoute(
            url: url,
            hasConfiguredCredentials: service.hasConfiguredCredentials()
        ) {
        case .openQrDirectly:
            presentWidgetQr()
            return true
        case .routeToSettings:
            if isSessionAuthenticated() {
                presentWidgetSettings()
            } else {
                presentLoggedOutWidgetNotice()
            }
            return true
        case nil:
            return false
        }
    }

    private func presentLoggedOutWidgetNotice() {
        ToastBanner.show(setupNoticeMessage)
    }

    func presentQrFromApp() {
        if service.hasConfiguredCredentials() {
            qrState.isWidgetEntry = false
            presentQr()
        } else {
            qrState.isWidgetEntry = false
            refreshWebAfterSettings = false
            presentSettings()
        }
    }

    func presentSettingsFromApp() {
        qrState.isWidgetEntry = false
        refreshWebAfterSettings = false
        presentSettings()
    }

    func presentSettingsFromHome() {
        refreshWebAfterSettings = true
        presentSettings()
    }

    func handleScenePhase(_ phase: ScenePhase) {
        if phase == .background {
            dismissQr(restoreLock: true)
        }
    }

    func refreshQr() {
        guard service.hasConfiguredCredentials() else {
            ToastBanner.show("QR 코드를 새로고침할 수 없습니다. 설정을 확인해주세요.")
            return
        }
        refreshTask?.cancel()
        refreshTask = nil
        loadQr(resetRetry: true)
    }

    func saveSettings() {
        guard settingsState.canSave else { return }
        let refreshWeb = refreshWebAfterSettings
        refreshWebAfterSettings = false
        service.saveCredentials(
            studentNumber: settingsState.studentNumber,
            phoneNumber: settingsState.phone,
            password: settingsState.password
        ) { [weak self] in
            ToastBanner.show("저장되었습니다.")
            guard let self else { return }
            self.settingsState.password = ""
            if refreshWeb {
                self.presentedSheet = nil
                self.onWebIdCardRefreshNeeded?()
            } else {
                Task { @MainActor in
                    self.presentQr()
                }
            }
        }
    }

    func onSheetDismissed() {
        guard presentedSheet == nil else { return }
        settingsState.password = ""
        refreshWebAfterSettings = false
        dismissQr(restoreLock: true)
    }

    private func presentWidgetSettings() {
        appLock.presentUnlock { [weak self] success in
            guard success, let self else { return }
            self.qrState.isWidgetEntry = false
            self.refreshWebAfterSettings = false
            self.presentSettings()
            // 바텀시트가 올라오면서 토스트가 가려지는 문제를 방지하기 위해 시트가 뜬 직후 시트 위에 노출
            Task { @MainActor in
                try? await Task.sleep(nanoseconds: 600_000_000)
                ToastBanner.show(self.setupNoticeMessage)
            }
        }
    }

    private func presentWidgetQr() {
        qrState.isWidgetEntry = true
        presentQr()
    }

    private func presentQr() {
        let isWidgetEntry = qrState.isWidgetEntry
        isQrBypassActive = isWidgetEntry
        appLock.isLibraryQrExempt = isWidgetEntry
        presentedSheet = .qr(isWidgetEntry: isWidgetEntry)
        captureBrightness()
        UIScreen.main.brightness = 1.0
        widgetInstalled { [weak self] installed in
            guard let self else { return }
            self.qrState.canAddWidget = !self.qrState.isWidgetEntry && !installed
        }
        loadQr(resetRetry: true)
    }

    private func presentSettings() {
        settingsState = LibraryQrSettingsUiState(
            studentNumber: service.settingsStudentNumber(),
            password: service.settingsPassword(),
            phone: service.settingsPhoneNumber()
        )
        presentedSheet = .settings
    }

    private func dismissQr(restoreLock: Bool) {
        refreshTask?.cancel()
        refreshTask = nil
        fetchGeneration += 1
        restoreBrightness()
        qrState = LibraryQrUiState(isWidgetEntry: qrState.isWidgetEntry)
        isQrBypassActive = false
        appLock.isLibraryQrExempt = false
        if restoreLock {
            appLock.requestUnlockIfNeeded()
        }
        if case .qr = presentedSheet {
            presentedSheet = nil
        }
    }

    private func loadQr(resetRetry: Bool) {
        if resetRetry { isRetry = false }
        fetchGeneration += 1
        let generation = fetchGeneration
        qrState.loading = true
        qrState.isError = false
        qrState.errorMessage = ""
        qrState.image = nil
        fetchQrData { [weak self] result in
            self?.handleQrResult(result, generation: generation)
        }
    }

    private func handleQrResult(_ result: LibraryQrResult, generation: Int) {
        guard generation == fetchGeneration else { return }
        if let success = result as? LibraryQrResultSuccess {
            isRetry = false
            display(success.data)
            return
        }
        retryOrShowError()
    }

    private func display(_ data: LibraryQrData) {
        guard
            let qrValue = string(data, "qr_code"), qrValue.count >= 5,
            let userName = string(data, "user_name"),
            let userCode = string(data, "user_code"),
            let department = string(data, "user_deptName"),
            let patternName = string(data, "user_patName")
        else {
            retryOrShowError()
            return
        }
        let dark = (colorScheme() == .dark)
            || UITraitCollection.current.userInterfaceStyle == .dark
        qrState.name = userName
        qrState.details = "광운대학교 \(userCode.trimmingCharacters(in: .whitespacesAndNewlines))\n\(department) \(patternName)"
        qrState.image = LibraryQrImageRenderer.image(from: qrValue, darkMode: dark)
        qrState.loading = false
        qrState.isError = false
        if qrState.image == nil {
            retryOrShowError()
        } else {
            startTimer()
        }
    }

    private func retryOrShowError() {
        if !isRetry {
            isRetry = true
            service.clearCache { [weak self] in
                self?.loadQr(resetRetry: false)
            }
            return
        }
        refreshTask?.cancel()
        refreshTask = nil
        qrState.loading = false
        qrState.image = nil
        qrState.isError = true
        qrState.errorMessage = errorMessage
    }

    func presentSettingsFromQr() {
        if qrState.isWidgetEntry {
            dismissQr(restoreLock: false)
            appLock.presentUnlock { [weak self] success in
                guard success, let self else { return }
                self.qrState.isWidgetEntry = false
                self.refreshWebAfterSettings = false
                self.presentSettings()
            }
        } else {
            restoreBrightness()
            qrState.isWidgetEntry = false
            refreshWebAfterSettings = false
            presentSettings()
        }
    }

    func presentSettingsFromQrError() {
        presentSettingsFromQr()
    }

    private func startTimer() {
        refreshTask?.cancel()
        qrState.secondsRemaining = 30
        refreshTask = Task { [weak self] in
            for remaining in stride(from: 29, through: 0, by: -1) {
                try? await Task.sleep(nanoseconds: 1_000_000_000)
                guard let self, !Task.isCancelled else { return }
                self.qrState.secondsRemaining = remaining
            }
            guard let self, !Task.isCancelled else { return }
            self.refreshQr()
        }
    }

    private func captureBrightness() {
        if originalBrightness == nil {
            originalBrightness = UIScreen.main.brightness
        }
    }

    private func restoreBrightness() {
        if let originalBrightness {
            UIScreen.main.brightness = originalBrightness
        }
        originalBrightness = nil
    }

    private func string(_ data: LibraryQrData, _ key: String) -> String? {
        let value = data.values[key]
        return value?.isEmpty == false ? value : nil
    }
}

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
    @Published var errorAlertPresented = false
    @Published var addWidgetAlertPresented = false
    @Published var isQrBypassActive = false

    let errorTitle = "오류"
    let errorMessage = "모바일 학생증 정보를 가져올 수 없습니다.\n모바일 학생증 설정에서 입력한 정보가 올바른지 확인한 후 다시 시도해주세요."
    let setupNoticeMessage = "먼저 앱에서 모바일 학생증 설정을 완료해주세요."
    let addWidgetMessage = "홈 화면을 길게 누른 뒤 위젯 추가에서 광운대학교+ 도서관 출입증을 선택해주세요."

    private let service: IosLibraryService
    private let appLock: AppLockController
    private let colorScheme: () -> ColorScheme?
    private let widgetInstalled: (@escaping (Bool) -> Void) -> Void
    private var originalBrightness: CGFloat?
    private var refreshTask: Task<Void, Never>?
    private var fetchGeneration = 0
    private var isRetry = false
    private var refreshWebAfterSettings = false
    private var suppressDismissCleanup = false
    var onWebIdCardRefreshNeeded: (() -> Void)?

    init(
        service: IosLibraryService,
        appLock: AppLockController,
        colorScheme: @escaping () -> ColorScheme? = { nil },
        widgetInstalled: ((@escaping (Bool) -> Void) -> Void)? = nil
    ) {
        self.service = service
        self.appLock = appLock
        self.colorScheme = colorScheme
        self.widgetInstalled = widgetInstalled ?? { completion in
            WidgetCenter.shared.getCurrentConfigurations { result in
                let installed = (try? result.get().contains { $0.kind == LibraryQrController.widgetKind }) ?? false
                DispatchQueue.main.async { completion(installed) }
            }
        }
    }

    func handleOpenURL(_ url: URL) -> Bool {
        switch LibraryQrRouter.resolveRoute(
            url: url,
            hasConfiguredCredentials: service.hasConfiguredCredentials()
        ) {
        case .openQrDirectly:
            presentWidgetQr()
            return true
        case .showUnconfiguredNotice:
            presentUnconfiguredWidgetNotice()
            return true
        case nil:
            return false
        }
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
        loadQr(resetRetry: true)
        startTimer()
    }

    func presentSettingsFromQr() {
        refreshWebAfterSettings = false
        presentSettings()
    }

    func saveSettings() {
        guard settingsState.canSave else { return }
        let refreshWeb = refreshWebAfterSettings
        refreshWebAfterSettings = false
        suppressDismissCleanup = !refreshWeb
        service.saveCredentials(
            studentNumber: settingsState.studentNumber,
            phoneNumber: settingsState.phone,
            password: settingsState.password
        ) { [weak self] in
            ToastBanner.show("저장되었습니다.")
            guard let self else { return }
            self.settingsState.password = ""
            if refreshWeb {
                self.onWebIdCardRefreshNeeded?()
            } else {
                Task { @MainActor in
                    self.presentQr()
                }
            }
        }
        presentedSheet = nil
    }

    func dismissErrorAlert() {
        errorAlertPresented = false
        dismissQr(restoreLock: true)
    }

    func onSheetDismissed() {
        guard presentedSheet == nil else { return }
        settingsState.password = ""
        if suppressDismissCleanup {
            suppressDismissCleanup = false
            return
        }
        refreshWebAfterSettings = false
        dismissQr(restoreLock: true)
    }

    private func presentUnconfiguredWidgetNotice() {
        Task { @MainActor in
            await Task.yield()
            ToastBanner.show(setupNoticeMessage)
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
        startTimer()
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
        if restoreLock {
            isQrBypassActive = false
            appLock.isLibraryQrExempt = false
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
        qrState.image = nil
        service.getLibraryQrData { [weak self] result in
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
        if qrState.image == nil {
            retryOrShowError()
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
        qrState.loading = false
        qrState.image = nil
        errorAlertPresented = true
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

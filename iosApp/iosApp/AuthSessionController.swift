import Foundation
import Network
import Shared
import SwiftUI
import UIKit
import WebKit

enum AuthPhase: Equatable {
    case checkingNetwork
    case bootstrapping
    case needsCredentials
    case authenticating
    case authenticated
    case blocked(AuthBlockReason)
}

enum AuthBlockReason: Equatable {
    case noNetwork
    case securityActionRequired
    case invalidCredentials(String?)
    case loginFailed
}

@MainActor
final class AuthSessionController: ObservableObject {
    @Published private(set) var phase: AuthPhase = .checkingNetwork
    @Published var loginState = LoginUiState(
        onboardingVisible: true,
        studentId: "",
        password: "",
        agreementAccepted: false
    )
    @Published var loadingMessage = "로그인 중"
    @Published var toastMessage: String?
    /// Android `openWebRoute` → LinkViewActivity 패리티 (학번/비번 찾기,최초 등록)
    @Published var presentedLinkURL: URL?

    let authRuntime: IosAuthRuntime
    private(set) var authWebView: WKWebView
    private var webAuthDriver: IosWebAuthDriver?
    private var loadingHintTask: Task<Void, Never>?
    private var toastTask: Task<Void, Never>?
    private var productHolder: WebViewHolder?
    private var activeCredential: StoredCredential?

    init(authRuntime: IosAuthRuntime = IosAuthRuntime.companion.createDefault()) {
        self.authRuntime = authRuntime
        let configuration = WKWebViewConfiguration()
        configuration.websiteDataStore = WebViewHolder.websiteDataStore
        self.authWebView = WKWebView(frame: .zero, configuration: configuration)
    }

    var productWebViewHolder: WebViewHolder {
        if let productHolder {
            return productHolder
        }
        let holder = WebViewHolder.withSmokeBridge(
            surface: .home,
            handler: AcceptingBridgeCommandHandler()
        )
        productHolder = holder
        return holder
    }

    func start() {
        guard isNetworkConnected() else {
            phase = .blocked(.noNetwork)
            return
        }
        phase = .bootstrapping
        authRuntime.loadCredential { [weak self] credential in
            Task { @MainActor in
                self?.handleLoadedCredential(credential)
            }
        }
    }

    func submitLogin() {
        if !loginState.agreementAccepted {
            showToast("개인정보 수집 및 제공에 동의해주세요.")
            return
        }
        if loginState.studentId.count != LoginUiState.studentIdLength || loginState.password.isEmpty {
            showToast("학번과 비밀번호를 입력해주세요.")
            return
        }
        let accountId = loginState.studentId
        let plainPassword = loginState.password
        authRuntime.prepareCredential(accountId: accountId, plainPassword: plainPassword) { [weak self] result in
            Task { @MainActor in
                self?.handlePrepareResult(result)
            }
        }
    }

    func retryWebLogin() {
        guard let credential = activeCredential else {
            phase = .needsCredentials
            return
        }
        beginWebLogin(credential: credential)
    }

    func openExternal(_ url: URL) {
        UIApplication.shared.open(url)
    }

    /// 로그인 화면 URL 분기: KLAS 복구/등록은 인앱 WebView, 그 외(동의 블로그 등)는 외부 브라우저
    func openLoginURL(_ url: URL) {
        if Self.shouldOpenInAppWeb(url) {
            presentedLinkURL = url
        } else {
            openExternal(url)
        }
    }

    func dismissLinkWeb() {
        presentedLinkURL = nil
    }

    private static func shouldOpenInAppWeb(_ url: URL) -> Bool {
        let absolute = url.absoluteString
        return absolute == KlasTheme.findIdURL.absoluteString
            || absolute == KlasTheme.findPasswordURL.absoluteString
            || absolute == KlasTheme.registerURL.absoluteString
            || absolute.contains("UserFindMemberNoPage.do")
            || absolute.contains("UserFindPwdPage.do")
            || absolute.contains("UserFrstModPwdPage.do")
    }

    func handleBlockedAction(_ action: BlockedAction) {
        switch action {
        case .dismissNoNetwork, .exit:
            // iOS에는 Activity finish가 없으므로 로그인 화면으로 되돌리거나 앱을 백그라운드로 처리
            phase = .needsCredentials
        case .openKlasBrowser:
            if let url = URL(string: KlasUrls.shared.KLAS_BASE) {
                openExternal(url)
            }
            phase = .needsCredentials
        case .openStatus:
            if let url = URL(string: KlasUrls.shared.STATUS) {
                openExternal(url)
            }
            phase = .needsCredentials
        case .retry:
            retryWebLogin()
        case .wipeAndExit:
            authRuntime.wipeForFailedLogin { [weak self] in
                Task { @MainActor in
                    self?.activeCredential = nil
                    self?.loginState = LoginUiState(
                        onboardingVisible: true,
                        studentId: "",
                        password: "",
                        agreementAccepted: false
                    )
                    self?.phase = .needsCredentials
                }
            }
        case .goToLogin:
            phase = .needsCredentials
        }
    }

    #if DEBUG
    func debugExpireSession() {
        authRuntime.expireSession { [weak self] in
            Task { @MainActor in
                self?.productHolder = nil
                self?.start()
            }
        }
    }

    /// 세션 + 저장된 자격증명까지 지우고 로그인 화면으로 이동
    func debugWipeCredentials() {
        authRuntime.wipeForFailedLogin { [weak self] in
            Task { @MainActor in
                self?.productHolder = nil
                self?.activeCredential = nil
                self?.loginState = LoginUiState(
                    onboardingVisible: false,
                    studentId: "",
                    password: "",
                    agreementAccepted: false
                )
                self?.phase = .needsCredentials
            }
        }
    }
    #endif

    enum BlockedAction {
        case dismissNoNetwork
        case exit
        case openKlasBrowser
        case openStatus
        case retry
        case wipeAndExit
        case goToLogin
    }

    private func handleLoadedCredential(_ credential: StoredCredential?) {
        guard let credential else {
            phase = .needsCredentials
            return
        }
        activeCredential = credential
        authRuntime.restoreSession { [weak self] result in
            Task { @MainActor in
                self?.handleRestoreResult(result, credential: credential)
            }
        }
    }

    private func handleRestoreResult(_ result: SessionResult, credential: StoredCredential) {
        if result is SessionResultActive {
            phase = .authenticated
            return
        }
        beginWebLogin(credential: credential)
    }

    private func handlePrepareResult(_ result: CredentialPreparationResult) {
        if let success = result as? CredentialPreparationResultSuccess {
            loginState.password = ""
            activeCredential = success.credential
            beginWebLogin(credential: success.credential)
            return
        }
        if let failure = result as? CredentialPreparationResultFailure {
            let message: String
            if failure.failure is AuthFailureTimeout {
                message = "요청 시간이 초과되었습니다. 다시 시도해주세요."
            } else if failure.failure is AuthFailureMalformedResponse {
                message = "서버 응답을 처리할 수 없습니다."
            } else if failure.failure is AuthFailureStorage {
                message = "로그인 정보를 안전하게 저장하지 못했습니다."
            } else {
                message = "로그인 정보를 확인하는 중 오류가 발생했습니다."
            }
            showToast(message)
        }
    }

    private func beginWebLogin(credential: StoredCredential) {
        phase = .authenticating
        loadingMessage = "로그인 중"
        startLoadingHint()
        let driver = IosWebAuthDriver(webView: authWebView)
        driver.onInvalidCredentialAlert = { [weak self] message in
            Task { @MainActor in
                self?.phase = .blocked(.invalidCredentials(message))
            }
        }
        webAuthDriver = driver
        authRuntime.resumeLogin(driver: driver, credential: credential) { [weak self] result in
            Task { @MainActor in
                self?.handleLoginResult(result)
            }
        }
    }

    private func handleLoginResult(_ result: LoginResult) {
        cancelLoadingHint()
        if result is LoginResultAuthenticated {
            phase = .authenticated
            return
        }
        if result is LoginResultUserActionRequired {
            phase = .blocked(.securityActionRequired)
            return
        }
        if let failed = result as? LoginResultFailed {
            if failed.failure is AuthFailureInvalidCredentials {
                phase = .blocked(.invalidCredentials(nil))
            } else {
                phase = .blocked(.loginFailed)
            }
        }
    }

    private func startLoadingHint() {
        cancelLoadingHint()
        loadingHintTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: 7_000_000_000)
            guard !Task.isCancelled, phase == .authenticating else { return }
            loadingMessage = "조금만 더 기다려주세요"
        }
    }

    private func cancelLoadingHint() {
        loadingHintTask?.cancel()
        loadingHintTask = nil
    }

    private func showToast(_ message: String) {
        toastMessage = message
        toastTask?.cancel()
        toastTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: 2_500_000_000)
            if toastMessage == message {
                toastMessage = nil
            }
        }
    }

    private func isNetworkConnected() -> Bool {
        let monitor = NWPathMonitor()
        let queue = DispatchQueue(label: "auth.network.check")
        let box = NetworkStatusBox()
        monitor.pathUpdateHandler = { path in
            box.set(path.status == .satisfied)
            monitor.cancel()
        }
        monitor.start(queue: queue)
        let deadline = Date().addingTimeInterval(0.4)
        while Date() < deadline {
            if let value = box.get() {
                return value
            }
            Thread.sleep(forTimeInterval: 0.02)
        }
        monitor.cancel()
        return true
    }
}

private final class NetworkStatusBox: @unchecked Sendable {
    private let lock = NSLock()
    private var value: Bool?

    func set(_ newValue: Bool) {
        lock.lock()
        value = newValue
        lock.unlock()
    }

    func get() -> Bool? {
        lock.lock()
        defer { lock.unlock() }
        return value
    }
}

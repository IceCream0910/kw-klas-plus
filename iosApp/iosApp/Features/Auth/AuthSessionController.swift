import Foundation
import Network
import Shared
import SwiftUI
import UIKit

enum AuthPhase: Equatable {
    case checkingNetwork
    case bootstrapping
    case needsCredentials
    case authenticating
    case setup
    case authenticated
    case blocked(AuthBlockReason)
}

enum AuthBlockReason: Equatable {
    case noNetwork
    case securityActionRequired
    case invalidCredentials(String?)
    case loginFailed
    case storageFailure
}

protocol FunnelStatusStoring {
    func read() -> String?
    func write(_ value: String) -> Bool
}

private struct UserDefaultsFunnelStatusStore: FunnelStatusStoring {
    let dependencies: IosSharedDependencies
    private let key = "login_funnel_status"

    func read() -> String? { dependencies.stringPreference(key: key) }
    func write(_ value: String) -> Bool {
        dependencies.writeStringPreferenceVerified(key: key, value: value)
    }
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
    @Published private(set) var canReturnToOnboarding = false
    @Published var loadingMessage = "로그인 중"
    @Published var toastMessage: String?
    /// Android `openWebRoute` → LinkViewActivity 패리티 (학번/비번 찾기,최초 등록)
    @Published var presentedLinkURL: URL?

    let authRuntime: IosAuthRuntime
    private let networkPath: NetworkPathChecking
    private let funnelStatusStore: FunnelStatusStoring
    private let loginTokenEncryptor = IosRsaLoginTokenEncryptor()
    private let platformUserAgent = HomeCoordinator.platformUserAgent()
    private var startTask: Task<Void, Never>?
    private var loadingHintTask: Task<Void, Never>?
    private var toastTask: Task<Void, Never>?
    private var activeCredential: StoredCredential?
    private var isAppActive = false

    init(
        authRuntime: IosAuthRuntime = IosAuthRuntime.companion.createDefault(),
        networkPath: NetworkPathChecking = SystemNetworkPathChecker(),
        funnelStatusStore: FunnelStatusStoring? = nil
    ) {
        self.authRuntime = authRuntime
        self.networkPath = networkPath
        self.funnelStatusStore = funnelStatusStore ?? UserDefaultsFunnelStatusStore(dependencies: authRuntime.dependencies)
    }

    func handleHomeLogout() {
        authRuntime.stopSessionKeepAlive()
        activeCredential = nil
        loginState = LoginUiState(
            onboardingVisible: false,
            studentId: "",
            password: "",
            agreementAccepted: false
        )
        phase = .needsCredentials
    }

    func handleHomeSessionExpired() {
        authRuntime.stopSessionKeepAlive()
        authRuntime.expireSession { [weak self] in
            Task { @MainActor in
                guard let self else { return }
                if let credential = self.activeCredential {
                    self.beginHttpLogin(credential: credential)
                } else {
                    self.beginBootstrap()
                }
            }
        }
    }

    func setAppActive(_ active: Bool) {
        isAppActive = active
        if active && (phase == .authenticated || phase == .setup) {
            startSessionKeepAlive(initialDelayMillis: 0)
        } else if !active {
            authRuntime.stopSessionKeepAlive()
        }
    }

    func start() {
        startTask?.cancel()
        phase = .checkingNetwork
        startTask = Task { @MainActor [weak self] in
            guard let self else { return }
            let connected = await self.networkPath.isSatisfied()
            guard !Task.isCancelled else { return }
            guard connected else {
                self.phase = .blocked(.noNetwork)
                return
            }
            self.beginBootstrap()
        }
    }

    func submitLogin() {
        guard !loginState.password.isEmpty else { return }
        if loginState.studentId.count != LoginUiState.studentIdLength {
            loginState.error = "학번을 확인해 주세요."
            return
        }
        guard funnelStatusStore.write("authenticating") else {
            loginState.error = "로그인 상태를 저장하지 못했습니다. 다시 시도해 주세요."
            return
        }
        loginState.step = .authenticating
        loginState.error = nil
        let accountId = loginState.studentId
        let plainPassword = loginState.password
        loginState.password = ""
        authRuntime.prepareCredential(accountId: accountId, plainPassword: plainPassword) { [weak self] result in
            Task { @MainActor in
                self?.handlePrepareResult(result)
            }
        }
    }

    func beginFunnelFromOnboarding() {
        canReturnToOnboarding = true
        loginState.onboardingVisible = false
    }

    func continueFunnel() {
        switch loginState.step {
        case .studentId:
            guard loginState.studentId.count == LoginUiState.studentIdLength else { return }
            loginState.step = .password
        case .password:
            submitLogin()
        case .agreements:
            guard phase == .setup else { return }
            loginState.privacyAccepted = true
            loginState.termsAccepted = true
            loginState.step = .complete
        case .complete:
            finishFunnel()
        default:
            break
        }
    }

    func updateStudentId(_ input: String) {
        let next = String(input.filter(\.isNumber).prefix(LoginUiState.studentIdLength))
        let wasIncomplete = loginState.studentId.count < LoginUiState.studentIdLength
        loginState.studentId = next
        loginState.error = nil
        if wasIncomplete, next.count == LoginUiState.studentIdLength, loginState.step == .studentId {
            loginState.step = .password
        }
    }

    func backFunnel() {
        switch loginState.step {
        case .studentId:
            if canReturnToOnboarding {
                canReturnToOnboarding = false
                loginState.onboardingVisible = true
            }
        case .password: loginState.step = .studentId
        case .agreements: loginState.step = .library
        case .complete: loginState.step = .agreements
        default: break
        }
    }

    func skipLibrary() {
        guard phase == .setup, loginState.step == .library else { return }
        loginState.libraryPassword = ""
        loginState.step = .agreements
    }

    func saveLibrary() {
        guard phase == .setup, loginState.step == .library,
              !loginState.libraryPassword.isEmpty, !loginState.libraryPhone.isEmpty else { return }
        authRuntime.dependencies.libraryService.saveCredentials(
            studentNumber: loginState.studentId,
            phoneNumber: loginState.libraryPhone,
            password: loginState.libraryPassword
        ) { [weak self] in
            Task { @MainActor in
                guard let self, self.phase == .setup, self.loginState.step == .library else { return }
                self.loginState.libraryPassword = ""
                self.loginState.step = .agreements
            }
        }
    }

    func finishFunnel() {
        guard phase == .setup, loginState.step == .complete,
              loginState.privacyAccepted, loginState.termsAccepted else { return }
        guard funnelStatusStore.write("complete") else {
            showToast("설정 완료 상태를 저장하지 못했습니다. 다시 시도해 주세요.")
            return
        }
        loginState.libraryPassword = ""
        phase = .authenticated
        if isAppActive { startSessionKeepAlive(initialDelayMillis: 0) }
    }

    func retryAuthentication() {
        guard let credential = activeCredential else {
            phase = .needsCredentials
            return
        }
        validateStoredSession(credential: credential)
    }

    func openExternal(_ url: URL) {
        _ = IosExternalNavigator.companion.system().openValidated(rawValue: url.absoluteString)
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
            retryAuthentication()
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
            loadAccountIdForLogin()
        }
    }

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
        if funnelStatusStore.read() == "authenticating" {
            loadAccountIdForLogin()
            return
        }
        guard let credential else {
            loadAccountIdForLogin()
            return
        }
        activeCredential = credential
        loginState.studentId = credential.accountId
        validateStoredSession(credential: credential)
    }

    private func validateStoredSession(credential: StoredCredential) {
        phase = .bootstrapping
        authRuntime.maintainSession(userAgent: platformUserAgent) { [weak self] result in
            Task { @MainActor in
                self?.handleLeaseResult(result, credential: credential)
            }
        }
    }

    private func handleLeaseResult(_ result: SessionLeaseResult, credential: StoredCredential) {
        if let active = result as? SessionLeaseResultActive {
            enterAuthenticated(initialDelayMillis: active.nextCheckAfterMillis)
            return
        }
        if result is SessionLeaseResultExpired || result is SessionLeaseResultMissing {
            beginHttpLogin(credential: credential)
            return
        }
        phase = .blocked(.loginFailed)
    }

    private func handlePrepareResult(_ result: CredentialPreparationResult) {
        if let success = result as? CredentialPreparationResultSuccess {
            loginState.password = ""
            activeCredential = success.credential
            beginHttpLogin(credential: success.credential)
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
            loginState.step = .password
            loginState.error = message
            phase = .needsCredentials
        }
    }

    private func beginHttpLogin(credential: StoredCredential) {
        phase = .authenticating
        loadingMessage = "로그인 중"
        startLoadingHint()
        authRuntime.resumeHttpLogin(
            tokenEncryptor: loginTokenEncryptor,
            credential: credential
        ) { [weak self] result in
            Task { @MainActor in
                self?.handleLoginResult(result)
            }
        }
    }

    private func handleLoginResult(_ result: LoginResult) {
        cancelLoadingHint()
        if result is LoginResultAuthenticated {
            enterAuthenticated(initialDelayMillis: 0)
            return
        }
        if funnelStatusStore.read() == "authenticating" {
            loginState.step = .password
            loginState.password = ""
            loginState.error = result is LoginResultUserActionRequired
                ? "KLAS에서 CAPTCHA 또는 임시 비밀번호 변경이 필요해요. 학교 사이트에서 조치를 마친 뒤 다시 시도해 주세요."
                : "KLAS 인증에 실패했어요. 학번과 비밀번호를 확인해 주세요."
            phase = .needsCredentials
            return
        }
        if result is LoginResultUserActionRequired {
            phase = .blocked(.securityActionRequired)
            return
        }
        if let failed = result as? LoginResultFailed {
            if failed.failure is AuthFailureInvalidCredentials {
                activeCredential = nil
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

    private func beginBootstrap() {
        phase = .bootstrapping
        authRuntime.loadCredential { [weak self] credential in
            Task { @MainActor in
                self?.handleLoadedCredential(credential)
            }
        }
    }

    func enterAuthenticated(initialDelayMillis: Int64) {
        var status = funnelStatusStore.read()
        if status == "authenticating" {
            guard funnelStatusStore.write("setup") else {
                phase = .blocked(.storageFailure)
                return
            }
            status = funnelStatusStore.read()
        }
        if status == "setup" {
            loginState.step = .library
            loginState.onboardingVisible = false
            loginState.password = ""
            if loginState.libraryPhone.isEmpty {
                loginState.libraryPhone = (authRuntime.dependencies.stringPreference(key: "library_phone") ?? "")
                    .filter { ("0"..."9").contains($0) }
            }
            phase = .setup
            if isAppActive { startSessionKeepAlive(initialDelayMillis: initialDelayMillis) }
            return
        }
        if status == nil {
            guard funnelStatusStore.write("complete") else {
                phase = .blocked(.storageFailure)
                return
            }
            status = funnelStatusStore.read()
        }
        guard status == "complete" else {
            phase = .blocked(.storageFailure)
            return
        }
        phase = .authenticated
        guard isAppActive else { return }
        startSessionKeepAlive(initialDelayMillis: initialDelayMillis)
    }

    private func startSessionKeepAlive(initialDelayMillis: Int64) {
        authRuntime.startSessionKeepAlive(
            userAgent: platformUserAgent,
            initialDelayMillis: initialDelayMillis
        ) { [weak self] in
            Task { @MainActor in
                guard let self else { return }
                guard let credential = self.activeCredential else {
                    self.beginBootstrap()
                    return
                }
                self.beginHttpLogin(credential: credential)
            }
        }
    }

    private func loadAccountIdForLogin() {
        authRuntime.loadAccountId { [weak self] accountId in
            Task { @MainActor in
                guard let self else { return }
                self.activeCredential = nil
                self.loginState = LoginUiState(
                    onboardingVisible: accountId == nil,
                    studentId: accountId ?? "",
                    password: "",
                    agreementAccepted: false
                )
                if self.funnelStatusStore.read() == "authenticating" {
                    self.loginState.step = .password
                    self.loginState.onboardingVisible = false
                }
                self.phase = .needsCredentials
            }
        }
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

}

protocol NetworkPathChecking: AnyObject {
    func isSatisfied() async -> Bool
}

final class SystemNetworkPathChecker: NetworkPathChecking, @unchecked Sendable {
    private let monitor = NWPathMonitor()
    private let queue = DispatchQueue(label: "auth.network.check")
    private let lock = NSLock()
    private var latestSatisfied: Bool?
    private var waiters: [(Bool) -> Void] = []

    init() {
        monitor.pathUpdateHandler = { [weak self] path in
            self?.publish(path.status == .satisfied)
        }
        monitor.start(queue: queue)
    }

    deinit {
        monitor.cancel()
    }

    func isSatisfied() async -> Bool {
        if let known = snapshot() {
            return known
        }
        return await withCheckedContinuation { continuation in
            let once = OnceResume(continuation)
            enqueue { once.resume($0) }
            queue.asyncAfter(deadline: .now() + .milliseconds(400)) { [weak self] in
                once.resume(self?.snapshot() ?? true)
            }
        }
    }

    private func snapshot() -> Bool? {
        lock.lock()
        defer { lock.unlock() }
        return latestSatisfied
    }

    private func enqueue(_ waiter: @escaping (Bool) -> Void) {
        lock.lock()
        if let latestSatisfied {
            lock.unlock()
            waiter(latestSatisfied)
            return
        }
        waiters.append(waiter)
        lock.unlock()
    }

    private func publish(_ satisfied: Bool) {
        lock.lock()
        latestSatisfied = satisfied
        let pending = waiters
        waiters.removeAll()
        lock.unlock()
        pending.forEach { $0(satisfied) }
    }
}

private final class OnceResume: @unchecked Sendable {
    private let lock = NSLock()
    private var continuation: CheckedContinuation<Bool, Never>?

    init(_ continuation: CheckedContinuation<Bool, Never>) {
        self.continuation = continuation
    }

    func resume(_ value: Bool) {
        lock.lock()
        let pending = continuation
        continuation = nil
        lock.unlock()
        pending?.resume(returning: value)
    }
}

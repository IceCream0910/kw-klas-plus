import Shared
import SwiftUI

struct StartupRootView: View {
    @StateObject private var controller: AuthSessionController
    @StateObject private var appLock: AppLockController
    @Environment(\.scenePhase) private var scenePhase

    init() {
        let auth = AuthSessionController()
        _controller = StateObject(wrappedValue: auth)
        _appLock = StateObject(
            wrappedValue: AppLockController(store: auth.authRuntime.dependencies.appLockStore)
        )
    }

    var body: some View {
        ZStack {
            switch controller.phase {
            case .checkingNetwork, .bootstrapping:
                LaunchSplashView()
            case .needsCredentials:
                LoginView(
                    state: $controller.loginState,
                    toastMessage: controller.toastMessage,
                    onStartClick: { controller.loginState.onboardingVisible = false },
                    onLoginClick: { controller.submitLogin() },
                    onOpenURL: { controller.openLoginURL($0) }
                )
                .fullScreenCover(
                    item: Binding(
                        get: { controller.presentedLinkURL.map { PresentedLinkURL(url: $0) } },
                        set: { controller.presentedLinkURL = $0?.url }
                    )
                ) { item in
                    LinkWebViewScreen(url: item.url, onDismiss: { controller.dismissLinkWeb() })
                }
            case .authenticating, .blocked:
                AuthenticationLoadingView(message: controller.loadingMessage)
            case .authenticated:
                HomeRootView(
                    authRuntime: controller.authRuntime,
                    onLogout: { controller.handleHomeLogout() },
                    onSessionExpired: { controller.handleHomeSessionExpired() }
                )
            }
        }
        .environmentObject(appLock)
        .alert(blockedAlertTitle, isPresented: blockedAlertPresented) {
            blockedAlertButtons
        } message: {
            Text(blockedAlertMessage)
        }
        .accessibilityIdentifier(isBlocked ? "auth_alert" : "")
        .fullScreenCover(item: $appLock.mode) { _ in
            LockScreenView(controller: appLock)
                .preferredColorScheme(lockColorScheme)
        }
        .onAppear {
            controller.setAppActive(scenePhase == .active)
            controller.start()
            appLock.handleScenePhase(scenePhase)
        }
        .onChange(of: scenePhase) { phase in
            controller.setAppActive(phase == .active)
            appLock.handleScenePhase(phase)
        }
    }

    private var lockColorScheme: ColorScheme? {
        switch controller.authRuntime.dependencies.stringPreference(key: "appTheme") {
        case "dark": return .dark
        case "light": return .light
        default: return nil
        }
    }

    private var isBlocked: Bool {
        if case .blocked = controller.phase { return true }
        return false
    }

    private var blockedAlertPresented: Binding<Bool> {
        Binding(
            get: { isBlocked },
            set: { _ in }
        )
    }

    private var blockedAlertTitle: String {
        guard case .blocked(let reason) = controller.phase else { return "" }
        switch reason {
        case .noNetwork:
            return "네트워크 연결 오류"
        case .securityActionRequired, .invalidCredentials, .loginFailed:
            return "로그인 실패"
        }
    }

    private var blockedAlertMessage: String {
        guard case .blocked(let reason) = controller.phase else { return "" }
        switch reason {
        case .noNetwork:
            return "네트워크 연결 상태를 확인해주세요."
        case .securityActionRequired:
            return "임시 비밀번호 변경이 필요하거나 3회 이상 로그인 실패로 인해 CAPTCHA 입력이 필요해요. 계정 보안을 위해 KLAS 웹사이트에서 먼저 로그인하신 후 다시 시도해 주세요."
        case .invalidCredentials(let message):
            return message ?? "학번 또는 비밀번호를 확인한 후 다시 로그인해주세요."
        case .loginFailed:
            return "알 수 없는 오류로 인해 로그인에 실패했어요. 먼저 기기의 네트워크 상태가 불안정한지 확인 후 다시 시도해보세요. 어쩌면 전체적인 서버 장애가 발생했을 수도 있어요. 이 경우 담당자가 빠르게 대응하고 있을거예요."
        }
    }

    @ViewBuilder
    private var blockedAlertButtons: some View {
        if case .blocked(let reason) = controller.phase {
            switch reason {
            case .noNetwork:
                Button("확인") { controller.handleBlockedAction(.dismissNoNetwork) }
            case .securityActionRequired:
                Button("브라우저 열기") { controller.handleBlockedAction(.openKlasBrowser) }
                Button("종료") { controller.handleBlockedAction(.exit) }
            case .invalidCredentials:
                Button("확인") { controller.handleBlockedAction(.goToLogin) }
            case .loginFailed:
                Button("다시 시도") { controller.handleBlockedAction(.retry) }
                Button("서버 상태 확인") { controller.handleBlockedAction(.openStatus) }
                Button("앱 종료", role: .destructive) {
                    controller.handleBlockedAction(.wipeAndExit)
                }
            }
        }
    }
}

private struct PresentedLinkURL: Identifiable {
    let url: URL
    var id: String { url.absoluteString }
}

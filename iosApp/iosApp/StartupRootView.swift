import Shared
import SwiftUI

struct StartupRootView: View {
    @StateObject private var controller = AuthSessionController()

    var body: some View {
        Group {
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
            case .authenticating:
                AuthenticationLoadingView(
                    message: controller.loadingMessage,
                    webView: controller.authWebView
                )
            case .authenticated:
                ContentView(holder: controller.productWebViewHolder)
            case .blocked(let reason):
                blockedOverlay(reason)
            }
        }
        .onAppear { controller.start() }
        #if DEBUG
        .overlay(alignment: .topTrailing) {
            if controller.phase == .authenticated {
                VStack(alignment: .trailing, spacing: 4) {
                    Button("Expire") { controller.debugExpireSession() }
                    Button("Wipe") { controller.debugWipeCredentials() }
                }
                .font(.caption2)
                .padding(8)
            }
        }
        #endif
    }

    @ViewBuilder
    private func blockedOverlay(_ reason: AuthBlockReason) -> some View {
        ZStack {
            KlasTheme.background.ignoresSafeArea()
            switch reason {
            case .noNetwork:
                AuthAlertCard(
                    title: "네트워크 연결 오류",
                    message: "네트워크 연결 상태를 확인해주세요.",
                    primary: ("확인", { controller.handleBlockedAction(.dismissNoNetwork) })
                )
            case .securityActionRequired:
                AuthAlertCard(
                    title: "로그인 실패",
                    message: "임시 비밀번호 변경이 필요하거나 3회 이상 로그인 실패로 인해 CAPTCHA 입력이 필요해요. 계정 보안을 위해 KLAS 웹사이트에서 먼저 로그인하신 후 다시 시도해 주세요.",
                    primary: ("브라우저 열기", { controller.handleBlockedAction(.openKlasBrowser) }),
                    secondary: ("종료", { controller.handleBlockedAction(.exit) })
                )
            case .invalidCredentials(let message):
                AuthAlertCard(
                    title: "로그인 실패",
                    message: message ?? "학번 또는 비밀번호를 확인한 후 다시 로그인해주세요.",
                    primary: ("확인", { controller.handleBlockedAction(.goToLogin) })
                )
            case .loginFailed:
                AuthAlertCard(
                    title: "로그인 실패",
                    message: "알 수 없는 오류로 인해 로그인에 실패했어요. 먼저 기기의 네트워크 상태가 불안정한지 확인 후 다시 시도해보세요. 어쩌면 전체적인 서버 장애가 발생했을 수도 있어요. 이 경우 담당자가 빠르게 대응하고 있을거예요.",
                    primary: ("다시 시도", { controller.handleBlockedAction(.retry) }),
                    secondary: ("서버 상태 확인", { controller.handleBlockedAction(.openStatus) }),
                    tertiary: ("앱 종료", { controller.handleBlockedAction(.wipeAndExit) })
                )
            }
        }
    }
}

private struct AuthAlertCard: View {
    var title: String
    var message: String
    var primary: (String, () -> Void)
    var secondary: (String, () -> Void)? = nil
    var tertiary: (String, () -> Void)? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text(title).font(.headline)
            Text(message).font(.body)
            VStack(spacing: 8) {
                Button(primary.0, action: primary.1)
                    .buttonStyle(KlasInverseButtonStyle())
                if let secondary {
                    Button(secondary.0, action: secondary.1)
                        .frame(maxWidth: .infinity)
                        .frame(height: KlasTheme.buttonHeight)
                }
                if let tertiary {
                    Button(tertiary.0, action: tertiary.1)
                        .frame(maxWidth: .infinity)
                        .frame(height: KlasTheme.buttonHeight)
                }
            }
        }
        .padding(24)
        .frame(maxWidth: 480)
        .background(KlasTheme.surface, in: RoundedRectangle(cornerRadius: 16))
        .padding(24)
    }
}

private struct PresentedLinkURL: Identifiable {
    let url: URL
    var id: String { url.absoluteString }
}

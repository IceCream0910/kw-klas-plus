import Shared
import SwiftUI

struct StartupRootView: View {
    @StateObject private var controller = AuthSessionController()

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
                AuthenticationLoadingView(
                    message: controller.loadingMessage,
                    webView: controller.authWebView
                )
            case .authenticated:
                ContentView(holder: controller.productWebViewHolder)
            }
            if case .blocked(let reason) = controller.phase {
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
            // Material3 Dialog scrim
            KlasTheme.scrim.opacity(0.32)
                .ignoresSafeArea()
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
        .accessibilityIdentifier("auth_alert")
    }
}

/// Android `MaterialAlertDialogBuilder` 패리티: scrim 위 다이얼로그, 우측 TextButton
private struct AuthAlertCard: View {
    var title: String
    var message: String
    var primary: (String, () -> Void)
    var secondary: (String, () -> Void)? = nil
    var tertiary: (String, () -> Void)? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text(title)
                .font(.title3.weight(.semibold))
                .foregroundStyle(KlasTheme.onSurface)
            Text(message)
                .font(.subheadline)
                .foregroundStyle(KlasTheme.onSurfaceVariant)
                .fixedSize(horizontal: false, vertical: true)
            actionRow
        }
        .padding(24)
        .frame(maxWidth: 560)
        .background(
            RoundedRectangle(cornerRadius: 28, style: .continuous)
                .fill(KlasTheme.surfaceContainerHigh)
                .shadow(color: KlasTheme.scrim.opacity(0.18), radius: 8, y: 4)
        )
        .padding(.horizontal, 40)
        .accessibilityIdentifier("auth_alert_card")
    }

    private var actionRow: some View {
        ViewThatFits(in: .horizontal) {
            HStack(spacing: 8) {
                Spacer(minLength: 0)
                actionButtons
            }
            VStack(alignment: .trailing, spacing: 0) {
                actionButtons
            }
            .frame(maxWidth: .infinity, alignment: .trailing)
        }
    }

    @ViewBuilder
    private var actionButtons: some View {
        if let tertiary {
            dialogTextButton(tertiary)
        }
        if let secondary {
            dialogTextButton(secondary)
        }
        dialogTextButton(primary)
    }

    private func dialogTextButton(_ item: (String, () -> Void)) -> some View {
        Button(action: item.1) {
            Text(item.0)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(KlasTheme.primary)
                .padding(.horizontal, 12)
                .frame(height: 40)
                .contentShape(Rectangle())
        }
        .buttonStyle(KlasPressHighlightButtonStyle())
    }
}

private struct PresentedLinkURL: Identifiable {
    let url: URL
    var id: String { url.absoluteString }
}

import Shared
import SwiftUI
import WebKit

struct LoginUiState: Equatable {
    static let studentIdLength = 10

    var onboardingVisible: Bool
    var studentId: String
    var password: String
    var agreementAccepted: Bool

    var passwordFieldVisible: Bool { studentId.count == Self.studentIdLength }

    var loginEnabled: Bool {
        passwordFieldVisible && !password.isEmpty && agreementAccepted
    }
}

struct LoginView: View {
    @Binding var state: LoginUiState
    var toastMessage: String?
    var onStartClick: () -> Void
    var onLoginClick: () -> Void
    var onOpenURL: (URL) -> Void

    var body: some View {
        ZStack {
            KlasTheme.background.ignoresSafeArea()
            if state.onboardingVisible {
                OnboardingContent(onStartClick: onStartClick)
                    .accessibilityIdentifier("login_onboarding")
            } else {
                LoginFormContent(
                    state: $state,
                    onLoginClick: onLoginClick,
                    onOpenURL: onOpenURL
                )
                .accessibilityIdentifier("login_form")
            }
            if let toastMessage {
                VStack {
                    Spacer()
                    Text(toastMessage)
                        .font(.subheadline)
                        .foregroundStyle(.white)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                        .background(Color.black.opacity(0.8), in: Capsule())
                        .padding(.bottom, 40)
                }
                .transition(.opacity)
                .allowsHitTesting(false)
            }
        }
    }
}

private enum LoginField {
    case studentId
    case password
}

private struct OnboardingContent: View {
    var onStartClick: () -> Void

    var body: some View {
        ZStack(alignment: .bottom) {
            OnboardingWebView(urlString: KlasUrls.shared.ONBOARDING)
                .ignoresSafeArea()
                .accessibilityIdentifier("login_onboarding_web_view")
            Button(action: onStartClick) {
                Text("시작하기")
            }
            .buttonStyle(KlasInverseButtonStyle())
            .padding(.horizontal, 16)
            .padding(.vertical, 24)
            .frame(maxWidth: 520)
            .accessibilityIdentifier("login_start")
        }
    }
}

private struct OnboardingWebView: UIViewRepresentable {
    let urlString: String

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    func makeUIView(context: Context) -> WKWebView {
        let configuration = WKWebViewConfiguration()
        configuration.websiteDataStore = .default()
        // 웹 _app.js는 KlasNativeBridge.completePageLoad 실패 시 Play Store로 replace한다.
        // 온보딩 WebView에도 Bridge v1을 심어 앱 내 온보딩이 유지되게 한다.
        let adapter = IosBridgeMessageAdapter(
            surface: .home,
            handler: AcceptingBridgeCommandHandler()
        )
        adapter.install(into: configuration)
        context.coordinator.bridgeAdapter = adapter

        let webView = WKWebView(frame: .zero, configuration: configuration)
        webView.navigationDelegate = context.coordinator
        if let url = URL(string: urlString) {
            webView.load(URLRequest(url: url))
        }
        return webView
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {}

    static func dismantleUIView(_ uiView: WKWebView, coordinator: Coordinator) {
        uiView.stopLoading()
        uiView.navigationDelegate = nil
        coordinator.bridgeAdapter?.dispose()
        coordinator.bridgeAdapter = nil
    }

    final class Coordinator: NSObject, WKNavigationDelegate {
        var bridgeAdapter: IosBridgeMessageAdapter?
        private let trustedOrigins = TrustedOriginPolicy(
            trustedOrigins: TrustedOriginPolicy.companion.DEFAULT_TRUSTED_ORIGINS
        )

        func webView(
            _ webView: WKWebView,
            decidePolicyFor navigationAction: WKNavigationAction,
            decisionHandler: @escaping (WKNavigationActionPolicy) -> Void
        ) {
            guard navigationAction.targetFrame?.isMainFrame != false,
                  let url = navigationAction.request.url?.absoluteString
            else {
                decisionHandler(.allow)
                return
            }
            if trustedOrigins.isTrustedUrl(url: url) {
                decisionHandler(.allow)
                return
            }
            // Play Store 등 외부 URL은 온보딩 WebView 안에서 열지 않는다.
            decisionHandler(.cancel)
        }
    }
}

private struct LoginFormContent: View {
    @Binding var state: LoginUiState
    var onLoginClick: () -> Void
    var onOpenURL: (URL) -> Void
    @FocusState private var focusedField: LoginField?

    var body: some View {
        GeometryReader { proxy in
            let widthClass = AppWindowWidthClass.classify(width: proxy.size.width)
            switch widthClass {
            case .expanded:
                HStack(spacing: 64) {
                    LoginHeader(passwordVisible: state.passwordFieldVisible)
                        .frame(maxWidth: .infinity)
                    LoginFields(
                        state: $state,
                        focusedField: $focusedField,
                        onLoginClick: onLoginClick,
                        onOpenURL: onOpenURL,
                        showSubmitActions: true
                    )
                    .frame(maxWidth: 520)
                    .frame(maxWidth: .infinity)
                }
                .padding(.horizontal, 64)
                .padding(.vertical, 32)
            case .compact:
                ZStack(alignment: .bottom) {
                    ScrollView {
                        VStack(alignment: .leading, spacing: 24) {
                            LoginHeader(passwordVisible: state.passwordFieldVisible)
                            LoginFields(
                                state: $state,
                                focusedField: $focusedField,
                                onLoginClick: onLoginClick,
                                onOpenURL: onOpenURL,
                                showSubmitActions: false
                            )
                        }
                        .padding(.horizontal, 20)
                        .padding(.top, 32)
                        .padding(.bottom, 140)
                        .frame(maxWidth: 560)
                        .frame(maxWidth: .infinity)
                    }
                    LoginSubmitActions(
                        state: $state,
                        onLoginClick: onLoginClick,
                        onOpenURL: onOpenURL
                    )
                    .padding(12)
                    .frame(maxWidth: 560)
                    .background(KlasTheme.surface)
                }
            case .medium:
                ScrollView {
                    VStack(alignment: .leading, spacing: 24) {
                        LoginHeader(passwordVisible: state.passwordFieldVisible)
                        LoginFields(
                            state: $state,
                            focusedField: $focusedField,
                            onLoginClick: onLoginClick,
                            onOpenURL: onOpenURL,
                            showSubmitActions: true
                        )
                    }
                    .padding(.horizontal, 48)
                    .padding(.vertical, 32)
                    .frame(maxWidth: 560)
                    .frame(maxWidth: .infinity)
                }
            }
        }
        .task {
            await focusStudentIdOnEntry()
        }
    }

    @MainActor
    private func focusStudentIdOnEntry() async {
        guard !state.passwordFieldVisible else { return }
        // FocusState는 필드가 윈도우에 붙은 뒤에 설정해야 키보드가 올라온다.
        try? await Task.sleep(nanoseconds: 50_000_000)
        guard !Task.isCancelled, !state.passwordFieldVisible else { return }
        focusedField = .studentId
    }
}

private struct LoginHeader: View {
    var passwordVisible: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(passwordVisible ? "KLAS 비밀번호를 입력해주세요." : "학번을 입력해주세요.")
                .font(.title3.bold())
                .foregroundStyle(KlasTheme.onBackground)
            Text("ⓘ 입력한 정보는 학교 공식 KLAS 서버로만 전송되며, KLAS+ 서버에는 별도로 저장되지 않습니다.")
                .font(.footnote)
                .foregroundStyle(KlasTheme.onSurfaceVariant)
        }
    }
}

/// Android Material3 OutlinedTextField 패리티
/// - 비어 있고 비포커스: 라벨이 필드 안 placeholder
/// - 포커스 또는 입력값 있음: 라벨이 테두리 위 floating outlined
private struct KlasOutlinedTextField<Trailing: View>: View {
    let label: String
    @Binding var text: String
    var focusedField: FocusState<LoginField?>.Binding
    var field: LoginField
    var keyboardType: UIKeyboardType
    var textContentType: UITextContentType?
    var isSecure: Bool
    var accessibilityId: String
    @ViewBuilder var trailing: () -> Trailing

    init(
        label: String,
        text: Binding<String>,
        focusedField: FocusState<LoginField?>.Binding,
        field: LoginField,
        keyboardType: UIKeyboardType = .default,
        textContentType: UITextContentType? = nil,
        isSecure: Bool = false,
        accessibilityId: String,
        @ViewBuilder trailing: @escaping () -> Trailing = { EmptyView() }
    ) {
        self.label = label
        self._text = text
        self.focusedField = focusedField
        self.field = field
        self.keyboardType = keyboardType
        self.textContentType = textContentType
        self.isSecure = isSecure
        self.accessibilityId = accessibilityId
        self.trailing = trailing
    }

    private var isFocused: Bool {
        focusedField.wrappedValue == field
    }

    private var isFloating: Bool {
        isFocused || !text.isEmpty
    }

    var body: some View {
        ZStack(alignment: .leading) {
            RoundedRectangle(cornerRadius: KlasTheme.controlCornerRadius, style: .continuous)
                .stroke(borderColor, lineWidth: isFocused ? 2 : 1)

            HStack(spacing: 8) {
                Group {
                    if isSecure {
                        SecureField("", text: $text)
                    } else {
                        TextField("", text: $text)
                    }
                }
                .keyboardType(keyboardType)
                .textContentType(textContentType)
                .focused(focusedField, equals: field)
                .font(.body)
                .foregroundStyle(KlasTheme.onSurface)
                .tint(KlasTheme.primary)
                .accessibilityLabel(label)
                .accessibilityIdentifier(accessibilityId)

                trailing()
            }
            .padding(.horizontal, 16)

            Text(label)
                .font(isFloating ? .caption : .body)
                .foregroundStyle(labelColor)
                .padding(.horizontal, isFloating ? 4 : 0)
                .background {
                    if isFloating {
                        KlasTheme.background
                            .padding(.horizontal, -2)
                    }
                }
                .offset(x: isFloating ? 12 : 16, y: isFloating ? -28 : 0)
                .allowsHitTesting(false)
        }
        .frame(height: 56)
        .padding(.top, 8)
        .animation(.easeOut(duration: 0.15), value: isFloating)
        .animation(.easeOut(duration: 0.15), value: isFocused)
    }

    private var borderColor: Color {
        isFocused ? KlasTheme.primary : KlasTheme.outline
    }

    private var labelColor: Color {
        if isFocused { return KlasTheme.primary }
        if isFloating { return KlasTheme.onSurfaceVariant }
        return KlasTheme.onSurfaceVariant.opacity(0.8)
    }
}

private struct LoginFields: View {
    @Binding var state: LoginUiState
    var focusedField: FocusState<LoginField?>.Binding
    var onLoginClick: () -> Void
    var onOpenURL: (URL) -> Void
    var showSubmitActions: Bool
    @State private var passwordRevealed = false

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            KlasOutlinedTextField(
                label: "학번",
                text: Binding(
                    get: { state.studentId },
                    set: { newValue in
                        let filtered = newValue.filter(\.isNumber)
                        if filtered.count <= LoginUiState.studentIdLength {
                            state.studentId = filtered
                        }
                    }
                ),
                focusedField: focusedField,
                field: .studentId,
                keyboardType: .numberPad,
                textContentType: .username,
                accessibilityId: "login_student_id"
            )

            if state.passwordFieldVisible {
                KlasOutlinedTextField(
                    label: "비밀번호",
                    text: $state.password,
                    focusedField: focusedField,
                    field: .password,
                    textContentType: .password,
                    isSecure: !passwordRevealed,
                    accessibilityId: "login_password"
                ) {
                    Button {
                        passwordRevealed.toggle()
                    } label: {
                        Image(systemName: passwordRevealed ? "eye.slash" : "eye")
                            .foregroundStyle(KlasTheme.onSurfaceVariant)
                    }
                    .accessibilityLabel(passwordRevealed ? "비밀번호 숨기기" : "비밀번호 표시")
                }
                .onAppear { focusedField.wrappedValue = .password }
            }

            VStack(alignment: .leading, spacing: 0) {
                Button {
                    onOpenURL(state.passwordFieldVisible ? KlasTheme.findPasswordURL : KlasTheme.findIdURL)
                } label: {
                    Text(state.passwordFieldVisible ? "비밀번호를 잊어버렸나요?" : "학번이 생각나지 않나요?")
                        .font(.subheadline)
                        .frame(height: 40)
                        .padding(.horizontal, 8)
                }
                .buttonStyle(KlasTextLinkButtonStyle())
                .accessibilityIdentifier("login_recovery")

                if !state.passwordFieldVisible {
                    Button {
                        onOpenURL(KlasTheme.registerURL)
                    } label: {
                        Text("KLAS에 처음 로그인하시나요?")
                            .font(.subheadline)
                            .frame(height: 40)
                            .padding(.horizontal, 8)
                    }
                    .buttonStyle(KlasTextLinkButtonStyle())
                    .accessibilityIdentifier("login_register")
                }
            }

            if showSubmitActions {
                LoginSubmitActions(
                    state: $state,
                    onLoginClick: onLoginClick,
                    onOpenURL: onOpenURL
                )
                .padding(.top, 8)
            }
        }
    }
}

private struct LoginSubmitActions: View {
    @Binding var state: LoginUiState
    var onLoginClick: () -> Void
    var onOpenURL: (URL) -> Void

    var body: some View {
        VStack(spacing: 8) {
            HStack(alignment: .center, spacing: 8) {
                Toggle(isOn: $state.agreementAccepted) {
                    Text("개인정보 수집 및 이용/제공 동의")
                        .font(.footnote)
                        .foregroundStyle(KlasTheme.onBackground)
                }
                .toggleStyle(KlasCheckboxToggleStyle())
                .tint(KlasTheme.primary)
                .accessibilityIdentifier("login_agreement")

                Button {
                    onOpenURL(KlasTheme.agreementURL)
                } label: {
                    Text("자세히")
                        .font(.footnote)
                        .frame(height: 40)
                        .padding(.horizontal, 8)
                }
                .buttonStyle(KlasTextLinkButtonStyle())
            }
            Button(action: onLoginClick) {
                Text("확인")
            }
            .buttonStyle(KlasInverseButtonStyle(enabled: state.loginEnabled))
            .allowsHitTesting(state.loginEnabled)
            .accessibilityIdentifier("login_submit")
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 16)
    }
}

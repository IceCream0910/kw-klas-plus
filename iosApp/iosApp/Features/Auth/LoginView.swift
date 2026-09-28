import Shared
import SwiftUI

struct LoginUiState: Equatable {
    static let studentIdLength = 10

    var onboardingVisible: Bool
    var studentId: String
    var password: String
    var agreementAccepted: Bool
    var step: LoginFunnelStep = .studentId
    var libraryPassword: String = ""
    var libraryPhone: String = ""
    var privacyAccepted: Bool = false
    var termsAccepted: Bool = false
    var error: String? = nil

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
    var onFunnelContinue: () -> Void = {}
    var onFunnelBack: () -> Void = {}
    var onLibrarySave: () -> Void = {}
    var onLibrarySkip: () -> Void = {}
    var onFunnelFinish: () -> Void = {}
    var onStudentIdChange: (String) -> Void = { _ in }
    var canReturnToOnboarding: Bool = false

    var body: some View {
        ZStack {
            KlasTheme.background.ignoresSafeArea()
            if state.onboardingVisible {
                OnboardingContent(onStartClick: onStartClick)
                    .accessibilityIdentifier("login_onboarding")
            } else {
                LoginFunnelView(
                    state: $state,
                    onContinue: onFunnelContinue,
                    onBack: onFunnelBack,
                    onSaveLibrary: onLibrarySave,
                    onSkipLibrary: onLibrarySkip,
                    onOpenURL: onOpenURL,
                    onFinish: onFunnelFinish,
                    onStudentIdChange: onStudentIdChange,
                    canReturnToOnboarding: canReturnToOnboarding
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
    @State private var page = 0

    private let slides: [(title: String, description: String, note: String, image: String)] = [
        ("불편했던 KLAS를\n더 편리하게.", "KLAS+는 모바일에 맞게 학사포털의\n사용자 경험을 다시 설계했어요.", "⚠️ KLAS+는 개인이 개발한 것으로, 학교의 공식 앱이 아닙니다.", "OnboardingPlaceholder"),
        ("남아있는 할 일을\n한 눈에.", "과제, 온라인 강의 등 남아있는 할 일을\n한 눈에 모아서 홈 화면에 보여줄게요.", "", "OnboardingTodo"),
        ("복잡한 메뉴를\n깔끔하게.", "PC와 달라 불편했던 복잡한 메뉴들을 한 페이지에서 찾고,\n자주 쓰는 메뉴를 상단에 고정할 수 있어요.", "", "OnboardingMenu"),
        ("학교 생활을 위한\n나만의 캘린더.", "학사일정, 개인 스케줄은 물론 과제 마감기한까지,\n대학 생활에 필요한 모든 일정을 관리해보세요.", "", "OnboardingCalendar"),
        ("궁금한 건\nKLAS AI에게.", "학교 홈페이지와 KLAS를 누비는\n다재다능한 AI 에이전트와 함께해보세요.", "", "OnboardingAI")
    ]

    var body: some View {
        GeometryReader { geometry in
            let horizontalPadding: CGFloat = geometry.size.width >= 840 ? 32 : 16
            let copyInset: CGFloat = geometry.size.width >= 840 ? 16 : 8
            VStack(spacing: 28) {
                TabView(selection: $page) {
                    ForEach(slides.indices, id: \.self) { index in
                        GeometryReader { pageGeometry in
                            let imageWidth = min(pageGeometry.size.width, min(600, pageGeometry.size.height * 0.62) * 8 / 9)
                            ScrollView {
                                VStack(spacing: 32) {
                                    Image(slides[index].image)
                                        .resizable()
                                        .frame(width: imageWidth, height: imageWidth * 9 / 8)
                                        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
                                        .frame(maxWidth: .infinity)
                                        .accessibilityLabel("KLAS+ 앱 화면 미리보기")
                                    VStack(alignment: .leading, spacing: 0) {
                                        Text(slides[index].title)
                                            .font(.title.bold())
                                            .foregroundStyle(KlasTheme.onBackground)
                                            .fixedSize(horizontal: false, vertical: true)
                                        Text(slides[index].description)
                                            .font(.body)
                                            .foregroundStyle(KlasTheme.onSurfaceVariant)
                                            .padding(.top, 16)
                                            .fixedSize(horizontal: false, vertical: true)
                                        if !slides[index].note.isEmpty {
                                            Text(slides[index].note)
                                                .font(.footnote)
                                                .foregroundStyle(KlasTheme.onSurfaceVariant)
                                                .padding(.top, 12)
                                                .fixedSize(horizontal: false, vertical: true)
                                        }
                                    }
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .padding(.horizontal, copyInset)
                                }
                                .frame(minHeight: pageGeometry.size.height, alignment: .bottom)
                            }
                        }
                        .tag(index)
                    }
                }
                .tabViewStyle(.page(indexDisplayMode: .never))
                .task(id: page) {
                    guard page < slides.count - 1 else { return }
                    try? await Task.sleep(nanoseconds: 5_000_000_000)
                    guard !Task.isCancelled else { return }
                    withAnimation(.easeInOut(duration: 0.35)) { page += 1 }
                }

                HStack {
                    HStack(spacing: 8) {
                        ForEach(slides.indices, id: \.self) { index in
                            Capsule()
                                .fill(index == page ? KlasTheme.onBackground : KlasTheme.outline.opacity(0.45))
                                .frame(width: index == page ? 22 : 8, height: 8)
                        }
                    }
                    .accessibilityLabel("\(page + 1)/\(slides.count) 페이지")
                    Spacer()
                    Button(action: onStartClick) {
                        HStack(spacing: 8) {
                            Text("로그인")
                            Image(systemName: "arrow.right")
                        }
                    }
                        .font(.body.weight(.semibold))
                        .foregroundStyle(KlasTheme.onPrimary)
                        .padding(.horizontal, 24)
                        .frame(height: 50)
                        .background(KlasTheme.primary, in: Capsule())
                        .accessibilityIdentifier("login_start")
                }
                .padding(.horizontal, copyInset)
            }
            .padding(.horizontal, horizontalPadding)
            .padding(.vertical, 24)
            .frame(maxWidth: 680)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
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
                HStack(alignment: .top, spacing: 64) {
                    LoginHeader(passwordVisible: state.passwordFieldVisible)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    LoginFields(
                        state: $state,
                        focusedField: $focusedField,
                        onLoginClick: onLoginClick,
                        onOpenURL: onOpenURL,
                        showSubmitActions: true
                    )
                    .frame(maxWidth: 520, alignment: .top)
                    .frame(maxWidth: .infinity, alignment: .top)
                }
                .padding(.horizontal, 64)
                .padding(.vertical, 32)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
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

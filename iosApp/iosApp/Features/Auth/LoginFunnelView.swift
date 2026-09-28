import SwiftUI
import UIKit

enum LoginFunnelStep: Int, Equatable {
    case studentId = 1
    case password
    case authenticating
    case library
    case agreements
    case complete

    var progress: Int {
        switch self {
        case .studentId: 1
        case .password, .authenticating: 2
        case .library: 3
        case .agreements: 4
        case .complete: 5
        }
    }
}

struct LoginFunnelView: View {
    @Binding var state: LoginUiState
    var onContinue: () -> Void
    var onBack: () -> Void
    var onSaveLibrary: () -> Void
    var onSkipLibrary: () -> Void
    var onOpenURL: (URL) -> Void
    var onFinish: () -> Void
    var onStudentIdChange: (String) -> Void
    var canReturnToOnboarding: Bool = false

    @State private var showPassword = false
    @State private var completionScale: CGFloat = 0.6
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @FocusState private var focusedField: Field?

    private enum Field { case studentId, password, libraryPassword, libraryPhone }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                if canNavigateBack {
                    backButton
                } else {
                    Color.clear.frame(width: 40, height: 40)
                }
                Spacer()
                Text("\(state.step.progress) / 5")
                    .font(.subheadline.monospacedDigit())
                    .foregroundStyle(KlasTheme.onSurfaceVariant)
            }
            ProgressView(value: Double(state.step.progress), total: 5)
                .tint(KlasTheme.primary)
                .padding(.top, 8)

            if state.step == .complete {
                completeContent
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ScrollView {
                    VStack(alignment: .leading, spacing: 0) {
                        Spacer(minLength: 32)
                        Group {
                            switch state.step {
                            case .studentId: studentIdContent
                            case .password: passwordContent
                            case .authenticating: authenticatingContent
                            case .library: libraryContent
                            case .agreements: agreementsContent
                            case .complete: EmptyView()
                            }
                        }
                        .id(state.step)
                        .transition(.opacity.combined(with: .move(edge: .trailing)))
                    }
                    .padding(.horizontal, 4)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.bottom, 40)
                }
                .scrollDismissesKeyboard(.interactively)
            }

            if state.step != .authenticating {
                primaryAction
                    .buttonStyle(KlasInverseButtonStyle(enabled: canContinue))
                if state.step == .library {
                    Button("지금은 건너뛰기", action: onSkipLibrary)
                        .font(.subheadline)
                        .padding(.top, 16)
                        .frame(maxWidth: .infinity)
                        .accessibilityIdentifier("funnel_skip_library")
                }
            }
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 16)
        .frame(maxWidth: 600)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(KlasTheme.background)
        .foregroundStyle(KlasTheme.onBackground)
        .animation(.smooth(duration: 0.32), value: state.step)
        .simultaneousGesture(
            DragGesture(minimumDistance: 24).onEnded { value in
                let horizontal = value.translation.width
                if canNavigateBack && value.startLocation.x <= 36 &&
                    horizontal > 80 && horizontal > abs(value.translation.height) * 1.5 {
                    onBack()
                }
            }
        )
        .task(id: state.step) {
            try? await Task.sleep(nanoseconds: 180_000_000)
            guard !Task.isCancelled else { return }
            switch state.step {
            case .studentId: focusedField = .studentId
            case .password: focusedField = .password
            default: focusedField = nil
            }
        }
        .accessibilityIdentifier("login_form")
    }

    @ViewBuilder
    private var backButton: some View {
        if #available(iOS 26.0, *) {
            Button(action: onBack) {
                Image(systemName: "chevron.left")
                    .font(.subheadline.weight(.semibold))
                    .frame(width: 30, height: 30)
            }
            .buttonStyle(.glass)
            .buttonBorderShape(.circle)
            .controlSize(.small)
            .accessibilityLabel("이전 단계")
        } else {
            Button(action: onBack) {
                Image(systemName: "chevron.left")
                    .font(.subheadline.weight(.semibold))
                    .frame(width: 40, height: 40)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("이전 단계")
        }
    }

    private var primaryAction: some View {
        Button(action: state.step == .library ? onSaveLibrary : state.step == .complete ? onFinish : onContinue) {
            Text(primaryTitle)
        }
        .tint(KlasTheme.primary)
        .disabled(!canContinue)
        .accessibilityIdentifier("login_submit")
    }

    private var studentIdContent: some View {
        VStack(alignment: .leading, spacing: 12) {
            heading("학번을 입력해주세요.", "ⓘ 입력한 정보는 학교 공식 KLAS 서버로만 전송되며, KLAS+ 서버에는 별도로 저장되지 않습니다.")
            KlasOutlinedTextField(
                label: "학번",
                text: Binding(get: { state.studentId }, set: onStudentIdChange),
                focusedField: $focusedField, field: .studentId,
                keyboardType: .numberPad, textContentType: .username,
                accessibilityId: "login_student_id"
            )
            Button("학번이 생각나지 않나요?") { onOpenURL(KlasTheme.findIdURL) }
                .font(.subheadline)
                .frame(height: 40)
                .padding(.horizontal, 8)
                .buttonStyle(KlasTextLinkButtonStyle())
        }
    }

    private var passwordContent: some View {
        VStack(alignment: .leading, spacing: 12) {
            heading("KLAS 비밀번호를 입력해주세요.", "ⓘ 입력한 정보는 학교 공식 KLAS 서버로만 전송되며, KLAS+ 서버에는 별도로 저장되지 않습니다.")
            KlasOutlinedTextField(
                label: "비밀번호", text: $state.password,
                focusedField: $focusedField, field: .password,
                textContentType: .password, isSecure: !showPassword,
                submitLabel: .done, accessibilityId: "login_password",
                onSubmit: { if canContinue { onContinue() } }
            ) {
                Button { showPassword.toggle() } label: {
                    Image(systemName: showPassword ? "eye.slash" : "eye")
                        .foregroundStyle(KlasTheme.onSurfaceVariant)
                }
                .accessibilityLabel(showPassword ? "비밀번호 숨기기" : "비밀번호 표시")
            }
            Button("비밀번호를 잊어버렸나요?") { onOpenURL(KlasTheme.findPasswordURL) }
                .font(.subheadline)
                .frame(height: 40)
                .padding(.horizontal, 8)
                .buttonStyle(KlasTextLinkButtonStyle())
            if let error = state.error {
                Text(error).foregroundStyle(.red).font(.footnote)
            }
        }
    }

    private var authenticatingContent: some View {
        VStack(alignment: .leading, spacing: 24) {
            ProgressView().controlSize(.large)
            heading("KLAS에 로그인하고 있어요", "잠시만 기다려 주세요.")
        }
    }

    private var libraryContent: some View {
        VStack(alignment: .leading, spacing: 8) {
            heading("도서관 출입증도 설정해보세요.", "중앙도서관 출입증 정보를 설정해놓으면, 홈 화면 위젯에서 QR을 바로 열 수 있어요. 지금 건너뛰어도 다시 설정할 수 있어요.")
            KlasOutlinedTextField(label: "학번", text: .constant(state.studentId),
                focusedField: $focusedField, field: .studentId)
                .disabled(true)
            KlasOutlinedTextField(label: "중앙도서관 비밀번호", text: $state.libraryPassword,
                focusedField: $focusedField, field: .libraryPassword,
                textContentType: .password, isSecure: true, submitLabel: .next,
                accessibilityId: "library_password",
                onSubmit: { focusedField = .libraryPhone })
            KlasOutlinedTextField(label: "전화번호", text: Binding(
                get: { state.libraryPhone }, set: { state.libraryPhone = $0.filter { ("0"..."9").contains($0) } }
            ), focusedField: $focusedField, field: .libraryPhone,
                keyboardType: .phonePad, textContentType: .telephoneNumber,
                submitLabel: .done, accessibilityId: "library_phone",
                onSubmit: { focusedField = nil })
            if let error = state.error {
                Text(error).foregroundStyle(.red).font(.footnote)
            }
        }
    }

    private var agreementsContent: some View {
        VStack(alignment: .leading, spacing: 20) {
            heading("마지막으로 약관에 동의해주세요.", "KLAS+를 사용하기 위해 아래 약관에 필수로 동의해야 해요.\n개인정보는 자체 서버에 저장되지 않고 안전하게 처리되며, 서비스 개선을 위해 사용 데이터가 수집돼요.")
            HStack(spacing: 12) {
                Toggle("[필수] 개인정보 처리방침 확인 및 동의", isOn: $state.privacyAccepted)
                    .toggleStyle(KlasCheckboxToggleStyle())
                    .font(.footnote)
                    .foregroundStyle(KlasTheme.onBackground)
                    .accessibilityIdentifier("login_agreement")
                Button("보기") { onOpenURL(KlasTheme.agreementURL) }
                    .font(.footnote)
            }
            HStack(spacing: 12) {
                Toggle("[필수] 이용약관 동의", isOn: $state.termsAccepted)
                    .toggleStyle(KlasCheckboxToggleStyle())
                    .font(.footnote)
                    .foregroundStyle(KlasTheme.onBackground)
                    .accessibilityIdentifier("login_terms_agreement")
                Button("보기") { onOpenURL(KlasTheme.termsURL) }
                    .font(.footnote)
            }
        }
    }

    private var completeContent: some View {
        VStack(spacing: 24) {
            Button(action: celebrateCompletion) {
                Image(systemName: "checkmark.circle.fill")
                    .font(.system(size: 80))
                    .foregroundStyle(KlasTheme.primary)
                    .scaleEffect(completionScale)
                    .frame(width: 96, height: 96)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("완료 축하 애니메이션 다시 재생")
            VStack(spacing: 8) {
                Text("모두 완료되었어요!")
                    .font(.title3.bold()).foregroundStyle(KlasTheme.onBackground)
                Text("이제 KLAS+를 사용할 준비가 끝났어요.")
                    .font(.footnote).foregroundStyle(KlasTheme.onSurfaceVariant)
            }
            .multilineTextAlignment(.center)
        }
        .onAppear(perform: celebrateCompletion)
    }

    private func celebrateCompletion() {
        UINotificationFeedbackGenerator().notificationOccurred(.success)
        guard !reduceMotion else { completionScale = 1; return }
        completionScale = 0.6
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 40_000_000)
            withAnimation(.spring(response: 0.5, dampingFraction: 0.45)) {
                completionScale = 1
            }
        }
    }

    private func heading(_ title: String, _ description: String) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title).font(.title3.bold()).foregroundStyle(KlasTheme.onBackground)
            Text(description).font(.footnote).foregroundStyle(KlasTheme.onSurfaceVariant)
        }
        .padding(.bottom, 12)
    }

    private var canContinue: Bool {
        switch state.step {
        case .studentId: state.studentId.count == LoginUiState.studentIdLength
        case .password: !state.password.isEmpty
        case .library: !state.libraryPassword.isEmpty && !state.libraryPhone.isEmpty
        case .agreements: true
        case .complete: true
        case .authenticating: false
        }
    }

    private var canNavigateBack: Bool {
        (state.step == .studentId && canReturnToOnboarding) ||
            state.step == .password || state.step == .agreements || state.step == .complete
    }

    private var primaryTitle: String {
        switch state.step {
        case .studentId: "다음"
        case .password: "로그인하기"
        case .library: "출입증 저장하기"
        case .agreements: "모두 동의하고 계속"
        case .complete: "홈으로 가기"
        case .authenticating: "다음"
        }
    }
}

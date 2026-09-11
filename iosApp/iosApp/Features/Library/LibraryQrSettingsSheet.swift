import SwiftUI

struct LibraryQrSettingsUiState: Equatable {
    var studentNumber: String
    var password: String
    var phone: String

    var canSave: Bool {
        !studentNumber.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            && !password.isEmpty
            && !phone.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }
}

struct LibraryQrSettingsSheet: View {
    @Binding var state: LibraryQrSettingsUiState
    var onSave: () -> Void

    @State private var passwordVisible = false
    @FocusState private var focusedField: Field?
    @ScaledMetric(relativeTo: .body) private var trailingMinHeight: CGFloat = 40

    private enum Field {
        case studentNumber, password, phone
    }

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 10) {
                    Text("중앙도서관 출입증 설정")
                        .font(.title2.weight(.bold))
                        .foregroundStyle(KlasTheme.onSurface)
                        .padding(.bottom, 14)
                    KlasOutlinedTextField(
                        label: "학번",
                        text: $state.studentNumber,
                        focusedField: $focusedField,
                        field: .studentNumber,
                        keyboardType: .numberPad,
                        submitLabel: .next,
                        accessibilityId: "library_qr_settings_student",
                        onSubmit: { moveFocus(to: .password) }
                    )
                    .onChange(of: state.studentNumber) { value in
                        let filtered = value.filter(\.isNumber)
                        if filtered != value { state.studentNumber = filtered }
                    }
                    KlasOutlinedTextField(
                        label: "중앙도서관 비밀번호",
                        text: $state.password,
                        focusedField: $focusedField,
                        field: .password,
                        textContentType: .password,
                        isSecure: !passwordVisible,
                        submitLabel: .next,
                        accessibilityId: "library_qr_settings_password",
                        onSubmit: { moveFocus(to: .phone) }
                    ) {
                        Button(passwordVisible ? "숨김" : "표시") {
                            passwordVisible.toggle()
                        }
                        .buttonStyle(KlasTextLinkButtonStyle())
                        .font(.subheadline)
                        .padding(.horizontal, 8)
                        .frame(minHeight: trailingMinHeight)
                        .accessibilityLabel(passwordVisible ? "비밀번호 숨기기" : "비밀번호 표시")
                    }
                    KlasOutlinedTextField(
                        label: "전화번호",
                        text: $state.phone,
                        focusedField: $focusedField,
                        field: .phone,
                        keyboardType: .phonePad,
                        textContentType: .telephoneNumber,
                        submitLabel: .done,
                        accessibilityId: "library_qr_settings_phone",
                        onSubmit: { moveFocus(to: nil) }
                    )
                }
                .frame(maxWidth: 640)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 20)
                .padding(.top, 32)
            }
            Button("저장", action: onSave)
                .buttonStyle(KlasInverseButtonStyle(enabled: state.canSave))
                .disabled(!state.canSave)
                .frame(maxWidth: 640)
                .padding(20)
                .accessibilityIdentifier("library_qr_settings_save")
        }
        .background(KlasTheme.surface)
        .accessibilityIdentifier("library_qr_settings")
    }

    private func moveFocus(to next: Field?) {
        let current = focusedField
        guard keyboardKind(current) != keyboardKind(next), current != nil, next != nil else {
            focusedField = next
            return
        }
        focusedField = nil
        Task { @MainActor in
            focusedField = next
        }
    }

    private func keyboardKind(_ field: Field?) -> UIKeyboardType? {
        switch field {
        case .studentNumber: return .numberPad
        case .password: return .default
        case .phone: return .phonePad
        case .none: return nil
        }
    }
}

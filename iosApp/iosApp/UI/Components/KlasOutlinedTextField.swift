import SwiftUI

/// Android Material3 OutlinedTextField 패리티
/// - 비어 있고 비포커스: 라벨이 필드 안 placeholder
/// - 포커스 또는 입력값 있음: 라벨이 테두리 위 floating outlined
struct KlasOutlinedTextField<Field: Hashable, Trailing: View>: View {
    let label: String
    @Binding var text: String
    var focusedField: FocusState<Field?>.Binding
    var field: Field
    var keyboardType: UIKeyboardType = .default
    var textContentType: UITextContentType?
    var isSecure: Bool = false
    var submitLabel: SubmitLabel = .next
    var accessibilityId: String?
    var onSubmit: (() -> Void)?
    @ViewBuilder var trailing: () -> Trailing
    @ScaledMetric(relativeTo: .body) private var fieldHeight: CGFloat = 56

    init(
        label: String,
        text: Binding<String>,
        focusedField: FocusState<Field?>.Binding,
        field: Field,
        keyboardType: UIKeyboardType = .default,
        textContentType: UITextContentType? = nil,
        isSecure: Bool = false,
        submitLabel: SubmitLabel = .next,
        accessibilityId: String? = nil,
        onSubmit: (() -> Void)? = nil,
        @ViewBuilder trailing: @escaping () -> Trailing
    ) {
        self.label = label
        self._text = text
        self.focusedField = focusedField
        self.field = field
        self.keyboardType = keyboardType
        self.textContentType = textContentType
        self.isSecure = isSecure
        self.submitLabel = submitLabel
        self.accessibilityId = accessibilityId
        self.onSubmit = onSubmit
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

            HStack(spacing: 0) {
                Group {
                    if isSecure {
                        SecureField("", text: $text)
                    } else {
                        TextField("", text: $text)
                    }
                }
                .keyboardType(keyboardType)
                .textContentType(textContentType)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .focused(focusedField, equals: field)
                .submitLabel(submitLabel)
                .onSubmit { onSubmit?() }
                .font(.body)
                .foregroundStyle(KlasTheme.onSurface)
                .tint(KlasTheme.primary)
                .accessibilityLabel(label)
                .accessibilityIdentifier(accessibilityId ?? "")

                trailing()
            }
            .padding(.leading, 16)
            .padding(.trailing, 4)

            Text(label)
                .font(isFloating ? .caption : .body)
                .foregroundStyle(labelColor)
                .padding(.horizontal, isFloating ? 4 : 0)
                .background {
                    if isFloating {
                        KlasTheme.surface
                            .padding(.horizontal, -2)
                    }
                }
                .offset(x: isFloating ? 12 : 16, y: isFloating ? -fieldHeight / 2 : 0)
                .allowsHitTesting(false)
        }
        .frame(height: fieldHeight)
        .fixedSize(horizontal: false, vertical: true)
        .padding(.top, 8)
        .animation(.easeOut(duration: 0.15), value: isFloating)
        .animation(.easeOut(duration: 0.15), value: isFocused)
    }

    private var borderColor: Color {
        isFocused ? KlasTheme.primary : KlasTheme.outline
    }

    private var labelColor: Color {
        if isFocused { return KlasTheme.primary }
        return KlasTheme.onSurfaceVariant.opacity(isFloating ? 1 : 0.8)
    }
}

extension KlasOutlinedTextField where Trailing == EmptyView {
    init(
        label: String,
        text: Binding<String>,
        focusedField: FocusState<Field?>.Binding,
        field: Field,
        keyboardType: UIKeyboardType = .default,
        textContentType: UITextContentType? = nil,
        isSecure: Bool = false,
        submitLabel: SubmitLabel = .next,
        accessibilityId: String? = nil,
        onSubmit: (() -> Void)? = nil
    ) {
        self.init(
            label: label,
            text: text,
            focusedField: focusedField,
            field: field,
            keyboardType: keyboardType,
            textContentType: textContentType,
            isSecure: isSecure,
            submitLabel: submitLabel,
            accessibilityId: accessibilityId,
            onSubmit: onSubmit,
            trailing: { EmptyView() }
        )
    }
}

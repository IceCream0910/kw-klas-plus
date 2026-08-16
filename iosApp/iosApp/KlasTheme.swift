import SwiftUI

enum KlasTheme {
    static let buttonHeight: CGFloat = 50
    static let controlCornerRadius: CGFloat = 16

    // Android values/colors.xml (light) 패리티
    static let primary = Color(hex: 0x8F4953)
    static let onPrimary = Color.white
    static let background = Color(hex: 0xFFF8F7)
    static let onBackground = Color(hex: 0x22191A)
    static let surface = Color(hex: 0xFFF8F7)
    static let onSurface = Color(hex: 0x22191A)
    static let surfaceVariant = Color(hex: 0xF4DDDE)
    static let onSurfaceVariant = Color(hex: 0x524344)
    static let outline = Color(hex: 0x847374)
    static let inversePrimary = Color(hex: 0xFFB2BA)
    static let inverseButtonContent = Color(hex: 0x561D27)
    static let secondaryContainer = Color(hex: 0xFFD9DC)
    static let surfaceContainerLow = Color(hex: 0xFFF0F0)
    static let surfaceContainerHigh = Color(hex: 0xF6E4E5)
    static let scrim = Color.black

    // Material3 Button disabled: onSurface @ 12% / 38%
    static let disabledContainer = onSurface.opacity(0.12)
    static let disabledContent = onSurface.opacity(0.38)

    static let agreementURL = URL(string: "https://blog.yuntae.in/11cfc9b9-3eca-8078-96a0-c41c4ca9cb8f")!
    static let findIdURL = URL(string: "https://klas.kw.ac.kr/usr/cmn/login/modal/UserFindMemberNoPage.do")!
    static let findPasswordURL = URL(string: "https://klas.kw.ac.kr/usr/cmn/login/modal/UserFindPwdPage.do")!
    static let registerURL = URL(string: "https://klas.kw.ac.kr/usr/cmn/login/modal/UserFrstModPwdPage.do")!
}

enum AppWindowWidthClass {
    case compact, medium, expanded

    static func classify(width: CGFloat) -> AppWindowWidthClass {
        if width < 600 { return .compact }
        if width < 840 { return .medium }
        return .expanded
    }
}

extension Color {
    init(hex: UInt32, opacity: Double = 1) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: opacity
        )
    }
}

struct KlasInverseButtonStyle: ButtonStyle {
    var enabled: Bool = true

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.body.weight(.semibold))
            .foregroundStyle(enabled ? KlasTheme.inverseButtonContent : KlasTheme.disabledContent)
            .frame(maxWidth: .infinity)
            .frame(height: KlasTheme.buttonHeight)
            .background(
                RoundedRectangle(cornerRadius: KlasTheme.controlCornerRadius, style: .continuous)
                    .fill(enabled ? KlasTheme.inversePrimary : KlasTheme.disabledContainer)
            )
            .opacity(configuration.isPressed && enabled ? 0.85 : 1)
    }
}

struct KlasTextLinkButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .foregroundStyle(KlasTheme.primary)
            // Material3 TextButton: CircleShape + pressed state layer (~primary 10~12%)
            .background(
                Capsule(style: .continuous)
                    .fill(configuration.isPressed ? KlasTheme.primary.opacity(0.12) : Color.clear)
            )
            .animation(.easeOut(duration: 0.08), value: configuration.isPressed)
    }
}

/// Material clickable / 라벨용 — 텍스트 색은 유지하고 pressed highlight만 표시
struct KlasPressHighlightButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .background(
                RoundedRectangle(cornerRadius: 8, style: .continuous)
                    .fill(configuration.isPressed ? KlasTheme.primary.opacity(0.12) : Color.clear)
            )
            .animation(.easeOut(duration: 0.08), value: configuration.isPressed)
    }
}

/// iOS에는 체크박스 UI가 없어서 접근성을 위해 Toggle을 커스텀한다.
struct KlasCheckboxToggleStyle: ToggleStyle {
    func makeBody(configuration: Configuration) -> some View {
        HStack(alignment: .center, spacing: 8) {
            Image(systemName: configuration.isOn ? "checkmark.square.fill" : "square")
                .font(.body)
                .foregroundStyle(configuration.isOn ? Color.accentColor : KlasTheme.outline)
            configuration.label
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .contentShape(Rectangle())
        .onTapGesture {
            configuration.isOn.toggle()
        }
    }
}

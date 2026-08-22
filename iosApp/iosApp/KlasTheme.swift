import SwiftUI
import UIKit

enum KlasTheme {
    static let buttonHeight: CGFloat = 50
    static let controlCornerRadius: CGFloat = 16

    // Android values/colors.xml · values-night/colors.xml 패리티
    static let primary = Color(light: 0x8F4953, dark: 0xFFB2BA)
    static let onPrimary = Color(light: 0xFFFFFF, dark: 0x561D27)
    static let background = Color(light: 0xFFF8F7, dark: 0x211E1E)
    static let onBackground = Color(light: 0x22191A, dark: 0xF0DEDF)
    static let surface = Color(light: 0xFFF8F7, dark: 0x211E1E)
    static let onSurface = Color(light: 0x22191A, dark: 0xF0DEDF)
    static let surfaceVariant = Color(light: 0xF4DDDE, dark: 0x524344)
    static let onSurfaceVariant = Color(light: 0x524344, dark: 0xD7C1C3)
    static let outline = Color(light: 0x847374, dark: 0x9F8C8D)
    static let inversePrimary = Color(light: 0xFFB2BA, dark: 0x8F4953)
    static let inverseButtonContent = Color(light: 0x561D27, dark: 0xFFFFFF)
    static let secondaryContainer = Color(light: 0xFFD9DC, dark: 0x5C3F42)
    static let surfaceContainerLow = Color(light: 0xFFF0F0, dark: 0x22191A)
    static let surfaceContainerHigh = Color(light: 0xF6E4E5, dark: 0x312828)
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

    init(light: UInt32, dark: UInt32) {
        self.init(
            uiColor: UIColor { traits in
                UIColor(hex: traits.userInterfaceStyle == .dark ? dark : light)
            }
        )
    }
}

private extension UIColor {
    convenience init(hex: UInt32) {
        self.init(
            red: CGFloat((hex >> 16) & 0xFF) / 255,
            green: CGFloat((hex >> 8) & 0xFF) / 255,
            blue: CGFloat(hex & 0xFF) / 255,
            alpha: 1
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

struct KlasSelectionRowButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 14, weight: .medium))
            .foregroundStyle(KlasTheme.onSurfaceVariant)
            .multilineTextAlignment(.leading)
            .frame(maxWidth: .infinity, alignment: .leading)
            .frame(height: KlasTheme.buttonHeight)
            .background(
                RoundedRectangle(cornerRadius: KlasTheme.controlCornerRadius, style: .continuous)
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

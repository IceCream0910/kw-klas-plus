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

/// Material3 `CircularProgressIndicator()` 패리티
/// - 크기 40dp, stroke 4dp, primary 활성 호 + secondaryContainer 트랙
struct KlasCircularProgressView: View {
    var size: CGFloat = 40
    var lineWidth: CGFloat = 4

    var body: some View {
        TimelineView(.animation) { context in
            let motion = Self.motion(at: context.date.timeIntervalSinceReferenceDate)
            ZStack {
                Circle()
                    .stroke(KlasTheme.secondaryContainer, lineWidth: lineWidth)
                Circle()
                    .trim(from: 0, to: motion.sweep)
                    .stroke(
                        KlasTheme.primary,
                        style: StrokeStyle(lineWidth: lineWidth, lineCap: .butt)
                    )
                    .rotationEffect(.degrees(motion.rotation))
            }
            .frame(width: size, height: size)
        }
        .accessibilityLabel("로딩 중")
    }

    /// Android CircularProgressIndicator 비결정 애니메이션(1332ms, FastOutSlowIn)
    private static func motion(at time: TimeInterval) -> (rotation: Double, sweep: CGFloat) {
        let duration = 1.332
        let jump = 290.0
        let base = 286.0
        let p = time.truncatingRemainder(dividingBy: duration) / duration
        let headDuration = 0.5
        let tailDelay = 0.25

        let end = jump * fastOutSlowIn(min(1, p / headDuration))
        let start = jump * fastOutSlowIn(min(1, max(0, p - tailDelay) / (1 - tailDelay)))
        let sweepAngle = max(end - start, 12)
        let rotation = -90 + (p * base) + start
        return (rotation, CGFloat(sweepAngle / 360))
    }

    private static func fastOutSlowIn(_ t: Double) -> Double {
        let x = min(max(t, 0), 1)
        return x * x * (3 - 2 * x)
    }
}

/// Material IconToggleButton pressed state layer (원형)
struct KlasIconToggleButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .background(
                Circle()
                    .fill(configuration.isPressed ? KlasTheme.primary.opacity(0.12) : Color.clear)
            )
            .animation(.easeOut(duration: 0.08), value: configuration.isPressed)
    }
}

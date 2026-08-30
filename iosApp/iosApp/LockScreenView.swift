import SwiftUI

struct LockScreenView: View {
    @ObservedObject var controller: AppLockController

    var body: some View {
        NavigationStack {
            GeometryReader { proxy in
                let widthClass = AppWindowWidthClass.classify(width: proxy.size.width)
                let useTwoPane = LockScreenMetrics.useTwoPane(
                    width: proxy.size.width,
                    height: proxy.size.height
                )
                let keyHeight = keypadKeyHeight(
                    availableHeight: proxy.size.height,
                    twoPane: useTwoPane
                )

                Group {
                    if useTwoPane {
                        HStack(spacing: 48) {
                            lockSummary
                                .frame(maxWidth: .infinity, maxHeight: .infinity)
                            lockKeypad(keyHeight: keyHeight)
                                .frame(maxWidth: 440)
                                .frame(maxWidth: .infinity)
                        }
                    } else {
                        VStack(spacing: 24) {
                            lockSummary
                                .frame(maxWidth: .infinity, maxHeight: .infinity)
                            lockKeypad(keyHeight: keyHeight)
                                .frame(maxWidth: 440)
                                .frame(maxWidth: .infinity)
                        }
                    }
                }
                .padding(.horizontal, horizontalPadding(widthClass))
                .padding(.vertical, useTwoPane ? 16 : 24)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
            .background(KlasTheme.background.ignoresSafeArea())
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                if controller.canCancel {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("취소") { controller.cancel() }
                    }
                }
            }
            .toolbar(controller.canCancel ? .visible : .hidden, for: .navigationBar)
        }
        .interactiveDismissDisabled()
        .overlay(alignment: .bottom) { toast }
        .onAppear { controller.promptUnlockBiometricIfNeeded() }
        .accessibilityIdentifier("lock_screen")
    }

    private var lockSummary: some View {
        VStack(spacing: 8) {
            Text(controller.title)
                .font(.title2.weight(.bold))
                .multilineTextAlignment(.center)
                .foregroundStyle(KlasTheme.onBackground)
            Text(controller.description)
                .font(.body)
                .multilineTextAlignment(.center)
                .foregroundStyle(KlasTheme.onSurfaceVariant)
            HStack(spacing: 16) {
                ForEach(0..<LockScreenMetrics.pinLength, id: \.self) { index in
                    Circle()
                        .fill(
                            index < controller.enteredDigits
                                ? KlasTheme.primary
                                : KlasTheme.outline.opacity(0.35)
                        )
                        .frame(width: 12, height: 12)
                }
            }
            .padding(.top, 24)
            .accessibilityIdentifier("pin_indicators")
            if controller.biometricVisible {
                Button("생체인식 사용") {
                    controller.requestUnlockBiometric()
                }
                .padding(.top, 8)
                .accessibilityIdentifier("biometric_button")
            }
        }
    }

    private func lockKeypad(keyHeight: CGFloat) -> some View {
        VStack(spacing: 4) {
            ForEach([[1, 2, 3], [4, 5, 6], [7, 8, 9]], id: \.first) { row in
                HStack(spacing: 4) {
                    ForEach(row, id: \.self) { number in
                        keypadButton(label: String(number), height: keyHeight) {
                            controller.appendDigit(number)
                        }
                        .accessibilityIdentifier("pin_\(number)")
                    }
                }
            }
            HStack(spacing: 4) {
                Button(action: controller.deleteDigit) {
                    Image(systemName: "delete.backward")
                        .font(.title2)
                        .frame(maxWidth: .infinity, minHeight: keyHeight, maxHeight: keyHeight)
                }
                .foregroundStyle(KlasTheme.onBackground)
                .accessibilityIdentifier("pin_delete")
                keypadButton(label: "0", height: keyHeight) {
                    controller.appendDigit(0)
                }
                .accessibilityIdentifier("pin_0")
                Color.clear
                    .frame(maxWidth: .infinity, minHeight: keyHeight, maxHeight: keyHeight)
            }
            Text("비밀번호를 잊어버린 경우 앱을 재설치해야 해요.")
                .font(.caption)
                .foregroundStyle(KlasTheme.outline)
                .multilineTextAlignment(.center)
                .padding(.top, 8)
        }
        .fixedSize(horizontal: false, vertical: true)
        .accessibilityIdentifier("pin_keypad")
    }

    private func keypadButton(label: String, height: CGFloat, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label)
                .font(.title2)
                .frame(maxWidth: .infinity, minHeight: height, maxHeight: height)
        }
        .foregroundStyle(KlasTheme.onBackground)
    }

    private func horizontalPadding(_ widthClass: AppWindowWidthClass) -> CGFloat {
        switch widthClass {
        case .compact: return 20
        case .medium: return 40
        case .expanded: return 64
        }
    }

    private func keypadKeyHeight(availableHeight: CGFloat, twoPane: Bool) -> CGFloat {
        let captionAndGaps: CGFloat = 56
        let summaryReserve: CGFloat = twoPane ? 0 : 140
        let rows: CGFloat = 4
        let rowSpacing: CGFloat = 12
        let leftover = availableHeight - captionAndGaps - summaryReserve - rowSpacing
        return min(64, max(44, leftover / rows))
    }

    @ViewBuilder
    private var toast: some View {
        if let toast = controller.toastMessage {
            Text(toast)
                .font(.subheadline)
                .foregroundStyle(KlasTheme.onSurface)
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
                .background(
                    Capsule(style: .continuous)
                        .fill(KlasTheme.surfaceContainerHigh)
                )
                .padding(.bottom, 32)
                .allowsHitTesting(false)
                .accessibilityIdentifier("lock_toast")
        }
    }
}

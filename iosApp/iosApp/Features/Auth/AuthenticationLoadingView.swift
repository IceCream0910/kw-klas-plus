import SwiftUI

struct AuthenticationLoadingView: View {
    var message: String

    var body: some View {
        ZStack {
            KlasTheme.background.ignoresSafeArea()
            VStack(spacing: 12) {
                ProgressView()
                    .controlSize(.large)
                    .tint(KlasTheme.primary)
                Text(message)
                    .font(.body)
                    .foregroundStyle(KlasTheme.onBackground)
                    .frame(maxWidth: 480)
                    .multilineTextAlignment(.center)
            }
            .accessibilityElement(children: .combine)
            .accessibilityIdentifier("authentication_loading")
        }
    }
}

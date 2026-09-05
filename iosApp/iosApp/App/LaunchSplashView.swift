import SwiftUI

/// Android 12+ SplashScreen 패리티 (아이콘 표시 크기는 제품 조정값)
struct LaunchSplashView: View {
    private static let splashIconDiameter: CGFloat = 160

    var body: some View {
        ZStack {
            Color.white.ignoresSafeArea()
            Image("SplashLogo")
                .resizable()
                .scaledToFit()
                .frame(
                    width: Self.splashIconDiameter,
                    height: Self.splashIconDiameter
                )
                .accessibilityLabel("KLAS+")
                .accessibilityIdentifier("launch_splash_logo")
        }
    }
}

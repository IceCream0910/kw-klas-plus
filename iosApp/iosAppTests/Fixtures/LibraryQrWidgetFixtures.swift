import Foundation

public enum LibraryQrRouteDecision: Equatable {
    /// 앱 잠금 여부와 무관하게 도서관 QR 바텀시트 즉시 오픈 (단독 예외)
    case openQrDirectly(isAppLockBypassed: Bool)
    /// 설정 미완료: 설정 안내 후 앱 잠금 해제를 거쳐 설정 화면으로 이동
    case requireUnlockThenOpenSettings
}

/// 화면 수명주기에 따른 메모리 및 밝기 상태 모델
public struct LibraryQrSessionState: Equatable {
    public var screenBrightness: Double
    public var qrData: String?
    public var userName: String?
    public var userCode: String?
    public var isSheetVisible: Bool
    public var isAppLocked: Bool

    public init(
        screenBrightness: Double = 0.5,
        qrData: String? = nil,
        userName: String? = nil,
        userCode: String? = nil,
        isSheetVisible: Bool = false,
        isAppLocked: Bool = false
    ) {
        self.screenBrightness = screenBrightness
        self.qrData = qrData
        self.userName = userName
        self.userCode = userCode
        self.isSheetVisible = isSheetVisible
        self.isAppLocked = isAppLocked
    }

    /// 바텀시트 팝업 진입: 밝기 1.0 설정 및 데이터 로드
    public mutating func enterSheet(qrData: String, userName: String, userCode: String) {
        self.screenBrightness = 1.0
        self.qrData = qrData
        self.userName = userName
        self.userCode = userCode
        self.isSheetVisible = true
    }

    /// 바텀시트 닫힘 또는 백그라운드 전환: 개인정보/QR 메모리 파기 및 원래 밝기 복원, 전체 잠금 유지
    public mutating func dismissSheet(originalBrightness: Double) {
        self.screenBrightness = originalBrightness
        self.qrData = nil
        self.userName = nil
        self.userCode = nil
        self.isSheetVisible = false
    }
}

/// 위젯 및 도서관 출입증 정책 검증을 위한 Fixture 데이터셋
public enum LibraryQrWidgetFixtures {
    public static let deepLinkQr = URL(string: "kwklasplus://library-qr")!
    public static let deepLinkSettings = URL(string: "kwklasplus://library-qr/settings")!

    /// 라우팅 결정 정책 함수 (순수 함수)
    public static func resolveRoute(
        url: URL,
        hasConfiguredCredentials: Bool,
        isAppLockActive: Bool
    ) -> LibraryQrRouteDecision? {
        guard url.scheme == "kwklasplus", url.host == "library-qr" else { return nil }

        if url.path == "/settings" {
            return .requireUnlockThenOpenSettings
        }

        if url.path.isEmpty {
            if hasConfiguredCredentials {
                // 설정되어 있으면 앱 잠금이 켜져 있어도 잠금 예외로 즉시 팝업
                return .openQrDirectly(isAppLockBypassed: isAppLockActive)
            } else {
                // 미설정 시에는 인증 없이 설정 화면을 열지 않고, 안내 후 잠금 해제 요구
                return .requireUnlockThenOpenSettings
            }
        }

        return nil
    }
}

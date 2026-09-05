import LocalAuthentication
import Shared

enum IosBiometricAvailability {
    static func canAuthenticate() -> Bool {
        var error: NSError?
        return LAContext().canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error)
    }

    static func errorMessage() -> String? {
        let context = LAContext()
        var error: NSError?
        if context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error) {
            return nil
        }
        switch LAError.Code(rawValue: error?.code ?? 0) {
        case .biometryNotAvailable:
            return "이 기기는 생체 인증을 지원하지 않아요."
        case .biometryNotEnrolled:
            return "등록된 생체 정보가 없습니다. 기기 설정에서 생체 정보를 등록해주세요."
        default:
            return "현재 생체 인증을 사용할 수 없어요."
        }
    }
}

struct IosBiometrics {
    func authenticate(
        purpose: BiometricPurpose,
        localizedReason: String? = nil
    ) async -> PlatformActionResult {
        let context = LAContext()
        var error: NSError?
        guard context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error) else {
            return Self.mapAvailability(error)
        }
        do {
            let success = try await context.evaluatePolicy(
                .deviceOwnerAuthenticationWithBiometrics,
                localizedReason: localizedReason ?? Self.reason(for: purpose)
            )
            return success
                ? PlatformActionResultSuccess()
                : PlatformActionResultFailed(reason: "biometric_authentication_failed")
        } catch let authError as LAError {
            return Self.mapAuth(authError)
        } catch {
            return PlatformActionResultFailed(reason: "biometric_authentication_failed")
        }
    }

    private static func reason(for purpose: BiometricPurpose) -> String {
        switch purpose {
        case .unlockApp, .disableAppLock:
            return "앱 잠금 해제"
        case .enableBiometrics:
            return "생체인증 사용"
        default:
            return "생체인증"
        }
    }

    private static func mapAvailability(_ error: NSError?) -> PlatformActionResult {
        switch LAError.Code(rawValue: error?.code ?? 0) {
        case .biometryNotEnrolled, .passcodeNotSet:
            return PlatformActionResultPermissionRequired()
        case .biometryNotAvailable:
            return PlatformActionResultUnsupported()
        default:
            return PlatformActionResultFailed(reason: "biometric_unavailable")
        }
    }

    private static func mapAuth(_ error: LAError) -> PlatformActionResult {
        switch error.code {
        case .userCancel, .appCancel, .systemCancel, .userFallback:
            return PlatformActionResultCancelled()
        case .biometryNotEnrolled, .passcodeNotSet:
            return PlatformActionResultPermissionRequired()
        case .biometryNotAvailable:
            return PlatformActionResultUnsupported()
        default:
            return PlatformActionResultFailed(reason: "biometric_authentication_failed")
        }
    }
}

import AVKit
import Foundation
import ObjectiveC

/// AVKit AVPictureInPictureController의 restore 콜백을 감지하여
/// PiP 종료 시 사용자가 복귀(Restore) 버튼을 눌렀는지, 닫기(X) 버튼을 눌렀는지 구분합니다.
final class PiPRestoreTracker {
    static let shared = PiPRestoreTracker()

    private(set) var isRestoreRequested = false
    private var swizzledClasses = Set<String>()
    private var isInstalled = false

    private init() {
        installControllerSwizzle()
    }

    func installIfNeeded() {
        // 싱글톤 초기화 시 자동 설치됨
    }

    func reset() {
        isRestoreRequested = false
    }

    func markRestoreRequested() {
        isRestoreRequested = true
    }

    private func installControllerSwizzle() {
        guard !isInstalled else { return }
        isInstalled = true

        let originalSelector = #selector(setter: AVPictureInPictureController.delegate)
        let swizzledSelector = #selector(AVPictureInPictureController.klas_setDelegate(_:))

        guard let originalMethod = class_getInstanceMethod(AVPictureInPictureController.self, originalSelector),
              let swizzledMethod = class_getInstanceMethod(AVPictureInPictureController.self, swizzledSelector) else {
            return
        }
        method_exchangeImplementations(originalMethod, swizzledMethod)
    }

    func hookDelegateClassIfNeeded(_ delegate: AVPictureInPictureControllerDelegate) {
        let targetClass: AnyClass = type(of: delegate)
        let className = NSStringFromClass(targetClass)
        guard !swizzledClasses.contains(className) else { return }
        swizzledClasses.insert(className)

        let originalSelector = NSSelectorFromString("pictureInPictureController:restoreUserInterfaceForPictureInPictureStopWithCompletionHandler:")
        guard let originalMethod = class_getInstanceMethod(targetClass, originalSelector) else {
            return
        }

        let origImp = method_getImplementation(originalMethod)
        typealias OrigFunc = @convention(c) (AnyObject, Selector, AnyObject, @escaping (Bool) -> Void) -> Void

        let block: @convention(block) (AnyObject, AnyObject, @escaping (Bool) -> Void) -> Void = { target, controller, completion in
            PiPRestoreTracker.shared.markRestoreRequested()
            let orig = unsafeBitCast(origImp, to: OrigFunc.self)
            orig(target, originalSelector, controller, completion)
        }

        let newImp = imp_implementationWithBlock(block)
        class_replaceMethod(targetClass, originalSelector, newImp, method_getTypeEncoding(originalMethod))
    }
}

extension AVPictureInPictureController {
    @objc func klas_setDelegate(_ delegate: AVPictureInPictureControllerDelegate?) {
        klas_setDelegate(delegate)
        if let delegate = delegate {
            PiPRestoreTracker.shared.hookDelegateClassIfNeeded(delegate)
        }
    }
}

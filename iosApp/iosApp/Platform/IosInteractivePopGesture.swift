import SwiftUI
import UIKit
import WebKit

enum InteractivePopDecision: Equatable {
    case ignore
    case consumeNativeBack
    case yieldToWebHistory
    case beginSystemPop
}

struct InteractivePopPolicy: Equatable {
    var isPushed: Bool
    var consumesNativeBack: Bool
    var activeWebViewCanGoBack: Bool

    var decision: InteractivePopDecision {
        guard isPushed else { return .ignore }
        if consumesNativeBack { return .consumeNativeBack }
        if activeWebViewCanGoBack { return .yieldToWebHistory }
        return .beginSystemPop
    }

    static func shouldCommitNativeBack(translationX: CGFloat, velocityX: CGFloat) -> Bool {
        translationX > 80 || velocityX > 500
    }
}

struct InteractivePopGestureInstaller: UIViewControllerRepresentable {
    var consumesNativeBack: Bool = false
    var onConsumeBack: (() -> Void)?

    func makeUIViewController(context: Context) -> Controller {
        let controller = Controller()
        controller.consumesNativeBack = consumesNativeBack
        controller.onConsumeBack = onConsumeBack
        return controller
    }

    func updateUIViewController(_ uiViewController: Controller, context: Context) {
        uiViewController.consumesNativeBack = consumesNativeBack
        uiViewController.onConsumeBack = onConsumeBack
        uiViewController.configureNav()
    }

    final class Controller: UIViewController {
        var consumesNativeBack = false
        var onConsumeBack: (() -> Void)?

        override func didMove(toParent parent: UIViewController?) {
            super.didMove(toParent: parent)
            configureNav()
        }

        override func viewDidAppear(_ animated: Bool) {
            super.viewDidAppear(animated)
            configureNav()
        }

        override func viewDidLayoutSubviews() {
            super.viewDidLayoutSubviews()
            configureNav()
        }

        func configureNav() {
            guard let nav = findNav() else { return }
            nav.interactivePopGestureRecognizer?.isEnabled = true
            nav.interactivePopGestureRecognizer?.delegate = nav
            nav.installConsumeBackRecognizerIfNeeded()
            guard nav.topInteractivePopInstaller() === self else { return }
            nav.applyWebHistoryGestures(enabled: !consumesNativeBack)
        }

        private func findNav() -> UINavigationController? {
            if let nav = navigationController ?? parent?.navigationController {
                return nav
            }
            if let root = view.window?.rootViewController ?? parent {
                if let found = search(root) { return found }
            }
            if let targetView = view.window ?? parent?.view {
                if let found = search(targetView) { return found }
            }
            return nil
        }

        private func search(_ vc: UIViewController) -> UINavigationController? {
            if let nav = vc as? UINavigationController { return nav }
            for child in vc.children {
                if let found = search(child) { return found }
            }
            return nil
        }

        private func search(_ view: UIView) -> UINavigationController? {
            var responder: UIResponder? = view
            while let r = responder {
                if let nav = r as? UINavigationController { return nav }
                responder = r.next
            }
            for subview in view.subviews {
                if let found = search(subview) { return found }
            }
            return nil
        }
    }
}

extension View {
    func interactivePopGesture(
        consumesNativeBack: Bool = false,
        onConsumeBack: (() -> Void)? = nil
    ) -> some View {
        background(
            InteractivePopGestureInstaller(
                consumesNativeBack: consumesNativeBack,
                onConsumeBack: onConsumeBack
            )
        )
    }
}

private enum InteractivePopAssociated {
    static var consumeController: UInt8 = 0
}

private final class InteractivePopConsumeController: NSObject {
    let recognizer: UIScreenEdgePanGestureRecognizer
    weak var navigationController: UINavigationController?

    init(navigationController: UINavigationController) {
        self.recognizer = UIScreenEdgePanGestureRecognizer()
        self.navigationController = navigationController
        super.init()
        recognizer.edges = .left
        recognizer.delegate = navigationController
        recognizer.addTarget(self, action: #selector(handle(_:)))
        navigationController.view.addGestureRecognizer(recognizer)
    }

    @objc func handle(_ gesture: UIScreenEdgePanGestureRecognizer) {
        guard gesture.state == .ended else { return }
        let view = gesture.view
        navigationController?.commitConsumeBackIfNeeded(
            translationX: gesture.translation(in: view).x,
            velocityX: gesture.velocity(in: view).x
        )
    }
}

extension UINavigationController: @retroactive UIGestureRecognizerDelegate {
    var consumeBackRecognizer: UIScreenEdgePanGestureRecognizer? {
        consumeController?.recognizer
    }

    func installConsumeBackRecognizerIfNeeded() {
        guard consumeController == nil else { return }
        consumeController = InteractivePopConsumeController(navigationController: self)
    }

    func commitConsumeBackIfNeeded(translationX: CGFloat, velocityX: CGFloat) {
        guard InteractivePopPolicy.shouldCommitNativeBack(translationX: translationX, velocityX: velocityX) else {
            return
        }
        topInteractivePopInstaller()?.onConsumeBack?()
    }

    public func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
        let decision = popDecision(for: gestureRecognizer)
        if gestureRecognizer === consumeBackRecognizer {
            return decision == .consumeNativeBack
        }
        guard gestureRecognizer === interactivePopGestureRecognizer else { return true }
        return decision == .beginSystemPop
    }

    func popDecision(for gestureRecognizer: UIGestureRecognizer) -> InteractivePopDecision {
        let location = gestureRecognizer.location(in: view)
        return InteractivePopPolicy(
            isPushed: viewControllers.count > 1,
            consumesNativeBack: topInteractivePopInstaller()?.consumesNativeBack ?? false,
            activeWebViewCanGoBack: activeWebView(at: location)?.canGoBack ?? false
        ).decision
    }

    func topInteractivePopInstaller() -> InteractivePopGestureInstaller.Controller? {
        guard let top = topViewController else { return nil }
        return innermostInstaller(in: top)
    }

    func applyWebHistoryGestures(enabled: Bool) {
        topViewController?.view.forEachDescendant(ofType: WKWebView.self) { webView in
            webView.allowsBackForwardNavigationGestures = enabled
        }
    }

    func activeWebView(at location: CGPoint) -> WKWebView? {
        guard let topView = topViewController?.view else { return nil }
        let pointInTopView = view.convert(location, to: topView)
        guard let hitView = topView.hitTest(pointInTopView, with: nil) else { return nil }
        return hitView.findAncestor(ofType: WKWebView.self)
    }

    private var consumeController: InteractivePopConsumeController? {
        get {
            objc_getAssociatedObject(self, &InteractivePopAssociated.consumeController) as? InteractivePopConsumeController
        }
        set {
            objc_setAssociatedObject(
                self,
                &InteractivePopAssociated.consumeController,
                newValue,
                .OBJC_ASSOCIATION_RETAIN_NONATOMIC
            )
        }
    }

    private func innermostInstaller(in viewController: UIViewController) -> InteractivePopGestureInstaller.Controller? {
        var deepest: (controller: InteractivePopGestureInstaller.Controller, depth: Int)?
        func walk(_ viewController: UIViewController, depth: Int) {
            if let controller = viewController as? InteractivePopGestureInstaller.Controller {
                if deepest == nil || depth >= deepest!.depth {
                    deepest = (controller, depth)
                }
            }
            for child in viewController.children {
                walk(child, depth: depth + 1)
            }
        }
        walk(viewController, depth: 0)
        return deepest?.controller
    }
}

extension UIView {
    func findAncestor<T: UIView>(ofType type: T.Type) -> T? {
        var current: UIView? = self
        while let view = current {
            if let target = view as? T { return target }
            current = view.superview
        }
        return nil
    }

    func forEachDescendant<T: UIView>(ofType type: T.Type, _ body: (T) -> Void) {
        if let match = self as? T {
            body(match)
        }
        for subview in subviews {
            subview.forEachDescendant(ofType: type, body)
        }
    }
}

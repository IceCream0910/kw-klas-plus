import Shared
import SwiftUI

struct HomeView: View {
    @ObservedObject var holder: WebViewHolder
    @ObservedObject var coordinator: HomeCoordinator

    var body: some View {
        ZStack {
            WebViewContainer(webView: holder.webView)
                .ignoresSafeArea(edges: .bottom)
                .ignoresSafeArea(.keyboard)
                .accessibilityLabel("KLAS+")
            if coordinator.isPageLoading {
                KlasLoadingView(message: "불러오는 중")
            }
        }
        .background(ScreenEdgeBackHook(onBack: { coordinator.handleHomeBack() }))
        .toolbar(.hidden, for: .navigationBar)
        .webJavaScriptAlert(holder)
        .accessibilityIdentifier("home_view")
    }
}

struct ScreenEdgeBackHook: UIViewControllerRepresentable {
    var onBack: () -> Void

    func makeUIViewController(context: Context) -> Controller {
        let controller = Controller()
        controller.onBack = onBack
        return controller
    }

    func updateUIViewController(_ uiViewController: Controller, context: Context) {
        uiViewController.onBack = onBack
    }

    final class Controller: UIViewController {
        var onBack: (() -> Void)?
        private var recognizer: UIScreenEdgePanGestureRecognizer?

        override func viewDidAppear(_ animated: Bool) {
            super.viewDidAppear(animated)
            guard recognizer == nil else { return }
            let edge = UIScreenEdgePanGestureRecognizer(target: self, action: #selector(handleEdge))
            edge.edges = .left
            view.addGestureRecognizer(edge)
            recognizer = edge
        }

        @objc private func handleEdge(_ gesture: UIScreenEdgePanGestureRecognizer) {
            guard gesture.state == .ended else { return }
            onBack?()
        }
    }
}

extension View {
    func webJavaScriptAlert(_ holder: WebViewHolder) -> some View {
        alert(
            "안내",
            isPresented: Binding(
                get: { holder.javaScriptAlertMessage != nil },
                set: { if !$0 { holder.confirmJavaScriptAlert() } }
            )
        ) {
            Button("확인") { holder.confirmJavaScriptAlert() }
        } message: {
            Text(holder.javaScriptAlertMessage ?? "")
        }
    }

    func homeOverlays(_ coordinator: HomeCoordinator) -> some View {
        modifier(HomeOverlayModifier(coordinator: coordinator))
    }
}

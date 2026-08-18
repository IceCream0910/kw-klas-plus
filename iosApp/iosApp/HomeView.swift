import Shared
import SwiftUI

struct HomeView: View {
    @ObservedObject var holder: WebViewHolder
    @ObservedObject var coordinator: HomeCoordinator

    var body: some View {
        ZStack {
            if holder.isDisposed {
                Color.clear
            } else {
                WebViewContainer(webView: holder.webView)
                    .ignoresSafeArea(edges: .bottom)
                    .ignoresSafeArea(.keyboard)
                    .accessibilityLabel("KLAS+")
            }
            if coordinator.isPageLoading {
                KlasLoadingView(message: "불러오는 중")
            }
        }
        .toolbar(.hidden, for: .navigationBar)
        .webJavaScriptAlert(holder)
        .accessibilityIdentifier("home_view")
    }
}

extension View {
    func webJavaScriptAlert(_ holder: WebViewHolder, enabled: Bool = true) -> some View {
        alert(
            "안내",
            isPresented: Binding(
                get: { enabled && holder.javaScriptAlertMessage != nil },
                set: { if !$0 { holder.confirmJavaScriptAlert() } }
            )
        ) {
            Button("확인") { holder.confirmJavaScriptAlert() }
        } message: {
            Text(holder.javaScriptAlertMessage ?? "")
        }
        .onReceive(holder.$javaScriptAlertMessage) { message in
            if !enabled, message != nil {
                holder.confirmJavaScriptAlert()
            }
        }
    }

    func homeOverlays(_ coordinator: HomeCoordinator) -> some View {
        modifier(HomeOverlayModifier(coordinator: coordinator))
    }
}

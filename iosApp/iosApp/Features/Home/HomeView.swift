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
                    .webSurfaceLayout()
                    .accessibilityLabel("KLAS+")
                    .accessibilityHidden(
                        coordinator.isPageLoading
                            || coordinator.showYearHakgiPicker
                            || coordinator.showOptionsMenu
                            || coordinator.showDatePicker
                            || holder.javaScriptAlertMessage != nil
                            || holder.downloadProgress != nil
                    )
            }
            if coordinator.isPageLoading {
                KlasLoadingView(message: "불러오는 중")
            } else if case let .failed(_, category) = holder.navigationState.loadPhase {
                HomeFailureView(
                    message: HomeCoordinator.pageLoadFailureMessage(for: category),
                    onRetry: { coordinator.reloadCurrentTab() }
                )
            }
        }
        .toolbar(.hidden, for: .navigationBar)
        .webJavaScriptAlert(holder)
        .onReceive(holder.$navigationState) { state in
            coordinator.handleHomeNavigation(state)
        }
        .webDownloadOverlay(holder)
        .accessibilityIdentifier("home_view")
    }
}

extension View {
    func homeOverlays(_ coordinator: HomeCoordinator) -> some View {
        modifier(HomeOverlayModifier(coordinator: coordinator))
    }
}

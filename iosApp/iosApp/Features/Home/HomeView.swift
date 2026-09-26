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
        .webSurfaceTopBackground()
        .toolbar(.hidden, for: .navigationBar)
        .overlay(alignment: .bottom) {
            if !coordinator.isPageLoading && !coordinator.isWebBottomSheetOpen {
                NativeHomeSystemTabBar(
                    selectedTab: coordinator.currentTab,
                    onSelect: coordinator.selectNativeTab
                )
                .frame(height: 60)
            }
        }
        .overlay {
            if coordinator.refreshPhase != .idle {
                HomeReloadOverlay()
            }
        }
        .webJavaScriptAlert(holder)
        .onReceive(holder.$navigationState) { state in
            coordinator.handleHomeNavigation(state)
        }
        .onAppear {
            updateScrollBounce(for: coordinator.currentTab)
        }
        .onChange(of: coordinator.currentTab) { tab in
            updateScrollBounce(for: tab)
        }
        .webDownloadOverlay(holder)
        .onDisappear {
            coordinator.endIdCardModalIfNeeded()
        }
        .accessibilityIdentifier("home_view")
    }

    private func updateScrollBounce(for tab: String) {
        guard !holder.isDisposed else { return }
        holder.webView.scrollView.bounces = tab != "feed"
    }
}

private struct HomeReloadOverlay: View {
    var body: some View {
        ZStack {
            KlasTheme.background.opacity(0.35)
                .ignoresSafeArea()
            VStack(spacing: 12) {
                ProgressView()
                    .controlSize(.large)
                    .tint(KlasTheme.primary)
                Text("새로고침 중")
                    .foregroundStyle(KlasTheme.onBackground)
            }
            .padding(24)
            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 20))
            .accessibilityElement(children: .combine)
        }
        .accessibilityIdentifier("home_reload_overlay")
    }
}

extension View {
    func homeOverlays(_ coordinator: HomeCoordinator) -> some View {
        modifier(HomeOverlayModifier(coordinator: coordinator))
    }
}

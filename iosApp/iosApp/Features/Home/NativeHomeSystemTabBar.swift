import SwiftUI
import UIKit

enum HomeSystemTabBarMetrics {
    static let overlayHeight: CGFloat = 60

    static var fillsHomeIndicator: Bool {
        if #available(iOS 26.0, *) {
            return false
        }
        return true
    }
}

struct NativeHomeSystemTabBar: UIViewRepresentable {
    let selectedTab: String
    let onSelect: (String) -> Void

    private static let tabs = ["feed", "timetable", "calendar", "menu"]

    func makeCoordinator() -> Coordinator {
        Coordinator(onSelect: onSelect)
    }

    func makeUIView(context: Context) -> UITabBar {
        let bar = UITabBar()
        bar.items = [
            UITabBarItem(title: "피드", image: UIImage(systemName: "square.grid.2x2"), tag: 0),
            UITabBarItem(title: "시간표", image: UIImage(systemName: "rectangle.split.3x1"), tag: 1),
            UITabBarItem(title: "캘린더", image: UIImage(systemName: "calendar"), tag: 2),
            UITabBarItem(title: "전체", image: UIImage(systemName: "line.3.horizontal"), tag: 3)
        ]
        bar.delegate = context.coordinator
        bar.isTranslucent = true
        bar.accessibilityIdentifier = "native_home_navigation"
        applyChrome(bar)
        return bar
    }

    func updateUIView(_ uiView: UITabBar, context: Context) {
        context.coordinator.onSelect = onSelect
        let index = Self.tabs.firstIndex(of: selectedTab) ?? 0
        uiView.selectedItem = uiView.items?[index]
        uiView.tintColor = UIColor(KlasTheme.primary)
        uiView.unselectedItemTintColor = UIColor(KlasTheme.onSurfaceVariant)
        applyChrome(uiView)
    }

    private func applyChrome(_ bar: UITabBar) {
        guard HomeSystemTabBarMetrics.fillsHomeIndicator else { return }
        let selected = UIColor(KlasTheme.primary)
        let unselected = UIColor(KlasTheme.onSurfaceVariant)
        let items = UITabBarItemAppearance()
        items.normal.iconColor = unselected
        items.normal.titleTextAttributes = [.foregroundColor: unselected]
        items.selected.iconColor = selected
        items.selected.titleTextAttributes = [.foregroundColor: selected]

        let appearance = UITabBarAppearance()
        appearance.configureWithTransparentBackground()
        appearance.stackedLayoutAppearance = items
        appearance.inlineLayoutAppearance = items
        appearance.compactInlineLayoutAppearance = items
        bar.standardAppearance = appearance
        bar.scrollEdgeAppearance = appearance
    }

    final class Coordinator: NSObject, UITabBarDelegate {
        var onSelect: (String) -> Void

        init(onSelect: @escaping (String) -> Void) {
            self.onSelect = onSelect
        }

        func tabBar(_ tabBar: UITabBar, didSelect item: UITabBarItem) {
            guard NativeHomeSystemTabBar.tabs.indices.contains(item.tag) else { return }
            onSelect(NativeHomeSystemTabBar.tabs[item.tag])
        }
    }
}

extension View {
    func homeSystemTabBarPlacement() -> some View {
        modifier(HomeSystemTabBarPlacement())
    }
}

private struct HomeSystemTabBarPlacement: ViewModifier {
    func body(content: Content) -> some View {
        let bar = content.frame(height: HomeSystemTabBarMetrics.overlayHeight)
        if HomeSystemTabBarMetrics.fillsHomeIndicator {
            bar
                .background(.bar, ignoresSafeAreaEdges: .bottom)
                .overlay(alignment: .top) { Divider() }
        } else {
            bar
        }
    }
}

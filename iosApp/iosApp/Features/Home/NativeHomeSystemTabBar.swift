import SwiftUI
import UIKit

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
            UITabBarItem(title: "홈", image: UIImage(systemName: "house"), tag: 0),
            UITabBarItem(title: "시간표", image: UIImage(systemName: "rectangle.split.3x1"), tag: 1),
            UITabBarItem(title: "캘린더", image: UIImage(systemName: "calendar"), tag: 2),
            UITabBarItem(title: "내 정보", image: UIImage(systemName: "person.crop.circle"), tag: 3)
        ]
        bar.delegate = context.coordinator
        bar.isTranslucent = true
        bar.accessibilityIdentifier = "native_home_navigation"
        return bar
    }

    func updateUIView(_ uiView: UITabBar, context: Context) {
        context.coordinator.onSelect = onSelect
        let index = Self.tabs.firstIndex(of: selectedTab) ?? 0
        uiView.selectedItem = uiView.items?[index]
        uiView.tintColor = UIColor(KlasTheme.primary)
        uiView.unselectedItemTintColor = UIColor(KlasTheme.onSurfaceVariant)
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

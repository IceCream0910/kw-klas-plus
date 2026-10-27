import SwiftUI
import UIKit
import WebKit

@available(iOS 18.0, *)
struct HomeTabContainer: UIViewControllerRepresentable {
    let webView: WKWebView
    let selectedTab: String
    let isTabBarHidden: Bool
    let isWebAccessibilityHidden: Bool
    let onSelect: (String) -> Void
    let onBottomNavigationHeightChange: (CGFloat) -> Void

    func makeUIViewController(context: Context) -> HomeTabController {
        HomeTabController(
            webView: webView,
            initialTab: selectedTab,
            onSelect: onSelect,
            onBottomNavigationHeightChange: onBottomNavigationHeightChange
        )
    }

    func updateUIViewController(_ controller: HomeTabController, context: Context) {
        controller.onSelect = onSelect
        controller.onBottomNavigationHeightChange = onBottomNavigationHeightChange
        controller.select(selectedTab)
        controller.setTabBarHiddenIfNeeded(isTabBarHidden)
        webView.accessibilityLabel = "KLAS+"
        webView.accessibilityElementsHidden = isWebAccessibilityHidden
    }

    static func dismantleUIViewController(_ controller: HomeTabController, coordinator: ()) {
        controller.detachWebViewIfOwned()
    }
}

/// 홈 웹 헤더가 창 제어 버튼·상단 탭 바와 한 줄에 놓이도록 주입하는 값
struct HomeHeaderLayout: Equatable {
    static let windowControlsInsetProperty = "--klas-window-controls-inset-left"
    static let topBarTopProperty = "--klas-top-bar-top"
    static let topBarHeightProperty = "--klas-top-bar-height"

    var windowControlsInset: CGFloat = 0
    /// 상단 탭 바나 창 제어 버튼이 놓이는 줄의 높이. 이 줄이 없거나 상태 바와 겹치면 nil이다.
    var topBarHeight: CGFloat?

    var cssValues: [String: Any] {
        [
            Self.windowControlsInsetProperty: "\(Int(windowControlsInset))px",
            // WebView 위쪽이 이 줄의 위쪽과 같은 safe area 기준이다
            Self.topBarTopProperty: topBarHeight == nil ? NSNull() : "0px",
            Self.topBarHeightProperty: topBarHeight.map { "\(Int($0))px" } ?? NSNull()
        ]
    }
}

// 탭마다 WebView를 만들지 않고 선택된 탭 화면으로 같은 WKWebView를 옮긴다
@available(iOS 18.0, *)
final class HomeTabController: UITabBarController, UITabBarControllerDelegate {
    static let tabIDs = ["feed", "timetable", "calendar", "menu"]

    let webView: WKWebView
    var onSelect: (String) -> Void
    var onBottomNavigationHeightChange: (CGFloat) -> Void
    private var isSelectingProgrammatically = false
    private var reportedBottomNavigationHeight: CGFloat?
    private let measureHeaderLayout: (UIViewController) -> HomeHeaderLayout
    private var headerLayout = HomeHeaderLayout()
    private var loadingObservation: NSKeyValueObservation?

    init(
        webView: WKWebView,
        initialTab: String,
        onSelect: @escaping (String) -> Void,
        onBottomNavigationHeightChange: @escaping (CGFloat) -> Void = { _ in },
        measureHeaderLayout: @escaping (UIViewController) -> HomeHeaderLayout = HomeTabController.measuredHeaderLayout
    ) {
        self.webView = webView
        self.onSelect = onSelect
        self.onBottomNavigationHeightChange = onBottomNavigationHeightChange
        self.measureHeaderLayout = measureHeaderLayout
        super.init(nibName: nil, bundle: nil)
        tabs = [
            makeTab(id: "feed", title: "피드", systemImage: "square.grid.2x2"),
            makeTab(id: "timetable", title: "시간표", systemImage: "rectangle.split.3x1"),
            makeTab(id: "calendar", title: "캘린더", systemImage: "calendar"),
            makeTab(id: "menu", title: "전체", systemImage: "line.3.horizontal")
        ]
        // 탭이 네 개뿐이라 iPad에서도 sidebar 없이 탭 바만 쓴다
        mode = .tabBar
        if #available(iOS 26.0, *) {
            tabBarMinimizeBehavior = .onScrollDown
        }
        delegate = self
        tabBar.accessibilityIdentifier = "native_home_navigation"
        tabBar.tintColor = UIColor(KlasTheme.primary)
        tabBar.unselectedItemTintColor = UIColor(KlasTheme.onSurfaceVariant)
        select(initialTab)
        // 전체 문서를 다시 읽으면 주입한 CSS 변수가 사라지므로 로드가 끝날 때마다 다시 넣는다
        loadingObservation = webView.observe(\.isLoading, options: [.new]) { [weak self] webView, _ in
            guard !webView.isLoading else { return }
            MainActor.assumeIsolated {
                guard let self, self.headerLayout != HomeHeaderLayout() else { return }
                self.applyHeaderLayout()
            }
        }
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor(KlasTheme.background)
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        let layout = measureHeaderLayout(self)
        guard layout != headerLayout else { return }
        headerLayout = layout
        applyHeaderLayout()
    }

    /// 창 제어 버튼 여백은 창 모서리까지 피하는 값이라 전체 화면에서도 작은 값이 나온다. 웹은 기존 여백과 큰 쪽을 쓴다.
    static func measuredHeaderLayout(in controller: UIViewController) -> HomeHeaderLayout {
        guard #available(iOS 26.0, *), let view = controller.viewIfLoaded else { return HomeHeaderLayout() }
        let adapted = view.edgeInsets(for: .safeArea(cornerAdaptation: .horizontal)).left
        let traits = controller.traitCollection
        let hasTopTabBar = traits.userInterfaceIdiom == .pad && traits.horizontalSizeClass == .regular
        // 창 모드를 알려 주는 공개 API가 없어 창 크기가 화면 크기와 다르면 창 모드로 본다.
        // 창 모드에서는 탭 바가 아래로 가도 창 제어 버튼 줄이 위에 남는다
        let scene = view.window?.windowScene
        let isWindowed = view.window.map { $0.bounds.size != scene?.screen.bounds.size } ?? false
        // 창 모드에서 전체 화면으로 바꾸면 safe area가 상태 바를 빼지 않는 경우가 있어, 그때는 헤더를 상태 바 아래 기존 위치에 둔다
        let statusBarBottom = scene.flatMap { scene in
            scene.statusBarManager.map { view.convert($0.statusBarFrame, from: scene.coordinateSpace).maxY }
        } ?? 0
        let rowClearsStatusBar = isWindowed || view.safeAreaInsets.top >= statusBarBottom
        // 이 줄의 높이를 알려 주는 공개 API가 없어 iPadOS 26 실측값을 쓴다
        return HomeHeaderLayout(
            windowControlsInset: max(0, (adapted - view.safeAreaInsets.left).rounded(.up)),
            topBarHeight: (hasTopTabBar || isWindowed) && rowClearsStatusBar ? 44 : nil
        )
    }

    func select(_ tabID: String) {
        guard let tab = tabs.first(where: { $0.identifier == tabID }), selectedTab !== tab else { return }
        // iOS 26은 selectedTab 대입에도 didSelectTab을 호출하므로 웹 시작 이동을 네이티브 선택으로 되돌려 보내지 않는다
        isSelectingProgrammatically = true
        selectedTab = tab
        isSelectingProgrammatically = false
    }

    func setTabBarHiddenIfNeeded(_ hidden: Bool) {
        guard isTabBarHidden != hidden else { return }
        setTabBarHidden(hidden, animated: true)
    }

    func detachWebViewIfOwned() {
        guard isViewLoaded, webView.isDescendant(of: view) else { return }
        webView.removeFromSuperview()
    }

    func tabBarController(_ tabBarController: UITabBarController, didSelectTab selectedTab: UITab, previousTab: UITab?) {
        guard !isSelectingProgrammatically, Self.tabIDs.contains(selectedTab.identifier) else { return }
        onSelect(selectedTab.identifier)
    }

    fileprivate func attachWebView(to content: UIViewController) {
        guard webView.superview !== content.view else { return }
        webView.translatesAutoresizingMaskIntoConstraints = false
        content.view.addSubview(webView)
        let guide = content.view.safeAreaLayoutGuide
        // 탭 화면 safe area에는 iPad 상단 탭 바 높이가 포함되므로 위쪽은 상태 바 기준에 맞춰 탭 바가 웹 헤더 위에 뜨게 한다
        NSLayoutConstraint.activate([
            webView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            webView.leadingAnchor.constraint(equalTo: guide.leadingAnchor),
            webView.trailingAnchor.constraint(equalTo: guide.trailingAnchor),
            webView.bottomAnchor.constraint(equalTo: content.view.bottomAnchor)
        ])
        content.setContentScrollView(webView.scrollView, for: .bottom)
        if #available(iOS 26.0, *) {
            webView.scrollView.topEdgeEffect.isHidden = true
        }
        if headerLayout != HomeHeaderLayout() {
            applyHeaderLayout()
        }
    }

    private func applyHeaderLayout() {
        guard isViewLoaded, webView.isDescendant(of: view) else { return }
        webView.callAsyncJavaScript(
            """
            const style = document.documentElement.style;
            for (const [name, value] of Object.entries(values)) {
                if (value === null) { style.removeProperty(name); } else { style.setProperty(name, value); }
            }
            """,
            arguments: ["values": headerLayout.cssValues],
            in: nil,
            in: .page
        )
    }

    fileprivate func reportBottomNavigationHeight(from content: UIViewController) {
        guard content === selectedViewController, isViewLoaded else { return }
        let height = max(0, content.view.safeAreaInsets.bottom - view.safeAreaInsets.bottom)
        guard height != reportedBottomNavigationHeight else { return }
        reportedBottomNavigationHeight = height
        onBottomNavigationHeightChange(height)
    }

    private func makeTab(id: String, title: String, systemImage: String) -> UITab {
        let tab = UITab(title: title, image: UIImage(systemName: systemImage), identifier: id) { [weak self] tab in
            HomeTabContentViewController(
                onAppearing: { self?.attachWebView(to: $0) },
                onSafeAreaChange: { self?.reportBottomNavigationHeight(from: $0) }
            )
        }
        // iPad 탭 바를 길게 눌러 편집할 때 탭을 지우거나 옮기지 못하게 한다
        tab.preferredPlacement = .fixed
        return tab
    }
}

private final class HomeTabContentViewController: UIViewController {
    private let onAppearing: (UIViewController) -> Void
    private let onSafeAreaChange: (UIViewController) -> Void

    init(onAppearing: @escaping (UIViewController) -> Void, onSafeAreaChange: @escaping (UIViewController) -> Void) {
        self.onAppearing = onAppearing
        self.onSafeAreaChange = onSafeAreaChange
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor(KlasTheme.background)
    }

    override func viewIsAppearing(_ animated: Bool) {
        super.viewIsAppearing(animated)
        onAppearing(self)
        onSafeAreaChange(self)
    }

    override func viewSafeAreaInsetsDidChange() {
        super.viewSafeAreaInsetsDidChange()
        onSafeAreaChange(self)
    }
}

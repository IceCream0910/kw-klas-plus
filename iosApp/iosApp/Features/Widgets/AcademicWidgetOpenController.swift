import Foundation

@MainActor
final class AcademicWidgetOpenController: ObservableObject {
    private(set) var pendingTab: String?
    private var openTab: ((String) -> Void)?

    func attach(openTab: @escaping (String) -> Void) {
        self.openTab = openTab
    }

    func handleOpenURL(
        _ url: URL,
        isAuthenticated: Bool,
        appLock: AppLockController
    ) -> Bool {
        guard let tab = AcademicWidgetRouter.tab(from: url) else { return false }
        pendingTab = tab
        consumeIfPossible(isAuthenticated: isAuthenticated, appLock: appLock)
        return true
    }

    func consumeIfPossible(isAuthenticated: Bool, appLock: AppLockController) {
        guard isAuthenticated, let tab = pendingTab, openTab != nil else { return }
        appLock.presentUnlock { [weak self] success in
            guard let self, success else { return }
            self.pendingTab = nil
            self.openTab?(tab)
        }
    }
}

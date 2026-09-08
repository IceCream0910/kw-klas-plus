import Foundation

enum LibraryQrRouteDecision: Equatable {
    case openQrDirectly(isAppLockBypassed: Bool)
    case showUnconfiguredNotice
}

enum LibraryQrRouter {
    static let qrURL = URL(string: "kwklasplus://library-qr")!

    static func isLibraryQrURL(_ url: URL) -> Bool {
        url.scheme == "kwklasplus" && url.host == "library-qr" && url.path.isEmpty
    }

    static func resolveRoute(
        url: URL,
        hasConfiguredCredentials: Bool,
        isAppLockActive: Bool
    ) -> LibraryQrRouteDecision? {
        guard isLibraryQrURL(url) else { return nil }

        if hasConfiguredCredentials {
            return .openQrDirectly(isAppLockBypassed: isAppLockActive)
        }
        return .showUnconfiguredNotice
    }
}

struct LibraryQrPendingGate: Equatable {
    private(set) var pendingURL: URL?

    mutating func remember(_ url: URL) {
        guard LibraryQrRouter.isLibraryQrURL(url) else { return }
        pendingURL = url
    }

    mutating func consume() -> URL? {
        let url = pendingURL
        pendingURL = nil
        return url
    }

    mutating func receive(url: URL, isAuthenticated: Bool) -> URL? {
        guard LibraryQrRouter.isLibraryQrURL(url) else { return nil }
        if isAuthenticated {
            pendingURL = nil
            return url
        }
        pendingURL = url
        return nil
    }

    mutating func takePending(isAuthenticated: Bool) -> URL? {
        guard isAuthenticated else { return nil }
        return consume()
    }
}

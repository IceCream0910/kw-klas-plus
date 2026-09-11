import Foundation

enum LibraryQrRouteDecision: Equatable {
    case openQrDirectly
    case routeToSettings
}

enum LibraryQrRouter {
    static let qrURL = URL(string: "kwklasplus://library-qr")!

    static func isLibraryQrURL(_ url: URL) -> Bool {
        url.scheme == "kwklasplus" && url.host == "library-qr" && url.path.isEmpty
    }

    static func resolveRoute(
        url: URL,
        hasConfiguredCredentials: Bool
    ) -> LibraryQrRouteDecision? {
        guard isLibraryQrURL(url) else { return nil }

        if hasConfiguredCredentials {
            return .openQrDirectly
        }
        return .routeToSettings
    }
}

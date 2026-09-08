import Foundation
@testable import kw_klas_plus

enum LibraryQrWidgetFixtures {
    public static let deepLinkQr = LibraryQrRouter.qrURL

    public static func resolveRoute(
        url: URL,
        hasConfiguredCredentials: Bool
    ) -> LibraryQrRouteDecision? {
        LibraryQrRouter.resolveRoute(
            url: url,
            hasConfiguredCredentials: hasConfiguredCredentials
        )
    }
}

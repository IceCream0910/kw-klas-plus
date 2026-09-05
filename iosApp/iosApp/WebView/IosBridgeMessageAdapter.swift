import Foundation
import Shared
import WebKit

enum BridgeAdapterAvailability {
    case installed
}

final class IosBridgeMessageAdapter: NSObject {
    static let nativeObjectName = KlasNativeBridgeScripts.shared.NATIVE_OBJECT_NAME

    private let surface: BridgeSurface
    private let router: JsonBridgeRouter
    private let routeScope: IosBridgeRouteScope
    private let bridgeTimeoutMillis: Int32
    private let replyProxy = WeakScriptMessageHandlerWithReply()
    private var installed = false
    private var disposed = false
    private weak var userContentController: WKUserContentController?

    init(
        surface: BridgeSurface,
        handler: BridgeCommandHandler,
        synchronousHandler: SynchronousBridgeCommandHandler? = nil,
        bridgeTimeoutMillis: Int32 = KlasNativeBridgeScripts.shared.DEFAULT_BRIDGE_TIMEOUT_MILLIS
    ) {
        self.surface = surface
        self.bridgeTimeoutMillis = bridgeTimeoutMillis
        self.router = IosBridgeRouting.shared.createRouter(
            handler: handler,
            synchronousHandler: synchronousHandler
        )
        self.routeScope = IosBridgeRouting.shared.createRouteScope()
        super.init()
        replyProxy.target = self
    }

    @discardableResult
    func install(into configuration: WKWebViewConfiguration) -> BridgeAdapterAvailability {
        precondition(!disposed, "해제된 bridge adapter는 다시 설치할 수 없다")
        if installed { return .installed }
        let controller = configuration.userContentController
        let transport = WKUserScript(
            source: KlasNativeBridgeScripts.shared.installWebKitTransport().reveal(),
            injectionTime: .atDocumentStart,
            forMainFrameOnly: true
        )
        let adapter = WKUserScript(
            source: KlasNativeBridgeScripts.shared.installAdapter(timeoutMillis: bridgeTimeoutMillis).reveal(),
            injectionTime: .atDocumentStart,
            forMainFrameOnly: true
        )
        controller.addUserScript(transport)
        controller.addUserScript(adapter)
        controller.addScriptMessageHandler(replyProxy, contentWorld: .page, name: Self.nativeObjectName)
        userContentController = controller
        installed = true
        return .installed
    }

    func dispose() {
        guard !disposed else { return }
        disposed = true
        if installed {
            userContentController?.removeScriptMessageHandler(forName: Self.nativeObjectName, contentWorld: .page)
        }
        userContentController = nil
        replyProxy.target = nil
        installed = false
        routeScope.dispose()
    }
}

extension IosBridgeMessageAdapter: WKScriptMessageHandlerWithReply {
    func userContentController(
        _ userContentController: WKUserContentController,
        didReceive message: WKScriptMessage,
        replyHandler: @escaping (Any?, String?) -> Void
    ) {
        guard message.name == Self.nativeObjectName else {
            replyHandler(IosBridgeRouting.shared.malformedResponse(), nil)
            return
        }
        guard let payload = message.body as? String else {
            replyHandler(IosBridgeRouting.shared.malformedResponse(), nil)
            return
        }
        let origin = Self.originString(from: message.frameInfo.securityOrigin)
        let context = BridgeContext(
            surface: surface,
            origin: origin,
            isMainFrame: message.frameInfo.isMainFrame,
            payloadSizeBytes: 0
        )
        routeScope.route(
            router: router,
            payload: payload,
            context: context
        ) { response in
            replyHandler(response, nil)
        }
    }

    static func originString(from origin: WKSecurityOrigin) -> String {
        let scheme = origin.`protocol`
        let host = origin.host
        let port = origin.port
        if port == 0 || (scheme == "https" && port == 443) || (scheme == "http" && port == 80) {
            return "\(scheme)://\(host)"
        }
        return "\(scheme)://\(host):\(port)"
    }
}

private final class WeakScriptMessageHandlerWithReply: NSObject, WKScriptMessageHandlerWithReply {
    weak var target: (NSObjectProtocol & WKScriptMessageHandlerWithReply)?

    func userContentController(
        _ userContentController: WKUserContentController,
        didReceive message: WKScriptMessage,
        replyHandler: @escaping (Any?, String?) -> Void
    ) {
        guard let target else {
            replyHandler(nil, "BRIDGE_DISPOSED")
            return
        }
        target.userContentController(userContentController, didReceive: message, replyHandler: replyHandler)
    }
}

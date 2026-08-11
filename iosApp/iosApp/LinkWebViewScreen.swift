import Shared
import SwiftUI
import UIKit
import WebKit

/// Android LinkViewActivity + ComposeWebViewHost(title=null) 패리티
/// Scaffold가 systemBars inset 안에 WebView를 채움
/// 웹 하단 닫기는 safe area 위에 오도록 배치
struct LinkWebViewScreen: View {
    let url: URL
    var onDismiss: () -> Void

    var body: some View {
        LinkWebView(url: url, onClose: onDismiss)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Color.white)
            .accessibilityIdentifier("link_web_view")
    }
}

private struct LinkWebView: UIViewRepresentable {
    let url: URL
    var onClose: () -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(onClose: onClose)
    }

    func makeUIView(context: Context) -> WKWebView {
        let configuration = WKWebViewConfiguration()
        configuration.websiteDataStore = .default()
        configuration.preferences.javaScriptCanOpenWindowsAutomatically = true
        configuration.defaultWebpagePreferences.allowsContentJavaScript = true

        let closeName = Coordinator.closeHandlerName
        configuration.userContentController.add(context.coordinator, name: closeName)
        configuration.userContentController.addUserScript(
            WKUserScript(
                source: """
                (function(){
                  window.close = function() {
                    try { window.webkit.messageHandlers.\(closeName).postMessage('close'); } catch (e) {}
                  };
                })();
                """,
                injectionTime: .atDocumentStart,
                forMainFrameOnly: true
            )
        )
        // KLAS 모달은 viewport width=1024 고정.
        // Android WebView는 useWideViewPort 기본값이 false라 이 meta를 거의 무시하고
        // WebView 폭·높이로 레이아웃하지만, WKWebView는 width=1024를 그대로 적용해 잘린다.
        if Self.needsMobileViewportOverride(url) {
            configuration.userContentController.addUserScript(
                WKUserScript(
                    source: Self.forceMobileLayoutJavaScript,
                    injectionTime: .atDocumentEnd,
                    forMainFrameOnly: true
                )
            )
        }

        let adapter = IosBridgeMessageAdapter(
            surface: .linkView,
            handler: AcceptingBridgeCommandHandler()
        )
        adapter.install(into: configuration)
        context.coordinator.bridgeAdapter = adapter

        let webView = WKWebView(frame: .zero, configuration: configuration)
        webView.navigationDelegate = context.coordinator
        webView.uiDelegate = context.coordinator
        webView.allowsBackForwardNavigationGestures = true
        webView.isOpaque = true
        webView.backgroundColor = .white
        webView.scrollView.backgroundColor = .white
        // SwiftUI safe area가 이미 inset을 적용하므로 WebView 자체 추가 inset은 끈다.
        webView.scrollView.contentInsetAdjustmentBehavior = .never
        webView.scrollView.contentInset = .zero
        webView.scrollView.scrollIndicatorInsets = .zero
        context.coordinator.webView = webView
        webView.load(URLRequest(url: url))
        return webView
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {}

    static func dismantleUIView(_ uiView: WKWebView, coordinator: Coordinator) {
        uiView.stopLoading()
        uiView.navigationDelegate = nil
        uiView.uiDelegate = nil
        uiView.configuration.userContentController.removeScriptMessageHandler(
            forName: Coordinator.closeHandlerName
        )
        coordinator.bridgeAdapter?.dispose()
        coordinator.bridgeAdapter = nil
        coordinator.webView = nil
    }

    private static func needsMobileViewportOverride(_ url: URL) -> Bool {
        needsMobileViewportOverride(url.absoluteString)
    }

    private static func needsMobileViewportOverride(_ absolute: String) -> Bool {
        absolute.contains("UserFindMemberNoPage.do")
            || absolute.contains("UserFindPwdPage.do")
            || absolute.contains("UserFrstModPwdPage.do")
    }

    /// Android `WebSettings.useWideViewPort=false`에 해당하는 iOS 보정(가로·세로).
    private static let forceMobileLayoutJavaScript = """
        (function(){
          var meta=document.querySelector('meta[name="viewport"]');
          var content='width=device-width, initial-scale=1, maximum-scale=1, user-scalable=yes, viewport-fit=cover';
          if(meta){meta.setAttribute('content',content);}
          else{
            meta=document.createElement('meta');
            meta.name='viewport';
            meta.content=content;
            document.head.appendChild(meta);
          }

          var root=document.documentElement;
          var body=document.body;
          root.style.width='100%';
          root.style.height='100%';
          root.style.maxWidth='100%';
          root.style.maxHeight='100%';
          if(body){
            body.style.width='100%';
            body.style.height='100%';
            body.style.maxWidth='100%';
            body.style.maxHeight='100%';
            body.style.margin='0';
            body.style.overflow='hidden';
          }

          var modal=document.getElementById('ax-modal-base-root');
          if(modal){
            modal.style.width='100%';
            modal.style.height='100%';
            modal.style.maxWidth='100%';
            modal.style.maxHeight='100%';
            modal.style.boxSizing='border-box';
            modal.style.display='flex';
            modal.style.flexDirection='column';
            modal.style.overflow='hidden';
          }

          var content=document.querySelector('.ax-base-content');
          if(content){
            content.style.flex='1 1 auto';
            content.style.minHeight='0';
            content.style.width='100%';
            content.style.overflow='auto';
            content.style.boxSizing='border-box';
          }

          document.querySelectorAll('[data-ax5layout], [data-split-panel], .panelWrap').forEach(function(el){
            el.style.width='100%';
            el.style.maxWidth='100%';
            el.style.height='100%';
            el.style.maxHeight='100%';
            el.style.boxSizing='border-box';
          });

          window.dispatchEvent(new Event('resize'));
          if(window.visualViewport){
            window.visualViewport.dispatchEvent(new Event('resize'));
          }
        })();
        """

    final class Coordinator: NSObject, WKNavigationDelegate, WKUIDelegate, WKScriptMessageHandler {
        static let closeHandlerName = "KlasLinkClose"

        var onClose: () -> Void
        var bridgeAdapter: IosBridgeMessageAdapter?
        weak var webView: WKWebView?

        private let trustedOrigins = TrustedOriginPolicy(
            trustedOrigins: TrustedOriginPolicy.companion.DEFAULT_TRUSTED_ORIGINS
        )
        private let externalPolicy = ExternalNavigationPolicy(maximumLength: 2048)

        init(onClose: @escaping () -> Void) {
            self.onClose = onClose
        }

        func userContentController(
            _ userContentController: WKUserContentController,
            didReceive message: WKScriptMessage
        ) {
            guard message.name == Self.closeHandlerName else { return }
            DispatchQueue.main.async { [weak self] in
                self?.onClose()
            }
        }

        func webView(
            _ webView: WKWebView,
            decidePolicyFor navigationAction: WKNavigationAction,
            decisionHandler: @escaping (WKNavigationActionPolicy) -> Void
        ) {
            guard navigationAction.targetFrame?.isMainFrame != false,
                  let urlString = navigationAction.request.url?.absoluteString
            else {
                decisionHandler(.allow)
                return
            }

            if trustedOrigins.isTrustedUrl(url: urlString) {
                decisionHandler(.allow)
                return
            }

            let resolution = externalPolicy.resolve(rawValue: urlString)
            if let allowed = resolution as? ExternalNavigationResolutionAllowed {
                openExternal(allowed.destination)
            }
            decisionHandler(.cancel)
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            let finished = webView.url?.absoluteString ?? ""
            let script: WebScript?
            if finished.contains("UserFindMemberNoPage.do") {
                script = KlasWebAutomationScripts.shared.configureMemberNumberRecoveryPage()
            } else if finished.contains("UserFrstModPwdPage.do") || finished.contains("UserFindPwdPage.do") {
                script = KlasWebAutomationScripts.shared.configurePasswordRecoveryPage()
            } else if finished.contains("notice.jsp") {
                script = KlasWebAutomationScripts.shared.makeNoticeScrollable()
            } else {
                script = nil
            }

            let applyMobileLayout = {
                webView.evaluateJavaScript(LinkWebView.forceMobileLayoutJavaScript, completionHandler: nil)
            }

            if let script {
                webView.evaluateJavaScript(script.reveal()) { _, _ in
                    // axboot auto-height가 데스크톱 높이로 다시 잡을 수 있어, 공유 스크립트 이후 한 번 더 맞춤
                    if LinkWebView.needsMobileViewportOverride(finished) {
                        applyMobileLayout()
                    }
                }
            } else if LinkWebView.needsMobileViewportOverride(finished) {
                applyMobileLayout()
            }
        }

        func webViewDidClose(_ webView: WKWebView) {
            onClose()
        }

        func webView(
            _ webView: WKWebView,
            createWebViewWith configuration: WKWebViewConfiguration,
            for navigationAction: WKNavigationAction,
            windowFeatures: WKWindowFeatures
        ) -> WKWebView? {
            if let urlString = navigationAction.request.url?.absoluteString {
                if trustedOrigins.isTrustedUrl(url: urlString) {
                    webView.load(navigationAction.request)
                } else {
                    let resolution = externalPolicy.resolve(rawValue: urlString)
                    if let allowed = resolution as? ExternalNavigationResolutionAllowed {
                        openExternal(allowed.destination)
                    }
                }
            }
            return nil
        }

        private func openExternal(_ destination: ExternalDestination) {
            let raw: String
            if let web = destination as? ExternalDestinationWeb {
                raw = web.url
            } else if let email = destination as? ExternalDestinationEmail {
                raw = "mailto:\(email.address)"
            } else if let tel = destination as? ExternalDestinationTelephone {
                raw = "tel:\(tel.number)"
            } else if let platform = destination as? ExternalDestinationPlatformUri {
                raw = platform.uri
            } else {
                return
            }
            guard let url = URL(string: raw) else { return }
            DispatchQueue.main.async {
                UIApplication.shared.open(url)
            }
        }
    }
}

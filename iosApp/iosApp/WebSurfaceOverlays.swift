import SwiftUI

extension View {
    func webJavaScriptAlert(_ holder: WebViewHolder, enabled: Bool = true) -> some View {
        alert(
            "안내",
            isPresented: Binding(
                get: { enabled && holder.javaScriptAlertMessage != nil },
                set: { if !$0 { holder.confirmJavaScriptAlert() } }
            )
        ) {
            Button("확인") { holder.confirmJavaScriptAlert() }
        } message: {
            Text(holder.javaScriptAlertMessage ?? "")
        }
        .onReceive(holder.$javaScriptAlertMessage) { message in
            if !enabled, message != nil {
                holder.confirmJavaScriptAlert()
            }
        }
    }

    func webJavaScriptAlert(
        _ primary: WebViewHolder,
        _ secondary: WebViewHolder,
        secondaryEnabled: Bool = true
    ) -> some View {
        overlay {
            WebJavaScriptAlertHost(
                primary: primary,
                secondary: secondary,
                secondaryEnabled: secondaryEnabled
            )
        }
    }
}

enum WebJavaScriptAlertPresentation {
    static func holder(
        primary: WebViewHolder,
        secondary: WebViewHolder,
        secondaryEnabled: Bool,
        sticky: WebViewHolder?
    ) -> WebViewHolder? {
        if let sticky, sticky.javaScriptAlertMessage != nil {
            return sticky
        }
        if primary.javaScriptAlertMessage != nil {
            return primary
        }
        if secondaryEnabled, secondary.javaScriptAlertMessage != nil {
            return secondary
        }
        return nil
    }
}

struct WebJavaScriptAlertHost: View {
    @ObservedObject var primary: WebViewHolder
    @ObservedObject var secondary: WebViewHolder
    var secondaryEnabled: Bool = true
    @State private var presentedHolder: WebViewHolder?

    var body: some View {
        EmptyView()
            .alert(
                "안내",
                isPresented: Binding(
                    get: { presentedHolder?.javaScriptAlertMessage != nil },
                    set: { if !$0 { presentedHolder?.confirmJavaScriptAlert() } }
                )
            ) {
                Button("확인") { presentedHolder?.confirmJavaScriptAlert() }
            } message: {
                Text(presentedHolder?.javaScriptAlertMessage ?? "")
            }
            .onReceive(primary.$javaScriptAlertMessage) { _ in
                refreshPresentedHolder()
            }
            .onReceive(secondary.$javaScriptAlertMessage) { message in
                if !secondaryEnabled, message != nil {
                    secondary.confirmJavaScriptAlert()
                    return
                }
                refreshPresentedHolder()
            }
            .onChange(of: secondaryEnabled) { enabled in
                if !enabled, secondary.javaScriptAlertMessage != nil {
                    secondary.confirmJavaScriptAlert()
                }
                refreshPresentedHolder()
            }
    }

    private func refreshPresentedHolder() {
        let next = WebJavaScriptAlertPresentation.holder(
            primary: primary,
            secondary: secondary,
            secondaryEnabled: secondaryEnabled,
            sticky: presentedHolder
        )
        if presentedHolder === next {
            return
        }
        if presentedHolder != nil, next != nil {
            presentedHolder = nil
            DispatchQueue.main.async {
                refreshPresentedHolder()
            }
            return
        }
        presentedHolder = next
    }
}

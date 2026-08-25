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
        _ secondary: WebViewHolder
    ) -> some View {
        overlay {
            WebJavaScriptAlertHost(primary: primary, secondary: secondary)
        }
    }
}

struct WebJavaScriptAlertHost: View {
    @ObservedObject var primary: WebViewHolder
    @ObservedObject var secondary: WebViewHolder

    var body: some View {
        EmptyView()
            .alert(
                "안내",
                isPresented: Binding(
                    get: { presentingHolder != nil },
                    set: { if !$0 { presentingHolder?.confirmJavaScriptAlert() } }
                )
            ) {
                Button("확인") { presentingHolder?.confirmJavaScriptAlert() }
            } message: {
                Text(presentingHolder?.javaScriptAlertMessage ?? "")
            }
    }

    private var presentingHolder: WebViewHolder? {
        if primary.javaScriptAlertMessage != nil { return primary }
        if secondary.javaScriptAlertMessage != nil { return secondary }
        return nil
    }
}

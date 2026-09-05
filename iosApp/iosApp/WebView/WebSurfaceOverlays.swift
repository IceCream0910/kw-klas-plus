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
        .background {
            Color.clear
                .alert(
                    "안내",
                    isPresented: Binding(
                        get: { enabled && holder.javaScriptConfirmMessage != nil },
                        set: { if !$0 { holder.answerJavaScriptConfirm(false) } }
                    )
                ) {
                    Button("확인") { holder.answerJavaScriptConfirm(true) }
                    Button("취소", role: .cancel) { holder.answerJavaScriptConfirm(false) }
                } message: {
                    Text(holder.javaScriptConfirmMessage ?? "")
                }
        }
        .onReceive(holder.$javaScriptConfirmMessage) { message in
            if !enabled, message != nil {
                holder.answerJavaScriptConfirm(false)
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

enum WebJavaScriptPresentation {
    static func holder(
        primary: WebViewHolder,
        secondary: WebViewHolder,
        secondaryEnabled: Bool,
        sticky: WebViewHolder?,
        keyPath: KeyPath<WebViewHolder, String?>
    ) -> WebViewHolder? {
        if let sticky, sticky[keyPath: keyPath] != nil {
            return sticky
        }
        if primary[keyPath: keyPath] != nil {
            return primary
        }
        if secondaryEnabled, secondary[keyPath: keyPath] != nil {
            return secondary
        }
        return nil
    }
}

enum WebJavaScriptConfirmPresentation {
    static func holder(
        primary: WebViewHolder,
        secondary: WebViewHolder,
        secondaryEnabled: Bool,
        sticky: WebViewHolder?
    ) -> WebViewHolder? {
        WebJavaScriptPresentation.holder(
            primary: primary,
            secondary: secondary,
            secondaryEnabled: secondaryEnabled,
            sticky: sticky,
            keyPath: \.javaScriptConfirmMessage
        )
    }
}

enum WebJavaScriptAlertPresentation {
    static func holder(
        primary: WebViewHolder,
        secondary: WebViewHolder,
        secondaryEnabled: Bool,
        sticky: WebViewHolder?
    ) -> WebViewHolder? {
        WebJavaScriptPresentation.holder(
            primary: primary,
            secondary: secondary,
            secondaryEnabled: secondaryEnabled,
            sticky: sticky,
            keyPath: \.javaScriptAlertMessage
        )
    }
}

struct WebJavaScriptAlertHost: View {
    @ObservedObject var primary: WebViewHolder
    @ObservedObject var secondary: WebViewHolder
    var secondaryEnabled: Bool = true
    @State private var presentedHolder: WebViewHolder?
    @State private var presentedConfirmHolder: WebViewHolder?

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
            .background {
                Color.clear
                    .alert(
                        "안내",
                        isPresented: Binding(
                            get: { presentedConfirmHolder?.javaScriptConfirmMessage != nil },
                            set: { if !$0 { presentedConfirmHolder?.answerJavaScriptConfirm(false) } }
                        )
                    ) {
                        Button("확인") { presentedConfirmHolder?.answerJavaScriptConfirm(true) }
                        Button("취소", role: .cancel) { presentedConfirmHolder?.answerJavaScriptConfirm(false) }
                    } message: {
                        Text(presentedConfirmHolder?.javaScriptConfirmMessage ?? "")
                    }
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
            .onReceive(primary.$javaScriptConfirmMessage) { _ in
                refreshPresentedConfirmHolder()
            }
            .onReceive(secondary.$javaScriptConfirmMessage) { message in
                if !secondaryEnabled, message != nil {
                    secondary.answerJavaScriptConfirm(false)
                    return
                }
                refreshPresentedConfirmHolder()
            }
            .onChange(of: secondaryEnabled) { enabled in
                if !enabled, secondary.javaScriptAlertMessage != nil {
                    secondary.confirmJavaScriptAlert()
                }
                if !enabled, secondary.javaScriptConfirmMessage != nil {
                    secondary.answerJavaScriptConfirm(false)
                }
                refreshPresentedHolder()
                refreshPresentedConfirmHolder()
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

    private func refreshPresentedConfirmHolder() {
        let next = WebJavaScriptConfirmPresentation.holder(
            primary: primary,
            secondary: secondary,
            secondaryEnabled: secondaryEnabled,
            sticky: presentedConfirmHolder
        )
        if presentedConfirmHolder === next {
            return
        }
        if presentedConfirmHolder != nil, next != nil {
            presentedConfirmHolder = nil
            DispatchQueue.main.async {
                refreshPresentedConfirmHolder()
            }
            return
        }
        presentedConfirmHolder = next
    }
}

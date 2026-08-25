import SwiftUI

extension View {
    func webDownloadOverlay(_ holder: WebViewHolder) -> some View {
        overlay {
            WebSurfaceOverlayHost(primary: holder)
        }
    }

    func webDownloadOverlay(_ primary: WebViewHolder, _ secondary: WebViewHolder) -> some View {
        overlay {
            WebSurfaceOverlayHost(primary: primary, secondary: secondary)
        }
    }

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

struct WebSurfaceOverlayHost: View {
    @ObservedObject var primary: WebViewHolder
    @ObservedObject var secondary: WebViewHolder
    private let isDual: Bool

    init(primary: WebViewHolder, secondary: WebViewHolder? = nil) {
        self.primary = primary
        self.secondary = secondary ?? primary
        self.isDual = secondary != nil
    }

    var body: some View {
        ZStack {
            if let holder = progressHolder, let progress = holder.downloadProgress {
                DownloadProgressOverlay(
                    fileName: progress.fileName,
                    fraction: progress.fraction,
                    onCancel: { holder.cancelDownload() }
                )
            }
        }
        .alert(
            "다운로드 실패",
            isPresented: Binding(
                get: { downloadErrorMessage != nil },
                set: { if !$0 { dismissDownloadError() } }
            )
        ) {
            Button("확인") { dismissDownloadError() }
        } message: {
            Text(downloadErrorMessage ?? "")
        }
        .accessibilityIdentifier("web_surface_overlay_host")
    }

    private var holders: [WebViewHolder] {
        isDual ? [primary, secondary] : [primary]
    }

    private var progressHolder: WebViewHolder? {
        holders.first { $0.downloadProgress != nil }
    }

    private var errorHolder: WebViewHolder? {
        holders.first { $0.downloadErrorMessage != nil }
    }

    private var downloadErrorMessage: String? {
        errorHolder?.downloadErrorMessage
    }

    private func dismissDownloadError() {
        errorHolder?.clearDownloadError()
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

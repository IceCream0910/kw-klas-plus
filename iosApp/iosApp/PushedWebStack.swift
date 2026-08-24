import SwiftUI

struct PushedWebStack: View {
    @ObservedObject var holder: WebViewHolder
    var isLoading: Bool
    var onBack: () -> Void

    var body: some View {
        ZStack {
            WebViewContainer(webView: holder.webView)
                .webSurfaceLayout()
                .accessibilityHidden(
                    isLoading
                        || holder.javaScriptAlertMessage != nil
                        || holder.downloadProgress != nil
                )
            if isLoading {
                KlasLoadingView(message: "불러오는 중")
            }
        }
        .navigationBarBackButtonHidden(true)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Button(action: onBack) {
                    Image(systemName: "chevron.left")
                }
            }
            ToolbarItem(placement: .topBarTrailing) {
                if holder.shareableFileURL != nil {
                    Button(action: { holder.shareCurrentFile() }) {
                        Image(systemName: "square.and.arrow.up")
                    }
                    .accessibilityIdentifier("pdf_share_button")
                    .accessibilityLabel("공유")
                }
            }
        }
        .webJavaScriptAlert(holder)
        .webDownloadOverlay(holder)
    }
}

extension WebViewHolder {
    var isLoading: Bool {
        if case .loading = navigationState.loadPhase { return true }
        return false
    }
}

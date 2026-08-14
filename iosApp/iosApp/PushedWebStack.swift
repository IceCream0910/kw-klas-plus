import SwiftUI

struct PushedWebStack: View {
    @ObservedObject var holder: WebViewHolder
    var isLoading: Bool
    var onBack: () -> Void

    var body: some View {
        ZStack {
            WebViewContainer(webView: holder.webView)
                .ignoresSafeArea(edges: .bottom)
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
        }
        .webJavaScriptAlert(holder)
    }
}

extension WebViewHolder {
    var isLoading: Bool {
        if case .loading = navigationState.loadPhase { return true }
        return false
    }
}

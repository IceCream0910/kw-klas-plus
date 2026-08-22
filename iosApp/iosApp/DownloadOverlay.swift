import Shared
import SwiftUI

struct DownloadProgressState: Equatable {
    var fileName: String
    var fraction: Double
}

struct DownloadProgressOverlay: View {
    let fileName: String
    let fraction: Double
    let onCancel: () -> Void

    var body: some View {
        ZStack {
            Color.black.opacity(0.35)
            VStack(spacing: 16) {
                Text(fileName)
                    .font(.headline)
                    .multilineTextAlignment(.center)
                    .lineLimit(2)
                if fraction > 0 {
                    ProgressView(value: min(max(fraction, 0), 1))
                } else {
                    ProgressView()
                }
                Button("취소", action: onCancel)
            }
            .padding(24)
            .frame(maxWidth: 320)
            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 12))
        }
        .accessibilityIdentifier("download_progress_overlay")
    }
}

extension View {
    func webDownloadOverlay(_ holder: WebViewHolder) -> some View {
        overlay {
            if let progress = holder.downloadProgress {
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
                get: { holder.downloadErrorMessage != nil },
                set: { if !$0 { holder.clearDownloadError() } }
            )
        ) {
            Button("확인") { holder.clearDownloadError() }
        } message: {
            Text(holder.downloadErrorMessage ?? "")
        }
    }
}

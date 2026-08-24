import SwiftUI

struct DownloadProgressState: Equatable {
    var fileName: String
    var fraction: Double
}

struct DownloadProgressOverlay: View {
    let fileName: String
    let fraction: Double
    let onCancel: () -> Void
    @AccessibilityFocusState private var focusedCancel: Bool

    var body: some View {
        ZStack {
            Color.black.opacity(0.35)
            VStack(spacing: 16) {
                Text(fileName)
                    .font(.headline)
                    .multilineTextAlignment(.center)
                    .lineLimit(2)
                    .minimumScaleFactor(0.7)
                    .fixedSize(horizontal: false, vertical: true)
                if fraction > 0 {
                    ProgressView(value: min(max(fraction, 0), 1))
                } else {
                    ProgressView()
                }
                Button("취소", action: onCancel)
                    .accessibilityFocused($focusedCancel)
            }
            .padding(24)
            .frame(maxWidth: 320)
            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 12))
            .padding(16)
        }
        .accessibilityElement(children: .contain)
        .accessibilityAddTraits(.isModal)
        .accessibilityIdentifier("download_progress_overlay")
        .onAppear {
            DispatchQueue.main.async {
                focusedCancel = true
            }
        }
    }
}

import SwiftUI

struct LibraryQrUiState: Equatable {
    var name = ""
    var details = ""
    var image: UIImage?
    var loading = true
    var secondsRemaining = 30
    var isWidgetEntry = false
    var canAddWidget = false
}

struct LibraryQrSheet: View {
    let state: LibraryQrUiState
    var onRefresh: () -> Void
    var onSettings: () -> Void
    var onAddWidget: () -> Void

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                HStack {
                    Button(action: onRefresh) {
                        Image(systemName: "arrow.clockwise")
                            .font(.body.weight(.semibold))
                            .foregroundStyle(KlasTheme.primary)
                            .frame(width: 44, height: 44)
                    }
                    .accessibilityLabel("QR 코드 새로고침, \(state.secondsRemaining)초 남음")
                    .accessibilityIdentifier("library_qr_refresh")
                    Spacer()
                    if !state.isWidgetEntry {
                        Button("설정", action: onSettings)
                            .buttonStyle(.bordered)
                    }
                }
                Text(state.name)
                    .font(.title2.weight(.bold))
                    .foregroundStyle(KlasTheme.onSurface)
                    .multilineTextAlignment(.center)
                Text(state.details)
                    .font(.body)
                    .foregroundStyle(KlasTheme.onSurfaceVariant)
                    .multilineTextAlignment(.center)
                Group {
                    if state.loading {
                        ProgressView()
                            .accessibilityIdentifier("library_qr_loading")
                    } else if let image = state.image {
                        Image(uiImage: image)
                            .interpolation(.none)
                            .resizable()
                            .scaledToFit()
                            .frame(width: 220, height: 220)
                            .accessibilityLabel("중앙도서관 출입증 QR")
                            .accessibilityIdentifier("library_qr_image")
                    } else {
                        Text("QR 코드를 표시할 수 없습니다.")
                            .foregroundStyle(.red)
                    }
                }
                Text("중앙도서관 이용 시 사용 가능합니다.\n공식 앱이 아니므로 이외 용도 사용 시 거절당할 수 있습니다.")
                    .font(.footnote)
                    .foregroundStyle(KlasTheme.onSurfaceVariant)
                    .multilineTextAlignment(.center)
                if !state.isWidgetEntry && state.canAddWidget {
                    Button("홈 화면에 학생증 위젯 추가하기", action: onAddWidget)
                        .buttonStyle(KlasInverseButtonStyle())
                }
            }
            .padding(20)
            .frame(maxWidth: 640)
            .frame(maxWidth: .infinity)
        }
        .background(KlasTheme.surface)
        .accessibilityIdentifier("library_qr_content")
    }
}

import SwiftUI

struct LibraryQrUiState: Equatable {
    var name = ""
    var details = ""
    var image: UIImage?
    var loading = true
    var secondsRemaining = 30
    var isWidgetEntry = false
    var canAddWidget = false
    var isError = false
    var errorMessage = ""
}

struct LibraryQrSheet: View {
    let state: LibraryQrUiState
    var onRefresh: () -> Void
    var onSettings: () -> Void
    var onAddWidget: () -> Void

    var body: some View {
        GeometryReader { geometry in
            ScrollView {
                VStack(spacing: 20) {
                    HStack {
                        Button(action: onRefresh) {
                            HStack(spacing: 6) {
                                Image(systemName: "arrow.clockwise")
                                    .font(.system(size: 14, weight: .bold))
                                Text(state.isError ? "새로고침" : "\(state.secondsRemaining)초 후 갱신")
                                    .font(.system(size: 14, weight: .medium))
                            }
                            .foregroundStyle(KlasTheme.onSecondaryContainer)
                            .padding(.horizontal, 14)
                            .padding(.vertical, 8)
                            .background(KlasTheme.secondaryContainer)
                            .clipShape(Capsule())
                        }
                        .accessibilityLabel(state.isError ? "QR 코드 새로고침" : "QR 코드 새로고침, \(state.secondsRemaining)초 후 갱신")
                        .accessibilityIdentifier("library_qr_refresh")

                        Spacer()

                        Button(action: onSettings) {
                            Image(systemName: "gearshape")
                                .font(.system(size: 18, weight: .medium))
                                .foregroundStyle(KlasTheme.onSecondaryContainer)
                                .frame(width: 36, height: 36)
                                .background(KlasTheme.secondaryContainer)
                                .clipShape(Circle())
                        }
                        .accessibilityLabel("출입증 설정")
                        .accessibilityIdentifier("library_qr_settings")
                    }

                    if state.isError {
                        VStack(spacing: 20) {
                            ZStack {
                                RoundedRectangle(cornerRadius: 18)
                                    .fill(KlasTheme.secondaryContainer)
                                    .frame(width: 64, height: 64)
                                Image(systemName: "exclamationmark.circle")
                                    .font(.system(size: 32, weight: .medium))
                                    .foregroundStyle(KlasTheme.onSecondaryContainer)
                            }
                            .padding(.top, 16)

                            VStack(spacing: 8) {
                                Text("도서관 출입증 정보를 가져올 수 없습니다.")
                                    .font(.body.weight(.bold))
                                    .foregroundStyle(KlasTheme.onSurface)
                                    .multilineTextAlignment(.center)
                                Text("설정에서 입력한 정보가 올바른지 확인한 후 다시 시도해주세요.")
                                    .font(.subheadline)
                                    .foregroundStyle(KlasTheme.onSurfaceVariant)
                                    .multilineTextAlignment(.center)
                            }
                        }
                        .padding(.vertical, 24)
                        .accessibilityIdentifier("library_qr_error_view")
                    } else {
                        VStack(spacing: 12) {
                            Text(state.name)
                                .font(.title2.weight(.bold))
                                .foregroundStyle(KlasTheme.onSurface)
                                .multilineTextAlignment(.center)
                            Text(state.details)
                                .font(.body)
                                .foregroundStyle(KlasTheme.onSurfaceVariant)
                                .multilineTextAlignment(.center)

                            Spacer()
                                .frame(height: 20)

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
                        }
                    }

                    Spacer(minLength: 20)

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
                .frame(minHeight: geometry.size.height)
            }
        }
        .background(KlasTheme.surface)
        .accessibilityIdentifier("library_qr_content")
    }
}

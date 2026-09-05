import SwiftUI

struct VideoPlayerUiState: Equatable {
    var lectureName = ""
    var lectureTime = ""
    var currentTime = "00:00"
    var totalTime = ""
    var progress: Float = 0
    var isPlaying = false
    var isMuted = false
    var speedText = "1.0x"
}

struct VideoPlayerOverlay<Media: View>: View {
    var state: VideoPlayerUiState
    var isPictureInPictureSupported: Bool
    var onSeek: (Float) -> Void
    var onPlayPauseClick: () -> Void
    var onBackwardClick: () -> Void
    var onForwardClick: () -> Void
    var onMuteClick: () -> Void
    var onFullscreenClick: () -> Void
    var onPictureInPictureClick: () -> Void
    var onSpeedClick: () -> Void
    var onCloseClick: () -> Void
    var onLectureTimeClick: () -> Void
    @ViewBuilder var media: () -> Media

    @State private var sliderProgress: Float = 0
    @State private var isSeeking = false

    var body: some View {
        GeometryReader { proxy in
            let isLandscape = proxy.size.width > proxy.size.height
            if isLandscape {
                HStack(spacing: 20) {
                    media()
                        .aspectRatio(16 / 9, contentMode: .fit)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                    ScrollView(.vertical, showsIndicators: false) {
                        playerControls
                            .frame(maxWidth: .infinity)
                            .frame(minHeight: max(0, proxy.size.height - 40), alignment: .center)
                    }
                    .frame(width: min(380, proxy.size.width * 0.45))
                }
                .padding(20)
            } else {
                ScrollView(.vertical, showsIndicators: false) {
                    VStack(spacing: 16) {
                        media()
                            .aspectRatio(16 / 9, contentMode: .fit)
                            .frame(maxWidth: .infinity)
                        playerControls
                    }
                }
            }
        }
        .background(KlasTheme.background)
        .accessibilityIdentifier("video_player_overlay")
        .onAppear { sliderProgress = state.progress }
        .onChange(of: state.progress) { progress in
            if !isSeeking {
                sliderProgress = progress
            }
        }
    }

    private var playerControls: some View {
        VStack(alignment: .leading, spacing: 20) {
            header
            VStack(spacing: 8) {
                Slider(
                    value: Binding(
                        get: { Double(sliderProgress) },
                        set: {
                            isSeeking = true
                            sliderProgress = Float($0)
                        }
                    ),
                    in: 0...1
                ) { editing in
                    if !editing {
                        isSeeking = false
                        onSeek(sliderProgress)
                    }
                }
                .accessibilityIdentifier("video_progress")
                HStack {
                    Text(state.currentTime)
                        .font(.caption)
                        .foregroundStyle(KlasTheme.onSurfaceVariant)
                    Spacer()
                    Text(state.totalTime)
                        .font(.caption)
                        .foregroundStyle(KlasTheme.onSurfaceVariant)
                }
            }
            HStack(spacing: 0) {
                controlButton(
                    systemName: state.isMuted ? "speaker.slash" : "speaker.wave.2",
                    label: state.isMuted ? "소리 켜기" : "음소거",
                    action: onMuteClick
                )
                Spacer()
                controlButton(systemName: "gobackward.10", label: "10초 뒤로", action: onBackwardClick)
                Spacer()
                Button(action: onPlayPauseClick) {
                    Image(systemName: state.isPlaying ? "pause.fill" : "play.fill")
                        .font(.system(size: 26, weight: .bold))
                        .foregroundStyle(KlasTheme.onPrimary)
                        .frame(width: 64, height: 64)
                        .background(KlasTheme.primary, in: Circle())
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("video_play_pause")
                .accessibilityLabel(state.isPlaying ? "일시정지" : "재생")
                Spacer()
                controlButton(systemName: "goforward.10", label: "10초 앞으로", action: onForwardClick)
                Spacer()
                controlButton(systemName: "arrow.up.left.and.arrow.down.right", label: "전체화면", action: onFullscreenClick)
            }
            HStack(spacing: 8) {
                Button(action: onSpeedClick) {
                    HStack(spacing: 4) {
                        Image(systemName: "gauge.with.dots.needle.67percent")
                            .font(.system(size: 13))
                        Text(state.speedText)
                            .font(.subheadline.weight(.medium))
                    }
                    .foregroundStyle(KlasTheme.onSecondaryContainer)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background(KlasTheme.secondaryContainer, in: Capsule())
                }
                .buttonStyle(.plain)

                if isPictureInPictureSupported {
                    Button(action: onPictureInPictureClick) {
                        HStack(spacing: 4) {
                            Image(systemName: "pip.enter")
                                .font(.system(size: 13))
                            Text("PIP로 재생")
                                .font(.subheadline.weight(.medium))
                        }
                        .foregroundStyle(KlasTheme.onSurface)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .background(KlasTheme.surfaceVariant.opacity(0.4), in: Capsule())
                        .overlay(
                            Capsule()
                                .stroke(KlasTheme.outline.opacity(0.3), lineWidth: 1)
                        )
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("video_pip")
                }
                Spacer()
                Button(action: onCloseClick) {
                    HStack(spacing: 4) {
                        Image(systemName: "xmark")
                            .font(.system(size: 13, weight: .semibold))
                        Text("학습 종료")
                            .font(.subheadline.weight(.medium))
                    }
                    .foregroundStyle(KlasTheme.primary)
                    .padding(.vertical, 8)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(20)
        .background(KlasTheme.surface, in: RoundedRectangle(cornerRadius: KlasTheme.controlCornerRadius, style: .continuous))
        .accessibilityIdentifier("video_player_controls")
    }

    private var header: some View {
        HStack(alignment: .center, spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text(state.lectureName.isEmpty ? "온라인 강의" : state.lectureName)
                    .font(.title3.weight(.bold))
                    .foregroundStyle(KlasTheme.onSurface)
                    .lineLimit(2)
                Text(state.lectureTime.isEmpty ? "진도율 불러오는 중" : state.lectureTime)
                    .font(.caption)
                    .foregroundStyle(KlasTheme.onSurfaceVariant)
            }
            Spacer()
            Button(action: onLectureTimeClick) {
                Image(systemName: "clock.arrow.circlepath")
                    .font(.system(size: 18, weight: .medium))
                    .foregroundStyle(KlasTheme.onSurfaceVariant)
                    .frame(width: 44, height: 44)
                    .background(KlasTheme.surfaceVariant, in: Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("마지막 시청 위치로 이동")
        }
    }

    private func controlButton(systemName: String, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: systemName)
                .font(.system(size: 18, weight: .medium))
                .foregroundStyle(KlasTheme.onSurfaceVariant)
                .frame(width: 44, height: 44)
                .background(KlasTheme.surfaceVariant, in: Circle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }
}

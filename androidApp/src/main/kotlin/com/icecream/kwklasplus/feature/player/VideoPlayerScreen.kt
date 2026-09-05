package com.icecream.kwklasplus.feature.player

import android.view.View
import com.icecream.kwklasplus.core.web.PlayerBridgeCodec
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.icecream.kwklasplus.ui.layout.AppWindowWidthClass
import com.icecream.kwklasplus.ui.layout.classifyWindowWidth
import com.icecream.kwklasplus.ui.theme.KlasControlShape
import com.icecream.kwklasplus.ui.theme.KlasPlusTheme
import com.icecream.kwklasplus.ui.web.ComposePlatformViewHost

data class VideoPlayerUiState(
    val lectureName: String = "",
    val lectureTime: String = "",
    val currentTime: String = "00:00",
    val totalTime: String = "",
    val durationSeconds: Float = 0f,
    val progress: Float = 0f,
    val isPlaying: Boolean = false,
    val isMuted: Boolean = true,
    val speedText: String = "1.0x",
)

@Composable
fun VideoPlayerScreen(
    webContainer: View,
    state: VideoPlayerUiState,
    isPlayerVisible: Boolean = true,
    isPictureInPicture: Boolean = false,
    onSeek: (Float) -> Unit,
    onPlayPauseClick: () -> Unit,
    onBackwardClick: () -> Unit,
    onForwardClick: () -> Unit,
    onMuteClick: () -> Unit,
    onFullscreenClick: () -> Unit,
    onPictureInPictureClick: () -> Unit,
    onSpeedClick: () -> Unit,
    onCloseClick: () -> Unit,
    onLectureTimeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mediaContent = remember(webContainer) {
        movableContentOf<Modifier> { contentModifier ->
            ComposePlatformViewHost(
                contentView = webContainer,
                isLoading = false,
                modifier = contentModifier,
                contentTag = "video_web_container",
            )
        }
    }
    PlayerScreenLayout(
        state = state,
        isPlayerVisible = isPlayerVisible,
        isPictureInPicture = isPictureInPicture,
        onSeek = onSeek,
        onPlayPauseClick = onPlayPauseClick,
        onBackwardClick = onBackwardClick,
        onForwardClick = onForwardClick,
        onMuteClick = onMuteClick,
        onFullscreenClick = onFullscreenClick,
        onPictureInPictureClick = onPictureInPictureClick,
        onSpeedClick = onSpeedClick,
        onCloseClick = onCloseClick,
        onLectureTimeClick = onLectureTimeClick,
        mediaContent = mediaContent,
        modifier = modifier,
    )
}

@Composable
private fun PlayerScreenLayout(
    state: VideoPlayerUiState,
    isPlayerVisible: Boolean,
    onSeek: (Float) -> Unit,
    onPlayPauseClick: () -> Unit,
    onBackwardClick: () -> Unit,
    onForwardClick: () -> Unit,
    onMuteClick: () -> Unit,
    onFullscreenClick: () -> Unit,
    onPictureInPictureClick: () -> Unit,
    onSpeedClick: () -> Unit,
    onCloseClick: () -> Unit,
    onLectureTimeClick: () -> Unit,
    mediaContent: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    isPictureInPicture: Boolean = false,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .then(if (isPictureInPicture) Modifier else Modifier.windowInsetsPadding(WindowInsets.safeDrawing)),
        ) {
            val expanded = classifyWindowWidth(maxWidth.value.toInt()) == AppWindowWidthClass.Expanded
            if (isPictureInPicture || !isPlayerVisible) {
                mediaContent(Modifier.fillMaxSize())
            } else if (expanded) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        mediaContent(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f),
                        )
                    }
                    PlayerControls(
                        state = state,
                        onSeek = onSeek,
                        onPlayPauseClick = onPlayPauseClick,
                        onBackwardClick = onBackwardClick,
                        onForwardClick = onForwardClick,
                        onMuteClick = onMuteClick,
                        onFullscreenClick = onFullscreenClick,
                        onPictureInPictureClick = onPictureInPictureClick,
                        onSpeedClick = onSpeedClick,
                        onCloseClick = onCloseClick,
                        onLectureTimeClick = onLectureTimeClick,
                        modifier = Modifier
                            .width(360.dp)
                            .fillMaxSize(),
                    )
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        mediaContent(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f),
                        )
                        PlayerControls(
                            state = state,
                            onSeek = onSeek,
                            onPlayPauseClick = onPlayPauseClick,
                            onBackwardClick = onBackwardClick,
                            onForwardClick = onForwardClick,
                            onMuteClick = onMuteClick,
                            onFullscreenClick = onFullscreenClick,
                            onPictureInPictureClick = onPictureInPictureClick,
                            onSpeedClick = onSpeedClick,
                            onCloseClick = onCloseClick,
                            onLectureTimeClick = onLectureTimeClick,
                            enableInternalScroll = false,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerControls(
    state: VideoPlayerUiState,
    onSeek: (Float) -> Unit,
    onPlayPauseClick: () -> Unit,
    onBackwardClick: () -> Unit,
    onForwardClick: () -> Unit,
    onMuteClick: () -> Unit,
    onFullscreenClick: () -> Unit,
    onPictureInPictureClick: () -> Unit,
    onSpeedClick: () -> Unit,
    onCloseClick: () -> Unit,
    onLectureTimeClick: () -> Unit,
    enableInternalScroll: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val controlsScrollState = rememberScrollState()
    val timeFormatter = remember { PlayerBridgeCodec() }
    var sliderProgress by remember {
        mutableFloatStateOf(state.progress.coerceIn(0f, 1f))
    }
    var isSeeking by remember { mutableStateOf(false) }

    LaunchedEffect(state.progress, isSeeking) {
        if (!isSeeking) {
            sliderProgress = state.progress.coerceIn(0f, 1f)
        }
    }

    Surface(
        modifier = modifier.padding(top = 16.dp),
        shape = KlasControlShape
    ) {
        Box(
            modifier = if (enableInternalScroll) {
                Modifier.fillMaxSize()
            } else {
                Modifier.fillMaxWidth()
            },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (enableInternalScroll) {
                            Modifier.verticalScroll(controlsScrollState)
                        } else {
                            Modifier
                        },
                    )
                    .padding(20.dp)
                    .testTag("video_player_controls"),
            ) {
                LectureHeader(
                    state = state,
                    onLectureTimeClick = onLectureTimeClick,
                )
                Spacer(Modifier.height(20.dp))
                Slider(
                    value = sliderProgress,
                    onValueChange = {
                        isSeeking = true
                        sliderProgress = it
                    },
                    onValueChangeFinished = {
                        isSeeking = false
                        onSeek(sliderProgress)
                    },
                    modifier = Modifier.testTag("video_progress"),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (isSeeking) {
                            timeFormatter.formatTime(sliderProgress * state.durationSeconds)
                        } else {
                            state.currentTime
                        },
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = state.totalTime,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlayerIconAction(
                        icon = if (state.isMuted) Icons.Outlined.VolumeOff else Icons.Outlined.VolumeUp,
                        contentDescription = if (state.isMuted) "소리 켜기" else "음소거",
                        onClick = onMuteClick,
                    )
                    PlayerIconAction(
                        icon = Icons.Outlined.Replay10,
                        contentDescription = "10초 뒤로",
                        onClick = onBackwardClick,
                    )
                    FilledIconButton(
                        onClick = onPlayPauseClick,
                        modifier = Modifier
                            .size(64.dp)
                            .testTag("video_play_pause"),
                        shape = CircleShape,
                    ) {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (state.isPlaying) "일시정지" else "재생",
                            modifier = Modifier.size(32.dp),
                        )
                    }
                    PlayerIconAction(
                        icon = Icons.Outlined.Forward10,
                        contentDescription = "10초 앞으로",
                        onClick = onForwardClick,
                    )
                    PlayerIconAction(
                        icon = Icons.Outlined.Fullscreen,
                        contentDescription = "전체화면",
                        onClick = onFullscreenClick,
                    )
                }
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AssistChip(
                        onClick = onSpeedClick,
                        label = { Text(state.speedText) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Speed,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            leadingIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    )
                    AssistChip(
                        onClick = onPictureInPictureClick,
                        label = { Text("PIP로 재생") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.PictureInPictureAlt,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onCloseClick) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("학습 종료")
                    }
                }
            }
        }
    }
}

@Composable
private fun LectureHeader(
    state: VideoPlayerUiState,
    onLectureTimeClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = state.lectureName.ifBlank { "온라인 강의" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = state.lectureTime.ifBlank { "진도율 불러오는 중" },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FilledTonalIconButton(
            onClick = onLectureTimeClick,
            modifier = Modifier.size(44.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.History,
                contentDescription = "마지막 시청 위치로 이동",
            )
        }
    }
}

@Composable
private fun PlayerIconAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(44.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
        )
    }
}

@Composable
private fun VideoPreviewSurface(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .clip(KlasControlShape),
        color = MaterialTheme.colorScheme.inverseSurface,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.inverseSurface),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.16f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.inverseOnSurface,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "WebView 플레이어 영역",
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}


@Preview(
    name = "강의 시청 - Mobile",
    showBackground = true,
    widthDp = 420,
    heightDp = 860,
)
@Preview(
    name = "강의 시청 - Tablet",
    showBackground = true,
    widthDp = 1280,
    heightDp = 800,
)
@Composable
private fun VideoPlayerScreenPreview() {
    KlasPlusTheme {
        PlayerScreenLayout(
            state = VideoPlayerUiState(
                lectureName = "2026학년도 1학기 운영체제",
                lectureTime = "3주차 · 프로세스와 스레드",
                currentTime = "12:34",
                totalTime = "47:20",
                progress = 0f,
                isPlaying = true,
                isMuted = false,
                speedText = "1.25x",
            ),
            isPlayerVisible = true,
            onSeek = {},
            onPlayPauseClick = {},
            onBackwardClick = {},
            onForwardClick = {},
            onMuteClick = {},
            onFullscreenClick = {},
            onPictureInPictureClick = {},
            onSpeedClick = {},
            onCloseClick = {},
            onLectureTimeClick = {},
            mediaContent = { VideoPreviewSurface(it) },
        )
    }
}

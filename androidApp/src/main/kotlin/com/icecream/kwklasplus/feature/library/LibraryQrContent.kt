package com.icecream.kwklasplus.feature.library

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

data class LibraryQrUiState(
    val name: String = "",
    val details: String = "",
    val bitmap: Bitmap? = null,
    val loading: Boolean = true,
    val secondsRemaining: Int = 30,
)

@Composable
fun LibraryQrContent(
    state: LibraryQrUiState,
    onRefreshClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 640.dp)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .testTag("library_qr_content"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(
                onClick = onRefreshClick,
                enabled = !state.loading,
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier.testTag("library_qr_refresh"),
            ) {
                Icon(Icons.Outlined.Refresh, contentDescription = null)
                Text(
                    text = when {
                        state.loading -> "불러오는 중"
                        state.bitmap != null -> "${state.secondsRemaining}초 후 갱신"
                        else -> "새로고침"
                    },
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            FilledTonalIconButton(
                onClick = onSettingsClick,
                modifier = Modifier.testTag("library_qr_settings"),
            ) {
                Icon(Icons.Outlined.Settings, contentDescription = "출입증 설정")
            }
        }
        Spacer(Modifier.height(20.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!state.loading && state.bitmap != null) {
                    Text(
                        text = state.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = state.details,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(20.dp))
                }
                when {
                    state.loading -> {
                        Spacer(Modifier.height(12.dp))
                        CircularProgressIndicator(
                        modifier = Modifier.testTag("library_qr_loading"),
                    )
                        Text("불러오는 중", style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface)
                    }
                    state.bitmap != null -> Image(
                        bitmap = state.bitmap.asImageBitmap(),
                        contentDescription = "중앙도서관 출입증 QR",
                        modifier = Modifier
                            .size(220.dp)
                            .testTag("library_qr_image"),
                    )
                    else -> {
                        Surface(shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.errorContainer) {
                            Icon(Icons.Outlined.ErrorOutline, null, Modifier.padding(16.dp).size(32.dp),
                                tint = MaterialTheme.colorScheme.onErrorContainer)
                        }
                        Text("도서관 출입증 정보를 가져올 수 없습니다. 설정에서 입력한 정보가 올바른지 확인한 후 다시 시도해주세요.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = "중앙도서관 이용 시 사용 가능합니다.\n공식 앱이 아니므로 이외 용도 사용 시 거절당할 수 있습니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

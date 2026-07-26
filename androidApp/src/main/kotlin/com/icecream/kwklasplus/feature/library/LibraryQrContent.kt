package com.icecream.kwklasplus.feature.library

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.icecream.kwklasplus.ui.theme.KlasButtonHeight
import com.icecream.kwklasplus.ui.theme.KlasControlShape
import com.icecream.kwklasplus.ui.theme.klasInverseButtonColors

data class LibraryQrUiState(
    val name: String = "",
    val details: String = "",
    val bitmap: Bitmap? = null,
    val loading: Boolean = true,
    val secondsRemaining: Int = 30,
    val isWidgetEntry: Boolean = false,
    val canAddWidget: Boolean = false,
)

@Composable
fun LibraryQrContent(
    state: LibraryQrUiState,
    onRefreshClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAddWidgetClick: () -> Unit,
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
            IconButton(
                onClick = onRefreshClick,
                modifier = Modifier.testTag("library_qr_refresh"),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = "QR 코드 새로고침, ${state.secondsRemaining}초 남음",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            if (!state.isWidgetEntry) {
                OutlinedButton(onClick = onSettingsClick) {
                    Text("설정")
                }
            }
        }
        Spacer(Modifier.height(20.dp))
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
        when {
            state.loading -> CircularProgressIndicator(
                modifier = Modifier.testTag("library_qr_loading"),
            )
            state.bitmap != null -> Image(
                bitmap = state.bitmap.asImageBitmap(),
                contentDescription = "중앙도서관 출입증 QR",
                modifier = Modifier
                    .size(220.dp)
                    .testTag("library_qr_image"),
            )
            else -> Text(
                text = "QR 코드를 표시할 수 없습니다.",
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = "중앙도서관 이용 시 사용 가능합니다.\n공식 앱이 아니므로 이외 용도 사용 시 거절당할 수 있습니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (!state.isWidgetEntry && state.canAddWidget) {
            Spacer(Modifier.height(20.dp))
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.padding(12.dp)) {
                    Button(
                        onClick = onAddWidgetClick,
                        shape = KlasControlShape,
                        colors = klasInverseButtonColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(KlasButtonHeight),
                    ) {
                        Text("홈 화면에 학생증 위젯 추가하기")
                    }
                }
            }
        }
    }
}

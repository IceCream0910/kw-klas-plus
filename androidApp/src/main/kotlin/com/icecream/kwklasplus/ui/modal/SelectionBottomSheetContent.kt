package com.icecream.kwklasplus.ui.modal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.icecream.kwklasplus.ui.theme.KlasButtonHeight
import com.icecream.kwklasplus.ui.theme.KlasControlShape
import com.icecream.kwklasplus.ui.theme.KlasPlusTheme

data class SelectionOption(
    val label: String,
    val onClick: () -> Unit,
)

@PreviewLightDark
@Composable
private fun Preview() {
    KlasPlusTheme {
        SelectionBottomSheetContent(
            options = listOf(
                SelectionOption(label = "Option 1", onClick = {}),
                SelectionOption(label = "Option 2", onClick = {}),
                SelectionOption(label = "Option 3", onClick = {}),
            ),
            title = "Selection Title",
            description = "This is a description of the selection options.",
        )
    }
}

@Composable
fun SelectionBottomSheetContent(
    options: List<SelectionOption>,
    title: String? = null,
    description: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 640.dp)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .testTag("selection_bottom_sheet"),
    ) {
        title?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        description?.let {
            Spacer(Modifier.height(4.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (title != null || description != null) Spacer(Modifier.height(16.dp))
        options.forEachIndexed { index, option ->
            TextButton(
                onClick = option.onClick,
                shape = KlasControlShape,
                contentPadding = PaddingValues(horizontal = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(KlasButtonHeight)
                    .testTag("selection_option_$index"),
            ) {
                Text(
                    text = option.label,
                    textAlign = TextAlign.Start,
                    color =  MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

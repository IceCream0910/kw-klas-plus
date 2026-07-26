package com.icecream.kwklasplus.feature.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.icecream.kwklasplus.ui.layout.AppWindowWidthClass
import com.icecream.kwklasplus.ui.layout.classifyWindowWidth

data class LockScreenUiState(
    val title: String,
    val description: String,
    val enteredDigits: Int,
    val biometricVisible: Boolean,
) {
    companion object {
        const val PIN_LENGTH = 6
    }
}

@Composable
fun LockScreen(
    state: LockScreenUiState,
    onNumberClick: (Int) -> Unit,
    onDeleteClick: () -> Unit,
    onBiometricClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            val widthClass = classifyWindowWidth(maxWidth.value.toInt())
            val useTwoPane = widthClass == AppWindowWidthClass.Expanded ||
                widthClass == AppWindowWidthClass.Medium && maxHeight < 600.dp
            val horizontalPadding = when (widthClass) {
                AppWindowWidthClass.Compact -> 20.dp
                AppWindowWidthClass.Medium -> 40.dp
                AppWindowWidthClass.Expanded -> 64.dp
            }

            if (useTwoPane) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = horizontalPadding, vertical = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LockSummary(
                        state = state,
                        onBiometricClick = onBiometricClick,
                        modifier = Modifier.weight(1f),
                    )
                    LockKeypad(
                        onNumberClick = onNumberClick,
                        onDeleteClick = onDeleteClick,
                        modifier = Modifier
                            .weight(1f)
                            .widthIn(max = 440.dp),
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = horizontalPadding, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    LockSummary(
                        state = state,
                        onBiometricClick = onBiometricClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                    Spacer(Modifier.height(24.dp))
                    LockKeypad(
                        onNumberClick = onNumberClick,
                        onDeleteClick = onDeleteClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 440.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LockSummary(
    state: LockScreenUiState,
    onBiometricClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = state.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = state.description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Row(
            modifier = Modifier.testTag("pin_indicators"),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            repeat(LockScreenUiState.PIN_LENGTH) { index ->
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(
                            if (index < state.enteredDigits) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                        ),
                )
            }
        }
        if (state.biometricVisible) {
            Spacer(Modifier.height(16.dp))
            TextButton(
                onClick = onBiometricClick,
                modifier = Modifier.testTag("biometric_button"),
            ) {
                Text("생체인식 사용")
            }
        }
    }
}

@Composable
private fun LockKeypad(
    onNumberClick: (Int) -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.testTag("pin_keypad"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        (1..9).chunked(3).forEach { row ->
            KeypadRow {
                row.forEach { number ->
                    KeypadButton(
                        label = number.toString(),
                        onClick = { onNumberClick(number) },
                        modifier = Modifier.testTag("pin_$number"),
                    )
                }
            }
        }
        KeypadRow {
            TextButton(
                onClick = onDeleteClick,
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .testTag("pin_delete"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Backspace,
                    contentDescription = "한 자리 삭제",
                )
            }
            KeypadButton(
                label = "0",
                onClick = { onNumberClick(0) },
                modifier = Modifier.testTag("pin_0"),
            )
            Spacer(
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "비밀번호를 잊어버린 경우 앱을 재설치해야 해요.",
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun KeypadRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

@Composable
private fun RowScope.KeypadButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier
            .weight(1f)
            .height(64.dp),
    ) {
        Text(
            text = label,
            style = if (label.length == 1) {
                MaterialTheme.typography.headlineSmall
            } else {
                MaterialTheme.typography.labelLarge
            },
        )
    }
}

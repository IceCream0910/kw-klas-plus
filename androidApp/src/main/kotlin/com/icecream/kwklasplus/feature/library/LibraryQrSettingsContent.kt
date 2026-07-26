package com.icecream.kwklasplus.feature.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.icecream.kwklasplus.ui.theme.KlasButtonHeight
import com.icecream.kwklasplus.ui.theme.KlasControlShape
import com.icecream.kwklasplus.ui.theme.klasInverseButtonColors

data class LibraryQrSettingsUiState(
    val studentNumber: String,
    val password: String,
    val phone: String,
) {
    val canSave: Boolean
        get() = studentNumber.isNotBlank() && password.isNotBlank() && phone.isNotBlank()
}

@Composable
fun LibraryQrSettingsContent(
    state: LibraryQrSettingsUiState,
    onStudentNumberChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var passwordVisible by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 640.dp)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(20.dp)
            .testTag("library_qr_settings"),
    ) {
        Text(
            text = "중앙도서관 출입증 설정",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = state.studentNumber,
            onValueChange = onStudentNumberChange,
            shape = KlasControlShape,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("학번") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = state.password,
            onValueChange = onPasswordChange,
            shape = KlasControlShape,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("중앙도서관 비밀번호") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = if (passwordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                TextButton(onClick = { passwordVisible = !passwordVisible }) {
                    Text(if (passwordVisible) "숨김" else "표시")
                }
            },
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = state.phone,
            onValueChange = onPhoneChange,
            shape = KlasControlShape,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("전화번호") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onSaveClick,
            enabled = state.canSave,
            shape = KlasControlShape,
            colors = klasInverseButtonColors(),
            modifier = Modifier
                .fillMaxWidth()
                .height(KlasButtonHeight)
                .testTag("library_qr_settings_save"),
        ) {
            Text("저장")
        }
    }
}

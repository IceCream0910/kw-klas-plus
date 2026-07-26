package com.icecream.kwklasplus.feature.auth

import android.webkit.WebView
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.icecream.kwklasplus.ui.layout.AppWindowWidthClass
import com.icecream.kwklasplus.ui.layout.classifyWindowWidth
import com.icecream.kwklasplus.ui.theme.KlasPlusTheme
import com.icecream.kwklasplus.ui.theme.KlasButtonHeight
import com.icecream.kwklasplus.ui.theme.KlasControlShape
import com.icecream.kwklasplus.ui.theme.klasInverseButtonColors

data class LoginUiState(
    val onboardingVisible: Boolean,
    val studentId: String,
    val password: String,
    val agreementAccepted: Boolean,
) {
    val passwordVisible: Boolean
        get() = studentId.length == STUDENT_ID_LENGTH

    val loginEnabled: Boolean
        get() = passwordVisible && password.isNotEmpty() && agreementAccepted

    companion object {
        const val STUDENT_ID_LENGTH = 10
    }
}

@Composable
fun LoginScreen(
    state: LoginUiState,
    onboardingWebView: WebView?,
    onStartClick: () -> Unit,
    onStudentIdChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onAgreementChange: (Boolean) -> Unit,
    onAgreementDetailsClick: () -> Unit,
    onFindIdClick: () -> Unit,
    onFindPasswordClick: () -> Unit,
    onRegisterClick: () -> Unit,
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        if (state.onboardingVisible) {
            OnboardingContent(
                webView = onboardingWebView,
                onStartClick = onStartClick,
            )
        } else {
            LoginContent(
                state = state,
                onStudentIdChange = onStudentIdChange,
                onPasswordChange = onPasswordChange,
                onAgreementChange = onAgreementChange,
                onAgreementDetailsClick = onAgreementDetailsClick,
                onFindIdClick = onFindIdClick,
                onFindPasswordClick = onFindPasswordClick,
                onRegisterClick = onRegisterClick,
                onLoginClick = onLoginClick,
            )
        }
    }
}

@Preview(
    name = "온보딩",
    showBackground = true,
    widthDp = 420,
    heightDp = 860,
)
@Preview(
    name = "온보딩 - Expanded",
    showBackground = true,
    widthDp = 1000,
    heightDp = 700,
)
@Composable
private fun LoginScreenPreview() {
    KlasPlusTheme {
        LoginScreen(
            state = LoginUiState(
                onboardingVisible = false,
                studentId = "",
                password = "",
                agreementAccepted = true,
            ),
            onboardingWebView = null,
            onStartClick = {},
            onStudentIdChange = {},
            onPasswordChange = {},
            onAgreementChange = {},
            onAgreementDetailsClick = {},
            onFindIdClick = {},
            onFindPasswordClick = {},
            onRegisterClick = {},
            onLoginClick = {},
        )
    }
}

@Composable
private fun OnboardingContent(
    webView: WebView?,
    onStartClick: () -> Unit,
) {
    val inspectionMode = LocalInspectionMode.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("login_onboarding"),
    ) {
        when {
            webView != null -> AndroidView(
                factory = {
                    webView.apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                    }
                },
                update = { view ->
                    val layoutParams = view.layoutParams
                    if (
                        layoutParams.width != ViewGroup.LayoutParams.MATCH_PARENT ||
                        layoutParams.height != ViewGroup.LayoutParams.MATCH_PARENT
                    ) {
                        layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
                        layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                        view.layoutParams = layoutParams
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("login_onboarding_web_view"),
            )

            inspectionMode -> OnboardingWebViewPreview()
            else -> error("온보딩에는 WebView가 필요합니다.")
        }
        Button(
            onClick = onStartClick,
            shape = KlasControlShape,
            colors = klasInverseButtonColors(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .height(KlasButtonHeight)
                .testTag("login_start"),
        ) {
            Text("시작하기")
        }
    }
}

@Composable
private fun OnboardingWebViewPreview() {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("login_onboarding_web_view"),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "온보딩 WebView 미리보기",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LoginContent(
    state: LoginUiState,
    onStudentIdChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onAgreementChange: (Boolean) -> Unit,
    onAgreementDetailsClick: () -> Unit,
    onFindIdClick: () -> Unit,
    onFindPasswordClick: () -> Unit,
    onRegisterClick: () -> Unit,
    onLoginClick: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
            .pointerInput(Unit) {
                detectTapGestures { focusManager.clearFocus() }
            }
            .testTag("login_form"),
    ) {
        val widthClass = classifyWindowWidth(maxWidth.value.toInt())
        if (widthClass == AppWindowWidthClass.Expanded) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 64.dp, vertical = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(64.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LoginHeader(
                    passwordVisible = state.passwordVisible,
                    modifier = Modifier.weight(1f),
                )
                Box(modifier = Modifier.weight(1f)) {
                    LoginFields(
                        state = state,
                        onStudentIdChange = onStudentIdChange,
                        onPasswordChange = onPasswordChange,
                        onAgreementChange = onAgreementChange,
                        onAgreementDetailsClick = onAgreementDetailsClick,
                        onFindIdClick = onFindIdClick,
                        onFindPasswordClick = onFindPasswordClick,
                        onRegisterClick = onRegisterClick,
                        onLoginClick = onLoginClick,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .widthIn(max = 520.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            }
        } else if (widthClass == AppWindowWidthClass.Compact) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 560.dp)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(top = 32.dp)
                        .align(Alignment.TopCenter),
                ) {
                    LoginHeader(passwordVisible = state.passwordVisible)
                    Spacer(Modifier.height(24.dp))
                    LoginFields(
                        state = state,
                        onStudentIdChange = onStudentIdChange,
                        onPasswordChange = onPasswordChange,
                        onAgreementChange = onAgreementChange,
                        onAgreementDetailsClick = onAgreementDetailsClick,
                        onFindIdClick = onFindIdClick,
                        onFindPasswordClick = onFindPasswordClick,
                        onRegisterClick = onRegisterClick,
                        onLoginClick = onLoginClick,
                        showSubmitActions = false,
                    )
                }
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .widthIn(max = 560.dp)
                ) {
                    LoginSubmitActions(
                        state = state,
                        onAgreementChange = onAgreementChange,
                        onAgreementDetailsClick = onAgreementDetailsClick,
                        onLoginClick = onLoginClick,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = if (widthClass == AppWindowWidthClass.Medium) 48.dp else 20.dp)
                    .padding(vertical = 32.dp)
                    .align(Alignment.TopCenter),
            ) {
                LoginHeader(passwordVisible = state.passwordVisible)
                Spacer(Modifier.height(24.dp))
                LoginFields(
                    state = state,
                    onStudentIdChange = onStudentIdChange,
                    onPasswordChange = onPasswordChange,
                    onAgreementChange = onAgreementChange,
                    onAgreementDetailsClick = onAgreementDetailsClick,
                    onFindIdClick = onFindIdClick,
                    onFindPasswordClick = onFindPasswordClick,
                    onRegisterClick = onRegisterClick,
                    onLoginClick = onLoginClick,
                )
            }
        }
    }
}

@Composable
private fun LoginHeader(
    passwordVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = if (passwordVisible) {
                "KLAS 비밀번호를 입력해주세요."
            } else {
                "학번을 입력해주세요."
            },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = "ⓘ 입력한 정보는 학교 공식 KLAS 서버로만 전송되며, KLAS+ 서버에는 별도로 저장되지 않습니다.",
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LoginFields(
    state: LoginUiState,
    onStudentIdChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onAgreementChange: (Boolean) -> Unit,
    onAgreementDetailsClick: () -> Unit,
    onFindIdClick: () -> Unit,
    onFindPasswordClick: () -> Unit,
    onRegisterClick: () -> Unit,
    onLoginClick: () -> Unit,
    showSubmitActions: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val idFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var passwordVisible by remember { mutableStateOf(false) }
    var idWasFocused by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        idFocusRequester.requestFocus()
        keyboardController?.show()
    }

    LaunchedEffect(state.passwordVisible, idWasFocused) {
        if (state.passwordVisible && idWasFocused) {
            passwordFocusRequester.requestFocus()
        }
    }

    Column(modifier = modifier
        .fillMaxWidth()
    ) {
        OutlinedTextField(
            value = state.studentId,
            onValueChange = onStudentIdChange,
            shape = KlasControlShape,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(idFocusRequester)
                .onFocusChanged { if (it.isFocused) idWasFocused = true }
                .testTag("login_student_id"),
            label = { Text("학번") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(
                onNext = {
                    if (state.passwordVisible) passwordFocusRequester.requestFocus()
                },
            ),
        )

        if (state.passwordVisible) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.password,
                onValueChange = onPasswordChange,
                shape = KlasControlShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(passwordFocusRequester)
                    .testTag("login_password"),
                label = { Text("비밀번호") },
                singleLine = true,
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) {
                                Icons.Outlined.VisibilityOff
                            } else {
                                Icons.Outlined.Visibility
                            },
                            contentDescription = if (passwordVisible) {
                                "비밀번호 숨기기"
                            } else {
                                "비밀번호 표시"
                            },
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (state.loginEnabled) {
                            onLoginClick()
                        } else {
                            keyboardController?.hide()
                        }
                    },
                ),
            )
        }


        Spacer(Modifier.height(12.dp))
        TextButton(
            onClick = if (state.passwordVisible) onFindPasswordClick else onFindIdClick,
            modifier = Modifier
                .height(40.dp)
                .testTag("login_recovery"),
            contentPadding = PaddingValues(horizontal = 8.dp),
        ) {
            Text(
                if (state.passwordVisible) {
                    "비밀번호를 잊어버렸나요?"
                } else {
                    "학번이 생각나지 않나요?"
                },
            )
        }
        if (!state.passwordVisible) {
            TextButton(
                onClick = onRegisterClick,
                modifier = Modifier
                    .height(40.dp)
                    .testTag("login_register"),
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Text("KLAS에 처음 로그인하시나요?")
            }
        }
        if (showSubmitActions) {
            Spacer(Modifier.height(20.dp))
            LoginSubmitActions(
                state = state,
                onAgreementChange = onAgreementChange,
                onAgreementDetailsClick = onAgreementDetailsClick,
                onLoginClick = onLoginClick,
            )
        }
    }
}

@Composable
private fun LoginSubmitActions(
    state: LoginUiState,
    onAgreementChange: (Boolean) -> Unit,
    onAgreementDetailsClick: () -> Unit,
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier
        .padding(8.dp, 16.dp)
        .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundCheckbox(
                checked = state.agreementAccepted,
                onCheckedChange = onAgreementChange,
                modifier = Modifier.testTag("login_agreement"),
            )
            Text(
                text = "개인정보 수집 및 이용/제공 동의",
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        onAgreementChange(!state.agreementAccepted)
                    }
                    .padding(vertical = 8.dp)
                    .testTag("login_agreement_label"),
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = onAgreementDetailsClick) {
                Text("자세히")
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onLoginClick,
            enabled = state.loginEnabled,
            shape = KlasControlShape,
            colors = klasInverseButtonColors(),
            modifier = Modifier
                .fillMaxWidth()
                .height(KlasButtonHeight)
                .testTag("login_submit"),
        ) {
            Text("확인", textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun RoundCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    IconToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier.size(40.dp),
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(
                    color = if (checked) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = RoundedCornerShape(8.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    modifier = Modifier.size(16.dp),
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

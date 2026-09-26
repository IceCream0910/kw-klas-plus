package com.icecream.kwklasplus.feature.auth

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.icecream.kwklasplus.ui.layout.AppWindowWidthClass
import com.icecream.kwklasplus.ui.layout.classifyWindowWidth
import com.icecream.kwklasplus.ui.theme.KlasPlusTheme
import com.icecream.kwklasplus.R
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
    autoAdvanceOnboarding: Boolean = true,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        if (state.onboardingVisible) {
            OnboardingContent(onStartClick = onStartClick, autoAdvance = autoAdvanceOnboarding)
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
                onboardingVisible = true,
                studentId = "",
                password = "",
                agreementAccepted = true,
            ),
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
    onStartClick: () -> Unit,
    autoAdvance: Boolean,
) {
    val slides = listOf(
        Triple("불편했던 KLAS를\n더 편리하게.", "KLAS+는 모바일에 맞게 학사포털의\n사용자 경험을 다시 설계했어요.", R.drawable.onboarding_placeholder),
        Triple("남아있는 할 일을\n한 눈에.", "과제, 온라인 강의 등 남아있는 할 일을\n한 눈에 모아서 홈 화면에 보여줄게요.", R.drawable.onboarding_todo),
        Triple("복잡한 메뉴를\n깔끔하게.", "PC와 달라 불편했던 복잡한 메뉴들을 한 페이지에서 찾고,\n자주 쓰는 메뉴를 상단에 고정할 수 있어요.", R.drawable.onboarding_menu),
        Triple("학교 생활을 위한\n나만의 캘린더.", "학사일정, 개인 스케줄은 물론 과제 마감기한까지,\n대학 생활에 필요한 모든 일정을 관리해보세요.", R.drawable.onboarding_calendar),
        Triple("궁금한 건\nKLAS AI에게.", "학교 홈페이지와 KLAS를 누비는\n다재다능한 AI 에이전트와 함께해보세요.", R.drawable.onboarding_ai),
    )
    val pagerState = rememberPagerState(pageCount = { slides.size })
    val scope = rememberCoroutineScope()
    LaunchedEffect(pagerState.settledPage) {
        val page = pagerState.settledPage
        if (autoAdvance && page < slides.lastIndex) {
            delay(5_000)
            pagerState.animateScrollToPage(page + 1)
        }
    }
    BackHandler(enabled = pagerState.currentPage > 0) {
        scope.launch {
            pagerState.animateScrollToPage(pagerState.currentPage - 1)
        }
    }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("login_onboarding"),
    ) {
        val horizontalPadding = if (maxWidth >= 840.dp) 32.dp else 16.dp
        val footerInset = if (maxWidth >= 840.dp) 16.dp else 8.dp
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 680.dp)
                .align(Alignment.Center)
                .padding(horizontal = horizontalPadding, vertical = 24.dp),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth().testTag("onboarding_pager"),
            ) { page ->
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val pageHeight = maxHeight
                    val imageAreaHeight = (pageHeight * 0.62f).coerceAtMost(600.dp)
                    val imageWidth = minOf(maxWidth, imageAreaHeight * (8f / 9f))
                    val copyInset = if (maxWidth >= 600.dp) 16.dp else 8.dp
                    Column(Modifier.fillMaxSize()) {
                        Spacer(Modifier.weight(1f))
                        Box(
                            modifier = Modifier.fillMaxWidth().height(imageAreaHeight),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            Image(
                                painter = painterResource(slides[page].third),
                                contentDescription = "KLAS+ 앱 화면 미리보기",
                                contentScale = ContentScale.FillBounds,
                                modifier = Modifier
                                    .width(imageWidth)
                                    .height(imageWidth * (9f / 8f))
                                    .clip(RoundedCornerShape(20.dp))
                                    .testTag("onboarding_image"),
                            )
                        }
                        Spacer(Modifier.height(32.dp))
                        Column(
                            Modifier
                                .heightIn(max = (pageHeight - imageAreaHeight - 24.dp).coerceAtLeast(0.dp))
                                .padding(horizontal = copyInset)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                slides[page].first,
                                color = MaterialTheme.colorScheme.onBackground,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                slides[page].second,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (page == 0) {
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "⚠️ KLAS+는 개인이 개발한 것으로, 학교의 공식 앱이 아닙니다.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(48.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = footerInset),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(slides.size) { index ->
                        Box(
                            Modifier
                                .size(if (index == pagerState.currentPage) 22.dp else 8.dp, 8.dp)
                                .background(
                                    if (index == pagerState.currentPage) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.outlineVariant,
                                    RoundedCornerShape(50),
                                )
                        )
                    }
                }
                Button(
                    onClick = onStartClick,
                    shape = RoundedCornerShape(24.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    modifier = Modifier.height(50.dp).testTag("login_start"),
                ) {
                    Text("로그인")
                    Spacer(Modifier.size(8.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                }
            }
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

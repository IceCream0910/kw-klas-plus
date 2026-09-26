package com.icecream.kwklasplus.feature.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.icecream.kwklasplus.ui.theme.KlasControlShape
import com.icecream.kwklasplus.ui.theme.KlasButtonHeight
import com.icecream.kwklasplus.ui.theme.klasInverseButtonColors
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

internal enum class LoginFunnelStep { StudentId, Password, Authenticating, Library, Agreements, Complete }

internal data class LoginFunnelUiState(
    val step: LoginFunnelStep,
    val studentId: String,
    val password: String,
    val libraryPassword: String,
    val libraryPhone: String,
    val privacyAccepted: Boolean,
    val termsAccepted: Boolean,
    val error: String?,
)

@OptIn(ExperimentalAnimationApi::class)
@Composable
internal fun LoginFunnelScreen(
    state: LoginFunnelUiState,
    onStudentIdChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLibraryPasswordChange: (String) -> Unit,
    onLibraryPhoneChange: (String) -> Unit,
    onPrivacyChange: (Boolean) -> Unit,
    onTermsChange: (Boolean) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    onSkipLibrary: () -> Unit,
    onSaveLibrary: () -> Unit,
    onPrivacyDetails: () -> Unit,
    onTermsDetails: () -> Unit,
    onFindId: () -> Unit,
    onFindPassword: () -> Unit,
    onFinish: () -> Unit,
    canReturnToOnboarding: Boolean = false,
) {
    val step = state.step
    val focusManager = LocalFocusManager.current
    val stepNumber = when (step) {
        LoginFunnelStep.StudentId -> 1
        LoginFunnelStep.Password, LoginFunnelStep.Authenticating -> 2
        LoginFunnelStep.Library -> 3
        LoginFunnelStep.Agreements -> 4
        LoginFunnelStep.Complete -> 5
    }
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.safeDrawing).imePadding().testTag("login_form"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 600.dp).fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if ((step == LoginFunnelStep.StudentId && canReturnToOnboarding) ||
                    step == LoginFunnelStep.Password || step == LoginFunnelStep.Agreements || step == LoginFunnelStep.Complete) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("funnel_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "이전 단계", tint = MaterialTheme.colorScheme.onBackground)
                    }
                } else {
                    Spacer(Modifier.height(48.dp))
                }
                Spacer(Modifier.weight(1f))
                Text("$stepNumber / 5", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                (slideInHorizontally { it / 4 } + fadeIn()) togetherWith
                    (slideOutHorizontally { -it / 4 } + fadeOut())
            },
            contentAlignment = Alignment.TopCenter,
            label = "로그인 단계",
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { current ->
            Column(
                modifier = Modifier.widthIn(max = 560.dp).fillMaxSize()
                    .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                verticalArrangement = if (current == LoginFunnelStep.Complete) Arrangement.Center else Arrangement.Top,
                horizontalAlignment = if (current == LoginFunnelStep.Complete) Alignment.CenterHorizontally else Alignment.Start,
            ) {
                if (current != LoginFunnelStep.Complete) Spacer(Modifier.height(32.dp))
                when (current) {
                    LoginFunnelStep.StudentId -> {
                        val focusRequester = remember { FocusRequester() }
                        LaunchedEffect(Unit) { focusRequester.requestFocus() }
                        FunnelHeading("학번을 입력해주세요.", "ⓘ 입력한 정보는 학교 공식 KLAS 서버로만 전송되며, KLAS+ 서버에는 별도로 저장되지 않습니다.")
                        OutlinedTextField(
                            value = state.studentId,
                            onValueChange = onStudentIdChange,
                            shape = KlasControlShape,
                            label = { Text("학번") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).testTag("login_student_id"),
                        )
                        TextButton(onClick = onFindId) { Text("학번이 생각나지 않나요?") }
                    }
                    LoginFunnelStep.Password -> {
                        FunnelHeading("KLAS 비밀번호를 입력해주세요.", "ⓘ 입력한 정보는 학교 공식 KLAS 서버로만 전송되며, KLAS+ 서버에는 별도로 저장되지 않습니다.")
                        val focusRequester = remember { FocusRequester() }
                        LaunchedEffect(Unit) { focusRequester.requestFocus() }
                        var revealed by remember { mutableStateOf(false) }
                        OutlinedTextField(
                            value = state.password,
                            onValueChange = onPasswordChange,
                            shape = KlasControlShape,
                            label = { Text("비밀번호") },
                            visualTransformation = if (revealed) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = { IconButton(onClick = { revealed = !revealed }) {
                                Icon(if (revealed) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = if (revealed) "비밀번호 숨기기" else "비밀번호 표시")
                            } },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).testTag("login_password"),
                        )
                        TextButton(onClick = onFindPassword) { Text("비밀번호를 잊어버렸나요?") }
                        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                    LoginFunnelStep.Authenticating -> {
                        Spacer(Modifier.height(56.dp))
                        CircularProgressIndicator()
                        Spacer(Modifier.height(32.dp))
                        FunnelHeading("KLAS에 로그인하고 있어요", "잠시만 기다려 주세요.")
                    }
                    LoginFunnelStep.Library -> {
                        val phoneFocusRequester = remember { FocusRequester() }
                        FunnelHeading("도서관 출입증도 설정해보세요.", "중앙도서관 출입증 정보를 설정해놓으면, 홈 화면 위젯에서 QR을 바로 열 수 있어요. 지금 건너뛰어도 언제든 앱 설정이나 전체 > 상단 프로필을 클릭해 모바일 학생증 팝업에서 다시 설정할 수 있어요.")
                        OutlinedTextField(state.studentId, {}, shape = KlasControlShape, label = { Text("학번") }, readOnly = true, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            state.libraryPassword, onLibraryPasswordChange,
                            shape = KlasControlShape,
                            label = { Text("중앙도서관 비밀번호") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { phoneFocusRequester.requestFocus() }),
                            modifier = Modifier.fillMaxWidth().testTag("library_password"),
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            state.libraryPhone, onLibraryPhoneChange,
                            shape = KlasControlShape,
                            label = { Text("전화번호") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            modifier = Modifier.fillMaxWidth().focusRequester(phoneFocusRequester).testTag("library_phone"),
                        )
                        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                    LoginFunnelStep.Agreements -> {
                        FunnelHeading("마지막으로 약관에 동의해주세요.", "KLAS+를 사용하기 위해 아래 약관에 필수로 동의해야 해요.\n개인정보는 자체 서버에 저장되지 않고 안전하게 처리되며, 서비스 개선을 위해 사용 데이터가 수집돼요.")
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onPrivacyChange(!state.privacyAccepted) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = state.privacyAccepted,
                                onCheckedChange = onPrivacyChange,
                                modifier = Modifier.testTag("login_agreement"),
                            )
                            Text("[필수] 개인정보 처리방침 확인 및 동의", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            TextButton(onClick = onPrivacyDetails) { Text("보기") }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onTermsChange(!state.termsAccepted) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = state.termsAccepted,
                                onCheckedChange = onTermsChange,
                                modifier = Modifier.testTag("login_terms_agreement"),
                            )
                            Text("[필수] 이용약관 동의", color = MaterialTheme.colorScheme.onBackground,
                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            TextButton(onClick = onTermsDetails) { Text("보기") }
                        }
                    }
                    LoginFunnelStep.Complete -> {
                        CompletionCheck()
                        Spacer(Modifier.height(24.dp))
                        Text("모두 완료되었어요!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                        Spacer(Modifier.height(8.dp))
                        Text("이제 KLAS+를 사용할 준비가 끝났어요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (step != LoginFunnelStep.Authenticating) {
            Column(Modifier.widthIn(max = 600.dp).fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp)) {
                val enabled = when (step) {
                    LoginFunnelStep.StudentId -> state.studentId.length == LoginUiState.STUDENT_ID_LENGTH
                    LoginFunnelStep.Password -> state.password.isNotEmpty()
                    LoginFunnelStep.Library -> state.libraryPassword.isNotBlank() && state.libraryPhone.isNotBlank()
                    LoginFunnelStep.Agreements -> true
                    else -> true
                }
                Button(
                    onClick = when (step) {
                        LoginFunnelStep.Library -> onSaveLibrary
                        LoginFunnelStep.Complete -> onFinish
                        else -> onContinue
                    },
                    enabled = enabled,
                    shape = KlasControlShape,
                    colors = klasInverseButtonColors(),
                    modifier = Modifier.fillMaxWidth().height(KlasButtonHeight).testTag("login_submit"),
                ) {
                    Text(when (step) {
                        LoginFunnelStep.StudentId -> "다음"
                        LoginFunnelStep.Password -> "로그인하기"
                        LoginFunnelStep.Library -> "출입증 저장하기"
                        LoginFunnelStep.Agreements -> "모두 동의하고 계속"
                        LoginFunnelStep.Complete -> "홈으로 가기"
                        LoginFunnelStep.Authenticating -> "다음"
                    })
                }
                if (step == LoginFunnelStep.Library) {
                    TextButton(
                        onClick = onSkipLibrary,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp).testTag("funnel_skip_library"),
                    ) { Text("지금은 건너뛰기", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

@Composable
private fun FunnelHeading(title: String, description: String) {
    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
    Spacer(Modifier.height(8.dp))
    Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun CompletionCheck() {
    val scale = remember { Animatable(0.6f) }
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    suspend fun celebrate() {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        scale.snapTo(0.6f)
        scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 300f))
    }
    LaunchedEffect(Unit) { celebrate() }
    IconButton(onClick = { scope.launch { celebrate() } }, modifier = Modifier.size(96.dp)) {
        Icon(Icons.Default.CheckCircle, contentDescription = "완료 축하 애니메이션 다시 재생",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(80.dp).graphicsLayer { scaleX = scale.value; scaleY = scale.value })
    }
}

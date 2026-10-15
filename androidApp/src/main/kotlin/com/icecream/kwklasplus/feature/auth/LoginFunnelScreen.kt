package com.icecream.kwklasplus.feature.auth

import com.icecream.kwklasplus.ui.onboarding.*
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import androidx.compose.runtime.derivedStateOf
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

internal enum class LoginFunnelStep { StudentId, Password, Authenticating, Library, AppLock, Notifications, Agreements, Complete }

internal data class LoginFunnelUiState(
    val step: LoginFunnelStep,
    val studentId: String,
    val password: String,
    val libraryPassword: String,
    val libraryPhone: String,
    val privacyAccepted: Boolean,
    val termsAccepted: Boolean,
    val error: String?,
    val appLockEnabled: Boolean = false,
    val deadlineNotificationsEnabled: Boolean = false,
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
    onConfigureNotifications: () -> Unit = {},
    onConfigureAppLock: () -> Unit = {},
) {
    val step = state.step
    androidx.compose.runtime.LaunchedEffect(step) {
        if (step == LoginFunnelStep.Complete) {
            kotlinx.coroutines.delay(2_000)
            onFinish()
        }
    }
    val focusManager = LocalFocusManager.current
    val keyboardInsets = WindowInsets.ime
    val density = LocalDensity.current
    val keyboardVisible by remember(keyboardInsets, density) {
        derivedStateOf { keyboardInsets.getBottom(density) > 0 }
    }
    val isLoginInput = step == LoginFunnelStep.StudentId || step == LoginFunnelStep.Password
    val stepNumber = when (step) {
        LoginFunnelStep.StudentId -> 1
        LoginFunnelStep.Password, LoginFunnelStep.Authenticating -> 2
        LoginFunnelStep.Library -> 3
        LoginFunnelStep.Agreements -> 3
        LoginFunnelStep.AppLock -> 4
        LoginFunnelStep.Notifications -> 5
        LoginFunnelStep.Complete -> 3
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        FunnelGlow(Modifier.align(Alignment.TopCenter))
        Column(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding().testTag("login_form"),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 600.dp).fillMaxWidth().padding(horizontal = 20.dp, vertical = if (isLoginInput) 4.dp else 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if ((step == LoginFunnelStep.StudentId && canReturnToOnboarding) ||
                        step == LoginFunnelStep.Password || step == LoginFunnelStep.AppLock || step == LoginFunnelStep.Notifications) {
                        IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp).testTag("funnel_back")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "이전 단계", tint = MaterialTheme.colorScheme.onBackground)
                        }
                    } else {
                        Spacer(Modifier.height(48.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    Text("$stepNumber / 3", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    if (current != LoginFunnelStep.Complete) {
                        Spacer(Modifier.height(12.dp))
                        FunnelIllustration(when (current) {
                            LoginFunnelStep.StudentId -> FunnelArtwork.Login
                            LoginFunnelStep.Password, LoginFunnelStep.Authenticating -> FunnelArtwork.Password
                            LoginFunnelStep.Library -> FunnelArtwork.Library
                            LoginFunnelStep.AppLock -> FunnelArtwork.Lock
                            LoginFunnelStep.Notifications -> FunnelArtwork.Notification
                            else -> FunnelArtwork.Agreement
                        }, modifier = if (isLoginInput) Modifier.height(48.dp) else Modifier, plain = true)
                    }
                    when (current) {
                        LoginFunnelStep.StudentId -> {
                            val focusRequester = remember { FocusRequester() }
                            LaunchedEffect(Unit) { focusRequester.requestFocus() }
                            FunnelHeading("학번을 입력해주세요.", "로그인 정보는 이 기기에서 학교 KLAS 서버로 직접 전달되며 KLAS+ 서버에 저장되지 않아요.", loginInfo = true)
                            LoginInputViewport(keyboardVisible = keyboardVisible, error = null) {
                                OutlinedTextField(
                                    value = state.studentId,
                                    onValueChange = onStudentIdChange,
                                    shape = KlasControlShape,
                                    label = { Text("학번") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).testTag("login_student_id"),
                                )
                            }
                            TextButton(onClick = onFindId) { Text("학번이 생각나지 않나요?") }
                        }
                        LoginFunnelStep.Password -> {
                            FunnelHeading("KLAS 비밀번호를 입력해주세요.", "로그인 정보는 이 기기에서 학교 KLAS 서버로 직접 전달되며 KLAS+ 서버에 저장되지 않아요.", loginInfo = true)
                            val focusRequester = remember { FocusRequester() }
                            LaunchedEffect(Unit) { focusRequester.requestFocus() }
                            var revealed by remember { mutableStateOf(false) }
                            LoginInputViewport(keyboardVisible = keyboardVisible, error = state.error) {
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
                                state.error?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                    )
                                }
                            }
                            TextButton(onClick = onFindPassword) { Text("비밀번호를 잊어버렸나요?") }
                        }
                        LoginFunnelStep.Authenticating -> {
                            Spacer(Modifier.height(56.dp))
                            CircularProgressIndicator()
                            Spacer(Modifier.height(32.dp))
                            FunnelHeading("KLAS에 로그인하고 있어요", "잠시만 기다려 주세요.")
                        }
                        LoginFunnelStep.Library -> {
                            val phoneFocusRequester = remember { FocusRequester() }
                            FunnelHeading("도서관 출입증도 설정해보세요.", "중앙도서관 출입증 정보를 설정해놓으면, 홈 화면 위젯에서 QR을 바로 열 수 있어요. 지금 건너뛰어도 다시 설정할 수 있어요.")
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
                            FunnelHeading("약관에 동의해주세요.", "로그인되었어요. 필수 약관에 동의하면 바로 과제와 시간표를 확인할 수 있어요.\n서비스 개선을 위한 사용 데이터 수집 내용도 확인해주세요.")
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
                            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        }
                        LoginFunnelStep.AppLock -> {
                            FunnelHeading("보안을 강화해보세요.", "비밀번호와 생체인증으로 앱을 잠글 수 있어요. 앱 잠금을 설정하지 않으면 자동 로그인이 적용되어 별도 인증없이 앱을 실행하면 KLAS에 접근할 수 있어요. 건너뛰어도 나중에 설정에서 다시 잠글 수 있어요.")
                            if (state.appLockEnabled) Text("앱 잠금 설정 완료", color = MaterialTheme.colorScheme.primary)
                            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        }
                        LoginFunnelStep.Notifications -> {
                            FunnelHeading("곧 마감되는 할 일을 알려드릴까요?", "앱을 사용하지 않고 있을 때, 주기적으로 24시간 안에 마감되는 할 일이 있는지 확인해서 알림을 보내줄게요.")
                            DeadlineNotificationPreview()
                            if (state.deadlineNotificationsEnabled) Text("알림 설정 완료", color = MaterialTheme.colorScheme.primary)
                            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        }
                        LoginFunnelStep.Complete -> {
                            CompletionCheck()
                            Spacer(Modifier.height(24.dp))
                            Text("모두 완료되었어요!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                            Spacer(Modifier.height(8.dp))
                            Text("이제 KLAS+를 사용할 준비가 끝났어요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
            if (step != LoginFunnelStep.Authenticating && step != LoginFunnelStep.Complete) {
                Column(Modifier.widthIn(max = 600.dp).fillMaxWidth().padding(horizontal = 20.dp, vertical = if (isLoginInput) 8.dp else 20.dp)) {
                    val enabled = when (step) {
                        LoginFunnelStep.StudentId -> state.studentId.length == LoginUiState.STUDENT_ID_LENGTH
                        LoginFunnelStep.Password -> state.password.isNotEmpty()
                        LoginFunnelStep.Library -> state.libraryPassword.isNotBlank() && state.libraryPhone.isNotBlank()
                        LoginFunnelStep.Agreements -> true
                        else -> true
                    }
                    Button(
                        onClick = when (step) {
                            LoginFunnelStep.AppLock -> if (state.appLockEnabled) onContinue else onConfigureAppLock
                            LoginFunnelStep.Notifications -> if (state.deadlineNotificationsEnabled) onContinue else onConfigureNotifications
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
                            LoginFunnelStep.AppLock -> if (state.appLockEnabled) "계속" else "앱 잠금 설정하기"
                            LoginFunnelStep.Notifications -> if (state.deadlineNotificationsEnabled) "계속" else "알림 설정하기"
                            LoginFunnelStep.Complete -> "홈으로 가기"
                            LoginFunnelStep.Authenticating -> "다음"
                        })
                    }
                    if (step == LoginFunnelStep.AppLock || step == LoginFunnelStep.Notifications) {
                        TextButton(onClick = onContinue, modifier = Modifier.fillMaxWidth().testTag(if (step == LoginFunnelStep.AppLock) "funnel_skip_app_lock" else "funnel_skip_notifications")) { Text("지금은 건너뛰기") }
                    }
                    if (step == LoginFunnelStep.Library) {
                        TextButton(
                            onClick = onSkipLibrary,
                            modifier = Modifier.fillMaxWidth().testTag("funnel_skip_library"),
                        ) { Text("지금은 건너뛰기", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }

}

@Composable
private fun FunnelHeading(title: String, description: String, loginInfo: Boolean = false) {
    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
    Spacer(Modifier.height(8.dp))
    Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (loginInfo) LoginSecurityInfo()
    Spacer(Modifier.height(if (loginInfo) 12.dp else 24.dp))
}

@Composable
private fun LoginInputViewport(keyboardVisible: Boolean, error: String?, content: @Composable () -> Unit) {
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(keyboardVisible) {
        if (keyboardVisible) {
            androidx.compose.runtime.withFrameNanos { }
            requester.bringIntoView()
        }
    }
    LaunchedEffect(error) {
        if (error != null) {
            androidx.compose.runtime.withFrameNanos { }
            requester.bringIntoView()
        }
    }
    Column(Modifier.fillMaxWidth().bringIntoViewRequester(requester)) { content() }
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

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun LoginSecurityInfo() {
    var visible by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    TextButton(
        onClick = { focus.clearFocus(); keyboard?.hide(); visible = true },
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
        modifier = Modifier.testTag("login_security_link"),
    ) { Text("로그인은 어떻게 처리되나요?") }
    if (visible) androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = { visible = false },
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("로그인은 어떻게 처리되나요?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("학번과 비밀번호는 이 기기에서 학교 KLAS 서버로 직접 전달돼요. KLAS+ 서버에는 로그인 정보를 저장하지 않아요.")
            Text("자동 로그인을 위해 학교가 반환한 암호화된 비밀번호와 세션은 이 기기의 Android Keystore로 보호되는 보안 저장소에 보관해요. 평문 비밀번호는 저장하거나 로그에 남기지 않아요.")
            Button(
                onClick = { visible = false },
                shape = KlasControlShape,
                colors = klasInverseButtonColors(),
                modifier = Modifier.fillMaxWidth().height(KlasButtonHeight).testTag("login_security_close"),
            ) { Text("닫기") }
        }
    }
}

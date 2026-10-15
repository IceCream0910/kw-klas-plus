package com.icecream.kwklasplus.feature.auth

import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.compose.ui.unit.dp

internal enum class SetupSuggestion(val key: String, val title: String, val message: String, val action: String) {
    Notifications("notifications", "마감 알림을 받을까요?", "곧 마감되는 할 일 알림을 보내줄게요.", "알림 켜기"),
    Library("library", "출입증을 설정할까요?", "중앙도서관 출입증 QR을 설정할 수 있어요.", "출입증 설정"),
    AppLock("app_lock", "보안을 강화해보세요.", "PIN과 생체인증으로 앱을 잠글 수 있어요.", "앱 잠금 설정"),
}

internal class ContextualSetup(private val preferences: SharedPreferences) {
    var suggestion by mutableStateOf<SetupSuggestion?>(null)
        private set
    private val pending = linkedSetOf<SetupSuggestion>()

    fun offer(kind: SetupSuggestion, configured: Boolean) {
        if (!configured && !preferences.getBoolean("setup_prompt_seen_${kind.key}", false)) {
            pending.add(kind)
            if (suggestion == null || kind == SetupSuggestion.Library) suggestion = kind
        }
    }

    fun displayed(kind: SetupSuggestion): Boolean =
        preferences.edit().putBoolean("setup_prompt_seen_${kind.key}", true).commit()

    fun dismiss() {
        pending.remove(suggestion)
        suggestion = null
    }

    companion object {
        const val LAUNCH_COUNT = "contextual_setup_launch_count"
        private var launchRecorded = false
        fun recordLaunch(preferences: SharedPreferences) {
            if (launchRecorded) return
            launchRecorded = true
            preferences.edit().putInt(LAUNCH_COUNT, (preferences.getInt(LAUNCH_COUNT, 0) + 1).coerceAtMost(2)).commit()
        }
    }
}

@Composable
internal fun SetupSuggestionCard(setup: ContextualSetup, onAction: (SetupSuggestion) -> Unit, modifier: Modifier = Modifier, verticalPadding: androidx.compose.ui.unit.Dp = 8.dp) {
    val lifecycle = (LocalContext.current as? LifecycleOwner)?.lifecycle
    var resumed by remember(lifecycle) { mutableStateOf(lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) != false) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> resumed = lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) != false }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }
    if (!resumed) return
    val kind = setup.suggestion ?: return
    LaunchedEffect(kind) { if (!setup.displayed(kind)) setup.dismiss() }
    Surface(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = verticalPadding),
        shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(kind.title, style = MaterialTheme.typography.bodyMedium)
                Text(kind.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { setup.dismiss(); onAction(kind) }) { Text(kind.action) }
            IconButton(onClick = setup::dismiss) {
                Icon(Icons.Default.Close, contentDescription = "추천 설정 닫기", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

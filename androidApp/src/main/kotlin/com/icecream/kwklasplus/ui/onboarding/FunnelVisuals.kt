package com.icecream.kwklasplus.ui.onboarding

import android.animation.ValueAnimator
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

internal enum class FunnelArtwork { Login, Password, Library, Lock, Notification, Agreement }

@Composable
internal fun FunnelGlow(modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.tertiary
    val drift = if (ValueAnimator.areAnimatorsEnabled()) {
        val transition = rememberInfiniteTransition(label = "funnel-glow")
        val value by transition.animateFloat(-0.18f, 0.18f, infiniteRepeatable(tween(7000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "glow-drift")
        value
    } else 0f
    Canvas(modifier.fillMaxWidth().height(300.dp).blur(28.dp)) {
        drawRect(Brush.verticalGradient(listOf(primary.copy(alpha = 0.12f), Color.Transparent)))
        val center = Offset(size.width * (0.3f + drift), 0f)
        drawCircle(Brush.radialGradient(listOf(primary.copy(alpha = 0.18f), Color.Transparent), center, size.width * 0.7f), size.width * 0.7f, center)
        val other = Offset(size.width * (0.85f - drift), size.height * 0.15f)
        drawCircle(Brush.radialGradient(listOf(secondary.copy(alpha = 0.12f), Color.Transparent), other, size.width * 0.5f), size.width * 0.5f, other)
    }
}

@Composable
internal fun FunnelIllustration(artwork: FunnelArtwork, modifier: Modifier = Modifier, plain: Boolean = false) {
    val icon = when (artwork) {
        FunnelArtwork.Login -> Icons.Outlined.School
        FunnelArtwork.Password -> Icons.Outlined.Key
        FunnelArtwork.Library -> Icons.Outlined.Badge
        FunnelArtwork.Lock -> Icons.Outlined.Lock
        FunnelArtwork.Notification -> Icons.Outlined.NotificationsActive
        FunnelArtwork.Agreement -> Icons.Outlined.FactCheck
    }
    if (plain) {
        Box(modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.CenterStart) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
        }
        return
    }
    val scope = rememberCoroutineScope()
    val pop = remember { Animatable(1f) }
    val motion = ValueAnimator.areAnimatorsEnabled()
    val float = if (motion) {
        val transition = rememberInfiniteTransition(label = "funnel-icon")
        val value by transition.animateFloat(-3f, 3f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "icon-float")
        value
    } else 0f
    LaunchedEffect(artwork) { if (motion) { pop.snapTo(0.85f); pop.animateTo(1f, spring(dampingRatio = 0.6f)) } }
    Box(modifier.height(116.dp).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        Box(Modifier.size(92.dp).graphicsLayer { translationY = float; scaleX = pop.value; scaleY = pop.value }
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.09f), CircleShape)
            .clickable(onClickLabel = "아이콘 애니메이션 다시 재생") {
                if (motion) scope.launch { pop.snapTo(0.88f); pop.animateTo(1f, spring(dampingRatio = 0.5f)) }
            }, contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
internal fun DeadlineNotificationPreview() {
    val motion = ValueAnimator.areAnimatorsEnabled()
    val entrance = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(Unit) { entrance.animateTo(1f, tween(if (motion) 550 else 0)) }
    Box(Modifier.fillMaxWidth().height(338.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.width(160.dp).height(328.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(30.dp))) {
            Box(Modifier.align(Alignment.TopCenter).padding(top = 12.dp).width(58.dp).height(6.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), CircleShape))
        }
        Surface(Modifier.fillMaxWidth().padding(horizontal = 6.dp).graphicsLayer { alpha = entrance.value; translationY = (1f - entrance.value) * 24f }, shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.NotificationsActive, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("KLAS+ · 알림 예시", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("곧 마감되는 할 일이 있어요", style = MaterialTheme.typography.titleSmall)
                    Text("자료구조 과제 · 약 2시간 뒤 마감", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

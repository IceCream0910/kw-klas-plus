package com.icecream.kwklasplus.ui.navigation

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.ViewWeek
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.icecream.kwklasplus.R

private data class HomeTab(val id: String, val label: String, val icon: ImageVector)

private val homeTabs = listOf(
    HomeTab("feed", "피드", Icons.Outlined.GridView),
    HomeTab("timetable", "시간표", Icons.Outlined.ViewWeek),
    HomeTab("calendar", "캘린더", Icons.Outlined.CalendarToday),
    HomeTab("menu", "전체", Icons.Default.Menu),
)

@Composable
fun NativeHomeNavigation(selectedTab: String, onSelect: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val barColor = colorResource(R.color.md_theme_background).copy(alpha = 0.96f)
    val selectedBackground = colorResource(
        if (isSystemInDarkTheme()) R.color.md_theme_surfaceContainerHighest else R.color.md_theme_primaryFixed
    )
    Surface(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(16.dp)
            .widthIn(max = 300.dp)
            .fillMaxWidth()
            .testTag("native_home_navigation"),
        shape = CircleShape,
        color = Color.Transparent,
        shadowElevation = 4.dp,
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(barColor)
                .border(1.dp, colors.onSurface.copy(alpha = 0.16f), CircleShape)
                .padding(4.dp),
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth().height(54.dp)) {
                val tabWidth = maxWidth / homeTabs.size
                val selectedIndex = homeTabs.indexOfFirst { it.id == selectedTab }.coerceAtLeast(0)
                val capsuleOffset by animateDpAsState(
                    targetValue = tabWidth * selectedIndex,
                    animationSpec = spring(dampingRatio = 0.82f, stiffness = 340f),
                    label = "home_tab_capsule",
                )
                Box(
                    Modifier
                        .offset(x = capsuleOffset)
                        .width(tabWidth)
                        .fillMaxHeight()
                        .background(selectedBackground, CircleShape),
                )
                Row(Modifier.fillMaxWidth()) {
                    homeTabs.forEach { tab ->
                        val isSelected = selectedTab == tab.id
                        val contentColor = if (isSelected) colors.primary else colors.onSurfaceVariant
                        Column(
                            modifier = Modifier
                                .width(tabWidth)
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .clickable(role = Role.Tab) { onSelect(tab.id) }
                                .semantics { selected = isSelected },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(Modifier.weight(1f), contentAlignment = Alignment.BottomCenter) {
                                Icon(
                                    tab.icon,
                                    contentDescription = null,
                                    tint = contentColor,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Text(
                                tab.label,
                                color = contentColor,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

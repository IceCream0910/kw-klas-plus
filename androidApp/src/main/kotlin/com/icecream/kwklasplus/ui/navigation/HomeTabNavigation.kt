package com.icecream.kwklasplus.ui.navigation

internal fun shouldLoadHomeTab(
    targetTab: String,
    selectedTab: String,
    displayedTab: String,
    forceReload: Boolean,
): Boolean = forceReload ||
    !((selectedTab.isNotEmpty() && selectedTab == targetTab) || displayedTab == targetTab)

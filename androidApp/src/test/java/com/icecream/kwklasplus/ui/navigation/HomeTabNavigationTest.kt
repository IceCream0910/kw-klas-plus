package com.icecream.kwklasplus.ui.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeTabNavigationTest {
    @Test
    fun semesterChangeReloadsEveryCurrentTab() {
        for (tab in listOf("feed", "timetable", "calendar", "menu")) {
            assertTrue(shouldLoadHomeTab(tab, tab, tab, forceReload = true))
        }
    }

    @Test
    fun clearingSelectedTabDoesNotPreventDisplayedTabFromBlockingOrdinaryNavigation() {
        assertFalse(shouldLoadHomeTab("feed", "", "feed", forceReload = false))
        assertTrue(shouldLoadHomeTab("feed", "", "feed", forceReload = true))
    }

    @Test
    fun repeatedTabSelectionDoesNotReload() {
        assertFalse(shouldLoadHomeTab("feed", "feed", "feed", forceReload = false))
        assertFalse(shouldLoadHomeTab("calendar", "calendar", "feed", forceReload = false))
    }

    @Test
    fun differentTabAndInitialNavigationLoad() {
        assertTrue(shouldLoadHomeTab("calendar", "feed", "feed", forceReload = false))
        assertTrue(shouldLoadHomeTab("feed", "", "", forceReload = false))
    }
}

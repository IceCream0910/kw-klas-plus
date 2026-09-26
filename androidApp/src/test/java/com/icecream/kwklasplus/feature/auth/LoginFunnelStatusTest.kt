package com.icecream.kwklasplus.feature.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginFunnelStatusTest {
    @Test
    fun interruptedAuthenticationReturnsToPasswordAndBlocksHome() {
        assertEquals(LoginFunnelStep.Password, LoginFunnelStatus.initialStep(LoginFunnelStatus.AUTHENTICATING))
        assertTrue(LoginFunnelStatus.blocksHome(LoginFunnelStatus.AUTHENTICATING))
    }

    @Test
    fun unfinishedSetupReturnsToLibraryAndBlocksHome() {
        assertEquals(LoginFunnelStep.Library, LoginFunnelStatus.initialStep(LoginFunnelStatus.SETUP))
        assertTrue(LoginFunnelStatus.blocksHome(LoginFunnelStatus.SETUP))
    }

    @Test
    fun existingInstallWithoutStatusKeepsAutomaticLogin() {
        assertFalse(LoginFunnelStatus.blocksHome(null))
        assertFalse(LoginFunnelStatus.blocksHome(LoginFunnelStatus.COMPLETE))
    }

    @Test
    fun fullStudentIdDoesNotAdvanceAgainAfterBackNavigation() {
        assertTrue(LoginFunnelStatus.shouldAdvanceToPassword("202012345", "2020123456", LoginFunnelStep.StudentId))
        assertFalse(LoginFunnelStatus.shouldAdvanceToPassword("2020123456", "2020123456", LoginFunnelStep.StudentId))
        assertFalse(LoginFunnelStatus.shouldAdvanceToPassword("202012345", "2020123456", LoginFunnelStep.Password))
    }
}

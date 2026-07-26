package com.icecream.kwklasplus.feature.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginUiStateTest {
    @Test
    fun loginRequiresCompleteIdPasswordAndAgreement() {
        val incomplete = LoginUiState(
            onboardingVisible = false,
            studentId = "202600001",
            password = "secret",
            agreementAccepted = true,
        )
        assertFalse(incomplete.passwordVisible)
        assertFalse(incomplete.loginEnabled)

        val complete = incomplete.copy(
            studentId = "2026000001",
            agreementAccepted = true,
        )
        assertTrue(complete.passwordVisible)
        assertTrue(complete.loginEnabled)
    }
}

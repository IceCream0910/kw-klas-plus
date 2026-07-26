package com.icecream.kwklasplus.feature.library

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryQrSettingsUiStateTest {
    @Test
    fun saveRequiresEveryCredentialField() {
        assertFalse(LibraryQrSettingsUiState("", "password", "010").canSave)
        assertFalse(LibraryQrSettingsUiState("2026000001", "", "010").canSave)
        assertTrue(LibraryQrSettingsUiState("2026000001", "password", "010").canSave)
    }
}

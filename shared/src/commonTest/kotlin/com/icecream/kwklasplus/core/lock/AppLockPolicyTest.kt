package com.icecream.kwklasplus.core.lock

import com.icecream.kwklasplus.core.web.LegacyWebScripts
import kotlin.test.Test
import kotlin.test.assertEquals

class AppLockPolicyTest {
    @Test
    fun requestsUnlockOnlyForLockedNonExemptHost() {
        val policy = AppLockPolicy()

        assertEquals(true, policy.shouldRequestUnlock(AppLockState(true, false), false))
        assertEquals(false, policy.shouldRequestUnlock(AppLockState(true, false), true))
        assertEquals(false, policy.shouldRequestUnlock(AppLockState(false, false), false))
    }

    @Test
    fun backgroundLocksEnabledApp() {
        val state = AppLockPolicy().reduce(
            AppLockState(enabled = true, unlocked = true),
            AppLockEvent.EnteredBackground,
        )

        assertEquals(AppLockState(enabled = true, unlocked = false), state)
    }

    @Test
    fun disablingLockLeavesAppUnlocked() {
        val state = AppLockPolicy().reduce(
            AppLockState(enabled = true, unlocked = false),
            AppLockEvent.LockDisabled,
        )

        assertEquals(AppLockState(enabled = false, unlocked = true), state)
    }

    @Test
    fun legacySettingsJsonAndCallbackAreStable() {
        val settings = AppLockSettings(true, false, true)

        assertEquals(
            "{\"enabled\":true,\"biometric\":false,\"hasPassword\":true}",
            settings.toLegacyJson(),
        )
        assertEquals(
            "window.onAppLockSettingChanged({\"enabled\":true,\"biometric\":false,\"hasPassword\":true});",
            LegacyWebScripts.appLockSettingChanged(settings).reveal(),
        )
    }
}

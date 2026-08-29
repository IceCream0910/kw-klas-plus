package com.icecream.kwklasplus.core.lock

data class AppLockSettings(
    val enabled: Boolean,
    val biometricEnabled: Boolean,
    val hasPassword: Boolean,
) {
    fun toLegacyJson(): String =
        "{\"enabled\":$enabled,\"biometric\":$biometricEnabled,\"hasPassword\":$hasPassword}"
}

sealed interface AppLockEvent {
    data object EnteredForeground : AppLockEvent
    data object EnteredBackground : AppLockEvent
    data object AuthenticationSucceeded : AppLockEvent
    data object AuthenticationFailed : AppLockEvent
    data object LockDisabled : AppLockEvent
}

data class AppLockState(
    val enabled: Boolean,
    val unlocked: Boolean,
)

class AppLockPolicy {
    fun shouldRequestUnlock(state: AppLockState, isExemptHost: Boolean): Boolean =
        state.enabled && !state.unlocked && !isExemptHost

    fun reduce(state: AppLockState, event: AppLockEvent): AppLockState = when (event) {
        AppLockEvent.EnteredForeground -> state
        AppLockEvent.EnteredBackground -> state.copy(unlocked = !state.enabled)
        AppLockEvent.AuthenticationSucceeded -> state.copy(unlocked = true)
        AppLockEvent.AuthenticationFailed -> state.copy(unlocked = false)
        AppLockEvent.LockDisabled -> AppLockState(enabled = false, unlocked = true)
    }

    companion object {
        const val BACKGROUND_LOCK_DELAY_MS = 700L
    }
}

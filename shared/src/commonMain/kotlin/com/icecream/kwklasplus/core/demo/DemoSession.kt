package com.icecream.kwklasplus.core.demo

import com.icecream.kwklasplus.core.security.SecretValue
import com.icecream.kwklasplus.core.session.Session
import kotlin.concurrent.Volatile

object DemoSession {
    const val ACCOUNT_ID = "0000000000"
    private const val PASSWORD = "KlasplusDemo!123"
    @Volatile var active: Boolean = false
        private set
    @Volatile var generation: Long = 0
        private set
    @Volatile var hasSession: Boolean = false
        private set
    val token: SecretValue get() = SecretValue.of("klasplus-demo-$generation")
    internal var appLockEnabled = false
    internal var biometricEnabled = false
    internal var remindersEnabled = false

    private val endListeners = mutableListOf<() -> Unit>()
    fun onEnd(listener: () -> Unit) { endListeners.add(listener) }

    fun isDemoAccount(accountId: String): Boolean = accountId == ACCOUNT_ID

    fun begin(accountId: String, password: String): Boolean {
        if (accountId != ACCOUNT_ID || password != PASSWORD) return false
        generation++
        DemoFixtures.reset()
        resetSettings()
        hasSession = true
        active = true
        return true
    }

    fun end() {
        active = false
        endListeners.toList().forEach { runCatching(it) }
        hasSession = false
        generation++
        DemoFixtures.reset()
        resetSettings()
    }

    fun restore() {
        if (active && hasSession) return
        generation++
        hasSession = true
        active = true
        DemoFixtures.reset()
        resetSettings()
    }

    fun session(now: Long): Session = Session(token, now)

    fun expire() {
        hasSession = false
        endListeners.toList().forEach { runCatching(it) }
        generation++
        DemoFixtures.reset()
        resetSettings()
    }

    private fun resetSettings() {
        appLockEnabled = false
        biometricEnabled = false
        remindersEnabled = false
    }
}

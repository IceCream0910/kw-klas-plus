package com.icecream.kwklasplus.core.library

class LibraryCachePolicy(
    private val secretLifetimeMillis: Long = 30L * 24 * 60 * 60 * 1_000,
    private val authKeyLifetimeMillis: Long = 12L * 60 * 60 * 1_000,
) {
    fun isSecretValid(savedAtMillis: Long?, nowMillis: Long): Boolean =
        isValid(savedAtMillis, nowMillis, secretLifetimeMillis)

    fun isAuthKeyValid(savedAtMillis: Long?, nowMillis: Long): Boolean =
        isValid(savedAtMillis, nowMillis, authKeyLifetimeMillis)

    private fun isValid(savedAtMillis: Long?, nowMillis: Long, lifetimeMillis: Long): Boolean {
        if (savedAtMillis == null) return true
        if (savedAtMillis < 0 || nowMillis < savedAtMillis) return false
        return nowMillis - savedAtMillis <= lifetimeMillis
    }
}

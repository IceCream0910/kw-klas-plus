package com.icecream.kwklasplus.core.library

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LibraryCachePolicyTest {
    private val policy = LibraryCachePolicy(
        secretLifetimeMillis = 1_000,
        authKeyLifetimeMillis = 100,
    )

    @Test
    fun acceptsLegacyEntryWithoutTimestampForReadThroughMigration() {
        assertTrue(policy.isSecretValid(null, 10_000))
        assertTrue(policy.isAuthKeyValid(null, 10_000))
    }

    @Test
    fun expiresSecretAndAuthKeyAtTheirOwnBoundaries() {
        assertTrue(policy.isSecretValid(1_000, 2_000))
        assertFalse(policy.isSecretValid(1_000, 2_001))
        assertTrue(policy.isAuthKeyValid(1_000, 1_100))
        assertFalse(policy.isAuthKeyValid(1_000, 1_101))
    }

    @Test
    fun rejectsFutureOrInvalidTimestamps() {
        assertFalse(policy.isSecretValid(2_000, 1_000))
        assertFalse(policy.isAuthKeyValid(-1, 1_000))
    }
}

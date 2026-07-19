package com.icecream.kwklasplus.core.platform

import kotlin.test.Test
import kotlin.test.assertEquals

class LegacyHapticEffectTest {
    @Test
    fun preservesEveryLegacyContractName() {
        LegacyHapticEffect.entries.forEach { effect ->
            assertEquals(effect, LegacyHapticEffect.fromContractName(effect.name))
        }
    }

    @Test
    fun unknownNameKeepsLegacyClockTickFallback() {
        assertEquals(
            LegacyHapticEffect.CLOCK_TICK,
            LegacyHapticEffect.fromContractName("UNKNOWN"),
        )
    }

    @Test
    fun exposesCrossPlatformSemanticEffects() {
        assertEquals(HapticEffect.CONFIRM, LegacyHapticEffect.TOGGLE_ON.semanticEffect)
        assertEquals(HapticEffect.REJECT, LegacyHapticEffect.REJECT.semanticEffect)
        assertEquals(HapticEffect.LONG_PRESS, LegacyHapticEffect.DRAG_START.semanticEffect)
    }
}

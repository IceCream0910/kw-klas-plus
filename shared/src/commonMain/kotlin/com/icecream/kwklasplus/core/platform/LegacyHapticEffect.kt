package com.icecream.kwklasplus.core.platform

enum class LegacyHapticEffect(
    val semanticEffect: HapticEffect,
) {
    CLOCK_TICK(HapticEffect.SELECTION),
    KEYBOARD_TAP(HapticEffect.SELECTION),
    KEYBOARD_RELEASE(HapticEffect.SELECTION),
    LONG_PRESS(HapticEffect.LONG_PRESS),
    VIRTUAL_KEY(HapticEffect.SELECTION),
    VIRTUAL_KEY_RELEASE(HapticEffect.SELECTION),
    TEXT_HANDLE_MOVE(HapticEffect.SELECTION),
    CONFIRM(HapticEffect.CONFIRM),
    REJECT(HapticEffect.REJECT),
    DRAG_START(HapticEffect.LONG_PRESS),
    GESTURE_START(HapticEffect.SELECTION),
    GESTURE_END(HapticEffect.SELECTION),
    TOGGLE_OFF(HapticEffect.SELECTION),
    TOGGLE_ON(HapticEffect.CONFIRM),
    ;

    companion object {
        fun fromContractName(value: String): LegacyHapticEffect =
            entries.firstOrNull { it.name == value } ?: CLOCK_TICK
    }
}

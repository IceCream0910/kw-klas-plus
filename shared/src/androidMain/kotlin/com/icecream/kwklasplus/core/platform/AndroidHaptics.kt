package com.icecream.kwklasplus.core.platform

import android.view.HapticFeedbackConstants
import android.view.View

class AndroidHaptics(
    private val view: View,
) : Haptics {
    override fun perform(effect: HapticEffect): PlatformActionResult {
        val constant = when (effect) {
            HapticEffect.SELECTION -> HapticFeedbackConstants.CLOCK_TICK
            HapticEffect.CONFIRM -> HapticFeedbackConstants.CONFIRM
            HapticEffect.REJECT -> HapticFeedbackConstants.REJECT
            HapticEffect.LONG_PRESS -> HapticFeedbackConstants.LONG_PRESS
        }
        return performConstant(constant)
    }

    fun performLegacy(contractName: String): PlatformActionResult {
        val constant = when (LegacyHapticEffect.fromContractName(contractName)) {
            LegacyHapticEffect.CLOCK_TICK -> HapticFeedbackConstants.CLOCK_TICK
            LegacyHapticEffect.KEYBOARD_TAP -> HapticFeedbackConstants.KEYBOARD_TAP
            LegacyHapticEffect.KEYBOARD_RELEASE -> HapticFeedbackConstants.KEYBOARD_RELEASE
            LegacyHapticEffect.LONG_PRESS -> HapticFeedbackConstants.LONG_PRESS
            LegacyHapticEffect.VIRTUAL_KEY -> HapticFeedbackConstants.VIRTUAL_KEY
            LegacyHapticEffect.VIRTUAL_KEY_RELEASE -> HapticFeedbackConstants.VIRTUAL_KEY_RELEASE
            LegacyHapticEffect.TEXT_HANDLE_MOVE -> HapticFeedbackConstants.TEXT_HANDLE_MOVE
            LegacyHapticEffect.CONFIRM -> HapticFeedbackConstants.CONFIRM
            LegacyHapticEffect.REJECT -> HapticFeedbackConstants.REJECT
            LegacyHapticEffect.DRAG_START -> HapticFeedbackConstants.DRAG_START
            LegacyHapticEffect.GESTURE_START -> HapticFeedbackConstants.GESTURE_START
            LegacyHapticEffect.GESTURE_END -> HapticFeedbackConstants.GESTURE_END
            LegacyHapticEffect.TOGGLE_OFF -> HapticFeedbackConstants.TOGGLE_OFF
            LegacyHapticEffect.TOGGLE_ON -> HapticFeedbackConstants.TOGGLE_ON
        }
        return performConstant(constant)
    }

    private fun performConstant(constant: Int): PlatformActionResult =
        if (view.performHapticFeedback(constant)) {
            PlatformActionResult.Success
        } else {
            PlatformActionResult.Unsupported
        }
}

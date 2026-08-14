package com.icecream.kwklasplus.core.platform

import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType
import platform.UIKit.UISelectionFeedbackGenerator

class IosHaptics : Haptics {
    override fun perform(effect: HapticEffect): PlatformActionResult {
        when (effect) {
            HapticEffect.SELECTION -> UISelectionFeedbackGenerator().apply {
                prepare()
                selectionChanged()
            }
            HapticEffect.CONFIRM -> UINotificationFeedbackGenerator().apply {
                prepare()
                notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeSuccess)
            }
            HapticEffect.REJECT -> UINotificationFeedbackGenerator().apply {
                prepare()
                notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeError)
            }
            HapticEffect.LONG_PRESS -> UIImpactFeedbackGenerator(
                UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium,
            ).apply {
                prepare()
                impactOccurred()
            }
        }
        return PlatformActionResult.Success
    }

    fun performLegacy(contractName: String): PlatformActionResult =
        perform(LegacyHapticEffect.fromContractName(contractName).semanticEffect)
}

package com.icecream.kwklasplus.platform.bridge.legacy

import com.icecream.kwklasplus.JavaScriptInterface
import com.icecream.kwklasplus.core.bridge.BridgeCommandHandler
import com.icecream.kwklasplus.core.bridge.BridgeErrorCode
import com.icecream.kwklasplus.core.bridge.BridgeHandlerResult
import com.icecream.kwklasplus.core.bridge.BridgeMethodId
import com.icecream.kwklasplus.core.bridge.BridgeValue
import com.icecream.kwklasplus.core.bridge.ValidatedBridgeCommand

class HomeLegacyBridgeCommandHandler(
    private val facade: JavaScriptInterface,
) : BridgeCommandHandler {
    override suspend fun handle(command: ValidatedBridgeCommand): BridgeHandlerResult {
        when (command.methodId) {
            BridgeMethodId.HOME_CHANGE_TAB -> facade.changeTab(command.text(0))
            BridgeMethodId.HOME_EVALUATE -> facade.evaluate(
                command.text(0),
                command.text(1),
                command.text(2),
            )
            BridgeMethodId.HOME_OPEN_PAGE -> facade.openPage(command.text(0))
            BridgeMethodId.HOME_OPEN_EXTERNAL_PAGE -> facade.openExternalPage(command.text(0))
            BridgeMethodId.HOME_COMPLETE_PAGE_LOAD -> facade.completePageLoad()
            BridgeMethodId.HOME_OPEN_LIBRARY_QR -> facade.openLibraryQR()
            BridgeMethodId.HOME_OPEN_LIBRARY_QR_SETTINGS_MODAL -> facade.openLibraryQRSettingsModal()
            BridgeMethodId.HOME_OPEN_LECTURE_ACTIVITY -> facade.openLectureActivity(
                command.text(0),
                command.text(1),
            )
            BridgeMethodId.HOME_QR_CHECK_IN -> facade.qrCheckIn(command.text(0), command.text(1))
            BridgeMethodId.HOME_OPEN_DATE_TIME_PICKER -> facade.openDateTimePicker(
                command.nullableText(0),
                command.boolean(1),
            )
            BridgeMethodId.HOME_OPEN_WEB_VIEW_BOTTOM_SHEET -> facade.openWebViewBottomSheet()
            BridgeMethodId.HOME_CLOSE_WEB_VIEW_BOTTOM_SHEET -> facade.closeWebViewBottomSheet()
            BridgeMethodId.HOME_OPEN_OPTIONS_MENU -> facade.openOptionsMenu()
            BridgeMethodId.HOME_OPEN_YEAR_HAKGI_BOTTOM_SHEET -> facade.openYearHakgiBottomSheet()
            BridgeMethodId.HOME_OPEN_CUSTOM_BOTTOM_SHEET -> facade.openCustomBottomSheet(
                command.text(0),
                command.optionalBoolean(1, default = true),
            )
            BridgeMethodId.HOME_RELOAD -> facade.reload()
            BridgeMethodId.HOME_PERFORM_HAPTIC_FEEDBACK -> facade.performHapticFeedback(
                command.text(0),
            )
            BridgeMethodId.HOME_REQUEST_ID_CARD_QR_VALUE -> facade.requestIdCardQRValue()
            else -> return BridgeHandlerResult.Failure(BridgeErrorCode.UNKNOWN_METHOD)
        }
        return BridgeHandlerResult.Success()
    }

    private fun ValidatedBridgeCommand.text(index: Int): String =
        (arguments[index] as BridgeValue.Text).value

    private fun ValidatedBridgeCommand.nullableText(index: Int): String? = when (
        val value = arguments[index]
    ) {
        is BridgeValue.Text -> value.value
        BridgeValue.Null -> null
        else -> null
    }

    private fun ValidatedBridgeCommand.boolean(index: Int): Boolean =
        (arguments[index] as BridgeValue.BooleanValue).value

    private fun ValidatedBridgeCommand.optionalBoolean(index: Int, default: Boolean): Boolean =
        (arguments.getOrNull(index) as? BridgeValue.BooleanValue)?.value ?: default
}

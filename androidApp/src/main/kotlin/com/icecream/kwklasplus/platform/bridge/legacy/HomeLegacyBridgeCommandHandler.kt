package com.icecream.kwklasplus.platform.bridge.legacy

import com.icecream.kwklasplus.HomeBridgeDelegate
import com.icecream.kwklasplus.core.bridge.BridgeCommandHandler
import com.icecream.kwklasplus.core.bridge.BridgeErrorCode
import com.icecream.kwklasplus.core.bridge.BridgeHandlerResult
import com.icecream.kwklasplus.core.bridge.BridgeMethodId
import com.icecream.kwklasplus.core.bridge.BridgeValue
import com.icecream.kwklasplus.core.bridge.ValidatedBridgeCommand

class HomeLegacyBridgeCommandHandler(
    private val delegate: HomeBridgeDelegate,
) : BridgeCommandHandler {
    override suspend fun handle(command: ValidatedBridgeCommand): BridgeHandlerResult {
        when (command.methodId) {
            BridgeMethodId.HOME_CHANGE_TAB -> delegate.changeTab(command.text(0))
            BridgeMethodId.HOME_EVALUATE -> delegate.evaluate(
                command.text(0),
                command.text(1),
                command.text(2),
            )
            BridgeMethodId.HOME_OPEN_PAGE -> delegate.openPage(command.text(0))
            BridgeMethodId.HOME_OPEN_EXTERNAL_PAGE -> delegate.openExternalPage(command.text(0))
            BridgeMethodId.HOME_COMPLETE_PAGE_LOAD -> delegate.completePageLoad()
            BridgeMethodId.HOME_OPEN_LIBRARY_QR -> delegate.openLibraryQR()
            BridgeMethodId.HOME_OPEN_LIBRARY_QR_SETTINGS_MODAL -> delegate.openLibraryQRSettingsModal()
            BridgeMethodId.HOME_OPEN_LECTURE_ACTIVITY -> delegate.openLectureActivity(
                command.text(0),
                command.text(1),
            )
            BridgeMethodId.HOME_QR_CHECK_IN -> delegate.qrCheckIn(command.text(0), command.text(1))
            BridgeMethodId.HOME_OPEN_DATE_TIME_PICKER -> delegate.openDateTimePicker(
                command.nullableText(0),
                command.boolean(1),
            )
            BridgeMethodId.HOME_OPEN_WEB_VIEW_BOTTOM_SHEET -> delegate.openWebViewBottomSheet()
            BridgeMethodId.HOME_CLOSE_WEB_VIEW_BOTTOM_SHEET -> delegate.closeWebViewBottomSheet()
            BridgeMethodId.HOME_OPEN_OPTIONS_MENU -> delegate.openOptionsMenu()
            BridgeMethodId.HOME_OPEN_YEAR_HAKGI_BOTTOM_SHEET -> delegate.openYearHakgiBottomSheet()
            BridgeMethodId.HOME_RELOAD -> delegate.reload()
            BridgeMethodId.HOME_PERFORM_HAPTIC_FEEDBACK -> delegate.performHapticFeedback(
                command.text(0),
            )
            BridgeMethodId.HOME_REQUEST_ID_CARD_QR_VALUE -> delegate.requestIdCardQRValue()
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

}

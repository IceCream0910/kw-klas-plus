package com.icecream.kwklasplus.platform.bridge.legacy

import com.icecream.kwklasplus.JavaScriptInterfaceForBoard
import com.icecream.kwklasplus.JavaScriptInterfaceForLinkView
import com.icecream.kwklasplus.JavaScriptInterfaceForSettings
import com.icecream.kwklasplus.JavaScriptInterfaceLecturePlan
import com.icecream.kwklasplus.WebAppInterface
import com.icecream.kwklasplus.WebAppInterfaceLectureHome
import com.icecream.kwklasplus.core.bridge.BridgeCommandHandler
import com.icecream.kwklasplus.core.bridge.BridgeErrorCode
import com.icecream.kwklasplus.core.bridge.BridgeHandlerResult
import com.icecream.kwklasplus.core.bridge.BridgeMethodId
import com.icecream.kwklasplus.core.bridge.BridgeValue
import com.icecream.kwklasplus.core.bridge.SynchronousBridgeCommandHandler
import com.icecream.kwklasplus.core.bridge.ValidatedBridgeCommand

class BoardLegacyBridgeCommandHandler(
    private val facade: JavaScriptInterfaceForBoard,
) : BridgeCommandHandler {
    override suspend fun handle(command: ValidatedBridgeCommand) = command.execute {
        when (command.methodId) {
            BridgeMethodId.BOARD_OPEN_PAGE -> facade.openPage(command.text(0))
            BridgeMethodId.BOARD_OPEN_EXTERNAL_LINK -> facade.openExternalLink(command.text(0))
            BridgeMethodId.BOARD_COMPLETE_PAGE_LOAD -> facade.completePageLoad()
            else -> return@execute false
        }
        true
    }
}

class LecturePlanLegacyBridgeCommandHandler(
    private val facade: JavaScriptInterfaceLecturePlan,
) : BridgeCommandHandler {
    override suspend fun handle(command: ValidatedBridgeCommand) = command.execute {
        when (command.methodId) {
            BridgeMethodId.LECTURE_PLAN_COMPLETE_PAGE_LOAD -> facade.completePageLoad()
            BridgeMethodId.LECTURE_PLAN_OPEN_PAGE -> facade.openPage(command.text(0))
            BridgeMethodId.LECTURE_PLAN_OPEN_EXTERNAL_PAGE -> facade.openExternalPage(command.text(0))
            else -> return@execute false
        }
        true
    }
}

class LectureLegacyBridgeCommandHandler(
    private val facade: WebAppInterfaceLectureHome,
) : BridgeCommandHandler {
    override suspend fun handle(command: ValidatedBridgeCommand) = command.execute {
        when (command.methodId) {
            BridgeMethodId.LECTURE_COMPLETE_PAGE_LOAD -> facade.completePageLoad()
            BridgeMethodId.LECTURE_OPEN_PAGE -> facade.openPage(command.text(0))
            BridgeMethodId.LECTURE_GET_BOARD_PATH -> facade.getBoardPath(
                command.text(0), command.text(1),
            )
            BridgeMethodId.LECTURE_OPEN_BOARD_LIST -> facade.openBoardList(
                command.text(0), command.text(1),
            )
            BridgeMethodId.LECTURE_OPEN_BOARD_VIEW -> facade.openBoardView(
                command.text(0), command.text(1), command.text(2),
            )
            BridgeMethodId.LECTURE_OPEN_EXTERNAL_LINK -> facade.openExternalLink(command.text(0))
            BridgeMethodId.LECTURE_EVALUTE_KLAS_SCRIPT -> facade.evaluteKLASScript(command.text(0))
            BridgeMethodId.LECTURE_OPEN_ONLINE_LECTURE -> facade.openOnlineLecture()
            BridgeMethodId.LECTURE_OPEN_LECTURE_PLAN -> facade.openLecturePlan()
            BridgeMethodId.LECTURE_OPEN_QR_SCAN -> facade.openQRScan()
            else -> return@execute false
        }
        true
    }
}

class LinkLegacyBridgeCommandHandler(
    private val facade: JavaScriptInterfaceForLinkView,
) : BridgeCommandHandler {
    override suspend fun handle(command: ValidatedBridgeCommand) = command.execute {
        when (command.methodId) {
            BridgeMethodId.LINK_VIEW_OPEN_PAGE -> facade.openPage(command.text(0))
            BridgeMethodId.LINK_VIEW_OPEN_LECTURE_PLAN_PAGE -> facade.openLecturePlanPage(
                command.text(0),
            )
            BridgeMethodId.LINK_VIEW_OPEN_WEB_VIEW_BOTTOM_SHEET -> facade.openWebViewBottomSheet()
            BridgeMethodId.LINK_VIEW_CLOSE_WEB_VIEW_BOTTOM_SHEET -> facade.closeWebViewBottomSheet()
            BridgeMethodId.LINK_VIEW_COMPLETE_PAGE_LOAD -> facade.completePageLoad()
            else -> return@execute false
        }
        true
    }
}

class SettingsLegacyBridgeCommandHandler(
    private val facade: JavaScriptInterfaceForSettings,
) : BridgeCommandHandler {
    override suspend fun handle(command: ValidatedBridgeCommand) = handleCommand(command, facade)

    companion object {
        fun handleCommand(
            command: ValidatedBridgeCommand,
            facade: JavaScriptInterfaceForSettings,
        ) = command.execute {
            when (command.methodId) {
                BridgeMethodId.SETTINGS_COMPLETE_PAGE_LOAD -> facade.completePageLoad()
                BridgeMethodId.SETTINGS_CHANGE_APP_THEME -> facade.changeAppTheme(command.text(0))
                BridgeMethodId.SETTINGS_OPEN_YEAR_HAKGI_SELECT_MODAL -> facade.openYearHakgiSelectModal()
                BridgeMethodId.SETTINGS_OPEN_LIBRARY_QR_SETTINGS_MODAL -> facade.openLibraryQRSettingsModal()
                BridgeMethodId.SETTINGS_OPEN_EXTERNAL_LINK -> facade.openExternalLink(command.text(0))
                BridgeMethodId.SETTINGS_PERFORM_HAPTIC_FEEDBACK -> facade.performHapticFeedback(
                    command.text(0),
                )
                BridgeMethodId.SETTINGS_SET_APP_LOCK_ENABLED -> facade.setAppLockEnabled(
                    command.boolean(0),
                )
                BridgeMethodId.SETTINGS_SET_APP_LOCK_PASSWORD -> facade.setAppLockPassword()
                BridgeMethodId.SETTINGS_SET_BIOMETRIC_ENABLED -> facade.setBiometricEnabled(
                    command.boolean(0),
                )
                BridgeMethodId.SETTINGS_GET_APP_LOCK_SETTINGS -> return@execute true
                else -> return@execute false
            }
            true
        }.let { result ->
            if (
                command.methodId == BridgeMethodId.SETTINGS_GET_APP_LOCK_SETTINGS &&
                result is BridgeHandlerResult.Success
            ) {
                BridgeHandlerResult.Success(BridgeValue.Text(facade.getAppLockSettings()))
            } else {
                result
            }
        }
    }
}

class SettingsLegacySynchronousBridgeCommandHandler(
    private val facade: JavaScriptInterfaceForSettings,
) : SynchronousBridgeCommandHandler {
    override fun handle(command: ValidatedBridgeCommand): BridgeHandlerResult =
        SettingsLegacyBridgeCommandHandler.handleCommand(command, facade)
}

class VideoLegacyBridgeCommandHandler(
    private val facade: WebAppInterface,
) : BridgeCommandHandler {
    override suspend fun handle(command: ValidatedBridgeCommand) = command.execute {
        when (command.methodId) {
            BridgeMethodId.VIDEO_COMPLETE_PAGE_LOAD -> facade.completePageLoad()
            BridgeMethodId.VIDEO_OPEN_EXTERNAL_LINK -> facade.openExternalLink(command.text(0))
            BridgeMethodId.VIDEO_OPEN_IN_KLAS -> facade.openInKLAS()
            BridgeMethodId.VIDEO_REQUEST_ONLINE_LECTURE -> facade.requestOnlineLecture(command.text(0))
            BridgeMethodId.VIDEO_RECEIVE_PLAYER_STATES -> facade.receivePlayerStates(
                command.text(0), command.text(1), command.text(2), command.text(3), command.text(4),
            )
            BridgeMethodId.VIDEO_RECEIVE_INIT_SPEED -> facade.receiveInitSpeed(command.text(0))
            BridgeMethodId.VIDEO_RECEIVE_VIDEO_DATA -> facade.receiveVideoData(
                command.text(0), command.text(1),
            )
            BridgeMethodId.VIDEO_RECEIVE_VIDEO_URL -> facade.receiveVideoURL(command.text(0))
            BridgeMethodId.VIDEO_PERFORM_HAPTIC_FEEDBACK -> facade.performHapticFeedback(
                command.text(0),
            )
            else -> return@execute false
        }
        true
    }
}

private inline fun ValidatedBridgeCommand.execute(block: () -> Boolean): BridgeHandlerResult =
    if (block()) BridgeHandlerResult.Success() else BridgeHandlerResult.Failure(
        BridgeErrorCode.UNKNOWN_METHOD,
    )

private fun ValidatedBridgeCommand.text(index: Int): String =
    (arguments[index] as BridgeValue.Text).value

private fun ValidatedBridgeCommand.boolean(index: Int): Boolean =
    (arguments[index] as BridgeValue.BooleanValue).value

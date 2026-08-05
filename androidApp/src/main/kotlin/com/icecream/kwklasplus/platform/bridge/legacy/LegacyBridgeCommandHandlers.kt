package com.icecream.kwklasplus.platform.bridge.legacy

import com.icecream.kwklasplus.BoardBridgeDelegate
import com.icecream.kwklasplus.LinkBridgeDelegate
import com.icecream.kwklasplus.SettingsBridgeDelegate
import com.icecream.kwklasplus.LecturePlanBridgeDelegate
import com.icecream.kwklasplus.VideoBridgeDelegate
import com.icecream.kwklasplus.LectureBridgeDelegate
import com.icecream.kwklasplus.core.bridge.BridgeCommandHandler
import com.icecream.kwklasplus.core.bridge.BridgeErrorCode
import com.icecream.kwklasplus.core.bridge.BridgeHandlerResult
import com.icecream.kwklasplus.core.bridge.BridgeMethodId
import com.icecream.kwklasplus.core.bridge.BridgeValue
import com.icecream.kwklasplus.core.bridge.SynchronousBridgeCommandHandler
import com.icecream.kwklasplus.core.bridge.ValidatedBridgeCommand

class BoardLegacyBridgeCommandHandler(
    private val delegate: BoardBridgeDelegate,
) : BridgeCommandHandler {
    override suspend fun handle(command: ValidatedBridgeCommand) = command.execute {
        when (command.methodId) {
            BridgeMethodId.BOARD_OPEN_PAGE -> delegate.openPage(command.text(0))
            BridgeMethodId.BOARD_OPEN_EXTERNAL_LINK -> delegate.openExternalLink(command.text(0))
            BridgeMethodId.BOARD_COMPLETE_PAGE_LOAD -> delegate.completePageLoad()
            else -> return@execute false
        }
        true
    }
}

class LecturePlanLegacyBridgeCommandHandler(
    private val delegate: LecturePlanBridgeDelegate,
) : BridgeCommandHandler {
    override suspend fun handle(command: ValidatedBridgeCommand) = command.execute {
        when (command.methodId) {
            BridgeMethodId.LECTURE_PLAN_COMPLETE_PAGE_LOAD -> delegate.completePageLoad()
            BridgeMethodId.LECTURE_PLAN_OPEN_PAGE -> delegate.openPage(command.text(0))
            BridgeMethodId.LECTURE_PLAN_OPEN_EXTERNAL_PAGE -> delegate.openExternalPage(command.text(0))
            else -> return@execute false
        }
        true
    }
}

class LectureLegacyBridgeCommandHandler(
    private val delegate: LectureBridgeDelegate,
) : BridgeCommandHandler {
    override suspend fun handle(command: ValidatedBridgeCommand) = command.execute {
        when (command.methodId) {
            BridgeMethodId.LECTURE_COMPLETE_PAGE_LOAD -> delegate.completePageLoad()
            BridgeMethodId.LECTURE_OPEN_PAGE -> delegate.openPage(command.text(0))
            BridgeMethodId.LECTURE_GET_BOARD_PATH -> delegate.getBoardPath(
                command.text(0), command.text(1),
            )
            BridgeMethodId.LECTURE_OPEN_BOARD_LIST -> delegate.openBoardList(
                command.text(0), command.text(1),
            )
            BridgeMethodId.LECTURE_OPEN_BOARD_VIEW -> delegate.openBoardView(
                command.text(0), command.text(1), command.text(2),
            )
            BridgeMethodId.LECTURE_OPEN_EXTERNAL_LINK -> delegate.openExternalLink(command.text(0))
            BridgeMethodId.LECTURE_EVALUTE_KLAS_SCRIPT -> delegate.evaluteKLASScript(command.text(0))
            BridgeMethodId.LECTURE_OPEN_ONLINE_LECTURE -> delegate.openOnlineLecture()
            BridgeMethodId.LECTURE_OPEN_LECTURE_PLAN -> delegate.openLecturePlan()
            BridgeMethodId.LECTURE_OPEN_QR_SCAN -> delegate.openQRScan()
            else -> return@execute false
        }
        true
    }
}

class LinkLegacyBridgeCommandHandler(
    private val delegate: LinkBridgeDelegate,
) : BridgeCommandHandler {
    override suspend fun handle(command: ValidatedBridgeCommand) = command.execute {
        when (command.methodId) {
            BridgeMethodId.LINK_VIEW_OPEN_PAGE -> delegate.openPage(command.text(0))
            BridgeMethodId.LINK_VIEW_OPEN_LECTURE_PLAN_PAGE -> delegate.openLecturePlanPage(
                command.text(0),
            )
            BridgeMethodId.LINK_VIEW_OPEN_WEB_VIEW_BOTTOM_SHEET -> delegate.openWebViewBottomSheet()
            BridgeMethodId.LINK_VIEW_CLOSE_WEB_VIEW_BOTTOM_SHEET -> delegate.closeWebViewBottomSheet()
            BridgeMethodId.LINK_VIEW_COMPLETE_PAGE_LOAD -> delegate.completePageLoad()
            else -> return@execute false
        }
        true
    }
}

class SettingsLegacyBridgeCommandHandler(
    private val delegate: SettingsBridgeDelegate,
) : BridgeCommandHandler {
    override suspend fun handle(command: ValidatedBridgeCommand) = handleCommand(command, delegate)

    companion object {
        fun handleCommand(
            command: ValidatedBridgeCommand,
            delegate: SettingsBridgeDelegate,
        ) = command.execute {
            when (command.methodId) {
                BridgeMethodId.SETTINGS_COMPLETE_PAGE_LOAD -> delegate.completePageLoad()
                BridgeMethodId.SETTINGS_CHANGE_APP_THEME -> delegate.changeAppTheme(command.text(0))
                BridgeMethodId.SETTINGS_OPEN_YEAR_HAKGI_SELECT_MODAL -> delegate.openYearHakgiSelectModal()
                BridgeMethodId.SETTINGS_OPEN_LIBRARY_QR_SETTINGS_MODAL -> delegate.openLibraryQRSettingsModal()
                BridgeMethodId.SETTINGS_OPEN_EXTERNAL_LINK -> delegate.openExternalLink(command.text(0))
                BridgeMethodId.SETTINGS_PERFORM_HAPTIC_FEEDBACK -> delegate.performHapticFeedback(
                    command.text(0),
                )
                BridgeMethodId.SETTINGS_SET_APP_LOCK_ENABLED -> delegate.setAppLockEnabled(
                    command.boolean(0),
                )
                BridgeMethodId.SETTINGS_SET_APP_LOCK_PASSWORD -> delegate.setAppLockPassword()
                BridgeMethodId.SETTINGS_SET_BIOMETRIC_ENABLED -> delegate.setBiometricEnabled(
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
                BridgeHandlerResult.Success(BridgeValue.Text(delegate.getAppLockSettings()))
            } else {
                result
            }
        }
    }
}

class SettingsLegacySynchronousBridgeCommandHandler(
    private val delegate: SettingsBridgeDelegate,
) : SynchronousBridgeCommandHandler {
    override fun handle(command: ValidatedBridgeCommand): BridgeHandlerResult =
        SettingsLegacyBridgeCommandHandler.handleCommand(command, delegate)
}

class VideoLegacyBridgeCommandHandler(
    private val delegate: VideoBridgeDelegate,
) : BridgeCommandHandler {
    override suspend fun handle(command: ValidatedBridgeCommand) = command.execute {
        when (command.methodId) {
            BridgeMethodId.VIDEO_COMPLETE_PAGE_LOAD -> delegate.completePageLoad()
            BridgeMethodId.VIDEO_OPEN_EXTERNAL_LINK -> delegate.openExternalLink(command.text(0))
            BridgeMethodId.VIDEO_OPEN_IN_KLAS -> delegate.openInKLAS()
            BridgeMethodId.VIDEO_REQUEST_ONLINE_LECTURE -> delegate.requestOnlineLecture(command.text(0))
            BridgeMethodId.VIDEO_RECEIVE_PLAYER_STATES -> delegate.receivePlayerStates(
                command.text(0), command.text(1), command.text(2), command.text(3), command.text(4),
            )
            BridgeMethodId.VIDEO_RECEIVE_INIT_SPEED -> delegate.receiveInitSpeed(command.text(0))
            BridgeMethodId.VIDEO_RECEIVE_VIDEO_DATA -> delegate.receiveVideoData(
                command.text(0), command.text(1),
            )
            BridgeMethodId.VIDEO_RECEIVE_VIDEO_URL -> delegate.receiveVideoURL(command.text(0))
            BridgeMethodId.VIDEO_PERFORM_HAPTIC_FEEDBACK -> delegate.performHapticFeedback(
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

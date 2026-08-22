package com.icecream.kwklasplus.core.bridge

class IosHomeLegacyBridgeCommandHandler(
    private val host: HomeBridgeHost,
) : BridgeCommandHandler {
    override suspend fun handle(command: ValidatedBridgeCommand) = command.execute {
        when (command.methodId) {
            BridgeMethodId.HOME_CHANGE_TAB -> host.changeTab(command.text(0))
            BridgeMethodId.HOME_EVALUATE -> host.evaluate(
                command.text(0),
                command.text(1),
                command.text(2),
            )
            BridgeMethodId.HOME_OPEN_PAGE -> host.openPage(command.text(0))
            BridgeMethodId.HOME_OPEN_EXTERNAL_PAGE -> host.openExternalPage(command.text(0))
            BridgeMethodId.HOME_COMPLETE_PAGE_LOAD -> host.completePageLoad()
            BridgeMethodId.HOME_OPEN_LIBRARY_QR -> host.openLibraryQR()
            BridgeMethodId.HOME_OPEN_LIBRARY_QR_SETTINGS_MODAL -> host.openLibraryQRSettingsModal()
            BridgeMethodId.HOME_OPEN_LECTURE_ACTIVITY -> host.openLectureActivity(
                command.text(0),
                command.text(1),
            )
            BridgeMethodId.HOME_QR_CHECK_IN -> host.qrCheckIn(command.text(0), command.text(1))
            BridgeMethodId.HOME_OPEN_DATE_TIME_PICKER -> host.openDateTimePicker(
                command.nullableText(0),
                command.boolean(1),
            )
            BridgeMethodId.HOME_OPEN_WEB_VIEW_BOTTOM_SHEET -> host.openWebViewBottomSheet()
            BridgeMethodId.HOME_CLOSE_WEB_VIEW_BOTTOM_SHEET -> host.closeWebViewBottomSheet()
            BridgeMethodId.HOME_OPEN_OPTIONS_MENU -> host.openOptionsMenu()
            BridgeMethodId.HOME_OPEN_YEAR_HAKGI_BOTTOM_SHEET -> host.openYearHakgiBottomSheet()
            BridgeMethodId.HOME_RELOAD -> host.reload()
            BridgeMethodId.HOME_PERFORM_HAPTIC_FEEDBACK -> host.performHapticFeedback(command.text(0))
            BridgeMethodId.HOME_REQUEST_ID_CARD_QR_VALUE -> host.requestIdCardQRValue()
            else -> return@execute false
        }
        true
    }
}

class IosLectureLegacyBridgeCommandHandler(
    private val host: LectureBridgeHost,
) : BridgeCommandHandler {
    override suspend fun handle(command: ValidatedBridgeCommand) = command.execute {
        when (command.methodId) {
            BridgeMethodId.LECTURE_COMPLETE_PAGE_LOAD -> host.completePageLoad()
            BridgeMethodId.LECTURE_OPEN_PAGE -> host.openPage(command.text(0))
            BridgeMethodId.LECTURE_GET_BOARD_PATH -> host.getBoardPath(
                command.text(0),
                command.text(1),
            )
            BridgeMethodId.LECTURE_OPEN_BOARD_LIST -> host.openBoardList(
                command.text(0),
                command.text(1),
            )
            BridgeMethodId.LECTURE_OPEN_BOARD_VIEW -> host.openBoardView(
                command.text(0),
                command.text(1),
                command.text(2),
            )
            BridgeMethodId.LECTURE_OPEN_EXTERNAL_LINK -> host.openExternalLink(command.text(0))
            BridgeMethodId.LECTURE_EVALUTE_KLAS_SCRIPT -> host.evaluteKLASScript(command.text(0))
            BridgeMethodId.LECTURE_OPEN_ONLINE_LECTURE -> host.openOnlineLecture()
            BridgeMethodId.LECTURE_OPEN_LECTURE_PLAN -> host.openLecturePlan()
            BridgeMethodId.LECTURE_OPEN_QR_SCAN -> host.openQRScan()
            else -> return@execute false
        }
        true
    }
}

class IosBoardLegacyBridgeCommandHandler(
    private val host: BoardBridgeHost,
) : BridgeCommandHandler {
    override suspend fun handle(command: ValidatedBridgeCommand) = command.execute {
        when (command.methodId) {
            BridgeMethodId.BOARD_OPEN_PAGE -> host.openPage(command.text(0))
            BridgeMethodId.BOARD_OPEN_EXTERNAL_LINK -> host.openExternalLink(command.text(0))
            BridgeMethodId.BOARD_COMPLETE_PAGE_LOAD -> host.completePageLoad()
            else -> return@execute false
        }
        true
    }
}

class IosLecturePlanLegacyBridgeCommandHandler(
    private val host: LecturePlanBridgeHost,
) : BridgeCommandHandler {
    override suspend fun handle(command: ValidatedBridgeCommand) = command.execute {
        when (command.methodId) {
            BridgeMethodId.LECTURE_PLAN_COMPLETE_PAGE_LOAD -> host.completePageLoad()
            BridgeMethodId.LECTURE_PLAN_OPEN_PAGE -> host.openPage(command.text(0))
            BridgeMethodId.LECTURE_PLAN_OPEN_EXTERNAL_PAGE -> host.openExternalPage(command.text(0))
            else -> return@execute false
        }
        true
    }
}

class IosLinkLegacyBridgeCommandHandler(
    private val host: LinkBridgeHost,
) : BridgeCommandHandler {
    override suspend fun handle(command: ValidatedBridgeCommand) = command.execute {
        when (command.methodId) {
            BridgeMethodId.LINK_VIEW_OPEN_PAGE -> host.openPage(command.text(0))
            BridgeMethodId.LINK_VIEW_OPEN_LECTURE_PLAN_PAGE -> host.openLecturePlanPage(command.text(0))
            BridgeMethodId.LINK_VIEW_OPEN_WEB_VIEW_BOTTOM_SHEET -> host.openWebViewBottomSheet()
            BridgeMethodId.LINK_VIEW_CLOSE_WEB_VIEW_BOTTOM_SHEET -> host.closeWebViewBottomSheet()
            BridgeMethodId.LINK_VIEW_COMPLETE_PAGE_LOAD -> host.completePageLoad()
            else -> return@execute false
        }
        true
    }
}

class IosSettingsLegacyBridgeCommandHandler(
    private val host: SettingsBridgeHost,
) : BridgeCommandHandler {
    override suspend fun handle(command: ValidatedBridgeCommand) = handleCommand(command, host)

    companion object {
        fun handleCommand(
            command: ValidatedBridgeCommand,
            host: SettingsBridgeHost,
        ) = command.execute {
            when (command.methodId) {
                BridgeMethodId.SETTINGS_COMPLETE_PAGE_LOAD -> host.completePageLoad()
                BridgeMethodId.SETTINGS_CHANGE_APP_THEME -> host.changeAppTheme(command.text(0))
                BridgeMethodId.SETTINGS_OPEN_YEAR_HAKGI_SELECT_MODAL -> host.openYearHakgiSelectModal()
                BridgeMethodId.SETTINGS_OPEN_LIBRARY_QR_SETTINGS_MODAL -> host.openLibraryQRSettingsModal()
                BridgeMethodId.SETTINGS_OPEN_EXTERNAL_LINK -> host.openExternalLink(command.text(0))
                BridgeMethodId.SETTINGS_PERFORM_HAPTIC_FEEDBACK -> host.performHapticFeedback(
                    command.text(0),
                )
                BridgeMethodId.SETTINGS_SET_APP_LOCK_ENABLED -> host.setAppLockEnabled(
                    command.boolean(0),
                )
                BridgeMethodId.SETTINGS_SET_APP_LOCK_PASSWORD -> host.setAppLockPassword()
                BridgeMethodId.SETTINGS_SET_BIOMETRIC_ENABLED -> host.setBiometricEnabled(
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
                BridgeHandlerResult.Success(BridgeValue.Text(host.getAppLockSettings()))
            } else {
                result
            }
        }
    }
}

class IosSettingsLegacySynchronousBridgeCommandHandler(
    private val host: SettingsBridgeHost,
) : SynchronousBridgeCommandHandler {
    override fun handle(command: ValidatedBridgeCommand): BridgeHandlerResult =
        IosSettingsLegacyBridgeCommandHandler.handleCommand(command, host)
}

private inline fun ValidatedBridgeCommand.execute(block: () -> Boolean): BridgeHandlerResult =
    if (block()) BridgeHandlerResult.Success() else BridgeHandlerResult.Failure(
        BridgeErrorCode.UNKNOWN_METHOD,
    )

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

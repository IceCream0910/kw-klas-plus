package com.icecream.kwklasplus.core.bridge

import com.icecream.kwklasplus.core.lock.AppLockSettings
import com.icecream.kwklasplus.core.session.runSuspendTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IosLegacyBridgeCommandHandlerTest {
    @Test
    fun homeChangeTabAndCompletePageLoadReachHost() = runSuspendTest {
        val host = RecordingHomeHost()
        val handler = IosHomeLegacyBridgeCommandHandler(host)
        handler.handle(command(BridgeMethodId.HOME_CHANGE_TAB, BridgeValue.Text("feed")))
        handler.handle(command(BridgeMethodId.HOME_COMPLETE_PAGE_LOAD))
        handler.handle(command(BridgeMethodId.HOME_OPEN_LECTURE_ACTIVITY, BridgeValue.Text("S1"), BridgeValue.Text("강의")))
        assertEquals(listOf("feed"), host.tabs)
        assertEquals(1, host.completeCount)
        assertEquals(listOf("S1" to "강의"), host.lectures)
    }

    @Test
    fun homeUnavailableCommandsStillSucceed() = runSuspendTest {
        val host = RecordingHomeHost()
        val handler = IosHomeLegacyBridgeCommandHandler(host)
        val result = handler.handle(command(BridgeMethodId.HOME_QR_CHECK_IN, BridgeValue.Text("S"), BridgeValue.Text("N")))
        assertTrue(result is BridgeHandlerResult.Success)
        assertEquals(1, host.unavailableCount)
    }

    @Test
    fun lectureBoardPathAndOpenBoardReachHost() = runSuspendTest {
        val host = RecordingLectureHost()
        val handler = IosLectureLegacyBridgeCommandHandler(host)
        handler.handle(
            command(BridgeMethodId.LECTURE_GET_BOARD_PATH, BridgeValue.Text("notice-id"), BridgeValue.Text("pds-id")),
        )
        handler.handle(
            command(BridgeMethodId.LECTURE_OPEN_BOARD_LIST, BridgeValue.Text("notice"), BridgeValue.Text("공지")),
        )
        assertEquals("notice-id", host.noticePath)
        assertEquals("pds-id", host.pdsPath)
        assertEquals(listOf("notice" to "공지"), host.boardLists)
    }

    @Test
    fun settingsGetAppLockSettingsReturnsDefaultJson() = runSuspendTest {
        val host = RecordingSettingsHost()
        val handler = IosSettingsLegacyBridgeCommandHandler(host)
        val result = handler.handle(command(BridgeMethodId.SETTINGS_GET_APP_LOCK_SETTINGS))
        val success = result as BridgeHandlerResult.Success
        val text = (success.value as BridgeValue.Text).value
        assertEquals(AppLockSettings(false, false, false).toLegacyJson(), text)
    }

    @Test
    fun boardCompletePageLoadReachesHost() = runSuspendTest {
        val host = RecordingBoardHost()
        val handler = IosBoardLegacyBridgeCommandHandler(host)
        handler.handle(command(BridgeMethodId.BOARD_COMPLETE_PAGE_LOAD))
        assertEquals(1, host.completeCount)
    }

    @Test
    fun videoCommandsReachHost() = runSuspendTest {
        val host = RecordingVideoHost()
        val handler = IosVideoLegacyBridgeCommandHandler(host)
        handler.handle(command(BridgeMethodId.VIDEO_COMPLETE_PAGE_LOAD))
        handler.handle(command(BridgeMethodId.VIDEO_OPEN_IN_KLAS))
        handler.handle(command(BridgeMethodId.VIDEO_REQUEST_ONLINE_LECTURE, BridgeValue.Text("{}")))
        handler.handle(
            command(
                BridgeMethodId.VIDEO_RECEIVE_PLAYER_STATES,
                BridgeValue.Text("1"),
                BridgeValue.Text("10"),
                BridgeValue.Text("true"),
                BridgeValue.Text("false"),
                BridgeValue.Text("false"),
            ),
        )
        handler.handle(command(BridgeMethodId.VIDEO_RECEIVE_INIT_SPEED, BridgeValue.Text("1.5")))
        handler.handle(
            command(
                BridgeMethodId.VIDEO_RECEIVE_VIDEO_DATA,
                BridgeValue.Text("progress"),
                BridgeValue.Text("time"),
            ),
        )
        handler.handle(
            command(
                BridgeMethodId.VIDEO_RECEIVE_VIDEO_URL,
                BridgeValue.Text("https://vod.kw.ac.kr/player"),
            ),
        )
        handler.handle(command(BridgeMethodId.VIDEO_OPEN_EXTERNAL_LINK, BridgeValue.Text("https://example.com")))
        handler.handle(command(BridgeMethodId.VIDEO_PERFORM_HAPTIC_FEEDBACK, BridgeValue.Text("CLOCK_TICK")))
        assertEquals(1, host.completeCount)
        assertEquals(1, host.openInKlasCount)
        assertEquals(listOf("{}"), host.onlineLectures)
        assertEquals(listOf("1" to "10"), host.playerStates)
        assertEquals(listOf("1.5"), host.speeds)
        assertEquals(listOf("progress" to "time"), host.videoData)
        assertEquals(listOf("https://vod.kw.ac.kr/player"), host.videoUrls)
        assertEquals(listOf("https://example.com"), host.externalLinks)
        assertEquals(listOf("CLOCK_TICK"), host.haptics)
    }

    private fun command(
        methodId: BridgeMethodId,
        vararg arguments: BridgeValue,
    ): ValidatedBridgeCommand {
        val method = requireNotNull(LegacyBridgeCatalog.find(methodId.surface, methodId.legacyName))
        return ValidatedBridgeCommand(
            requestId = "test",
            surface = methodId.surface,
            methodId = methodId,
            method = method,
            arguments = arguments.toList(),
        )
    }
}

private class RecordingHomeHost : HomeBridgeHost {
    val tabs = mutableListOf<String>()
    val lectures = mutableListOf<Pair<String, String>>()
    var completeCount = 0
    var unavailableCount = 0

    override fun changeTab(tab: String) {
        tabs += tab
    }

    override fun evaluate(url: String, yearHakgi: String, subj: String) = Unit
    override fun openPage(url: String) = Unit
    override fun openExternalPage(url: String) = Unit
    override fun completePageLoad() {
        completeCount += 1
    }

    override fun openLibraryQR() {
        unavailableCount += 1
    }

    override fun openLibraryQRSettingsModal() {
        unavailableCount += 1
    }

    override fun openLectureActivity(subj: String, subjName: String) {
        lectures += subj to subjName
    }

    override fun qrCheckIn(subjId: String, subjName: String) {
        unavailableCount += 1
    }

    override fun openDateTimePicker(currentDateTime: String?, isStart: Boolean) = Unit
    override fun openWebViewBottomSheet() = Unit
    override fun closeWebViewBottomSheet() = Unit
    override fun openOptionsMenu() = Unit
    override fun openYearHakgiBottomSheet() = Unit
    override fun reload() = Unit
    override fun performHapticFeedback(type: String) = Unit
    override fun requestIdCardQRValue() {
        unavailableCount += 1
    }
}

private class RecordingLectureHost : LectureBridgeHost {
    var noticePath = ""
    var pdsPath = ""
    val boardLists = mutableListOf<Pair<String, String>>()

    override fun completePageLoad() = Unit
    override fun openPage(url: String) = Unit
    override fun getBoardPath(noticePath: String, pdsPath: String) {
        this.noticePath = noticePath
        this.pdsPath = pdsPath
    }

    override fun openBoardList(type: String, title: String) {
        boardLists += type to title
    }

    override fun openBoardView(type: String, boardNo: String, masterNo: String) = Unit
    override fun openExternalLink(url: String) = Unit
    override fun evaluteKLASScript(script: String) = Unit
    override fun openOnlineLecture() = Unit
    override fun openLecturePlan() = Unit
    override fun openQRScan() = Unit
}

private class RecordingBoardHost : BoardBridgeHost {
    var completeCount = 0
    override fun openPage(url: String) = Unit
    override fun openExternalLink(url: String) = Unit
    override fun completePageLoad() {
        completeCount += 1
    }
}

private class RecordingSettingsHost : SettingsBridgeHost {
    override fun completePageLoad() = Unit
    override fun changeAppTheme(type: String) = Unit
    override fun openYearHakgiSelectModal() = Unit
    override fun openLibraryQRSettingsModal() = Unit
    override fun openExternalLink(url: String) = Unit
    override fun performHapticFeedback(type: String) = Unit
    override fun setAppLockEnabled(enabled: Boolean) = Unit
    override fun setAppLockPassword() = Unit
    override fun setBiometricEnabled(enabled: Boolean) = Unit
    override fun getAppLockSettings(): String = AppLockSettings(false, false, false).toLegacyJson()
}

private class RecordingVideoHost : VideoBridgeHost {
    var completeCount = 0
    var openInKlasCount = 0
    val onlineLectures = mutableListOf<String>()
    val playerStates = mutableListOf<Pair<String, String>>()
    val speeds = mutableListOf<String>()
    val videoData = mutableListOf<Pair<String, String>>()
    val videoUrls = mutableListOf<String>()
    val externalLinks = mutableListOf<String>()
    val haptics = mutableListOf<String>()

    override fun completePageLoad() {
        completeCount += 1
    }

    override fun openExternalLink(url: String) {
        externalLinks += url
    }

    override fun openInKLAS() {
        openInKlasCount += 1
    }

    override fun requestOnlineLecture(json: String) {
        onlineLectures += json
    }

    override fun receivePlayerStates(
        currentTime: String,
        duration: String,
        isMuted: String,
        isPlaying: String,
        isFullscreen: String,
    ) {
        playerStates += currentTime to duration
    }

    override fun receiveInitSpeed(currentSpeed: String) {
        speeds += currentSpeed
    }

    override fun receiveVideoData(progress: String, time: String) {
        videoData += progress to time
    }

    override fun receiveVideoURL(videoURL: String) {
        videoUrls += videoURL
    }

    override fun performHapticFeedback(type: String) {
        haptics += type
    }
}

package com.icecream.kwklasplus.core.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WebAutomationScriptsTest {
    @Test
    fun lectureArgumentsAreEncoded() {
        assertEquals(
            "appModule.goLctrum(\"2026,1\",\"SUBJ'01\");",
            KlasWebAutomationScripts.openLecture("2026,1", "SUBJ'01").reveal(),
        )
    }

    @Test
    fun playerNumbersAreValidated() {
        assertEquals(
            "bcPlayController.getPlayController()._eventTarget.fire(VCPlayControllerEvent.CHANGE_PLAYBACK_RATE,Number(1.5));",
            PlayerWebScripts.changePlaybackRate(1.5).reveal(),
        )
        assertFailsWith<IllegalArgumentException> { PlayerWebScripts.changePlaybackRate(Double.NaN) }
        assertFailsWith<IllegalArgumentException> { PlayerWebScripts.seekTo(-1.0) }
    }

    @Test
    fun onlineContentArgumentsAreJsonEncoded() {
        val script = PlayerWebScripts.openOnlineContent(
            PlayerWebScripts.OnlineContentRequest(
                groupCode = "G'\"\\",
                subjectId = "한글\n과목",
                year = "2026",
                semester = "1",
                classNumber = "01",
                module = "M",
                lesson = "L",
                objectId = "O",
                starting = "S",
                contentsType = "V",
                weekNumber = 1,
                weeklySequence = 2,
                width = 1280,
                height = 720,
                today = "20260717",
                startDate = "20260701",
                endDate = "20260731",
                playerType = "P",
                learnTime = "30",
                progress = 50,
                playTime = "10",
            ),
        ).reveal()

        assertTrue(script.startsWith("lrnCerti.checkCerti(\"G'\\\"\\\\\",\"한글\\n과목\""))
        assertTrue(script.endsWith(",50,\"C\",\"10\");"))
    }

    @Test
    fun platformIndependentPageAndPlayerScriptsPreserveContracts() {
        val scripts = listOf(
            KlasWebAutomationScripts.collectLectureBoardPaths().reveal(),
            KlasWebAutomationScripts.monitorLectureProgress().reveal(),
            KlasWebAutomationScripts.reportViewerVideoUrl().reveal(),
            PlayerWebScripts.monitorState().reveal(),
        )

        scripts.forEach {
            assertTrue(it.contains("window.KlasNativeBridge."))
            assertTrue(!it.contains("KlasNativeBridgeNative"))
            assertTrue(!it.contains("Android."))
        }
        assertTrue(scripts[0].contains("KlasNativeBridge.getBoardPath("))
        assertTrue(scripts[1].contains("KlasNativeBridge.receiveVideoData("))
        assertTrue(scripts[2].contains("KlasNativeBridge.receiveVideoURL("))
        assertTrue(scripts[3].contains("KlasNativeBridge.receiveInitSpeed("))
        assertTrue(scripts[3].contains("KlasNativeBridge.receivePlayerStates("))
        assertTrue(PlayerWebScripts.move(PlayerSeekDirection.FORWARD).reveal().contains("_seekLimit"))
        assertTrue(PlayerWebScripts.setControllerVisible(false).reveal().contains("display: none"))
    }

    @Test
    fun nativeBridgeAdapterOwnsBridgeV1TransportDetails() {
        val source = KlasNativeBridgeScripts.installAdapter().reveal()

        assertTrue(source.contains("global.KlasNativeBridgeNative"))
        assertTrue(source.contains("global.KlasNativeBridge=new Proxy"))
        assertTrue(source.contains("transport.postMessage(JSON.stringify({version:1"))
        assertTrue(source.contains("arguments:args"))
        assertTrue(source.contains("BRIDGE_TIMEOUT"))
    }

    @Test
    fun calendarFooterInsetTargetsOnlyTheFooterAndReusesOneStyleElement() {
        val source = KlasWebAutomationScripts.updateCalendarBottomSheetFooterInset(48).reveal()

        assertTrue(source.contains(".bottom-sheet-footer"))
        assertTrue(source.contains("--klas-calendar-footer-inset"))
        assertTrue(source.contains("getElementById(id)"))
        assertTrue(source.endsWith("})(48);"))
        assertFailsWith<IllegalArgumentException> {
            KlasWebAutomationScripts.updateCalendarBottomSheetFooterInset(-1)
        }
    }
}

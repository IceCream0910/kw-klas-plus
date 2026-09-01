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
    fun openLectureWhenReadyWaitsForGoLctrumThenCallsOnce() {
        val script = KlasWebAutomationScripts.openLectureWhenReady("2026,1", "SUBJ'01").reveal()
        assertTrue(script.contains("appModule.goLctrum(\"2026,1\",\"SUBJ'01\");"))
        assertTrue(script.contains("typeof appModule.goLctrum==='function'"))
        assertTrue(script.contains("setTimeout"))
        assertTrue(!script.contains("window.alert"))
        assertTrue(script.startsWith("(function(){"))
        assertTrue(script.endsWith("go(20);})();"))
    }

    @Test
    fun lectureBoardPathCollectionWaitsForRenderedLinksWithoutJquery() {
        val script = KlasWebAutomationScripts.collectLectureBoardPaths(
            maxRetries = 8,
            intervalMs = 400,
        ).reveal()

        assertTrue(script.contains("document.querySelectorAll('a[onclick],a[href]')"))
        assertTrue(script.contains("findPath('공지사항')"))
        assertTrue(script.contains("findPath('자료실')"))
        assertTrue(script.contains("KlasNativeBridge.getBoardPath(notice,pds)"))
        assertTrue(script.contains("setTimeout(function(){collect(remaining-1);},400)"))
        assertTrue(script.endsWith("collect(8);})();"))
        assertTrue(!script.contains("a:contains"))
    }

    @Test
    fun lectureBoardPathCollectionRejectsInvalidRetryOptions() {
        assertFailsWith<IllegalArgumentException> {
            KlasWebAutomationScripts.collectLectureBoardPaths(maxRetries = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            KlasWebAutomationScripts.collectLectureBoardPaths(intervalMs = 0)
        }
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
    fun redirectWindowOpenToSameFrameNavigatesCurrentWindow() {
        val script = KlasWebAutomationScripts.redirectWindowOpenToSameFrame().reveal()
        assertTrue(script.contains("w.open=go"))
        assertTrue(script.contains("location.href=href"))
        assertTrue(script.contains("about:blank"))
        assertTrue(script.contains("install(frames[i])"))
        assertTrue(!script.contains("Android."))
    }

    @Test
    fun onlineContentViewerUsesGoViewCntntsWithoutCertFlag() {
        val request = PlayerWebScripts.OnlineContentRequest(
            groupCode = "G",
            subjectId = "S",
            year = "2026",
            semester = "1",
            classNumber = "01",
            module = "M",
            lesson = "L",
            objectId = "O",
            starting = "S",
            contentsType = "V",
            weekNumber = 2,
            weeklySequence = 1,
            width = 1280,
            height = 720,
            today = "20260717",
            startDate = "20260701",
            endDate = "20260731",
            playerType = "P",
            learnTime = "30",
            progress = 50,
            playTime = "10",
        )
        val viewer = PlayerWebScripts.openOnlineContentViewer(request).reveal()
        assertTrue(viewer.startsWith("appModule.goViewCntnts("))
        assertTrue(!viewer.contains(",\"C\","))
        val certi = PlayerWebScripts.openOnlineContent(request).reveal()
        assertTrue(certi.startsWith("lrnCerti.checkCerti("))
        assertTrue(certi.contains(",\"C\","))
    }

    @Test
    fun nativeBridgeAdapterOwnsBridgeV1TransportDetails() {
        val source = KlasNativeBridgeScripts.installAdapter().reveal()

        assertTrue(source.contains("global.KlasNativeBridgeNative"))
        assertTrue(source.contains("global.KlasNativeBridge=new Proxy"))
        assertTrue(source.contains("transport.postMessage(JSON.stringify({version:1"))
        assertTrue(source.contains("arguments:args"))
        assertTrue(source.contains("BRIDGE_TIMEOUT"))
        assertTrue(source.contains(",${KlasNativeBridgeScripts.DEFAULT_BRIDGE_TIMEOUT_MILLIS});"))
    }

    @Test
    fun webKitTransportShimBridgesPromiseReplyToOnmessage() {
        val source = KlasNativeBridgeScripts.installWebKitTransport().reveal()

        assertTrue(source.contains("webkit.messageHandlers"))
        assertTrue(source.contains("handlers.KlasNativeBridgeNative"))
        assertTrue(source.contains(KlasNativeBridgeScripts.NATIVE_OBJECT_NAME))
        assertTrue(source.contains("__klasWebKitTransport"))
        assertTrue(source.contains("onmessage"))
        assertTrue(source.contains("Promise.resolve(native.postMessage(data))"))
        assertTrue(source.contains("self.onmessage({data:response})"))
    }

    @Test
    fun installAdapterRejectsNonPositiveTimeout() {
        assertFailsWith<IllegalArgumentException> {
            KlasNativeBridgeScripts.installAdapter(timeoutMillis = 0)
        }
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

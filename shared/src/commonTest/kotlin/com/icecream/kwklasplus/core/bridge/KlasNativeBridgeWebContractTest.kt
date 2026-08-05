package com.icecream.kwklasplus.core.bridge

import kotlin.test.Test
import kotlin.test.assertEquals

class KlasNativeBridgeWebContractTest {
    @Test
    fun activeWebAdapterMethodsAreSupportedByNativeBridgeV1() {
        val nativeMethods = LegacyBridgeCatalog.methods.values
            .flatten()
            .mapTo(mutableSetOf()) { it.name }

        assertEquals(emptySet(), activeWebMethods - nativeMethods)
    }

    private companion object {
        val activeWebMethods = setOf(
            "changeAppTheme",
            "closeWebViewBottomSheet",
            "completePageLoad",
            "evaluate",
            "evaluteKLASScript",
            "getAppLockSettings",
            "getBoardPath",
            "openBoardList",
            "openBoardView",
            "openDateTimePicker",
            "openExternalLink",
            "openExternalPage",
            "openInKLAS",
            "openLectureActivity",
            "openLecturePlan",
            "openLecturePlanPage",
            "openLibraryQR",
            "openLibraryQRSettingsModal",
            "openOnlineLecture",
            "openOptionsMenu",
            "openPage",
            "openQRScan",
            "openWebViewBottomSheet",
            "openYearHakgiBottomSheet",
            "openYearHakgiSelectModal",
            "performHapticFeedback",
            "qrCheckIn",
            "receiveInitSpeed",
            "receivePlayerStates",
            "receiveVideoData",
            "receiveVideoURL",
            "reload",
            "requestIdCardQRValue",
            "requestOnlineLecture",
            "setAppLockEnabled",
            "setAppLockPassword",
            "setBiometricEnabled",
        )
    }
}

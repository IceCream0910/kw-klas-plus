package com.icecream.kwklasplus.core.bridge

object LegacyBridgeCatalog {
    private val string = BridgeArgumentType.STRING
    private val nullableString = BridgeArgumentType.NULLABLE_STRING
    private val boolean = BridgeArgumentType.BOOLEAN

    val methods: Map<BridgeSurface, List<LegacyBridgeMethod>> = mapOf(
        BridgeSurface.BOARD to listOf(
            method("openPage", string), method("openExternalLink", string), method("completePageLoad"),
        ),
        BridgeSurface.HOME to listOf(
            method("changeTab", string),
            method("evaluate", string, string, string),
            method("openPage", string),
            method("openExternalPage", string),
            method("completePageLoad"),
            method("openLibraryQR"),
            method("openLibraryQRSettingsModal"),
            method("openLectureActivity", string, string),
            method("qrCheckIn", string, string),
            method("openDateTimePicker", nullableString, boolean),
            method("openWebViewBottomSheet"),
            method("closeWebViewBottomSheet"),
            method("openOptionsMenu"),
            method("openYearHakgiBottomSheet"),
            method("reload"),
            method("performHapticFeedback", string),
            method("requestIdCardQRValue"),
        ),
        BridgeSurface.LECTURE_PLAN to listOf(
            method("completePageLoad"), method("openPage", string), method("openExternalPage", string),
        ),
        BridgeSurface.LECTURE to listOf(
            method("completePageLoad"),
            method("openPage", string),
            method("getBoardPath", string, string),
            method("openBoardList", string, string),
            method("openBoardView", string, string, string),
            method("openExternalLink", string),
            method("evaluteKLASScript", string),
            method("openOnlineLecture"),
            method("openLecturePlan"),
            method("openQRScan"),
        ),
        BridgeSurface.LINK_VIEW to listOf(
            method("openPage", string),
            method("openLecturePlanPage", string),
            method("openWebViewBottomSheet"),
            method("closeWebViewBottomSheet"),
            method("completePageLoad"),
        ),
        BridgeSurface.SETTINGS to listOf(
            method("completePageLoad"),
            method("changeAppTheme", string),
            method("openYearHakgiSelectModal"),
            method("openLibraryQRSettingsModal"),
            method("openExternalLink", string),
            method("performHapticFeedback", string),
            method("setAppLockEnabled", boolean),
            method("setAppLockPassword"),
            method("setBiometricEnabled", boolean),
            LegacyBridgeMethod("getAppLockSettings", synchronousReturn = true),
        ),
        BridgeSurface.VIDEO to listOf(
            method("completePageLoad"),
            method("openExternalLink", string),
            method("openInKLAS"),
            method("requestOnlineLecture", string),
            method("receivePlayerStates", string, string, string, string, string),
            method("receiveInitSpeed", string),
            method("receiveVideoData", string, string),
            method("receiveVideoURL", string),
            method("performHapticFeedback", string),
        ),
    )

    fun find(surface: BridgeSurface, name: String): LegacyBridgeMethod? =
        methods[surface]?.firstOrNull { it.name == name }

    private fun method(name: String, vararg arguments: BridgeArgumentType) =
        LegacyBridgeMethod(name, arguments.toList())
}

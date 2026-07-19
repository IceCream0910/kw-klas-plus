package com.icecream.kwklasplus.core.bridge

enum class BridgeMethodId(
    val surface: BridgeSurface,
    val legacyName: String,
) {
    BOARD_OPEN_PAGE(BridgeSurface.BOARD, "openPage"),
    BOARD_OPEN_EXTERNAL_LINK(BridgeSurface.BOARD, "openExternalLink"),
    BOARD_COMPLETE_PAGE_LOAD(BridgeSurface.BOARD, "completePageLoad"),

    HOME_CHANGE_TAB(BridgeSurface.HOME, "changeTab"),
    HOME_EVALUATE(BridgeSurface.HOME, "evaluate"),
    HOME_OPEN_PAGE(BridgeSurface.HOME, "openPage"),
    HOME_OPEN_EXTERNAL_PAGE(BridgeSurface.HOME, "openExternalPage"),
    HOME_COMPLETE_PAGE_LOAD(BridgeSurface.HOME, "completePageLoad"),
    HOME_OPEN_LIBRARY_QR(BridgeSurface.HOME, "openLibraryQR"),
    HOME_OPEN_LIBRARY_QR_SETTINGS_MODAL(BridgeSurface.HOME, "openLibraryQRSettingsModal"),
    HOME_OPEN_LECTURE_ACTIVITY(BridgeSurface.HOME, "openLectureActivity"),
    HOME_QR_CHECK_IN(BridgeSurface.HOME, "qrCheckIn"),
    HOME_OPEN_DATE_TIME_PICKER(BridgeSurface.HOME, "openDateTimePicker"),
    HOME_OPEN_WEB_VIEW_BOTTOM_SHEET(BridgeSurface.HOME, "openWebViewBottomSheet"),
    HOME_CLOSE_WEB_VIEW_BOTTOM_SHEET(BridgeSurface.HOME, "closeWebViewBottomSheet"),
    HOME_OPEN_OPTIONS_MENU(BridgeSurface.HOME, "openOptionsMenu"),
    HOME_OPEN_YEAR_HAKGI_BOTTOM_SHEET(BridgeSurface.HOME, "openYearHakgiBottomSheet"),
    HOME_OPEN_CUSTOM_BOTTOM_SHEET(BridgeSurface.HOME, "openCustomBottomSheet"),
    HOME_RELOAD(BridgeSurface.HOME, "reload"),
    HOME_PERFORM_HAPTIC_FEEDBACK(BridgeSurface.HOME, "performHapticFeedback"),
    HOME_REQUEST_ID_CARD_QR_VALUE(BridgeSurface.HOME, "requestIdCardQRValue"),

    LECTURE_PLAN_COMPLETE_PAGE_LOAD(BridgeSurface.LECTURE_PLAN, "completePageLoad"),
    LECTURE_PLAN_OPEN_PAGE(BridgeSurface.LECTURE_PLAN, "openPage"),
    LECTURE_PLAN_OPEN_EXTERNAL_PAGE(BridgeSurface.LECTURE_PLAN, "openExternalPage"),

    LECTURE_COMPLETE_PAGE_LOAD(BridgeSurface.LECTURE, "completePageLoad"),
    LECTURE_OPEN_PAGE(BridgeSurface.LECTURE, "openPage"),
    LECTURE_GET_BOARD_PATH(BridgeSurface.LECTURE, "getBoardPath"),
    LECTURE_OPEN_BOARD_LIST(BridgeSurface.LECTURE, "openBoardList"),
    LECTURE_OPEN_BOARD_VIEW(BridgeSurface.LECTURE, "openBoardView"),
    LECTURE_OPEN_EXTERNAL_LINK(BridgeSurface.LECTURE, "openExternalLink"),
    LECTURE_EVALUTE_KLAS_SCRIPT(BridgeSurface.LECTURE, "evaluteKLASScript"),
    LECTURE_OPEN_ONLINE_LECTURE(BridgeSurface.LECTURE, "openOnlineLecture"),
    LECTURE_OPEN_LECTURE_PLAN(BridgeSurface.LECTURE, "openLecturePlan"),
    LECTURE_OPEN_QR_SCAN(BridgeSurface.LECTURE, "openQRScan"),

    LINK_VIEW_OPEN_PAGE(BridgeSurface.LINK_VIEW, "openPage"),
    LINK_VIEW_OPEN_LECTURE_PLAN_PAGE(BridgeSurface.LINK_VIEW, "openLecturePlanPage"),
    LINK_VIEW_OPEN_WEB_VIEW_BOTTOM_SHEET(BridgeSurface.LINK_VIEW, "openWebViewBottomSheet"),
    LINK_VIEW_CLOSE_WEB_VIEW_BOTTOM_SHEET(BridgeSurface.LINK_VIEW, "closeWebViewBottomSheet"),
    LINK_VIEW_COMPLETE_PAGE_LOAD(BridgeSurface.LINK_VIEW, "completePageLoad"),

    SETTINGS_COMPLETE_PAGE_LOAD(BridgeSurface.SETTINGS, "completePageLoad"),
    SETTINGS_CHANGE_APP_THEME(BridgeSurface.SETTINGS, "changeAppTheme"),
    SETTINGS_OPEN_YEAR_HAKGI_SELECT_MODAL(BridgeSurface.SETTINGS, "openYearHakgiSelectModal"),
    SETTINGS_OPEN_LIBRARY_QR_SETTINGS_MODAL(BridgeSurface.SETTINGS, "openLibraryQRSettingsModal"),
    SETTINGS_OPEN_EXTERNAL_LINK(BridgeSurface.SETTINGS, "openExternalLink"),
    SETTINGS_PERFORM_HAPTIC_FEEDBACK(BridgeSurface.SETTINGS, "performHapticFeedback"),
    SETTINGS_SET_APP_LOCK_ENABLED(BridgeSurface.SETTINGS, "setAppLockEnabled"),
    SETTINGS_SET_APP_LOCK_PASSWORD(BridgeSurface.SETTINGS, "setAppLockPassword"),
    SETTINGS_SET_BIOMETRIC_ENABLED(BridgeSurface.SETTINGS, "setBiometricEnabled"),
    SETTINGS_GET_APP_LOCK_SETTINGS(BridgeSurface.SETTINGS, "getAppLockSettings"),

    VIDEO_COMPLETE_PAGE_LOAD(BridgeSurface.VIDEO, "completePageLoad"),
    VIDEO_OPEN_EXTERNAL_LINK(BridgeSurface.VIDEO, "openExternalLink"),
    VIDEO_OPEN_IN_KLAS(BridgeSurface.VIDEO, "openInKLAS"),
    VIDEO_REQUEST_ONLINE_LECTURE(BridgeSurface.VIDEO, "requestOnlineLecture"),
    VIDEO_RECEIVE_PLAYER_STATES(BridgeSurface.VIDEO, "receivePlayerStates"),
    VIDEO_RECEIVE_INIT_SPEED(BridgeSurface.VIDEO, "receiveInitSpeed"),
    VIDEO_RECEIVE_VIDEO_DATA(BridgeSurface.VIDEO, "receiveVideoData"),
    VIDEO_RECEIVE_VIDEO_URL(BridgeSurface.VIDEO, "receiveVideoURL"),
    VIDEO_PERFORM_HAPTIC_FEEDBACK(BridgeSurface.VIDEO, "performHapticFeedback"),

    WEB_VIEW_MODAL_COMPLETE_PAGE_LOAD(BridgeSurface.WEB_VIEW_MODAL, "completePageLoad"),
    WEB_VIEW_MODAL_CLOSE_MODAL(BridgeSurface.WEB_VIEW_MODAL, "closeModal"),
    WEB_VIEW_MODAL_SHOW_TOAST(BridgeSurface.WEB_VIEW_MODAL, "showToast"),
    WEB_VIEW_MODAL_OPEN_EXTERNAL_PAGE(BridgeSurface.WEB_VIEW_MODAL, "openExternalPage"),
    WEB_VIEW_MODAL_OPEN_LIBRARY_QR(BridgeSurface.WEB_VIEW_MODAL, "openLibraryQR"),
    WEB_VIEW_MODAL_OPEN_PAGE(BridgeSurface.WEB_VIEW_MODAL, "openPage"),
    ;

    companion object {
        private val byLegacyContract = entries.associateBy { it.surface to it.legacyName }

        fun from(surface: BridgeSurface, legacyName: String): BridgeMethodId? =
            byLegacyContract[surface to legacyName]
    }
}

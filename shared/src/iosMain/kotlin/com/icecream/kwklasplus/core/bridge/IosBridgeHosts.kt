package com.icecream.kwklasplus.core.bridge

interface HomeBridgeHost {
    fun changeTab(tab: String)
    fun evaluate(url: String, yearHakgi: String, subj: String)
    fun openPage(url: String)
    fun openExternalPage(url: String)
    fun completePageLoad()
    fun openLibraryQR()
    fun openLibraryQRSettingsModal()
    fun openLectureActivity(subj: String, subjName: String)
    fun qrCheckIn(subjId: String, subjName: String)
    fun openDateTimePicker(currentDateTime: String?, isStart: Boolean)
    fun openWebViewBottomSheet()
    fun closeWebViewBottomSheet()
    fun openOptionsMenu()
    fun openYearHakgiBottomSheet()
    fun reload()
    fun performHapticFeedback(type: String)
    fun requestIdCardQRValue()
}

interface LectureBridgeHost {
    fun completePageLoad()
    fun openPage(url: String)
    fun getBoardPath(noticePath: String, pdsPath: String)
    fun openBoardList(type: String, title: String)
    fun openBoardView(type: String, boardNo: String, masterNo: String)
    fun openExternalLink(url: String)
    fun evaluteKLASScript(script: String)
    fun openOnlineLecture()
    fun openLecturePlan()
    fun openQRScan()
}

interface BoardBridgeHost {
    fun openPage(url: String)
    fun openExternalLink(url: String)
    fun completePageLoad()
}

interface LecturePlanBridgeHost {
    fun completePageLoad()
    fun openPage(url: String)
    fun openExternalPage(url: String)
}

interface LinkBridgeHost {
    fun openPage(url: String)
    fun openLecturePlanPage(id: String)
    fun openWebViewBottomSheet()
    fun closeWebViewBottomSheet()
    fun completePageLoad()
}

interface SettingsBridgeHost {
    fun completePageLoad()
    fun changeAppTheme(type: String)
    fun openYearHakgiSelectModal()
    fun openLibraryQRSettingsModal()
    fun openExternalLink(url: String)
    fun performHapticFeedback(type: String)
    fun setAppLockEnabled(enabled: Boolean)
    fun setAppLockPassword()
    fun setBiometricEnabled(enabled: Boolean)
    fun getAppLockSettings(): String
}

interface VideoBridgeHost {
    fun completePageLoad()
    fun openExternalLink(url: String)
    fun openInKLAS()
    fun requestOnlineLecture(json: String)
    fun receivePlayerStates(
        currentTime: String,
        duration: String,
        isMuted: String,
        isPlaying: String,
        isFullscreen: String,
    )
    fun receiveInitSpeed(currentSpeed: String)
    fun receiveVideoData(progress: String, time: String)
    fun receiveVideoURL(videoURL: String)
    fun performHapticFeedback(type: String)
}

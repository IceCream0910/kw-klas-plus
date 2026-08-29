package com.icecream.kwklasplus.core.web

import com.icecream.kwklasplus.core.lock.AppLockSettings

object IosWebCallbacks {
    fun receiveToken(token: String): WebScript =
        LegacyWebScripts.call(LegacyWebCallback.RECEIVE_TOKEN, JavaScriptArgument.Text(token))

    fun receivedData(token: String, subjectId: String): WebScript =
        LegacyWebScripts.call(
            LegacyWebCallback.RECEIVE_DATA,
            JavaScriptArgument.Text(token),
            JavaScriptArgument.Text(subjectId),
        )

    fun receivedData(token: String, subjectId: String, yearHakgi: String): WebScript =
        LegacyWebScripts.call(
            LegacyWebCallback.RECEIVE_DATA,
            JavaScriptArgument.Text(token),
            JavaScriptArgument.Text(subjectId),
            JavaScriptArgument.Text(yearHakgi),
        )

    fun receivedData(
        token: String,
        subjectId: String,
        yearHakgi: String,
        path: String,
    ): WebScript =
        LegacyWebScripts.call(
            LegacyWebCallback.RECEIVE_DATA,
            JavaScriptArgument.Text(token),
            JavaScriptArgument.Text(subjectId),
            JavaScriptArgument.Text(yearHakgi),
            JavaScriptArgument.Text(path),
        )

    fun receiveTimetable(json: String): WebScript =
        LegacyWebScripts.call(LegacyWebCallback.RECEIVE_TIMETABLE, JavaScriptArgument.Text(json))

    fun receiveDeadline(json: String): WebScript =
        LegacyWebScripts.call(LegacyWebCallback.RECEIVE_DEADLINE, JavaScriptArgument.Text(json))

    fun receiveYearHakgi(value: String): WebScript =
        LegacyWebScripts.call(LegacyWebCallback.RECEIVE_YEAR_SEMESTER, JavaScriptArgument.Text(value))

    fun receiveTheme(theme: String): WebScript =
        LegacyWebScripts.call(LegacyWebCallback.RECEIVE_THEME, JavaScriptArgument.Text(theme))

    fun receiveVersion(version: String): WebScript =
        LegacyWebScripts.call(LegacyWebCallback.RECEIVE_VERSION, JavaScriptArgument.Text(version))

    fun updateYearHakgiButtonText(value: String): WebScript =
        LegacyWebScripts.call(
            LegacyWebCallback.UPDATE_YEAR_SEMESTER_TEXT,
            JavaScriptArgument.Text(value),
        )

    fun setDateTime(value: String, isStart: Boolean): WebScript =
        LegacyWebScripts.call(
            LegacyWebCallback.SET_DATE_TIME,
            JavaScriptArgument.Text(value),
            JavaScriptArgument.BooleanValue(isStart),
        )

    fun setLocalStorage(key: String, value: String): WebScript =
        LegacyWebScripts.setLocalStorage(key, value)

    fun appLockSettingChanged(settings: AppLockSettings): WebScript =
        LegacyWebScripts.appLockSettingChanged(settings)

    fun appLockSettingChanged(enabled: Boolean): WebScript =
        LegacyWebScripts.call(
            LegacyWebCallback.APP_LOCK_SETTING_CHANGED,
            JavaScriptArgument.BooleanValue(enabled),
        )

    fun biometricSettingChanged(enabled: Boolean): WebScript =
        LegacyWebScripts.call(
            LegacyWebCallback.BIOMETRIC_SETTING_CHANGED,
            JavaScriptArgument.BooleanValue(enabled),
        )

    fun requestSettingsReload(): WebScript =
        WebScript("document.dispatchEvent(new Event('visibilitychange'));")
}

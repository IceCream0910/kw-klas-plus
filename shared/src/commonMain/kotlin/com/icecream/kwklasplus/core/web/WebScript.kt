package com.icecream.kwklasplus.core.web

import com.icecream.kwklasplus.core.lock.AppLockSettings

class WebScript internal constructor(private val source: String) {
    fun reveal(): String = source

    override fun toString(): String = "[WEB_SCRIPT_REDACTED]"
}

sealed interface JavaScriptArgument {
    data class Text(val value: String) : JavaScriptArgument
    data class BooleanValue(val value: Boolean) : JavaScriptArgument
    data class NumberValue(val value: Double) : JavaScriptArgument
    data object Null : JavaScriptArgument
}

enum class LegacyWebCallback(
    internal val functionPath: String,
    internal val minimumArgumentCount: Int,
    internal val maximumArgumentCount: Int = minimumArgumentCount,
) {
    LOGIN_SET_INITIAL("appLogin.setInitial", 3),
    RECEIVE_TOKEN("window.receiveToken", 1),
    RECEIVE_DATA("window.receivedData", 2, 4),
    RECEIVE_TIMETABLE("window.receiveTimetableData", 1),
    RECEIVE_DEADLINE("window.receiveDeadlineData", 1),
    RECEIVE_ID_CARD_QR("window.receiveIdCardQRValue", 2),
    UPDATE_YEAR_SEMESTER_TEXT("window.updateYearHakgiBtnText", 1),
    SET_DATE_TIME("window.setDateTime", 2),
    RECEIVE_THEME("window.receiveTheme", 1),
    RECEIVE_YEAR_SEMESTER("window.receiveYearHakgi", 1),
    RECEIVE_VERSION("window.receiveVersion", 1),
    APP_LOCK_SETTING_CHANGED("window.onAppLockSettingChanged", 1),
    BIOMETRIC_SETTING_CHANGED("window.onBiometricSettingChanged", 1),
    CLOSE_WEB_VIEW_BOTTOM_SHEET("window.closeWebViewBottomSheet", 0),
    PAGE_RELOAD("window.pageReload", 0),
}

object LegacyWebScripts {
    fun call(callback: LegacyWebCallback, vararg arguments: JavaScriptArgument): WebScript {
        require(arguments.size in callback.minimumArgumentCount..callback.maximumArgumentCount)
        val encodedArguments = arguments.joinToString(",") { JavaScriptEncoder.encode(it) }
        return WebScript("${callback.functionPath}($encodedArguments);")
    }

    fun setLocalStorage(key: String, value: String): WebScript = WebScript(
        "window.localStorage.setItem(${JavaScriptEncoder.encodeText(key)},${JavaScriptEncoder.encodeText(value)});",
    )

    fun appLockSettingChanged(settings: AppLockSettings): WebScript = WebScript(
        "window.onAppLockSettingChanged(${settings.toLegacyJson()});",
    )
}

fun interface WebScriptExecutor {
    fun execute(script: WebScript)
}

internal object JavaScriptEncoder {
    fun encode(argument: JavaScriptArgument): String = when (argument) {
        is JavaScriptArgument.Text -> encodeText(argument.value)
        is JavaScriptArgument.BooleanValue -> argument.value.toString()
        is JavaScriptArgument.NumberValue -> encodeNumber(argument.value)
        JavaScriptArgument.Null -> "null"
    }

    fun encodeText(value: String): String = buildString(value.length + 2) {
        append('"')
        value.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\u2028' -> append("\\u2028")
                '\u2029' -> append("\\u2029")
                else -> if (character.code < 0x20) {
                    append("\\u")
                    append(character.code.toString(16).padStart(4, '0'))
                } else {
                    append(character)
                }
            }
        }
        append('"')
    }

    private fun encodeNumber(value: Double): String {
        require(value.isFinite())
        return value.toString()
    }
}

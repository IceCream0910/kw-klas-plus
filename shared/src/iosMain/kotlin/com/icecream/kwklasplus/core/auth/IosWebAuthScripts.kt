package com.icecream.kwklasplus.core.auth

import com.icecream.kwklasplus.core.web.JavaScriptArgument
import com.icecream.kwklasplus.core.web.LegacyWebCallback
import com.icecream.kwklasplus.core.web.LegacyWebScripts

object IosWebAuthScripts {
    const val DESKTOP_LOGIN_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Whale/3.25.232.19 Safari/537.36"

    const val TIMEOUT_MILLIS: Long = 15_000

    fun loginSetInitial(accountId: String, encryptedPassword: String): String =
        LegacyWebScripts.call(
            LegacyWebCallback.LOGIN_SET_INITIAL,
            JavaScriptArgument.Text("on"),
            JavaScriptArgument.Text(accountId),
            JavaScriptArgument.Text(encryptedPassword),
        ).reveal()
}

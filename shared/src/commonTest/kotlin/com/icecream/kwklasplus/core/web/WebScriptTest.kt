package com.icecream.kwklasplus.core.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WebScriptTest {
    @Test
    fun callbackArgumentsAreJsonEncoded() {
        val script = LegacyWebScripts.call(
            LegacyWebCallback.RECEIVE_TOKEN,
            JavaScriptArgument.Text("a'b\"c\\d\n한글\u2028end"),
        )

        assertEquals(
            "window.receiveToken(\"a'b\\\"c\\\\d\\n한글\\u2028end\");",
            script.reveal(),
        )
        assertEquals("[WEB_SCRIPT_REDACTED]", script.toString())
    }

    @Test
    fun loginInjectionPreservesLegacyFunctionAndUsesEncodedValues() {
        val script = LegacyWebScripts.call(
            LegacyWebCallback.LOGIN_SET_INITIAL,
            JavaScriptArgument.Text("on"),
            JavaScriptArgument.Text("student'number"),
            JavaScriptArgument.Text("encrypted\"password"),
        )

        assertEquals(
            "appLogin.setInitial(\"on\",\"student'number\",\"encrypted\\\"password\");",
            script.reveal(),
        )
    }

    @Test
    fun localStorageKeyAndValueAreBothEncoded() {
        val script = LegacyWebScripts.setLocalStorage("key'", "value\n${'$'}{payload}")

        assertEquals(
            "window.localStorage.setItem(\"key'\",\"value\\n${'$'}{payload}\");",
            script.reveal(),
        )
    }

    @Test
    fun receivedDataSupportsLegacyArityAndRejectsInvalidCounts() {
        val accepted = LegacyWebScripts.call(
            LegacyWebCallback.RECEIVE_DATA,
            JavaScriptArgument.Text("session"),
            JavaScriptArgument.Text("subject"),
            JavaScriptArgument.Text("semester"),
            JavaScriptArgument.Text("path"),
        )

        assertTrue(accepted.reveal().startsWith("window.receivedData("))
        assertFailsWith<IllegalArgumentException> {
            LegacyWebScripts.call(
                LegacyWebCallback.RECEIVE_DATA,
                JavaScriptArgument.Text("session"),
            )
        }
    }

    @Test
    fun largeJsonPayloadRemainsOneEncodedStringArgument() {
        val payload = "[{\"title\":\"과제 ${"x".repeat(70_000)}\"}]"
        val script = LegacyWebScripts.call(
            LegacyWebCallback.RECEIVE_DEADLINE,
            JavaScriptArgument.Text(payload),
        )

        assertTrue(script.reveal().startsWith("window.receiveDeadlineData(\"[{") )
        assertTrue(script.reveal().endsWith("}]\");"))
    }

    @Test
    fun viewportNotificationRunsAfterTwoAnimationFrames() {
        val source = KlasWebAutomationScripts.notifyViewportChanged().reveal()

        assertTrue(source.contains("window.dispatchEvent(new Event('resize'))"))
        assertTrue(source.contains("window.visualViewport.dispatchEvent(new Event('resize'))"))
        assertTrue(
            source.contains(
                "window.requestAnimationFrame(function(){window.requestAnimationFrame(notify);",
            ),
        )
    }

}

package com.icecream.kwklasplus.core.web

import kotlin.test.*

class NativeKlasSessionScriptTest {
    @Test fun nativeApiCallbacksOmitSessionWhileOldWebKeepsItsContract() {
        val script = LegacyWebScripts.call(LegacyWebCallback.RECEIVE_DATA, JavaScriptArgument.Text("SECRET"), JavaScriptArgument.Text("subject"), JavaScriptArgument.Text("2026,2"))
        assertEquals("window.receivedData(null,\"subject\",\"2026,2\");", script.revealForNativeApi(true))
        assertTrue(script.reveal().contains("SECRET"))
        assertFalse(script.revealForNativeApi(true).contains("SECRET"))
        val storage = LegacyWebScripts.setLocalStorage("klasSessionToken", "SECRET")
        assertEquals("window.localStorage.removeItem('klasSessionToken');", storage.revealForNativeApi(true))
        assertFalse(storage.revealForNativeApi(true).contains("SECRET"))
    }
}

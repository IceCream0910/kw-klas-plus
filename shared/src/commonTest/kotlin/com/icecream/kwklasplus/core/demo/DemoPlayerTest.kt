package com.icecream.kwklasplus.core.demo

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DemoPlayerTest {
    @Test fun acceptsDeployedRedirectAndLegacyUrlOnlyOnTheTrustedHost() {
        for (path in listOf("/demo/native-player", "/demo/native-player.html")) {
            assertTrue(DemoPlayer.isPlayerUrl("https://klasplus.yuntae.in$path?start=24"))
        }
        for (url in listOf(
            "http://klasplus.yuntae.in/demo/native-player",
            "https://klasplus.yuntae.in:444/demo/native-player",
            "https://klasplus.yuntae.in.evil.test/demo/native-player",
            "https://evil.test/demo/native-player",
            "https://user@klasplus.yuntae.in/demo/native-player",
            "https://klasplus.yuntae.in/demo/native-player/other",
            "https://klasplus.yuntae.in/other",
        )) assertFalse(DemoPlayer.isPlayerUrl(url), url)
    }
}

package com.icecream.kwklasplus.core.demo

import com.icecream.kwklasplus.core.bridge.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class DemoBridgeTest {
    @AfterTest fun cleanup() { DemoSession.end() }
    private val context = BridgeContext(BridgeSurface.SETTINGS, "https://klasplus.yuntae.in", true, 0)
    private fun request(method: String, vararg args: BridgeValue) = BridgeRequest(1, "demo-test", method, args.toList())

    @Test fun demoLinksAndSettingsAreHandledByNativeWhileLectureOpensExistingPlayer() = runBlocking {
        DemoSession.begin("0000000000", "KlasplusDemo!123")
        val scripts = mutableListOf<String>()
        val forwarded = mutableListOf<String>()
        val handler = DemoBridgeCommandHandler(BridgeCommandHandler {
            forwarded.add(it.method.name)
            BridgeHandlerResult.Success()
        }, scripts::add)
        val router = BridgeRouter(handler)
        val lecture = context.copy(surface = BridgeSurface.LECTURE)
        router.route(request("evaluteKLASScript", BridgeValue.Text("var link='https://klas.kw.ac.kr';")), lecture)
        assertTrue(scripts.last().contains("unavailable"))
        router.route(request("openOnlineLecture"), lecture)
        assertEquals(listOf("openOnlineLecture"), forwarded)
        router.route(request("openLibraryQRSettingsModal"), context)
        assertTrue(scripts.last().contains("library"))
        router.route(request("setAppLockEnabled", BridgeValue.BooleanValue(true)), context)
        assertEquals("setAppLockEnabled", forwarded.last())
        assertFalse(DemoSession.appLockEnabled)
        Unit
    }

    @Test fun apiRequestsReturnCompleteDemoResponsesThroughBridgeChunksWithoutHttp() = runBlocking {
        DemoSession.begin("0000000000", "KlasplusDemo!123")
        val client = com.icecream.kwklasplus.core.network.createKlasHttpClient(io.ktor.client.engine.mock.MockEngine { error("HTTP must not run") })
        val api = com.icecream.kwklasplus.core.network.KlasApiCore(client, { error("real session must not be read") }, { error("real user agent must not be read") })
        val router = BridgeRouter(DemoBridgeCommandHandler(KlasApiBridgeHandler(api, BridgeCommandHandler { error("unexpected fallback") })))
        suspend fun read(endpoint: String, payload: String = "{}"): kotlinx.serialization.json.JsonElement {
            val id = "request-" + endpoint.hashCode().toString().replace('-', 'x') + payload.hashCode().toString().replace('-', 'x')
            val response = assertIs<BridgeResponse.Success>(router.route(request("requestKlasApi", BridgeValue.Text(id), BridgeValue.Text(endpoint), BridgeValue.Text(payload)), context))
            val metadata = assertIs<BridgeValue.ObjectValue>(response.result).value
            val count = assertIs<BridgeValue.NumberValue>(metadata.getValue("chunkCount")).value.toInt()
            val text = StringBuilder()
            for (index in 0 until count) {
                val chunk = assertIs<BridgeResponse.Success>(router.route(request("readKlasApiChunk", BridgeValue.Text(id), BridgeValue.Text(index.toString())), context))
                text.append(assertIs<BridgeValue.Text>(chunk.result).value)
            }
            return kotlinx.serialization.json.Json.parseToJsonElement(text.toString())
        }
        val home = read("/std/lis/evltn/LctrumHomeStdInfo.do") as kotlinx.serialization.json.JsonObject
        listOf("noticeList", "cntntList", "atendSubList", "taskTop", "examTop", "anQuizTop", "prjctTop", "dscsnTop").forEach {
            assertIs<kotlinx.serialization.json.JsonArray>(home.getValue(it))
        }
        assertIs<kotlinx.serialization.json.JsonArray>(read("/demo/PublicNotices.do"))
        val page = assertIs<kotlinx.serialization.json.JsonObject>(read("/demo/PublicNotices.do", "{\"pageInfo\":\"1\"}"))
        assertIs<kotlinx.serialization.json.JsonArray>(page.getValue("items"))
        val food = assertIs<kotlinx.serialization.json.JsonObject>(read("/demo/PublicCafeteria.do"))
        assertIs<kotlinx.serialization.json.JsonArray>(food.getValue("restaurants"))
        client.close()
    }

    @Test fun deviceSettingsUseExistingNativeHandlersAndStaleDemoCommandsAreRejected() = runBlocking {
        assertTrue(DemoSession.begin("0000000000", "KlasplusDemo!123"))
        val forwarded = mutableListOf<String>()
        val fallback = BridgeCommandHandler { forwarded.add(it.method.name); BridgeHandlerResult.Success() }
        val router = BridgeRouter(DemoBridgeCommandHandler(fallback))
        for (method in listOf("getAppLockSettings", "getDeadlineNotificationState")) {
            assertIs<BridgeResponse.Success>(router.route(request(method), context))
        }
        assertIs<BridgeResponse.Success>(router.route(request("setAppLockEnabled", BridgeValue.BooleanValue(true)), context))
        assertIs<BridgeResponse.Success>(router.route(request("setDeadlineNotificationsEnabled", BridgeValue.BooleanValue(true)), context))
        assertEquals(4, forwarded.size)
        assertIs<BridgeResponse.Failure>(router.route(request("getAppLockSettings"), context.copy(origin="https://evil.example")))
        DemoSession.end()
        assertIs<BridgeResponse.Failure>(router.route(request("getAppLockSettings"), context))
        DemoSession.begin("0000000000", "KlasplusDemo!123")
        assertIs<BridgeResponse.Failure>(router.route(request("getAppLockSettings"), context))
        Unit
    }
}

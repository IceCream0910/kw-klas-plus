package com.icecream.kwklasplus.core.demo

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.callContext
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.*
import io.ktor.http.content.TextContent
import io.ktor.utils.io.InternalAPI
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteReadChannel

@OptIn(InternalAPI::class)
class DemoHttpEngine(private val delegate: HttpClientEngine) : HttpClientEngineBase("demo-routing") {
    override val config get() = delegate.config
    override val dispatcher get() = delegate.dispatcher
    override val supportedCapabilities get() = delegate.supportedCapabilities

    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        if (!DemoSession.active) {
            check(data.headers[HttpHeaders.Cookie]?.contains("klasplus-demo-") != true) { "DEMO_SESSION_ENDED" }
            return delegate.execute(data)
        }
        val response = DemoFixtures.respond(data.url.encodedPath, (data.body as? TextContent)?.text.orEmpty())
        return HttpResponseData(
            HttpStatusCode.fromValue(response.status), GMTDate(),
            headersOf(HttpHeaders.ContentType, response.contentType), HttpProtocolVersion.HTTP_1_1,
            ByteReadChannel(response.body), callContext(),
        )
    }

    override fun close() {
        super.close()
        delegate.close()
    }
}

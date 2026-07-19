package com.icecream.kwklasplus.core.media

import com.icecream.kwklasplus.core.network.createKlasHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class MediaMetadataRepositoryTest {
    @Test
    fun fetchesTrustedKlasTitleAndDecodesHtml() = runBlocking {
        val client = createKlasHttpClient(
            MockEngine { respond("<html><title>강의 &amp; 실습</title></html>", HttpStatusCode.OK) },
        )

        assertEquals(
            MediaMetadataResult.Success("강의 & 실습"),
            MediaMetadataRepository(client).fetchTitle("https://vod.kw.ac.kr/video/index.html"),
        )
        client.close()
    }

    @Test
    fun rejectsExternalAndMissingTitles() = runBlocking {
        val client = createKlasHttpClient(MockEngine { respond("<html></html>", HttpStatusCode.OK) })
        val repository = MediaMetadataRepository(client)

        assertEquals(
            MediaMetadataResult.UntrustedUrl,
            repository.fetchTitle("https://example.com/video"),
        )
        assertEquals(
            MediaMetadataResult.InvalidResponse,
            repository.fetchTitle("https://klas.kw.ac.kr/video"),
        )
        client.close()
    }
}

package com.icecream.kwklasplus.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.cookies.CookiesStorage

fun createIosKlasHttpClient(
    timeoutMillis: Long = 15_000,
    cookieStorage: CookiesStorage? = null,
): HttpClient = createKlasHttpClient(
    engine = Darwin.create(),
    timeoutMillis = timeoutMillis,
    cookieStorage = cookieStorage,
)

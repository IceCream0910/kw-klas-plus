package com.icecream.kwklasplus.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

fun createIosKlasHttpClient(timeoutMillis: Long = 15_000): HttpClient =
    createKlasHttpClient(Darwin.create(), timeoutMillis)

package com.icecream.kwklasplus.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

fun createIosKlasHttpClient(): HttpClient = createKlasHttpClient(Darwin.create())

package com.icecream.kwklasplus

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
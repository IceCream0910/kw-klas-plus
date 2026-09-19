package com.icecream.kwklasplus.core.network

internal fun String.isKlasLoginHtml(): Boolean =
    contains("<!DOCTYPE html", ignoreCase = true) || contains("<html", ignoreCase = true)

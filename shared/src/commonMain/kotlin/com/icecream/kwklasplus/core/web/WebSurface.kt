package com.icecream.kwklasplus.core.web

import kotlinx.coroutines.flow.StateFlow

sealed interface WebLoadState {
    data object Idle : WebLoadState
    data class Loading(val url: String) : WebLoadState
    data class Ready(val url: String) : WebLoadState
    data class Failed(val url: String?, val category: WebFailureCategory) : WebLoadState
    data object Disposed : WebLoadState
}

enum class WebFailureCategory {
    NETWORK,
    TLS,
    HTTP,
    CANCELLED,
    UNKNOWN,
}

data class WebSurfaceSnapshot(
    val loadState: WebLoadState = WebLoadState.Idle,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
)

interface WebSurface {
    val snapshot: StateFlow<WebSurfaceSnapshot>

    fun load(url: String)
    fun reload()
    fun stopLoading()
    fun goBack(): Boolean
    fun goForward(): Boolean
    suspend fun evaluate(script: WebScript): String?
    fun dispose()
}

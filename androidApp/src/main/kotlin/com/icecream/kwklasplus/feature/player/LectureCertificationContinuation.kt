package com.icecream.kwklasplus.feature.player

internal class LectureCertificationContinuation<T> {
    private var pendingViewerScript: T? = null

    fun begin(viewerScript: T) {
        pendingViewerScript = viewerScript
    }

    fun clear() {
        pendingViewerScript = null
    }

    fun onAlert(message: String): T? {
        val normalized = message.filterNot(Char::isWhitespace).trimEnd('.', '!')
        if (!SUCCESS.matches(normalized)) return null
        return pendingViewerScript.also { clear() }
    }

    private companion object {
        val SUCCESS = Regex("(?:본인)?인증(?:이|가)?(?:정상적으로)?(?:완료되었습니다|되었습니다)")
    }
}

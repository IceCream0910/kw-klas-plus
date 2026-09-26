package com.icecream.kwklasplus.feature.auth

internal object LoginFunnelStatus {
    const val KEY = "login_funnel_status"
    const val AUTHENTICATING = "authenticating"
    const val SETUP = "setup"
    const val COMPLETE = "complete"

    fun blocksHome(status: String?): Boolean = status == AUTHENTICATING || status == SETUP

    fun initialStep(status: String?): LoginFunnelStep = when (status) {
        SETUP -> LoginFunnelStep.Library
        AUTHENTICATING -> LoginFunnelStep.Password
        else -> LoginFunnelStep.StudentId
    }

    fun shouldAdvanceToPassword(previousId: String, nextId: String, step: LoginFunnelStep): Boolean =
        step == LoginFunnelStep.StudentId &&
            previousId.length < LoginUiState.STUDENT_ID_LENGTH &&
            nextId.length == LoginUiState.STUDENT_ID_LENGTH
}

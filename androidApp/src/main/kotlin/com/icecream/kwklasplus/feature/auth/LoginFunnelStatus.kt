package com.icecream.kwklasplus.feature.auth

import android.content.SharedPreferences
import com.icecream.kwklasplus.core.legacy.LegacyPreferenceKeys

internal object LoginFunnelStatus {
    const val KEY = "login_funnel_status"
    const val NOT_STARTED = "not_started"
    const val AUTHENTICATING = "authenticating"
    const val SETUP = "setup"
    const val COMPLETE = "complete"

    fun blocksHome(status: String?): Boolean = status != COMPLETE

    fun migrateLegacyInstall(preferences: SharedPreferences): Boolean {
        if (preferences.contains(KEY)) return true
        val hasAccount = !preferences.getString(LegacyPreferenceKeys.KW_ID, null).isNullOrBlank()
        return preferences.edit().putString(KEY, if (hasAccount) COMPLETE else NOT_STARTED).commit()
    }

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

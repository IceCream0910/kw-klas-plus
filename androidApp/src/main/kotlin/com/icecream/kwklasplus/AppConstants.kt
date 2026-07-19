package com.icecream.kwklasplus

import com.icecream.kwklasplus.core.legacy.KlasUrls
import com.icecream.kwklasplus.core.legacy.LegacyPreferenceKeys

object AppPrefs {
    const val MAIN = LegacyPreferenceKeys.MAIN
    const val LIBRARY_QR_CACHE = LegacyPreferenceKeys.LIBRARY_QR_CACHE

    const val KW_ID = LegacyPreferenceKeys.KW_ID
    const val KW_PASSWORD = LegacyPreferenceKeys.KW_PASSWORD
    const val KW_SESSION = LegacyPreferenceKeys.KW_SESSION
    const val KW_SESSION_TIMESTAMP = LegacyPreferenceKeys.KW_SESSION_TIMESTAMP
    const val APP_THEME = LegacyPreferenceKeys.APP_THEME
    const val YEAR_HAKGI = LegacyPreferenceKeys.YEAR_HAKGI
    const val YEAR_HAKGI_LIST = LegacyPreferenceKeys.YEAR_HAKGI_LIST

    const val LIBRARY_STD_NUMBER = LegacyPreferenceKeys.LIBRARY_STD_NUMBER
    const val LIBRARY_PHONE = LegacyPreferenceKeys.LIBRARY_PHONE
    const val LIBRARY_PASSWORD = LegacyPreferenceKeys.LIBRARY_PASSWORD
}

object AppUrls {
    const val KLAS_BASE = KlasUrls.KLAS_BASE
    const val KLAS_PLUS_BASE = KlasUrls.KLAS_PLUS_BASE

    const val KLAS_LOGIN = KlasUrls.KLAS_LOGIN
    const val KLAS_PASSWORD_ENCRYPT = KlasUrls.KLAS_PASSWORD_ENCRYPT
    const val KLAS_FRAME = KlasUrls.KLAS_FRAME
    const val KLAS_LECTURE_HOME = KlasUrls.KLAS_LECTURE_HOME
    const val KLAS_ONLINE_CONTENTS = KlasUrls.KLAS_ONLINE_CONTENTS
    const val KLAS_QR_CHECKIN = KlasUrls.KLAS_QR_CHECKIN
    const val KLAS_ATTEND_SUBJECTS = KlasUrls.KLAS_ATTEND_SUBJECTS
    const val KLAS_ATTEND_LIST = KlasUrls.KLAS_ATTEND_LIST
    const val KLAS_RANDOM_KEY = KlasUrls.KLAS_RANDOM_KEY

    const val STATUS = KlasUrls.STATUS
    const val ONBOARDING = KlasUrls.ONBOARDING
    const val SETTINGS = KlasUrls.SETTINGS
    const val LECTURE_HOME = KlasUrls.LECTURE_HOME
    const val LECTURE_PLAN = KlasUrls.LECTURE_PLAN
    const val ONLINE_LECTURE = KlasUrls.ONLINE_LECTURE
}

package com.icecream.kwklasplus

object AppPrefs {
    const val MAIN = "com.icecream.kwklasplus"
    const val LIBRARY_QR_CACHE = "LibraryQRCache"

    const val KW_ID = "kwID"
    const val KW_PASSWORD = "kwPWD"
    const val KW_SESSION = "kwSESSION"
    const val KW_SESSION_TIMESTAMP = "kwSESSION_timestamp"
    const val APP_THEME = "appTheme"
    const val YEAR_HAKGI = "yearHakgi"
    const val YEAR_HAKGI_LIST = "yearHakgiList"

    const val LIBRARY_STD_NUMBER = "library_stdNumber"
    const val LIBRARY_PHONE = "library_phone"
    const val LIBRARY_PASSWORD = "library_password"
}

object AppUrls {
    const val KLAS_BASE = "https://klas.kw.ac.kr"
    const val KLAS_PLUS_BASE = "https://klasplus.yuntae.in"

    const val KLAS_LOGIN = "$KLAS_BASE/mst/cmn/login/LoginForm.do"
    const val KLAS_PASSWORD_ENCRYPT = "$KLAS_BASE/mst/cmn/login/SelectScrtyPwd.do"
    const val KLAS_FRAME = "$KLAS_BASE/std/cmn/frame/Frame.do"
    const val KLAS_LECTURE_HOME = "$KLAS_BASE/std/lis/evltn/LctrumHomeStdPage.do"
    const val KLAS_ONLINE_CONTENTS = "$KLAS_BASE/std/lis/evltn/OnlineCntntsStdPage.do"
    const val KLAS_QR_CHECKIN = "$KLAS_BASE/mst/ads/admst/KwAttendQRCodeInsert.do"
    const val KLAS_ATTEND_SUBJECTS = "$KLAS_BASE/std/ads/admst/KwAttendStdGwakmokList.do"
    const val KLAS_ATTEND_LIST = "$KLAS_BASE/mst/ads/admst/KwAttendStdAttendList.do"
    const val KLAS_RANDOM_KEY = "$KLAS_BASE/std/lis/evltn/CertiPushSucStd.do"

    const val STATUS = "https://status.klasplus.yuntae.in"
    const val ONBOARDING = "$KLAS_PLUS_BASE/onboarding"
    const val SETTINGS = "$KLAS_PLUS_BASE/settings"
    const val LECTURE_HOME = "$KLAS_PLUS_BASE/lectureHome"
    const val LECTURE_PLAN = "$KLAS_PLUS_BASE/lecturePlan"
    const val ONLINE_LECTURE = "$KLAS_PLUS_BASE/onlineLecture"
}
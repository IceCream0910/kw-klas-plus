package com.icecream.kwklasplus.core.legacy

object LegacyPreferenceKeys {
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

object LegacyIntentKeys {
    const val SESSION_ID = "sessionID"
    const val LEGACY_SESSION_ID = "sessionId"
    const val URL = "url"
    const val YEAR_HAKGI = "yearHakgi"
    const val SUBJECT_ID = "subjID"
    const val SUBJECT = "subj"
    const val SUBJECT_NAME = "subjName"
    const val BODY_JSON = "bodyJSON"
}

object KlasUrls {
    const val KLAS_BASE = "https://klas.kw.ac.kr"
    const val KLAS_PLUS_BASE = "https://klasplus.yuntae.in"
    const val KLAS_LOGIN = "$KLAS_BASE/mst/cmn/login/LoginForm.do"
    const val KLAS_PASSWORD_ENCRYPT = "$KLAS_BASE/mst/cmn/login/SelectScrtyPwd.do"
    const val KLAS_LOGIN_SECURITY = "$KLAS_BASE/mst/cmn/login/LoginSecurity.do"
    const val KLAS_LOGIN_CAPTCHA = "$KLAS_BASE/usr/cmn/login/LoginCaptcha.do"
    const val KLAS_LOGIN_CONFIRM = "$KLAS_BASE/mst/cmn/login/LoginConfirm.do"
    const val KLAS_SESSION_INFO = "$KLAS_BASE/api/v1/session/info"
    const val KLAS_SESSION_UPDATE = "$KLAS_BASE/usr/cmn/login/UpdateSession.do"
    const val KLAS_FRAME = "$KLAS_BASE/std/cmn/frame/Frame.do"
    const val KLAS_LECTURE_HOME = "$KLAS_BASE/std/lis/evltn/LctrumHomeStdPage.do"
    const val KLAS_ONLINE_CONTENTS = "$KLAS_BASE/std/lis/evltn/OnlineCntntsStdPage.do"
    const val KLAS_QR_CHECKIN = "$KLAS_BASE/mst/ads/admst/KwAttendQRCodeInsert.do"
    const val KLAS_ATTEND_SUBJECTS = "$KLAS_BASE/std/ads/admst/KwAttendStdGwakmokList.do"
    const val KLAS_ATTEND_LIST = "$KLAS_BASE/mst/ads/admst/KwAttendStdAttendList.do"
    const val KLAS_RANDOM_KEY = "$KLAS_BASE/std/lis/evltn/CertiPushSucStd.do"
    const val KLAS_ACADEMIC_TERM_SUBJECTS =
        "$KLAS_BASE/mst/cmn/frame/YearhakgiAtnlcSbjectList.do"
    const val KLAS_TIMETABLE = "$KLAS_BASE/std/cps/atnlc/TimetableStdList.do"
    const val KLAS_ONLINE_LECTURE_DEADLINES =
        "$KLAS_BASE/std/lis/evltn/SelectOnlineCntntsStdList.do"
    const val KLAS_TASK_DEADLINES = "$KLAS_BASE/std/lis/evltn/TaskStdList.do"
    const val KLAS_TEAM_TASK_DEADLINES = "$KLAS_BASE/std/lis/evltn/PrjctStdList.do"
    const val STATUS = "https://status.klasplus.yuntae.in"
    const val ONBOARDING = "$KLAS_PLUS_BASE/onboarding"
    const val SETTINGS = "$KLAS_PLUS_BASE/settings"
    const val LECTURE_HOME = "$KLAS_PLUS_BASE/lectureHome"
    const val LECTURE_PLAN = "$KLAS_PLUS_BASE/lecturePlan"
    const val ONLINE_LECTURE = "$KLAS_PLUS_BASE/onlineLecture"
}

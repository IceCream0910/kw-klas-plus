package com.icecream.kwklasplus.core.legacy

import kotlin.test.Test
import kotlin.test.assertEquals

class LegacyContractsTest {
    @Test
    fun preservesAuthenticationStorageAndIntentKeys() {
        assertEquals("kwPWD", LegacyPreferenceKeys.KW_PASSWORD)
        assertEquals("kwSESSION", LegacyPreferenceKeys.KW_SESSION)
        assertEquals("kwSESSION_timestamp", LegacyPreferenceKeys.KW_SESSION_TIMESTAMP)
        assertEquals("sessionID", LegacyIntentKeys.SESSION_ID)
        assertEquals("sessionId", LegacyIntentKeys.LEGACY_SESSION_ID)
    }

    @Test
    fun preservesLoginAndPasswordEncryptionUrls() {
        assertEquals("https://klas.kw.ac.kr/mst/cmn/login/LoginForm.do", KlasUrls.KLAS_LOGIN)
        assertEquals("https://klas.kw.ac.kr/mst/cmn/login/SelectScrtyPwd.do", KlasUrls.KLAS_PASSWORD_ENCRYPT)
        assertEquals("https://klas.kw.ac.kr/mst/cmn/login/LoginSecurity.do", KlasUrls.KLAS_LOGIN_SECURITY)
        assertEquals("https://klas.kw.ac.kr/usr/cmn/login/LoginCaptcha.do", KlasUrls.KLAS_LOGIN_CAPTCHA)
        assertEquals("https://klas.kw.ac.kr/mst/cmn/login/LoginConfirm.do", KlasUrls.KLAS_LOGIN_CONFIRM)
        assertEquals("https://klas.kw.ac.kr/api/v1/session/info", KlasUrls.KLAS_SESSION_INFO)
        assertEquals("https://klas.kw.ac.kr/usr/cmn/login/UpdateSession.do", KlasUrls.KLAS_SESSION_UPDATE)
    }
}

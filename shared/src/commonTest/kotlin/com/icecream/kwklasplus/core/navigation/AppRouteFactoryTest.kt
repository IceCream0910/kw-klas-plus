package com.icecream.kwklasplus.core.navigation

import com.icecream.kwklasplus.core.security.SecretValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AppRouteFactoryTest {
    private val factory = AppRouteFactory()
    private val session = SecretValue.of("session")

    @Test
    fun acceptsTypedWebAndLectureRoutes() {
        assertEquals(
            AppRouteResolution.Accepted(AppRoute.Web("https://klas.kw.ac.kr/page", session)),
            factory.web("https://klas.kw.ac.kr/page", session),
        )
        assertEquals(
            AppRouteResolution.Accepted(AppRoute.Lecture("id", "name", "2026,1", session)),
            factory.lecture("id", "name", "2026,1", session),
        )
        assertEquals(
            AppRouteResolution.Accepted(AppRoute.Task("/std/task", "id", "2026,1", session)),
            factory.task("/std/task", "id", "2026,1", session),
        )
    }

    @Test
    fun rejectsNonWebAndIncompleteRoutes() {
        assertIs<AppRouteResolution.Rejected>(factory.web("javascript:alert(1)", session))
        assertIs<AppRouteResolution.Rejected>(factory.video("", "2026,1", session))
        assertIs<AppRouteResolution.Rejected>(factory.task("/std/task\n", "id", "2026,1", session))
        assertIs<AppRouteResolution.Rejected>(
            factory.boardView("path", "board\n", "master", "subject", "2026,1", session),
        )
    }
}

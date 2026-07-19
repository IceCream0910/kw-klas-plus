package com.icecream.kwklasplus.core.navigation

import com.icecream.kwklasplus.core.platform.ExternalDestination
import com.icecream.kwklasplus.core.platform.ExternalNavigationPolicy
import com.icecream.kwklasplus.core.platform.ExternalNavigationResolution
import com.icecream.kwklasplus.core.platform.PlatformCapability
import com.icecream.kwklasplus.core.security.SecretValue

sealed interface AppRoute {
    data class Web(
        val url: String,
        val session: SecretValue?,
    ) : AppRoute

    data class Lecture(
        val subjectId: String,
        val subjectName: String,
        val yearSemester: String,
        val session: SecretValue,
    ) : AppRoute

    data class LecturePlan(
        val subjectId: String,
        val session: SecretValue,
    ) : AppRoute

    data class Video(
        val subjectId: String,
        val yearSemester: String,
        val session: SecretValue,
    ) : AppRoute

    data class Task(
        val path: String,
        val subjectId: String,
        val yearSemester: String,
        val session: SecretValue,
    ) : AppRoute

    sealed interface Board : AppRoute {
        val path: String
        val subjectId: String
        val yearSemester: String
        val session: SecretValue

        data class List(
            override val path: String,
            val title: String,
            override val subjectId: String,
            override val yearSemester: String,
            override val session: SecretValue,
        ) : Board

        data class View(
            override val path: String,
            val boardNumber: String,
            val masterNumber: String,
            override val subjectId: String,
            override val yearSemester: String,
            override val session: SecretValue,
        ) : Board
    }

    data class Overlay(val type: OverlayType) : AppRoute
    data class PlatformFeature(val capability: PlatformCapability) : AppRoute
}

enum class OverlayType {
    WEB_MODAL,
    LIBRARY_QR,
    LIBRARY_SETTINGS,
    YEAR_SEMESTER_PICKER,
    DATE_TIME_PICKER,
}

sealed interface AppRouteResolution {
    data class Accepted(val route: AppRoute) : AppRouteResolution
    data object Rejected : AppRouteResolution
}

class AppRouteFactory(
    private val webPolicy: ExternalNavigationPolicy = ExternalNavigationPolicy(),
) {
    fun web(url: String, session: SecretValue?): AppRouteResolution {
        val resolution = webPolicy.resolve(url)
        return if (resolution is ExternalNavigationResolution.Allowed &&
            resolution.destination is ExternalDestination.Web
        ) {
            AppRouteResolution.Accepted(AppRoute.Web(url, session))
        } else {
            AppRouteResolution.Rejected
        }
    }

    fun lecture(
        subjectId: String,
        subjectName: String,
        yearSemester: String,
        session: SecretValue,
    ) = required(subjectId, subjectName, yearSemester) {
        AppRoute.Lecture(subjectId, subjectName, yearSemester, session)
    }

    fun lecturePlan(subjectId: String, session: SecretValue) = required(subjectId) {
        AppRoute.LecturePlan(subjectId, session)
    }

    fun video(subjectId: String, yearSemester: String, session: SecretValue) =
        required(subjectId, yearSemester) { AppRoute.Video(subjectId, yearSemester, session) }

    fun task(path: String, subjectId: String, yearSemester: String, session: SecretValue) =
        required(path, subjectId, yearSemester) {
            AppRoute.Task(path, subjectId, yearSemester, session)
        }

    fun boardList(
        path: String,
        title: String,
        subjectId: String,
        yearSemester: String,
        session: SecretValue,
    ) = required(path, title, subjectId, yearSemester) {
        AppRoute.Board.List(path, title, subjectId, yearSemester, session)
    }

    fun boardView(
        path: String,
        boardNumber: String,
        masterNumber: String,
        subjectId: String,
        yearSemester: String,
        session: SecretValue,
    ) = required(path, boardNumber, masterNumber, subjectId, yearSemester) {
        AppRoute.Board.View(path, boardNumber, masterNumber, subjectId, yearSemester, session)
    }

    private inline fun required(vararg values: String, create: () -> AppRoute): AppRouteResolution =
        if (values.all { it.isNotBlank() && !it.any(Char::isISOControl) }) {
            AppRouteResolution.Accepted(create())
        } else {
            AppRouteResolution.Rejected
        }
}

package com.icecream.kwklasplus.platform.navigation

import android.app.Activity
import com.icecream.kwklasplus.appDependencies
import com.icecream.kwklasplus.core.navigation.AppRouteFactory
import com.icecream.kwklasplus.core.platform.PlatformActionResult
import com.icecream.kwklasplus.core.security.SecretValue

private val routeFactory = AppRouteFactory()

fun Activity.openWebRoute(url: String, session: String?): PlatformActionResult {
    if (com.icecream.kwklasplus.core.demo.DemoSession.active && com.icecream.kwklasplus.core.demo.DemoWebScripts.isSchoolUrl(url)) {
        android.widget.Toast.makeText(this, "데모 모드에서는 학교 페이지를 사용할 수 없어요.", android.widget.Toast.LENGTH_LONG).show()
        return PlatformActionResult.Failed("demo_school_page_unavailable")
    }
    return appDependencies.routeNavigator(this).open(
        routeFactory.web(url, session?.takeIf(String::isNotBlank)?.let(SecretValue::of)),
    )
}

fun Activity.openLectureRoute(
    subjectId: String,
    subjectName: String,
    yearSemester: String,
    session: String,
) = appDependencies.routeNavigator(this).open(
    routeFactory.lecture(subjectId, subjectName, yearSemester, SecretValue.of(session)),
)

fun Activity.openLecturePlanRoute(subjectId: String, session: String) =
    appDependencies.routeNavigator(this).open(
        routeFactory.lecturePlan(subjectId, SecretValue.of(session)),
    )

fun Activity.openVideoRoute(
    subjectId: String?,
    yearSemester: String?,
    session: String?,
): PlatformActionResult {
    if (subjectId.isNullOrBlank() || yearSemester.isNullOrBlank() || session.isNullOrBlank()) {
        return PlatformActionResult.Failed("invalid_app_route")
    }
    return appDependencies.routeNavigator(this).open(
        routeFactory.video(subjectId, yearSemester, SecretValue.of(session)),
    )
}

fun Activity.openTaskRoute(
    path: String,
    subjectId: String,
    yearSemester: String,
    session: String,
) = appDependencies.routeNavigator(this).open(
    routeFactory.task(path, subjectId, yearSemester, SecretValue.of(session)),
)

fun Activity.openBoardListRoute(
    path: String,
    title: String,
    subjectId: String,
    yearSemester: String,
    session: String,
) = appDependencies.routeNavigator(this).open(
    routeFactory.boardList(path, title, subjectId, yearSemester, SecretValue.of(session)),
)

fun Activity.openBoardViewRoute(
    path: String,
    boardNumber: String,
    masterNumber: String,
    subjectId: String,
    yearSemester: String,
    session: String,
) = appDependencies.routeNavigator(this).open(
    routeFactory.boardView(
        path,
        boardNumber,
        masterNumber,
        subjectId,
        yearSemester,
        SecretValue.of(session),
    ),
)

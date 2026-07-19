package com.icecream.kwklasplus.platform.navigation

import android.app.Activity
import android.content.Intent
import com.icecream.kwklasplus.BoardActivity
import com.icecream.kwklasplus.IntentExtras
import com.icecream.kwklasplus.LctPlanActivity
import com.icecream.kwklasplus.LectureActivity
import com.icecream.kwklasplus.LinkViewActivity
import com.icecream.kwklasplus.TaskViewActivity
import com.icecream.kwklasplus.VideoPlayerActivity
import com.icecream.kwklasplus.core.navigation.AppRoute
import com.icecream.kwklasplus.core.navigation.AppRouteResolution
import com.icecream.kwklasplus.core.platform.PlatformActionResult

class AndroidRouteNavigator(
    private val activity: Activity,
) {
    fun open(resolution: AppRouteResolution): PlatformActionResult = when (resolution) {
        is AppRouteResolution.Accepted -> open(resolution.route)
        AppRouteResolution.Rejected -> PlatformActionResult.Failed("invalid_app_route")
    }

    fun open(route: AppRoute): PlatformActionResult {
        val intent = when (route) {
            is AppRoute.Web -> Intent(activity, LinkViewActivity::class.java).apply {
                putExtra("url", route.url)
                putExtra(IntentExtras.SESSION_ID, route.session?.reveal().orEmpty())
            }
            is AppRoute.Lecture -> Intent(activity, LectureActivity::class.java).apply {
                putExtra(IntentExtras.SUBJECT_ID, route.subjectId)
                putExtra(IntentExtras.SUBJECT_NAME, route.subjectName)
                putExtra(IntentExtras.YEAR_HAKGI, route.yearSemester)
                putExtra(IntentExtras.SESSION_ID, route.session.reveal())
            }
            is AppRoute.LecturePlan -> Intent(activity, LctPlanActivity::class.java).apply {
                putExtra(IntentExtras.SUBJECT_ID, route.subjectId)
                putExtra(IntentExtras.LEGACY_SESSION_ID, route.session.reveal())
                putExtra(IntentExtras.SESSION_ID, route.session.reveal())
            }
            is AppRoute.Video -> Intent(activity, VideoPlayerActivity::class.java).apply {
                putExtra(IntentExtras.SUBJECT, route.subjectId)
                putExtra(IntentExtras.YEAR_HAKGI, route.yearSemester)
                putExtra(IntentExtras.SESSION_ID, route.session.reveal())
            }
            is AppRoute.Task -> Intent(activity, TaskViewActivity::class.java).apply {
                putExtra("url", route.path)
                putExtra(IntentExtras.SUBJECT, route.subjectId)
                putExtra(IntentExtras.YEAR_HAKGI, route.yearSemester)
                putExtra(IntentExtras.SESSION_ID, route.session.reveal())
            }
            is AppRoute.Board.List -> boardIntent(route).apply {
                putExtra("type", "list")
                putExtra("title", route.title)
            }
            is AppRoute.Board.View -> boardIntent(route).apply {
                putExtra("type", "view")
                putExtra("title", "")
                putExtra("boardNo", route.boardNumber)
                putExtra("masterNo", route.masterNumber)
            }
            is AppRoute.Overlay,
            is AppRoute.PlatformFeature,
            -> return PlatformActionResult.Unsupported
        }
        return runCatching { activity.startActivity(intent) }
            .fold(
                onSuccess = { PlatformActionResult.Success },
                onFailure = { PlatformActionResult.Failed("app_route_launch_failed") },
            )
    }

    private fun boardIntent(route: AppRoute.Board) = Intent(activity, BoardActivity::class.java).apply {
        putExtra("path", route.path)
        putExtra(IntentExtras.SUBJECT_ID, route.subjectId)
        putExtra(IntentExtras.YEAR_HAKGI, route.yearSemester)
        putExtra(IntentExtras.SESSION_ID, route.session.reveal())
    }
}

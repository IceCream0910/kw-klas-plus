package com.icecream.kwklasplus.core.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

class AndroidExternalNavigator(
    private val context: Context,
) : ExternalNavigator {
    override suspend fun open(destination: ExternalDestination): PlatformActionResult = openNow(destination)

    fun openNow(destination: ExternalDestination): PlatformActionResult {
        val uri = when (destination) {
            is ExternalDestination.Web -> Uri.parse(destination.url)
            is ExternalDestination.Email -> Uri.fromParts("mailto", destination.address, null)
            is ExternalDestination.Telephone -> Uri.fromParts("tel", destination.number, null)
            is ExternalDestination.PlatformUri -> return PlatformActionResult.Unsupported
        }
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            PlatformActionResult.Success
        } catch (_: ActivityNotFoundException) {
            PlatformActionResult.Unsupported
        } catch (_: SecurityException) {
            PlatformActionResult.Failed("external_navigation_denied")
        }
    }
}

fun Context.openValidatedExternalDestination(
    rawValue: String,
    policy: ExternalNavigationPolicy = ExternalNavigationPolicy(),
): PlatformActionResult {
    if (com.icecream.kwklasplus.core.demo.DemoSession.active && com.icecream.kwklasplus.core.demo.DemoWebScripts.isSchoolUrl(rawValue)) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.widget.Toast.makeText(this, "데모 모드에서는 학교 페이지를 사용할 수 없어요.", android.widget.Toast.LENGTH_LONG).show()
        }
        return PlatformActionResult.Failed("demo_school_page_unavailable")
    }
    return when (val resolution = policy.resolve(rawValue)) {
    is ExternalNavigationResolution.Allowed -> AndroidExternalNavigator(this).openNow(resolution.destination)
    ExternalNavigationResolution.Rejected -> PlatformActionResult.Failed("invalid_external_destination")
}
}

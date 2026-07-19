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
): PlatformActionResult = when (val resolution = policy.resolve(rawValue)) {
    is ExternalNavigationResolution.Allowed -> AndroidExternalNavigator(this).openNow(resolution.destination)
    ExternalNavigationResolution.Rejected -> PlatformActionResult.Failed("invalid_external_destination")
}

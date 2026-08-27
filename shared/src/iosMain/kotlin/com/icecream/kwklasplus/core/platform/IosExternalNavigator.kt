package com.icecream.kwklasplus.core.platform

import platform.Foundation.NSURL
import platform.UIKit.UIApplication

fun interface IosUrlOpener {
    fun open(url: NSURL): Boolean
}

class IosExternalNavigator(
    private val opener: IosUrlOpener,
) : ExternalNavigator {
    override suspend fun open(destination: ExternalDestination): PlatformActionResult = openNow(destination)

    fun openNow(destination: ExternalDestination): PlatformActionResult {
        val raw = when (destination) {
            is ExternalDestination.Web -> destination.url
            is ExternalDestination.Email -> "mailto:${destination.address}"
            is ExternalDestination.Telephone -> "tel:${destination.number}"
            is ExternalDestination.PlatformUri -> return PlatformActionResult.Unsupported
        }
        val url = NSURL.URLWithString(raw) ?: return PlatformActionResult.Failed("invalid_external_destination")
        return if (opener.open(url)) {
            PlatformActionResult.Success
        } else {
            PlatformActionResult.Failed("external_navigation_denied")
        }
    }

    fun openValidated(rawValue: String): PlatformActionResult =
        when (val resolution = ExternalNavigationPolicy().resolve(rawValue)) {
            is ExternalNavigationResolution.Allowed -> openNow(resolution.destination)
            ExternalNavigationResolution.Rejected -> PlatformActionResult.Failed("invalid_external_destination")
        }

    companion object {
        fun system(): IosExternalNavigator = IosExternalNavigator { url ->
            UIApplication.sharedApplication.openURL(
                url,
                options = emptyMap<Any?, Any>(),
                completionHandler = null,
            )
            true
        }
    }
}

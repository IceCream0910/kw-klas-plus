package com.icecream.kwklasplus.core.platform

sealed interface ExternalNavigationResolution {
    data class Allowed(val destination: ExternalDestination) : ExternalNavigationResolution
    data object Rejected : ExternalNavigationResolution
}

class ExternalNavigationPolicy(
    private val maximumLength: Int = 2_048,
) {
    fun resolve(rawValue: String): ExternalNavigationResolution {
        if (rawValue.isEmpty() || rawValue.length > maximumLength) {
            return ExternalNavigationResolution.Rejected
        }
        if (rawValue != rawValue.trim() || rawValue.any { it.isISOControl() }) {
            return ExternalNavigationResolution.Rejected
        }

        val separator = rawValue.indexOf(':')
        if (separator <= 0) return ExternalNavigationResolution.Rejected
        val scheme = rawValue.substring(0, separator).lowercase()
        val value = rawValue.substring(separator + 1)

        return when (scheme) {
            "http", "https" -> resolveWeb(rawValue, value)
            "mailto" -> resolveEmail(value)
            "tel" -> resolveTelephone(value)
            else -> ExternalNavigationResolution.Rejected
        }
    }

    private fun resolveWeb(
        rawValue: String,
        schemeSpecificPart: String,
    ): ExternalNavigationResolution {
        if (!schemeSpecificPart.startsWith("//")) return ExternalNavigationResolution.Rejected
        val authority = schemeSpecificPart.drop(2).substringBefore('/').substringBefore('?').substringBefore('#')
        if (authority.isEmpty() || authority.contains('@')) return ExternalNavigationResolution.Rejected
        return ExternalNavigationResolution.Allowed(ExternalDestination.Web(rawValue))
    }

    private fun resolveEmail(address: String): ExternalNavigationResolution {
        if (address.isEmpty() || address.contains('?') || address.count { it == '@' } != 1) {
            return ExternalNavigationResolution.Rejected
        }
        return ExternalNavigationResolution.Allowed(ExternalDestination.Email(address))
    }

    private fun resolveTelephone(number: String): ExternalNavigationResolution {
        val allowedCharacters = "+0123456789-(). "
        if (number.isEmpty() || number.any { it !in allowedCharacters }) {
            return ExternalNavigationResolution.Rejected
        }
        return ExternalNavigationResolution.Allowed(ExternalDestination.Telephone(number))
    }
}

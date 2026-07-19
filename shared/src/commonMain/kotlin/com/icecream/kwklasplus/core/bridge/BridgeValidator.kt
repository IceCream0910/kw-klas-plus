package com.icecream.kwklasplus.core.bridge

class TrustedOriginPolicy(
    private val trustedOrigins: Set<String> = DEFAULT_TRUSTED_ORIGINS,
) {
    fun isTrusted(origin: String): Boolean = origin in trustedOrigins

    fun isTrustedUrl(url: String): Boolean {
        if (url.isBlank() || url != url.trim() || url.any(Char::isISOControl)) return false
        val schemeSeparator = url.indexOf("://")
        if (schemeSeparator <= 0) return false
        val scheme = url.substring(0, schemeSeparator).lowercase()
        if (scheme != "https") return false
        val authority = url.substring(schemeSeparator + 3)
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .lowercase()
        if (authority.isBlank() || '@' in authority) return false
        return isTrusted("$scheme://$authority")
    }

    companion object {
        val DEFAULT_TRUSTED_ORIGINS = setOf(
            "https://klas.kw.ac.kr",
            "https://klasplus.yuntae.in",
        )
    }
}

class KlasContentOriginPolicy {
    fun isTrustedUrl(url: String): Boolean {
        if (url.isBlank() || url != url.trim() || url.any(Char::isISOControl)) return false
        val schemeSeparator = url.indexOf("://")
        if (schemeSeparator <= 0 || url.substring(0, schemeSeparator).lowercase() != "https") {
            return false
        }
        val authority = url.substring(schemeSeparator + 3)
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .lowercase()
        if (authority.isBlank() || '@' in authority || ':' in authority) return false
        return authority == ROOT_HOST || authority.endsWith(".$ROOT_HOST")
    }

    private companion object {
        const val ROOT_HOST = "kw.ac.kr"
    }
}

class BridgeValidator(
    private val originPolicy: TrustedOriginPolicy = TrustedOriginPolicy(),
    private val maximumPayloadSizeBytes: Int = 64 * 1_024,
) {
    fun validate(request: BridgeRequest, context: BridgeContext): BridgeValidationResult {
        if (request.version != CURRENT_VERSION) return rejected(BridgeRejection.UNSUPPORTED_VERSION)
        if (request.id.isBlank() || request.id.length > MAXIMUM_REQUEST_ID_LENGTH) {
            return rejected(BridgeRejection.INVALID_REQUEST_ID)
        }
        if (!originPolicy.isTrusted(context.origin)) return rejected(BridgeRejection.UNTRUSTED_ORIGIN)
        if (!context.isMainFrame) return rejected(BridgeRejection.NOT_MAIN_FRAME)
        if (context.payloadSizeBytes !in 0..maximumPayloadSizeBytes) {
            return rejected(BridgeRejection.PAYLOAD_TOO_LARGE)
        }

        val method = LegacyBridgeCatalog.find(context.surface, request.method)
            ?: return rejected(BridgeRejection.UNKNOWN_METHOD)
        if (request.arguments.size !in method.minimumArgumentCount..method.arguments.size) {
            return rejected(BridgeRejection.INVALID_ARGUMENT_COUNT)
        }
        if (!request.arguments.indices.all { matches(request.arguments[it], method.arguments[it]) }) {
            return rejected(BridgeRejection.INVALID_ARGUMENT_TYPE)
        }
        return BridgeValidationResult.Accepted(method)
    }

    private fun matches(value: BridgeValue, type: BridgeArgumentType): Boolean = when (type) {
        BridgeArgumentType.STRING -> value is BridgeValue.Text
        BridgeArgumentType.NULLABLE_STRING -> value is BridgeValue.Text || value is BridgeValue.Null
        BridgeArgumentType.BOOLEAN -> value is BridgeValue.BooleanValue
    }

    private fun rejected(reason: BridgeRejection) = BridgeValidationResult.Rejected(reason)

    companion object {
        const val CURRENT_VERSION = 1
        const val MAXIMUM_REQUEST_ID_LENGTH = 128
    }
}

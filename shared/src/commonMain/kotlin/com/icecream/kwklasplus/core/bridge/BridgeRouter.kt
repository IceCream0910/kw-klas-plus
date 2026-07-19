package com.icecream.kwklasplus.core.bridge

import kotlinx.coroutines.CancellationException

data class ValidatedBridgeCommand(
    val requestId: String,
    val surface: BridgeSurface,
    val methodId: BridgeMethodId,
    val method: LegacyBridgeMethod,
    val arguments: List<BridgeValue>,
)

sealed interface BridgeHandlerResult {
    data class Success(val value: BridgeValue = BridgeValue.Null) : BridgeHandlerResult
    data class Failure(val code: BridgeErrorCode) : BridgeHandlerResult
}

fun interface BridgeCommandHandler {
    suspend fun handle(command: ValidatedBridgeCommand): BridgeHandlerResult
}

fun interface SynchronousBridgeCommandHandler {
    fun handle(command: ValidatedBridgeCommand): BridgeHandlerResult
}

enum class BridgeErrorCode {
    UNSUPPORTED_VERSION,
    INVALID_REQUEST_ID,
    UNTRUSTED_ORIGIN,
    NOT_MAIN_FRAME,
    PAYLOAD_TOO_LARGE,
    UNKNOWN_METHOD,
    INVALID_ARGUMENT_COUNT,
    INVALID_ARGUMENT_TYPE,
    MALFORMED_REQUEST,
    ASYNC_METHOD_REQUIRED,
    HANDLER_FAILURE,
}

sealed interface BridgeResponse {
    val version: Int
    val id: String?

    data class Success(
        override val version: Int,
        override val id: String,
        val result: BridgeValue,
    ) : BridgeResponse

    data class Failure(
        override val version: Int,
        override val id: String?,
        val error: BridgeErrorCode,
    ) : BridgeResponse
}

class BridgeRouter(
    private val handler: BridgeCommandHandler,
    private val synchronousHandler: SynchronousBridgeCommandHandler? = null,
    private val validator: BridgeValidator = BridgeValidator(),
) {
    suspend fun route(request: BridgeRequest, context: BridgeContext): BridgeResponse {
        val command = when (val validation = validator.validate(request, context)) {
            is BridgeValidationResult.Accepted -> validation.toCommand(request, context)
            is BridgeValidationResult.Rejected -> return validation.toResponse(request)
        }
        return try {
            handler.handle(command).toResponse(request)
        } catch (cause: CancellationException) {
            throw cause
        } catch (_: Throwable) {
            failure(request, BridgeErrorCode.HANDLER_FAILURE)
        }
    }

    fun routeSynchronously(request: BridgeRequest, context: BridgeContext): BridgeResponse {
        val command = when (val validation = validator.validate(request, context)) {
            is BridgeValidationResult.Accepted -> validation.toCommand(request, context)
            is BridgeValidationResult.Rejected -> return validation.toResponse(request)
        }
        val syncHandler = synchronousHandler
        if (!command.method.synchronousReturn || syncHandler == null) {
            return failure(request, BridgeErrorCode.ASYNC_METHOD_REQUIRED)
        }
        return try {
            syncHandler.handle(command).toResponse(request)
        } catch (_: Throwable) {
            failure(request, BridgeErrorCode.HANDLER_FAILURE)
        }
    }

    private fun BridgeValidationResult.Accepted.toCommand(
        request: BridgeRequest,
        context: BridgeContext,
    ) = ValidatedBridgeCommand(
        request.id,
        context.surface,
        requireNotNull(BridgeMethodId.from(context.surface, method.name)),
        method,
        request.arguments,
    )

    private fun BridgeValidationResult.Rejected.toResponse(request: BridgeRequest) =
        failure(request, reason.toErrorCode())

    private fun BridgeHandlerResult.toResponse(request: BridgeRequest): BridgeResponse = when (this) {
        is BridgeHandlerResult.Success -> BridgeResponse.Success(request.version, request.id, value)
        is BridgeHandlerResult.Failure -> failure(request, code)
    }

    private fun failure(request: BridgeRequest, code: BridgeErrorCode) = BridgeResponse.Failure(
        BridgeValidator.CURRENT_VERSION,
        request.id.takeIf { it.isNotBlank() && it.length <= BridgeValidator.MAXIMUM_REQUEST_ID_LENGTH },
        code,
    )

    private fun BridgeRejection.toErrorCode(): BridgeErrorCode = when (this) {
        BridgeRejection.UNSUPPORTED_VERSION -> BridgeErrorCode.UNSUPPORTED_VERSION
        BridgeRejection.INVALID_REQUEST_ID -> BridgeErrorCode.INVALID_REQUEST_ID
        BridgeRejection.UNTRUSTED_ORIGIN -> BridgeErrorCode.UNTRUSTED_ORIGIN
        BridgeRejection.NOT_MAIN_FRAME -> BridgeErrorCode.NOT_MAIN_FRAME
        BridgeRejection.PAYLOAD_TOO_LARGE -> BridgeErrorCode.PAYLOAD_TOO_LARGE
        BridgeRejection.UNKNOWN_METHOD -> BridgeErrorCode.UNKNOWN_METHOD
        BridgeRejection.INVALID_ARGUMENT_COUNT -> BridgeErrorCode.INVALID_ARGUMENT_COUNT
        BridgeRejection.INVALID_ARGUMENT_TYPE -> BridgeErrorCode.INVALID_ARGUMENT_TYPE
    }
}

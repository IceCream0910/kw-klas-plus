package com.icecream.kwklasplus.core.bridge

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay

class AcceptingBridgeCommandHandler(
    private val delayMillis: Long,
    private val result: BridgeHandlerResult,
) : BridgeCommandHandler {
    constructor() : this(0, BridgeHandlerResult.Success())

    constructor(delayMillis: Long) : this(delayMillis, BridgeHandlerResult.Success())

    override suspend fun handle(command: ValidatedBridgeCommand): BridgeHandlerResult {
        if (delayMillis > 0) delay(delayMillis)
        return result
    }
}

class HangingBridgeCommandHandler : BridgeCommandHandler {
    override suspend fun handle(command: ValidatedBridgeCommand): BridgeHandlerResult {
        CompletableDeferred<Nothing>().await()
    }
}

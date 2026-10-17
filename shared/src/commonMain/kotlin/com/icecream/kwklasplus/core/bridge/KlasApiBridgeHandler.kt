package com.icecream.kwklasplus.core.bridge

import com.icecream.kwklasplus.core.network.*
import kotlinx.coroutines.*
import com.icecream.kwklasplus.core.security.SecretValue
import kotlin.time.TimeSource
import kotlin.time.Duration.Companion.seconds

class KlasApiBridgeHandler(
    private val api: KlasApiCore,
    private val fallback: BridgeCommandHandler,
    private val markNow: () -> kotlin.time.TimeMark = { TimeSource.Monotonic.markNow() },
) : ResettableBridgeCommandHandler {
    private val jobs = mutableMapOf<String, Job>()
    private data class BufferedResponse(val chunks: List<String>, val session: SecretValue, val context: String?, val created: kotlin.time.TimeMark)
    private val mutations = mutableMapOf<String, kotlin.time.TimeMark>()
    private val responses = mutableMapOf<String, BufferedResponse>()

    override fun reset() {
        val active = jobs.values.toList()
        jobs.clear()
        responses.clear()
        mutations.clear()
        active.forEach { it.cancel() }
    }

    override suspend fun handle(command: ValidatedBridgeCommand): BridgeHandlerResult {
        if (command.method.name !in KlasApiContract.methods) return fallback.handle(command)
        responses.entries.removeAll { it.value.created.elapsedNow() > 30.seconds }
        mutations.entries.removeAll { it.value.elapsedNow() > 120.seconds }
        val args = command.arguments.map { (it as BridgeValue.Text).value }
        val id = args[0]
        if (!id.matches(Regex("[A-Za-z0-9_-]{1,128}"))) return failure("INVALID_REQUEST_ID")
        when (command.method.name) {
            "cancelKlasApi" -> {
                jobs.remove(id)?.cancel()
                responses.remove(id)
                return BridgeHandlerResult.Success()
            }
            "readKlasApiChunk" -> {
                val response = responses[id] ?: return failure("RESPONSE_EXPIRED")
                if (api.currentSession() != response.session) {
                    responses.remove(id)
                    return failure("STALE_SESSION")
                }
                if (api.currentContext() != response.context) {
                    responses.remove(id)
                    return failure("STALE_CONTEXT")
                }
                val chunks = response.chunks
                val index = args[1].toIntOrNull() ?: return failure("INVALID_CHUNK")
                val chunk = chunks.getOrNull(index) ?: return failure("INVALID_CHUNK")
                if (index == chunks.lastIndex) responses.remove(id)
                return BridgeHandlerResult.Success(BridgeValue.Text(chunk))
            }
        }
        if (id in mutations) return failure("DUPLICATE_REQUEST")
        if (jobs.size + responses.size >= 16 || id in jobs || id in responses) return failure("BUSY")
        if (KlasApiContract.isMutation(args[1])) {
            if (mutations.size >= 128) return failure("BUSY")
            mutations[id] = markNow()
        }
        jobs[id] = currentCoroutineContext().job
        try {
            return when (val result = api.request(args[1], args[2])) {
                is KlasApiResult.Failure -> failure(result.code, result.status)
                is KlasApiResult.Success -> {
                    // JSON 에스케이프와 UTF-8 확장 뒤에도 메시지 크기를 제한한다.
                    if (responses.values.sumOf { buffered -> buffered.chunks.sumOf { it.length.toLong() * 2 } } + result.body.length.toLong() * 2 > 16 * 1024 * 1024) return failure("BUSY")
                    val chunks = mutableListOf<String>()
                    var start = 0
                    while (start < result.body.length) {
                        var end = minOf(start + 6 * 1024, result.body.length)
                        if (end < result.body.length && result.body[end - 1].isHighSurrogate() && result.body[end].isLowSurrogate()) end--
                        chunks.add(result.body.substring(start, end))
                        start = end
                    }
                    responses[id] = BufferedResponse(chunks, result.session, result.context, markNow())
                    BridgeHandlerResult.Success(BridgeValue.ObjectValue(mapOf(
                        "responseId" to BridgeValue.Text(id),
                        "chunkCount" to BridgeValue.NumberValue(chunks.size.toDouble()),
                    )))
                }
            }
        } finally { jobs.remove(id) }
    }

    private fun failure(code: String, status: Int? = null) = BridgeHandlerResult.Success(
        BridgeValue.ObjectValue(mapOf(
            "error" to BridgeValue.Text(code),
            "status" to (status?.let { BridgeValue.NumberValue(it.toDouble()) } ?: BridgeValue.Null),
        )),
    )
}

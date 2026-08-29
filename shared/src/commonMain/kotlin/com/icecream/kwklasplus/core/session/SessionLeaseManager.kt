package com.icecream.kwklasplus.core.session

import com.icecream.kwklasplus.core.network.KlasUserAgent
import com.icecream.kwklasplus.core.security.SecretValue
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SessionLeaseInfo(
    val logoutCountDownSeconds: Long,
    val sessionNotificationSeconds: Long,
    val remainingSeconds: Long,
) {
    init {
        require(logoutCountDownSeconds >= 0)
        require(sessionNotificationSeconds >= 0)
        require(remainingSeconds >= 0)
    }
}

sealed interface SessionInfoResult {
    data class Success(val info: SessionLeaseInfo) : SessionInfoResult
    data object SessionExpired : SessionInfoResult
    data class HttpFailure(val statusCode: Int) : SessionInfoResult
    data object MalformedResponse : SessionInfoResult
    data object Timeout : SessionInfoResult
    data object NetworkFailure : SessionInfoResult
}

sealed interface SessionExtensionResult {
    data object Success : SessionExtensionResult
    data object SessionExpired : SessionExtensionResult
    data class HttpFailure(val statusCode: Int) : SessionExtensionResult
    data object MalformedResponse : SessionExtensionResult
    data object Timeout : SessionExtensionResult
    data object NetworkFailure : SessionExtensionResult
}

interface SessionLeaseGateway {
    suspend fun fetchInfo(session: SecretValue, userAgent: KlasUserAgent): SessionInfoResult
    suspend fun extend(session: SecretValue, userAgent: KlasUserAgent): SessionExtensionResult
}

fun interface SessionLeaseMaintainer {
    suspend fun maintain(): SessionLeaseResult
}

sealed interface SessionLeaseFailure {
    data object Storage : SessionLeaseFailure
    data object Timeout : SessionLeaseFailure
    data object Network : SessionLeaseFailure
    data object MalformedResponse : SessionLeaseFailure
    data object ExtensionNotConfirmed : SessionLeaseFailure
    data class Http(val statusCode: Int) : SessionLeaseFailure
}

sealed interface SessionLeaseResult {
    data class Active(
        val info: SessionLeaseInfo,
        val nextCheckAfterMillis: Long,
        val extended: Boolean,
    ) : SessionLeaseResult

    data object Missing : SessionLeaseResult
    data object Expired : SessionLeaseResult
    data class Retry(
        val failure: SessionLeaseFailure,
        val retryAfterMillis: Long,
    ) : SessionLeaseResult
}

class SessionLeasePolicy(
    private val minimumExtensionWindowSeconds: Long = DEFAULT_EXTENSION_WINDOW_SECONDS,
    private val countdownSafetySeconds: Long = DEFAULT_COUNTDOWN_SAFETY_SECONDS,
    private val minimumCheckIntervalMillis: Long = DEFAULT_MINIMUM_CHECK_INTERVAL_MILLIS,
    private val maximumCheckIntervalMillis: Long = DEFAULT_MAXIMUM_CHECK_INTERVAL_MILLIS,
    val retryIntervalMillis: Long = DEFAULT_RETRY_INTERVAL_MILLIS,
) {
    init {
        require(minimumExtensionWindowSeconds > 0)
        require(countdownSafetySeconds >= 0)
        require(minimumCheckIntervalMillis > 0)
        require(maximumCheckIntervalMillis >= minimumCheckIntervalMillis)
        require(retryIntervalMillis > 0)
    }

    fun shouldExtend(info: SessionLeaseInfo): Boolean =
        info.remainingSeconds <= extensionWindowSeconds(info)

    fun nextCheckAfterMillis(info: SessionLeaseInfo): Long {
        val secondsUntilExtension = info.remainingSeconds - extensionWindowSeconds(info)
        val desiredMillis = secondsUntilExtension.coerceAtLeast(0) * MILLIS_PER_SECOND
        return desiredMillis.coerceIn(minimumCheckIntervalMillis, maximumCheckIntervalMillis)
    }

    private fun extensionWindowSeconds(info: SessionLeaseInfo): Long = maxOf(
        minimumExtensionWindowSeconds,
        info.logoutCountDownSeconds + countdownSafetySeconds,
    )

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
        const val DEFAULT_EXTENSION_WINDOW_SECONDS = 10L * 60L
        const val DEFAULT_COUNTDOWN_SAFETY_SECONDS = 60L
        const val DEFAULT_MINIMUM_CHECK_INTERVAL_MILLIS = 30L * 1_000L
        const val DEFAULT_MAXIMUM_CHECK_INTERVAL_MILLIS = 30L * 60L * 1_000L
        const val DEFAULT_RETRY_INTERVAL_MILLIS = 60L * 1_000L
    }
}

class SessionLeaseManager(
    private val sessionCoordinator: SessionCoordinator,
    private val gateway: SessionLeaseGateway,
    private val userAgent: KlasUserAgent,
    private val policy: SessionLeasePolicy = SessionLeasePolicy(),
) : SessionLeaseMaintainer {
    private val operationMutex = Mutex()

    override suspend fun maintain(): SessionLeaseResult = operationMutex.withLock {
        val session = when (val restored = sessionCoordinator.restore()) {
            is SessionResult.Active -> restored.session
            SessionResult.Missing -> return@withLock SessionLeaseResult.Missing
            SessionResult.Expired -> return@withLock SessionLeaseResult.Expired
            is SessionResult.Failed -> return@withLock retry(SessionLeaseFailure.Storage)
        }

        when (val infoResult = gateway.fetchInfo(session.token, userAgent)) {
            is SessionInfoResult.Success -> maintainActiveSession(session.token, infoResult.info)
            SessionInfoResult.SessionExpired -> expireSession()
            is SessionInfoResult.HttpFailure -> retry(SessionLeaseFailure.Http(infoResult.statusCode))
            SessionInfoResult.MalformedResponse -> retry(SessionLeaseFailure.MalformedResponse)
            SessionInfoResult.Timeout -> retry(SessionLeaseFailure.Timeout)
            SessionInfoResult.NetworkFailure -> retry(SessionLeaseFailure.Network)
        }
    }

    private suspend fun maintainActiveSession(
        token: SecretValue,
        initialInfo: SessionLeaseInfo,
    ): SessionLeaseResult {
        if (initialInfo.remainingSeconds == 0L) return expireSession()
        if (!policy.shouldExtend(initialInfo)) return markActive(token, initialInfo, extended = false)

        return when (val extension = gateway.extend(token, userAgent)) {
            SessionExtensionResult.Success -> verifyExtension(token, initialInfo)
            SessionExtensionResult.SessionExpired -> expireSession()
            is SessionExtensionResult.HttpFailure -> retry(SessionLeaseFailure.Http(extension.statusCode))
            SessionExtensionResult.MalformedResponse -> retry(SessionLeaseFailure.MalformedResponse)
            SessionExtensionResult.Timeout -> retry(SessionLeaseFailure.Timeout)
            SessionExtensionResult.NetworkFailure -> retry(SessionLeaseFailure.Network)
        }
    }

    private suspend fun verifyExtension(
        token: SecretValue,
        previousInfo: SessionLeaseInfo,
    ): SessionLeaseResult = when (val verified = gateway.fetchInfo(token, userAgent)) {
        is SessionInfoResult.Success -> {
            if (verified.info.remainingSeconds <= previousInfo.remainingSeconds) {
                retry(SessionLeaseFailure.ExtensionNotConfirmed)
            } else {
                markActive(token, verified.info, extended = true)
            }
        }
        SessionInfoResult.SessionExpired -> expireSession()
        is SessionInfoResult.HttpFailure -> retry(SessionLeaseFailure.Http(verified.statusCode))
        SessionInfoResult.MalformedResponse -> retry(SessionLeaseFailure.MalformedResponse)
        SessionInfoResult.Timeout -> retry(SessionLeaseFailure.Timeout)
        SessionInfoResult.NetworkFailure -> retry(SessionLeaseFailure.Network)
    }

    private suspend fun markActive(
        token: SecretValue,
        info: SessionLeaseInfo,
        extended: Boolean,
    ): SessionLeaseResult = when (sessionCoordinator.observe(token)) {
        is SessionResult.Active -> SessionLeaseResult.Active(
            info = info,
            nextCheckAfterMillis = policy.nextCheckAfterMillis(info),
            extended = extended,
        )
        else -> retry(SessionLeaseFailure.Storage)
    }

    private suspend fun expireSession(): SessionLeaseResult = when (sessionCoordinator.expire()) {
        SessionResult.Expired -> SessionLeaseResult.Expired
        else -> retry(SessionLeaseFailure.Storage)
    }

    private fun retry(failure: SessionLeaseFailure) = SessionLeaseResult.Retry(
        failure = failure,
        retryAfterMillis = policy.retryIntervalMillis,
    )
}

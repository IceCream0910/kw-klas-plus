package com.icecream.kwklasplus.core

import com.icecream.kwklasplus.core.auth.CredentialPreparationResult
import com.icecream.kwklasplus.core.auth.LoginResult
import com.icecream.kwklasplus.core.auth.LoginTokenEncryptor
import com.icecream.kwklasplus.core.auth.PlainPassword
import com.icecream.kwklasplus.core.auth.StoredCredential
import com.icecream.kwklasplus.core.auth.WebAuthDriver
import com.icecream.kwklasplus.core.security.SecretValue
import com.icecream.kwklasplus.core.session.SessionResult
import com.icecream.kwklasplus.core.session.SessionLeaseManager
import com.icecream.kwklasplus.core.session.SessionLeaseResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import platform.Foundation.NSUserDefaults

// Swift에서 suspend 유스케이스를 completion으로 호출하기 위한 브릿지
class IosAuthRuntime(
    val dependencies: IosSharedDependencies,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {
    private var sessionLeaseManager: SessionLeaseManager? = null
    private var sessionKeepAliveJob: Job? = null

    fun loadCredential(onResult: (StoredCredential?) -> Unit) {
        scope.launch {
            onResult(runCatching { dependencies.credentialStore.load() }.getOrNull())
        }
    }

    fun loadAccountId(onResult: (String?) -> Unit) {
        scope.launch {
            onResult(runCatching { dependencies.credentialStore.loadAccountId() }.getOrNull())
        }
    }

    fun restoreSession(onResult: (SessionResult) -> Unit) {
        scope.launch {
            onResult(dependencies.sessionCoordinator.restore())
        }
    }

    fun maintainSession(userAgent: String, onResult: (SessionLeaseResult) -> Unit) {
        scope.launch {
            onResult(leaseManager(userAgent).maintain())
        }
    }

    fun prepareCredential(
        accountId: String,
        plainPassword: String,
        onResult: (CredentialPreparationResult) -> Unit,
    ) {
        scope.launch {
            onResult(
                dependencies.prepareCredentialUseCase.prepare(
                    accountId,
                    PlainPassword.of(plainPassword),
                ),
            )
        }
    }

    fun resumeLogin(
        driver: WebAuthDriver,
        credential: StoredCredential,
        onResult: (LoginResult) -> Unit,
    ) {
        scope.launch {
            onResult(dependencies.loginUseCase(driver).resume(credential))
        }
    }

    fun resumeHttpLogin(
        tokenEncryptor: LoginTokenEncryptor,
        credential: StoredCredential,
        onResult: (LoginResult) -> Unit,
    ) {
        scope.launch {
            onResult(
                dependencies.loginUseCase(dependencies.httpAuthDriver(tokenEncryptor))
                    .resume(credential),
            )
        }
    }

    fun startSessionKeepAlive(
        userAgent: String,
        initialDelayMillis: Long,
        onExpired: () -> Unit,
    ) {
        sessionKeepAliveJob?.cancel()
        sessionKeepAliveJob = scope.launch {
            if (initialDelayMillis > 0L) delay(initialDelayMillis)
            val manager = leaseManager(userAgent)
            while (isActive) {
                when (val result = manager.maintain()) {
                    is SessionLeaseResult.Active -> delay(result.nextCheckAfterMillis)
                    SessionLeaseResult.Missing -> return@launch
                    SessionLeaseResult.Expired -> {
                        onExpired()
                        return@launch
                    }
                    is SessionLeaseResult.Retry -> delay(result.retryAfterMillis)
                }
            }
        }
    }

    fun stopSessionKeepAlive() {
        sessionKeepAliveJob?.cancel()
        sessionKeepAliveJob = null
    }

    fun wipeForFailedLogin(onDone: () -> Unit) {
        stopSessionKeepAlive()
        scope.launch {
            runCatching { dependencies.sessionCoordinator.expire() }
            runCatching { dependencies.credentialStore.clear() }
            dependencies.clearNonSecretPreferences()
            onDone()
        }
    }

    fun expireSession(onDone: () -> Unit) {
        stopSessionKeepAlive()
        scope.launch {
            runCatching { dependencies.sessionCoordinator.expire() }
            onDone()
        }
    }

    private fun leaseManager(userAgent: String): SessionLeaseManager {
        val existing = sessionLeaseManager
        if (existing != null) return existing
        return dependencies.sessionLeaseManager(userAgent).also { sessionLeaseManager = it }
    }

    fun observeSessionToken(token: String, onResult: (SessionResult) -> Unit) {
        scope.launch {
            onResult(dependencies.sessionCoordinator.observe(SecretValue.of(token)))
        }
    }

    companion object {
        fun createDefault(): IosAuthRuntime = IosAuthRuntime(IosSharedDependencies())

        fun create(defaults: NSUserDefaults): IosAuthRuntime =
            IosAuthRuntime(IosSharedDependencies(defaults = defaults))
    }
}

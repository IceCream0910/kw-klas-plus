package com.icecream.kwklasplus.core

import com.icecream.kwklasplus.core.academic.AcademicSubject
import com.icecream.kwklasplus.core.academic.AcademicTermDisplay
import com.icecream.kwklasplus.core.academic.AcademicTermKey
import com.icecream.kwklasplus.core.academic.AcademicTermSelector
import com.icecream.kwklasplus.core.academic.AcademicTermsResult
import com.icecream.kwklasplus.core.academic.DeadlinesResult
import com.icecream.kwklasplus.core.academic.DeadlinesWebCodec
import com.icecream.kwklasplus.core.academic.TimetableResult
import com.icecream.kwklasplus.core.academic.TimetableWebCodec
import com.icecream.kwklasplus.core.legacy.LegacyPreferenceKeys
import com.icecream.kwklasplus.core.lock.AppLockSettings
import com.icecream.kwklasplus.core.network.KlasUserAgent
import com.icecream.kwklasplus.core.security.SecretValue
import com.icecream.kwklasplus.core.session.SessionResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import platform.Foundation.NSUserDefaults

sealed interface HomeBootstrapResult {
    class Ready(
        val sessionToken: SecretValue,
        val yearHakgi: String,
        val yearHakgiListJoined: String,
        val timetableJson: String,
        val deadlineJson: String,
        val promptYearHakgiChange: Boolean,
    ) : HomeBootstrapResult

    class EmptyTerms(val sessionToken: SecretValue) : HomeBootstrapResult
    data object SessionExpired : HomeBootstrapResult
    class Failure(val message: String) : HomeBootstrapResult
}

class IosHomeRuntime(
    private val dependencies: IosSharedDependencies,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {
    fun bootstrapHome(userAgent: String, onResult: (HomeBootstrapResult) -> Unit) {
        scope.launch {
            onResult(runBootstrap(userAgent))
        }
    }

    fun refreshHome(yearHakgi: String, userAgent: String, onResult: (HomeBootstrapResult) -> Unit) {
        scope.launch {
            dependencies.writeStringPreference(LegacyPreferenceKeys.YEAR_HAKGI, yearHakgi)
            onResult(runBootstrap(userAgent, selectedYearHakgi = yearHakgi))
        }
    }

    fun saveYearHakgi(value: String) {
        dependencies.writeStringPreference(LegacyPreferenceKeys.YEAR_HAKGI, value)
    }

    fun currentTheme(): String =
        dependencies.stringPreference(LegacyPreferenceKeys.APP_THEME) ?: "system"

    fun saveTheme(value: String) {
        dependencies.writeStringPreference(LegacyPreferenceKeys.APP_THEME, value)
    }

    fun currentYearHakgi(): String =
        dependencies.stringPreference(LegacyPreferenceKeys.YEAR_HAKGI).orEmpty()

    fun currentYearHakgiListJoined(): String =
        dependencies.stringPreference(LegacyPreferenceKeys.YEAR_HAKGI_LIST).orEmpty()

    fun defaultAppLockSettingsJson(): String = AppLockSettings(
        enabled = false,
        biometricEnabled = false,
        hasPassword = false,
    ).toLegacyJson()

    fun yearHakgiButtonText(value: String): String = AcademicTermDisplay.buttonText(value)

    fun logout(onDone: () -> Unit) {
        scope.launch {
            runCatching { dependencies.sessionCoordinator.expire() }
            runCatching { dependencies.credentialStore.clear() }
            dependencies.clearNonSecretPreferences()
            onDone()
        }
    }

    private suspend fun runBootstrap(
        userAgent: String,
        selectedYearHakgi: String? = null,
    ): HomeBootstrapResult {
        val session = when (val restored = dependencies.sessionCoordinator.restore()) {
            is SessionResult.Active -> restored.session.token
            SessionResult.Expired, SessionResult.Missing -> return HomeBootstrapResult.SessionExpired
            is SessionResult.Failed -> return HomeBootstrapResult.Failure("세션을 확인하지 못했습니다.")
        }
        val agent = runCatching { KlasUserAgent.fromPlatform(userAgent) }.getOrElse {
            return HomeBootstrapResult.Failure("수강과목 정보를 불러오지 못했습니다. 잠시 후 다시 시도해주세요.")
        }
        return when (val termsResult = dependencies.academicRepository.fetchTerms(session, agent)) {
            is AcademicTermsResult.Success -> {
                val terms = termsResult.terms
                if (terms.isEmpty()) return HomeBootstrapResult.EmptyTerms(session)
                val savedYearHakgi = selectedYearHakgi
                    ?: dependencies.stringPreference(LegacyPreferenceKeys.YEAR_HAKGI)
                val savedList = dependencies.stringPreference(LegacyPreferenceKeys.YEAR_HAKGI_LIST)
                val joined = terms.joinToString("&") { it.value }
                val selection = AcademicTermSelector.select(terms, savedYearHakgi)
                    ?: return HomeBootstrapResult.EmptyTerms(session)
                val yearHakgi = selection.term.value
                dependencies.writeStringPreference(LegacyPreferenceKeys.YEAR_HAKGI_LIST, joined)
                dependencies.writeStringPreference(LegacyPreferenceKeys.YEAR_HAKGI, yearHakgi)
                val promptChange = !savedList.isNullOrBlank() && joined != savedList
                val (timetableJson, deadlineJson) = coroutineScope {
                    val timetable = async { fetchTimetable(session, agent, yearHakgi) }
                    val deadlines = async {
                        fetchDeadlines(session, agent, yearHakgi, selection.term.subjects)
                    }
                    timetable.await() to deadlines.await()
                }
                if (timetableJson == SESSION_EXPIRED || deadlineJson == SESSION_EXPIRED) {
                    return HomeBootstrapResult.SessionExpired
                }
                HomeBootstrapResult.Ready(
                    sessionToken = session,
                    yearHakgi = yearHakgi,
                    yearHakgiListJoined = joined,
                    timetableJson = timetableJson,
                    deadlineJson = deadlineJson,
                    promptYearHakgiChange = promptChange,
                )
            }
            AcademicTermsResult.SessionExpired -> HomeBootstrapResult.SessionExpired
            else -> HomeBootstrapResult.Failure("수강과목 정보를 불러오지 못했습니다. 잠시 후 다시 시도해주세요.")
        }
    }

    private suspend fun fetchTimetable(
        session: SecretValue,
        userAgent: KlasUserAgent,
        yearHakgi: String,
    ): String {
        val term = AcademicTermKey.parse(yearHakgi) ?: return ""
        return when (
            val result = dependencies.timetableRepository.fetch(
                session,
                userAgent,
                term.year,
                term.semester,
            )
        ) {
            is TimetableResult.Success -> TimetableWebCodec().encode(result.entriesBySubject)
            TimetableResult.SessionExpired -> SESSION_EXPIRED
            else -> ""
        }
    }

    private suspend fun fetchDeadlines(
        session: SecretValue,
        userAgent: KlasUserAgent,
        yearHakgi: String,
        subjects: List<AcademicSubject>,
    ): String = when (
        val result = dependencies.deadlineRepository.fetch(session, userAgent, yearHakgi, subjects)
    ) {
        is DeadlinesResult.Success -> DeadlinesWebCodec().encode(result.subjects)
        DeadlinesResult.SessionExpired -> SESSION_EXPIRED
        else -> ""
    }

    companion object {
        private const val SESSION_EXPIRED = "__SESSION_EXPIRED__"

        fun createDefault(): IosHomeRuntime = IosHomeRuntime(IosSharedDependencies())

        fun create(defaults: NSUserDefaults): IosHomeRuntime =
            IosHomeRuntime(IosSharedDependencies(defaults = defaults))

        fun create(dependencies: IosSharedDependencies): IosHomeRuntime =
            IosHomeRuntime(dependencies)
    }
}

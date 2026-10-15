package com.icecream.kwklasplus.core

import com.icecream.kwklasplus.core.academic.AcademicSubject
import com.icecream.kwklasplus.core.academic.AcademicTermDisplay
import com.icecream.kwklasplus.core.academic.AcademicTermKey
import com.icecream.kwklasplus.core.academic.AcademicTermSelector
import com.icecream.kwklasplus.core.academic.AcademicTermsResult
import com.icecream.kwklasplus.core.academic.DeadlinesResult
import com.icecream.kwklasplus.core.academic.DeadlinesWebCodec
import com.icecream.kwklasplus.core.academic.IosAcademicWidgets
import com.icecream.kwklasplus.core.academic.TimetableResult
import com.icecream.kwklasplus.core.academic.TimetableWebCodec
import com.icecream.kwklasplus.core.legacy.LegacyPreferenceKeys
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
import platform.Foundation.NSUUID
import com.icecream.kwklasplus.core.search.SearchBatch
import com.icecream.kwklasplus.core.search.SearchSnapshot

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
    private val academicWidgets: IosAcademicWidgets? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {
    private var bootstrapRequestVersion=0L
    private var searchScope = NSUUID().UUIDString
    private var searchGeneration = 0L
    private var searchSnapshot: SearchSnapshot? = null
    fun searchDataJson(): String = (searchSnapshot?.takeIf { it.term == currentYearHakgi() }
        ?: SearchSnapshot(searchScope, currentYearHakgi(), searchGeneration, emptyList())).encode()
    fun canOpenSearchCourse(term: String, courseId: String): Boolean = term == currentYearHakgi() &&
        searchSnapshot?.let { it.term == term && it.batches.any { batch -> batch.courseId == courseId } } == true
    fun bootstrapHome(userAgent: String, onResult: (HomeBootstrapResult) -> Unit) {
        val request=++bootstrapRequestVersion
        scope.launch {
            val result=runBootstrap(userAgent,requestVersion=request)
            if(request==bootstrapRequestVersion)onResult(result)
        }
    }

    fun refreshHome(yearHakgi: String, userAgent: String, onResult: (HomeBootstrapResult) -> Unit) {
        val request=++bootstrapRequestVersion
        scope.launch {
            if(request!=bootstrapRequestVersion)return@launch
            dependencies.writeStringPreference(LegacyPreferenceKeys.YEAR_HAKGI, yearHakgi)
            val result=runBootstrap(userAgent, selectedYearHakgi = yearHakgi,requestVersion=request)
            if(request==bootstrapRequestVersion)onResult(result)
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

    fun defaultAppLockSettingsJson(): String =
        dependencies.appLockStore.currentSettings().toLegacyJson()

    fun yearHakgiButtonText(value: String): String = AcademicTermDisplay.buttonText(value)

    fun onForeground(userAgent: String) {
        com.icecream.kwklasplus.core.notification.IosReminderRuntime.foreground()
        scope.launch {
            if (dependencies.academicWidgetFlags.cookieSyncNeeded()) {
                runCatching { dependencies.sessionCoordinator.restore() }
            }
            academicWidgets?.refreshIdentity()
            academicWidgets?.syncCalendar(userAgent)
        }
    }

    fun requestCalendarSync(userAgent: String) {
        scope.launch { academicWidgets?.syncCalendar(userAgent) }
    }

    fun logout(onDone: () -> Unit) {
        searchSnapshot = null
        searchScope = NSUUID().UUIDString
        ++searchGeneration
        ++bootstrapRequestVersion
        ++feedRequestVersion
        scope.launch {
            com.icecream.kwklasplus.core.search.SearchAgentHandoff.clear()
            runCatching { com.icecream.kwklasplus.core.notification.IosReminderRuntime.clear() }
            academicWidgets?.clear()
            runCatching { dependencies.sessionCoordinator.expire() }
            runCatching { dependencies.credentialStore.clear() }
            runCatching { dependencies.libraryService.clearAll() }
            dependencies.clearNonSecretPreferences()
            onDone()
        }
    }

    private suspend fun runBootstrap(
        userAgent: String,
        selectedYearHakgi: String? = null,
        requestVersion: Long,
    ): HomeBootstrapResult {
        val selectedAccount=dependencies.stringPreference(LegacyPreferenceKeys.KW_ID)
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
                if(requestVersion!=bootstrapRequestVersion || selectedAccount!=dependencies.stringPreference(LegacyPreferenceKeys.KW_ID))return HomeBootstrapResult.Failure("새 요청으로 대체되었습니다.")
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
                academicWidgets?.refreshIdentity()
                val promptChange = !savedList.isNullOrBlank() && joined != savedList
                val (timetableJson, deadlineJson) = coroutineScope {
                    val timetable = async { fetchTimetable(session, agent, yearHakgi) }
                    val deadlines = async {
                        fetchDeadlines(session, agent, yearHakgi, selection.term.subjects)
                    }
                    timetable.await() to deadlines.await()
                }
                if(requestVersion!=bootstrapRequestVersion || selectedAccount!=dependencies.stringPreference(LegacyPreferenceKeys.KW_ID) || yearHakgi!=dependencies.stringPreference(LegacyPreferenceKeys.YEAR_HAKGI))return HomeBootstrapResult.Failure("새 요청으로 대체되었습니다.")
                if (timetableJson == SESSION_EXPIRED || deadlineJson == SESSION_EXPIRED) {
                    return HomeBootstrapResult.SessionExpired
                }
                scope.launch { academicWidgets?.syncCalendar(userAgent) }
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
            is TimetableResult.Success -> {
                academicWidgets?.recordTimetable(yearHakgi, result.entriesBySubject.values.flatten())
                TimetableWebCodec().encode(result.entriesBySubject)
            }
            TimetableResult.SessionExpired -> SESSION_EXPIRED
            else -> ""
        }
    }

    private suspend fun fetchDeadlines(
        session: SecretValue,
        userAgent: KlasUserAgent,
        yearHakgi: String,
        subjects: List<AcademicSubject>,
    ): String {
        val account=dependencies.stringPreference(LegacyPreferenceKeys.KW_ID)
        val ticket=runCatching { com.icecream.kwklasplus.core.notification.IosReminderRuntime.beginDeadlineRefresh() }.getOrNull()
        val generation = ++searchGeneration
        val batches = mutableListOf<SearchBatch>()
        val result=dependencies.deadlineRepository.fetch(session,userAgent,yearHakgi,subjects) { batches.add(it) }
        if(account!=dependencies.stringPreference(LegacyPreferenceKeys.KW_ID) || yearHakgi!=dependencies.stringPreference(LegacyPreferenceKeys.YEAR_HAKGI))return ""
        if (generation == searchGeneration) searchSnapshot = SearchSnapshot(searchScope, yearHakgi, generation, batches)
        runCatching { com.icecream.kwklasplus.core.notification.IosReminderRuntime.acceptHomeDeadlines(ticket,result) }
        return when(result) {
            is DeadlinesResult.Success -> DeadlinesWebCodec().encode(result.subjects)
            DeadlinesResult.SessionExpired -> SESSION_EXPIRED
            else -> ""
        }
    }
    private var feedRequestVersion=0L
    fun refreshFeed(yearHakgi: String,userAgent: String,onResult: (String,String)->Unit) {
        val version=++feedRequestVersion
        val account=dependencies.stringPreference(LegacyPreferenceKeys.KW_ID)
        scope.launch {
            val restored=dependencies.sessionCoordinator.restore()
            if(restored !is SessionResult.Active) { if(version==feedRequestVersion)onResult("SESSION_EXPIRED","[]");return@launch }
            val agent=KlasUserAgent.fromPlatform(userAgent)
            val terms=dependencies.academicRepository.fetchTerms(restored.session.token,agent)
            if(version!=feedRequestVersion || account!=dependencies.stringPreference(LegacyPreferenceKeys.KW_ID) || yearHakgi!=dependencies.stringPreference(LegacyPreferenceKeys.YEAR_HAKGI))return@launch
            val subjects=(terms as? AcademicTermsResult.Success)?.terms?.firstOrNull { it.value==yearHakgi }?.subjects
            val json=if(subjects!=null)fetchDeadlines(restored.session.token,agent,yearHakgi,subjects) else ""
            if(version!=feedRequestVersion || account!=dependencies.stringPreference(LegacyPreferenceKeys.KW_ID) || yearHakgi!=dependencies.stringPreference(LegacyPreferenceKeys.YEAR_HAKGI))return@launch
            onResult(if(json==SESSION_EXPIRED || terms==AcademicTermsResult.SessionExpired)"SESSION_EXPIRED" else if(json.isEmpty())"FAILED" else "READY",json.takeUnless { it.isEmpty() || it==SESSION_EXPIRED } ?: "[]")
        }
    }

    companion object {
        private const val SESSION_EXPIRED = "__SESSION_EXPIRED__"

        fun createDefault(): IosHomeRuntime = IosHomeRuntime(IosSharedDependencies())

        fun create(defaults: NSUserDefaults): IosHomeRuntime =
            IosHomeRuntime(IosSharedDependencies.create(defaults = defaults))

        fun create(dependencies: IosSharedDependencies): IosHomeRuntime =
            IosHomeRuntime(dependencies)

        fun create(
            dependencies: IosSharedDependencies,
            academicWidgets: IosAcademicWidgets,
        ): IosHomeRuntime = IosHomeRuntime(dependencies, academicWidgets)
    }
}

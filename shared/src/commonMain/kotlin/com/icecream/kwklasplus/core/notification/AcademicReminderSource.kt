package com.icecream.kwklasplus.core.notification

import com.icecream.kwklasplus.core.academic.AcademicRepository
import com.icecream.kwklasplus.core.academic.AcademicTermsResult
import com.icecream.kwklasplus.core.network.*
import com.icecream.kwklasplus.core.security.SecretValue
import kotlinx.serialization.json.*

class AcademicReminderSource(
    private val transport: KlasAuthenticatedTransport,
    private val session: suspend () -> SecretValue?,
    private val userAgent: () -> KlasUserAgent,
) : ReminderDataSource {
    override suspend fun deadlines(term: String): ReminderSourceResult<List<ReminderDeadline>> {
        val token=session() ?: return ReminderSourceResult.NeedsLogin
        val terms=AcademicRepository(transport).fetchTerms(token,userAgent())
        if(terms==AcademicTermsResult.SessionExpired) return ReminderSourceResult.NeedsLogin
        if(terms !is AcademicTermsResult.Success) return ReminderSourceResult.Retry
        val subjects=terms.terms.firstOrNull { it.value==term }?.subjects ?: return ReminderSourceResult.UnverifiedSource
        val items=mutableListOf<ReminderDeadline>()
        for(subject in subjects) {
            for((kind, endpoint) in listOf("onlineLecture" to AuthenticatedKlasEndpoint.ONLINE_LECTURE_DEADLINES,"task" to AuthenticatedKlasEndpoint.TASK_DEADLINES,"teamTask" to AuthenticatedKlasEndpoint.TEAM_TASK_DEADLINES)) {
                val r=transport.postJson(endpoint,token,userAgent(),buildJsonObject {
                    put("selectChangeYn","Y"); put("selectYearhakgi",term); put("selectSubj",subject.id)
                })
                if(r==KlasAuthenticatedResult.SessionExpired) return ReminderSourceResult.NeedsLogin
                if(r !is KlasAuthenticatedResult.Success) return ReminderSourceResult.Retry
                val rows=r.body as? JsonArray ?: return ReminderSourceResult.UnverifiedSource
                items += DeadlineNotificationProjection.parse(term, subject.id, subject.name, kind, rows)
                    ?: return ReminderSourceResult.UnverifiedSource
            }
        }
        if(items.map { it.key }.distinct().size!=items.size) return ReminderSourceResult.UnverifiedSource
        return ReminderSourceResult.Success(items)
    }
}

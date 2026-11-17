package com.icecream.kwklasplus.core.notification

import com.icecream.kwklasplus.core.academic.AcademicRepository
import com.icecream.kwklasplus.core.academic.AcademicTermsResult
import com.icecream.kwklasplus.core.network.*
import com.icecream.kwklasplus.core.security.SecretValue
import kotlinx.serialization.json.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class AcademicReminderSource(
    private val transport: KlasAuthenticatedTransport,
    private val session: suspend () -> SecretValue?,
    private val userAgent: () -> KlasUserAgent,
) : ReminderDataSource {
    override suspend fun deadlines(term: String): ReminderSourceResult<List<ReminderDeadline>> {
        val token=session() ?: return ReminderSourceResult.NeedsLogin
        val terms=AcademicRepository(transport).fetchSelectedTerm(token, userAgent(), term, allowFallback = false)
        if(terms==AcademicTermsResult.SessionExpired) return ReminderSourceResult.NeedsLogin
        if(terms !is AcademicTermsResult.Selected) return ReminderSourceResult.Retry
        val subjects=terms.selection?.term?.subjects ?: return ReminderSourceResult.UnverifiedSource
        val permits=Semaphore(4)
        val results=coroutineScope {
            subjects.map { subject -> async {
                permits.withPermit {
                    val items=mutableListOf<ReminderDeadline>()
                    var isComplete = true
                    for((kind, endpoint) in listOf("onlineLecture" to AuthenticatedKlasEndpoint.ONLINE_LECTURE_DEADLINES,"task" to AuthenticatedKlasEndpoint.TASK_DEADLINES,"teamTask" to AuthenticatedKlasEndpoint.TEAM_TASK_DEADLINES)) {
                        val r=transport.postJson(endpoint,token,userAgent(),buildJsonObject {
                            put("selectChangeYn","Y"); put("selectYearhakgi",term); put("selectSubj",subject.id)
                        })
                        if(r==KlasAuthenticatedResult.SessionExpired)return@withPermit ReminderSourceResult.NeedsLogin
                        if(r !is KlasAuthenticatedResult.Success)return@withPermit ReminderSourceResult.Retry
                        val rows=r.body as? JsonArray ?: return@withPermit ReminderSourceResult.UnverifiedSource
                        val projection = DeadlineNotificationProjection.parse(term, subject.id, subject.name, kind, rows)
                        items += projection.items
                        isComplete = isComplete && projection.isComplete
                    }
                    ReminderSourceResult.Success(items, isComplete = isComplete)
                }
            } }.awaitAll()
        }
        if(results.any { it==ReminderSourceResult.NeedsLogin })return ReminderSourceResult.NeedsLogin
        results.firstOrNull { it !is ReminderSourceResult.Success }?.let { return it }
        val snapshots = results.filterIsInstance<ReminderSourceResult.Success<List<ReminderDeadline>>>()
        val items = snapshots.flatMap { it.value }
        val isComplete = snapshots.all { it.isComplete }
        if (items.distinctBy(ReminderDeadline::key).size != items.size || (!isComplete && items.isEmpty())) {
            return ReminderSourceResult.UnverifiedSource
        }
        return ReminderSourceResult.Success(items, isComplete = isComplete)
    }
}

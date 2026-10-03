package com.icecream.kwklasplus.core.notification

import com.icecream.kwklasplus.core.network.*
import com.icecream.kwklasplus.core.security.SecretValue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

class AcademicReminderSourceTest {
    private fun source(rows: String, failure: Boolean=false)=AcademicReminderSource(
        KlasAuthenticatedTransport { endpoint,_,_,_ ->
            when {
                endpoint==AuthenticatedKlasEndpoint.ACADEMIC_TERM_SUBJECTS -> KlasAuthenticatedResult.Success(Json.parseToJsonElement("""[{"value":"2026,2","subjList":[{"value":"SUBJECT","name":"fixture"}]}]"""))
                endpoint==AuthenticatedKlasEndpoint.TEAM_TASK_DEADLINES && failure -> KlasAuthenticatedResult.NetworkFailure
                endpoint==AuthenticatedKlasEndpoint.TASK_DEADLINES -> KlasAuthenticatedResult.Success(Json.parseToJsonElement(rows))
                else -> KlasAuthenticatedResult.Success(JsonArray(emptyList()))
            }
        },{ SecretValue.of("synthetic-session") },{ KlasUserAgent.fromPlatform("fixture") },
    )
    private val row="""{"taskNo":"A","submityn":"N","startdate":"202610010900","expiredate":"202610031000"}"""
    @Test fun missingStableIdOrUnknownCompletionFailsClosed()=runBlocking {
        assertEquals(ReminderSourceResult.UnverifiedSource,source("[${row.replace("\"taskNo\":\"A\",","")}]").deadlines("2026,2"))
        assertEquals(ReminderSourceResult.UnverifiedSource,source("[${row.replace("\"submityn\":\"N\",","")}]").deadlines("2026,2"))
    }
    @Test fun duplicateIdsAndPartialNetworkFailureDoNotProduceSnapshot()=runBlocking {
        assertEquals(ReminderSourceResult.UnverifiedSource,source("[$row,$row]").deadlines("2026,2"))
        assertEquals(ReminderSourceResult.Retry,source("[$row]",failure=true).deadlines("2026,2"))
    }
    @Test fun completedRowsAreExcludedAndSyntheticStableIdPreserved()=runBlocking {
        val result=assertIs<ReminderSourceResult.Success<List<ReminderDeadline>>>(source("[$row,${row.replace("\"A\"","\"B\"").replace("\"N\"","\"Y\"")}]").deadlines("2026,2"))
        assertEquals("2026,2/SUBJECT/task/A",result.value.single().key)
        assertEquals(ReminderTime.parse("2026-10-03 10:00:00"),result.value.single().dueAt)
    }
    @Test fun isoMillisecondsAndExplicitTimeZonesParse() {
        assertEquals(ReminderTime.parse("202610020900"),ReminderTime.parse("2026-10-02T09:00:00.000+09:00"))
        assertEquals(ReminderTime.parse("202610020900"),ReminderTime.parse("2026-10-02T00:00:00.000Z"))
    }
    @Test fun sessionRecoveryRetriesOnceAndKeepsUserActionFailuresSeparate()=runBlocking {
        var calls=0;var recovered=0
        val delegate=object: ReminderDataSource {
            override suspend fun deadlines(term: String): ReminderSourceResult<List<ReminderDeadline>> { calls++;return if(calls==1)ReminderSourceResult.NeedsLogin else ReminderSourceResult.Success(emptyList()) }
        }
        val source=SessionRefreshingReminderSource(delegate) { recovered++;ReminderSourceResult.Success(Unit) }
        assertIs<ReminderSourceResult.Success<List<ReminderDeadline>>>(source.deadlines("2026,2"))
        assertEquals(2,calls);assertEquals(1,recovered)
        calls=0
        assertEquals(ReminderSourceResult.NeedsLogin,SessionRefreshingReminderSource(delegate) { ReminderSourceResult.NeedsLogin }.deadlines("2026,2"))
    }
}

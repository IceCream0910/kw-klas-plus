package com.icecream.kwklasplus.core.academic

import com.icecream.kwklasplus.core.network.AuthenticatedKlasEndpoint
import com.icecream.kwklasplus.core.network.KlasAuthenticatedResult
import com.icecream.kwklasplus.core.network.KlasAuthenticatedTransport
import com.icecream.kwklasplus.core.network.KlasUserAgent
import com.icecream.kwklasplus.core.security.SecretValue
import com.icecream.kwklasplus.core.session.Clock
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DeadlineRepositoryTest {
    @Test
    fun filtersCompletedSubmittedAndExpiredItems() = runBlocking {
        val repository = repository { endpoint ->
            when (endpoint) {
                AuthenticatedKlasEndpoint.ONLINE_LECTURE_DEADLINES ->
                    KlasAuthenticatedResult.Success(
                        buildJsonArray {
                            add(onlineLecture("future", progress = 20))
                            add(onlineLecture("completed", progress = 100))
                            add(onlineLecture("past", progress = 20))
                        },
                    )
                AuthenticatedKlasEndpoint.TASK_DEADLINES ->
                    KlasAuthenticatedResult.Success(
                        buildJsonArray {
                            add(assignment("future", submitted = "N"))
                            add(assignment("future", submitted = "Y"))
                        },
                    )
                AuthenticatedKlasEndpoint.TEAM_TASK_DEADLINES ->
                    KlasAuthenticatedResult.Success(buildJsonArray {})
                else -> error("unexpected endpoint")
            }
        }

        val result = repository.fetch(
            SecretValue.of("session"),
            KlasUserAgent.fromPlatform("UA"),
            "2026,1",
            listOf(AcademicSubject("SUBJECT", "공통 테스트")),
        )

        val deadlines = assertIs<DeadlinesResult.Success>(result).subjects.single()
        assertEquals("공통 테스트", deadlines.name)
        assertEquals(2, deadlines.onlineLecture.single().hourGap)
        assertEquals(2, deadlines.task.single().hourGap)
        assertEquals(emptyList(), deadlines.teamTask)
    }

    @Test
    fun requestOrderAndWebFieldNamesMatchLegacyContract() = runBlocking {
        val endpoints = mutableListOf<AuthenticatedKlasEndpoint>()
        val repository = repository { endpoint ->
            endpoints += endpoint
            KlasAuthenticatedResult.Success(buildJsonArray {})
        }

        val result = repository.fetch(
            SecretValue.of("session"),
            KlasUserAgent.fromPlatform("UA"),
            "2026,1",
            listOf(AcademicSubject("SUBJECT", "강의")),
        )

        assertIs<DeadlinesResult.Success>(result)
        assertEquals(
            listOf(
                AuthenticatedKlasEndpoint.ONLINE_LECTURE_DEADLINES,
                AuthenticatedKlasEndpoint.TASK_DEADLINES,
                AuthenticatedKlasEndpoint.TEAM_TASK_DEADLINES,
            ),
            endpoints,
        )
        assertEquals(
            "[{\"name\":\"강의\",\"subj\":\"SUBJECT\",\"onlineLecture\":[],\"task\":[],\"teamTask\":[]}]",
            DeadlinesWebCodec().encode(result.subjects),
        )
    }

    @Test
    fun malformedDatesAndFailuresRemainDistinct() = runBlocking {
        val malformed = repository(
            dateParser = DeadlineDateParser { null },
        ) { endpoint ->
            if (endpoint == AuthenticatedKlasEndpoint.ONLINE_LECTURE_DEADLINES) {
                KlasAuthenticatedResult.Success(buildJsonArray { add(onlineLecture("unknown", 20)) })
            } else {
                KlasAuthenticatedResult.Success(buildJsonArray {})
            }
        }.fetch(
            SecretValue.of("session"),
            KlasUserAgent.fromPlatform("UA"),
            "2026,1",
            listOf(AcademicSubject("SUBJECT", "강의")),
        )
        assertEquals(DeadlinesResult.MalformedResponse, malformed)

        val expired = repository { KlasAuthenticatedResult.SessionExpired }.fetch(
            SecretValue.of("session"),
            KlasUserAgent.fromPlatform("UA"),
            "2026,1",
            listOf(AcademicSubject("SUBJECT", "강의")),
        )
        assertEquals(DeadlinesResult.SessionExpired, expired)
    }

    @Test
    fun subHourPastDeadlinePreservesLegacyIntegerTruncation() = runBlocking {
        val repository = repository(
            dateParser = DeadlineDateParser { -1L },
        ) { endpoint ->
            if (endpoint == AuthenticatedKlasEndpoint.ONLINE_LECTURE_DEADLINES) {
                KlasAuthenticatedResult.Success(buildJsonArray { add(onlineLecture("past", 20)) })
            } else {
                KlasAuthenticatedResult.Success(buildJsonArray {})
            }
        }

        val result = repository.fetch(
            SecretValue.of("session"),
            KlasUserAgent.fromPlatform("UA"),
            "2026,1",
            listOf(AcademicSubject("SUBJECT", "강의")),
        )

        val item = assertIs<DeadlinesResult.Success>(result)
            .subjects.single().onlineLecture.single()
        assertEquals(0, item.hourGap)
    }

    private fun repository(
        dateParser: DeadlineDateParser = DeadlineDateParser { value ->
            when {
                value.startsWith("future") -> 7_200_000L
                value.startsWith("past") -> -7_200_000L
                else -> null
            }
        },
        response: (AuthenticatedKlasEndpoint) -> KlasAuthenticatedResult,
    ) = DeadlineRepository(
        transport = KlasAuthenticatedTransport { endpoint, _, _, _ -> response(endpoint) },
        clock = Clock { 0L },
        onlineLectureEndParser = dateParser,
        assignmentEndParser = dateParser,
    )

    private fun onlineLecture(endDate: String, progress: Int) = buildJsonObject {
        put("evltnSe", "lesson")
        put("prog", progress)
        put("startDate", "start")
        put("endDate", endDate)
    }

    private fun assignment(endDate: String, submitted: String) = buildJsonObject {
        put("submityn", submitted)
        put("startdate", "start")
        put("expiredate", endDate)
    }
}

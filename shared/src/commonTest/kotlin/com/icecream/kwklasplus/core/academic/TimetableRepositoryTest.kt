package com.icecream.kwklasplus.core.academic

import com.icecream.kwklasplus.core.network.AuthenticatedKlasEndpoint
import com.icecream.kwklasplus.core.network.KlasAuthenticatedResult
import com.icecream.kwklasplus.core.network.KlasAuthenticatedTransport
import com.icecream.kwklasplus.core.network.KlasUserAgent
import com.icecream.kwklasplus.core.security.SecretValue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TimetableRepositoryTest {
    @Test
    fun mapsLegacyDynamicDayFieldsAndPeriodSpan() = runBlocking {
        var endpoint: AuthenticatedKlasEndpoint? = null
        val repository = TimetableRepository(
            KlasAuthenticatedTransport { requestedEndpoint, _, _, _ ->
                endpoint = requestedEndpoint
                KlasAuthenticatedResult.Success(
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("wtTime", 2)
                                put("wtHasSchedule", "Y")
                                put("wtSubj_1", "SUBJECT")
                                put("wtSubjNm_1", "공통 테스트")
                                put("wtLocHname_1", "연구관")
                                put("wtProfNm_1", "교수")
                                put("wtSpan_1", 2)
                            },
                        )
                        add(
                            buildJsonObject {
                                put("wtTime", 4)
                                put("wtHasSchedule", "N")
                            },
                        )
                    },
                )
            },
        )

        val result = repository.fetch(
            SecretValue.of("session"),
            KlasUserAgent.fromPlatform("UA"),
            "2026",
            "1",
        )

        assertEquals(AuthenticatedKlasEndpoint.TIMETABLE, endpoint)
        val entries = assertIs<TimetableResult.Success>(result).entriesBySubject
        assertEquals(
            TimetableEntry(
                title = "공통 테스트",
                day = 0,
                startTime = "10:30",
                endTime = "13:15",
                info = "연구관/교수",
                subj = "SUBJECT",
            ),
            entries.getValue("SUBJECT").single(),
        )
    }

    @Test
    fun webCodecPreservesLegacyFieldNames() {
        val encoded = TimetableWebCodec().encode(
            mapOf(
                "SUBJECT" to listOf(
                    TimetableEntry("강의", 2, "10:30", "11:45", "강의실/교수", "SUBJECT"),
                ),
            ),
        )

        assertEquals(
            "{\"SUBJECT\":[{\"title\":\"강의\",\"day\":2,\"startTime\":\"10:30\",\"endTime\":\"11:45\",\"info\":\"강의실/교수\",\"subj\":\"SUBJECT\"}]}",
            encoded,
        )
    }

    @Test
    fun historicalRowsMayOmitProfessorName() = runBlocking {
        val repository = TimetableRepository(
            KlasAuthenticatedTransport { _, _, _, _ ->
                KlasAuthenticatedResult.Success(
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("wtTime", 1)
                                put("wtHasSchedule", "Y")
                                put("wtSubj_2", "OLD-SUBJECT")
                                put("wtSubjNm_2", "과거 학기 강의")
                                put("wtLocHname_2", "")
                                put("wtSpan_2", 1)
                            },
                        )
                    },
                )
            },
        )

        val result = repository.fetch(
            SecretValue.of("session"),
            KlasUserAgent.fromPlatform("UA"),
            "2024",
            "1",
        )

        val entry = assertIs<TimetableResult.Success>(result)
            .entriesBySubject.getValue("OLD-SUBJECT").single()
        assertEquals("/", entry.info)
    }

    @Test
    fun malformedRowsAndTransportFailuresRemainDistinct() = runBlocking {
        suspend fun mapped(result: KlasAuthenticatedResult) =
            TimetableRepository(KlasAuthenticatedTransport { _, _, _, _ -> result }).fetch(
                SecretValue.of("session"),
                KlasUserAgent.fromPlatform("UA"),
                "2026",
                "1",
            )

        assertEquals(
            TimetableResult.MalformedResponse,
            mapped(KlasAuthenticatedResult.Success(buildJsonObject {})),
        )
        assertEquals(TimetableResult.SessionExpired, mapped(KlasAuthenticatedResult.SessionExpired))
        assertEquals(TimetableResult.Timeout, mapped(KlasAuthenticatedResult.Timeout))
        assertEquals(
            TimetableResult.HttpFailure(503),
            mapped(KlasAuthenticatedResult.HttpFailure(503)),
        )
    }

    @Test
    fun periodPolicyMatchesLegacyBoundaries() {
        assertEquals("8:0", AcademicPeriodTimePolicy.start(0))
        assertEquals("21:30", AcademicPeriodTimePolicy.start(11))
        assertEquals("8:50", AcademicPeriodTimePolicy.end(0))
        assertEquals("22:5", AcademicPeriodTimePolicy.end(11))
        assertEquals("0:0", AcademicPeriodTimePolicy.start(99))
    }
}

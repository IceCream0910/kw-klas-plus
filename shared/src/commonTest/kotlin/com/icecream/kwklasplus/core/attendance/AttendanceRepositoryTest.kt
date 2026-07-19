package com.icecream.kwklasplus.core.attendance

import com.icecream.kwklasplus.core.network.AuthenticatedKlasEndpoint
import com.icecream.kwklasplus.core.network.KlasAuthenticatedResult
import com.icecream.kwklasplus.core.network.KlasAuthenticatedTransport
import com.icecream.kwklasplus.core.network.KlasUserAgent
import com.icecream.kwklasplus.core.security.SecretValue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AttendanceRepositoryTest {
    @Test
    fun preparesQrPayloadAcrossLegacyThreeStepWorkflow() = runBlocking {
        val requestedEndpoints = mutableListOf<AuthenticatedKlasEndpoint>()
        val repository = AttendanceRepository(
            KlasAuthenticatedTransport { endpoint, _, _, _ ->
                requestedEndpoints += endpoint
                when (endpoint) {
                    AuthenticatedKlasEndpoint.ATTENDANCE_SUBJECTS ->
                        KlasAuthenticatedResult.Success(buildJsonArray { add(subjectJson()) })
                    AuthenticatedKlasEndpoint.ATTENDANCE_LIST ->
                        KlasAuthenticatedResult.Success(
                            buildJsonArray { add(buildJsonObject { put("attend", "Y") }) },
                        )
                    AuthenticatedKlasEndpoint.ATTENDANCE_RANDOM_KEY ->
                        KlasAuthenticatedResult.Success(
                            buildJsonObject { put("randomKey", "random-key") },
                        )
                    AuthenticatedKlasEndpoint.QR_CHECKIN -> error("unexpected endpoint")
                    AuthenticatedKlasEndpoint.ACADEMIC_TERM_SUBJECTS -> error("unexpected endpoint")
                    AuthenticatedKlasEndpoint.TIMETABLE -> error("unexpected endpoint")
                    AuthenticatedKlasEndpoint.ONLINE_LECTURE_DEADLINES -> error("unexpected endpoint")
                    AuthenticatedKlasEndpoint.TASK_DEADLINES -> error("unexpected endpoint")
                    AuthenticatedKlasEndpoint.TEAM_TASK_DEADLINES -> error("unexpected endpoint")
                }
            },
        )

        val result = repository.prepareCheckIn(
            SecretValue.of("session"),
            KlasUserAgent.fromPlatform("UA"),
            QrPreparationRequest("2026", "1", "SUBJECT-ID", "공통 테스트"),
        )

        val payload = assertIs<QrPreparationResult.Success>(result).payload
        assertEquals("SUBJECT-ID", payload.subj)
        assertEquals("random-key", payload.randomKey)
        assertEquals(1, payload.list.size)
        assertEquals(
            listOf(
                AuthenticatedKlasEndpoint.ATTENDANCE_SUBJECTS,
                AuthenticatedKlasEndpoint.ATTENDANCE_LIST,
                AuthenticatedKlasEndpoint.ATTENDANCE_RANDOM_KEY,
            ),
            requestedEndpoints,
        )
    }

    @Test
    fun unsupportedSubjectStopsPreparationAfterFirstRequest() = runBlocking {
        var requestCount = 0
        val repository = AttendanceRepository(
            KlasAuthenticatedTransport { _, _, _, _ ->
                requestCount += 1
                KlasAuthenticatedResult.Success(buildJsonArray { add(subjectJson()) })
            },
        )

        val result = repository.prepareCheckIn(
            SecretValue.of("session"),
            KlasUserAgent.fromPlatform("UA"),
            QrPreparationRequest("2026", "1", "SUBJECT-ID", "없는 강의"),
        )

        assertEquals(QrPreparationResult.UnsupportedSubject, result)
        assertEquals(1, requestCount)
    }

    @Test
    fun legacyPayloadIsDecodedWithoutAndroidJsonTypes() {
        val result = QrAttendancePayloadCodec().decode(
            """{"list":[],"selectYear":"2026","selectHakgi":"1","gwamokKname":"공통 테스트","subj":"SUBJ","randomKey":"random"}""",
        )

        val payload = assertIs<QrAttendancePayloadDecodeResult.Success>(result).payload
        assertEquals("2026", payload.selectYear)
        assertEquals("공통 테스트", payload.gwamokKname)
        assertEquals("random", payload.randomKey)
    }

    @Test
    fun scannedCodeIsAddedOnlyAtTransportBoundary() = runBlocking {
        var endpoint: AuthenticatedKlasEndpoint? = null
        var requestBody: JsonElement? = null
        val repository = AttendanceRepository(
            KlasAuthenticatedTransport { requestedEndpoint, _, _, body ->
                endpoint = requestedEndpoint
                requestBody = body
                KlasAuthenticatedResult.Success(buildJsonObject {})
            },
        )

        val result = repository.checkIn(
            SecretValue.of("session"),
            KlasUserAgent.fromPlatform("UA"),
            QrAttendancePayload(selectYear = "2026"),
            SecretValue.of("scanned-qr"),
        )

        assertEquals(QrCheckInResult.Success, result)
        assertEquals(AuthenticatedKlasEndpoint.QR_CHECKIN, endpoint)
        val sentBody = requireNotNull(requestBody).jsonObject
        assertEquals("scanned-qr", sentBody["encrypt"]!!.jsonPrimitive.content)
        assertEquals("2026", sentBody["selectYear"]!!.jsonPrimitive.content)
    }

    @Test
    fun fieldErrorsAreMappedToRejectedMessages() = runBlocking {
        val repository = AttendanceRepository(
            transportReturning(
                KlasAuthenticatedResult.Success(
                    buildJsonObject {
                        put(
                            "fieldErrors",
                            buildJsonArray {
                                add(buildJsonObject { put("message", "출석 시간이 아닙니다.") })
                                add(buildJsonObject { put("message", " ") })
                            },
                        )
                    },
                ),
            ),
        )

        val result = repository.checkIn(
            SecretValue.of("session"),
            KlasUserAgent.fromPlatform("UA"),
            QrAttendancePayload(),
            SecretValue.of("qr"),
        )

        assertEquals(QrCheckInResult.Rejected(listOf("출석 시간이 아닙니다.")), result)
    }

    @Test
    fun transportFailuresRemainDistinct() = runBlocking {
        suspend fun mapped(result: KlasAuthenticatedResult): QrCheckInResult =
            AttendanceRepository(transportReturning(result)).checkIn(
                SecretValue.of("session"),
                KlasUserAgent.fromPlatform("UA"),
                QrAttendancePayload(),
                SecretValue.of("qr"),
            )

        assertEquals(QrCheckInResult.SessionExpired, mapped(KlasAuthenticatedResult.SessionExpired))
        assertEquals(QrCheckInResult.Timeout, mapped(KlasAuthenticatedResult.Timeout))
        assertEquals(QrCheckInResult.NetworkFailure, mapped(KlasAuthenticatedResult.NetworkFailure))
        assertEquals(
            QrCheckInResult.HttpFailure(503),
            mapped(KlasAuthenticatedResult.HttpFailure(503)),
        )
        assertEquals(
            QrCheckInResult.MalformedResponse,
            mapped(KlasAuthenticatedResult.Success(buildJsonArray {})),
        )
    }

    private fun transportReturning(result: KlasAuthenticatedResult) =
        KlasAuthenticatedTransport { _, _, _, _ -> result }

    private fun subjectJson() = buildJsonObject {
        put("thisYear", "2026")
        put("hakgi", "1")
        put("openMajorCode", "MAJOR")
        put("openGrade", "1")
        put("openGwamokNo", "COURSE")
        put("bunbanNo", "01")
        put("gwamokKname", "공통 테스트")
        put("codeName1", "전공")
        put("hakjumNum", "3")
        put("sisuNum", "3")
        put("memberName", "교수")
        put("currentNum", "10")
        put("yoil", "월")
    }
}

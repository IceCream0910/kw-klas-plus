package com.icecream.kwklasplus.core.attendance

import com.icecream.kwklasplus.core.network.AuthenticatedKlasEndpoint
import com.icecream.kwklasplus.core.network.KlasAuthenticatedResult
import com.icecream.kwklasplus.core.network.KlasAuthenticatedTransport
import com.icecream.kwklasplus.core.network.KlasUserAgent
import com.icecream.kwklasplus.core.security.SecretValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

@Serializable
data class QrAttendancePayload(
    val list: JsonArray = JsonArray(emptyList()),
    val selectYear: String = "",
    val selectHakgi: String = "",
    val openMajorCode: String = "",
    val openGrade: String = "",
    val openGwamokNo: String = "",
    val bunbanNo: String = "",
    val gwamokKname: String = "",
    val codeName1: String = "",
    val hakjumNum: String = "",
    val sisuNum: String = "",
    val memberName: String = "",
    val currentNum: String = "",
    val yoil: String = "",
    val subj: String = "",
    val randomKey: String = "",
)

sealed interface QrAttendancePayloadDecodeResult {
    data class Success(val payload: QrAttendancePayload) : QrAttendancePayloadDecodeResult
    data object Malformed : QrAttendancePayloadDecodeResult
}

class QrAttendancePayloadCodec(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun decode(value: String): QrAttendancePayloadDecodeResult = try {
        QrAttendancePayloadDecodeResult.Success(json.decodeFromString(value))
    } catch (_: Throwable) {
        QrAttendancePayloadDecodeResult.Malformed
    }

    fun encode(payload: QrAttendancePayload): String = json.encodeToString(payload)
}

data class QrPreparationRequest(
    val year: String,
    val semester: String,
    val subjectId: String,
    val subjectName: String,
) {
    init {
        require(year.isNotBlank())
        require(semester.isNotBlank())
        require(subjectId.isNotBlank())
        require(subjectName.isNotBlank())
    }
}

sealed interface QrPreparationResult {
    data class Success(val payload: QrAttendancePayload) : QrPreparationResult
    data object UnsupportedSubject : QrPreparationResult
    data object SessionExpired : QrPreparationResult
    data object Timeout : QrPreparationResult
    data object NetworkFailure : QrPreparationResult
    data class HttpFailure(val statusCode: Int) : QrPreparationResult
    data object EmptyResponse : QrPreparationResult
    data object MalformedResponse : QrPreparationResult
}

sealed interface QrCheckInResult {
    data object Success : QrCheckInResult
    data class Rejected(val messages: List<String>) : QrCheckInResult
    data object SessionExpired : QrCheckInResult
    data object Timeout : QrCheckInResult
    data object NetworkFailure : QrCheckInResult
    data class HttpFailure(val statusCode: Int) : QrCheckInResult
    data object EmptyResponse : QrCheckInResult
    data object MalformedResponse : QrCheckInResult
}

class AttendanceRepository(
    private val transport: KlasAuthenticatedTransport,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun prepareCheckIn(
        session: SecretValue,
        userAgent: KlasUserAgent,
        request: QrPreparationRequest,
    ): QrPreparationResult {
        val query = buildJsonObject {
            put("list", JsonArray(emptyList()))
            put("selectYear", request.year)
            put("selectHakgi", request.semester)
            put("openMajorCode", "")
            put("openGrade", "")
            put("openGwamokNo", "")
            put("bunbanNo", "")
            put("gwamokKname", "")
            put("codeName1", "")
            put("hakjumNum", "")
            put("sisuNum", "")
            put("memberName", "")
            put("currentNum", "")
            put("yoil", "")
        }
        val subjectsResult = transport.postJson(
            AuthenticatedKlasEndpoint.ATTENDANCE_SUBJECTS,
            session,
            userAgent,
            query,
        )
        val subjects = when (subjectsResult) {
            is KlasAuthenticatedResult.Success -> subjectsResult.body as? JsonArray
                ?: return QrPreparationResult.MalformedResponse
            else -> return subjectsResult.toPreparationFailure()
        }
        val subject = subjects
            .mapNotNull { it as? JsonObject }
            .firstOrNull { it.string("gwamokKname") == request.subjectName }
            ?: return QrPreparationResult.UnsupportedSubject
        val payload = subject.toPayload(request.subjectId)
            ?: return QrPreparationResult.MalformedResponse

        val attendanceResult = transport.postJson(
            AuthenticatedKlasEndpoint.ATTENDANCE_LIST,
            session,
            userAgent,
            json.encodeToJsonElement(payload),
        )
        val attendanceList = when (attendanceResult) {
            is KlasAuthenticatedResult.Success -> attendanceResult.body as? JsonArray
                ?: return QrPreparationResult.MalformedResponse
            else -> return attendanceResult.toPreparationFailure()
        }
        val payloadWithAttendance = payload.copy(list = attendanceList)

        val randomKeyResult = transport.postJson(
            AuthenticatedKlasEndpoint.ATTENDANCE_RANDOM_KEY,
            session,
            userAgent,
            json.encodeToJsonElement(payloadWithAttendance),
        )
        val randomKeyBody = when (randomKeyResult) {
            is KlasAuthenticatedResult.Success -> randomKeyResult.body as? JsonObject
                ?: return QrPreparationResult.MalformedResponse
            else -> return randomKeyResult.toPreparationFailure()
        }
        val randomKey = randomKeyBody.string("randomKey")
            ?.takeIf(String::isNotBlank)
            ?: return QrPreparationResult.MalformedResponse
        return QrPreparationResult.Success(payloadWithAttendance.copy(randomKey = randomKey))
    }

    suspend fun checkIn(
        session: SecretValue,
        userAgent: KlasUserAgent,
        payload: QrAttendancePayload,
        scannedCode: SecretValue,
    ): QrCheckInResult {
        val body = json.encodeToJsonElement(payload).jsonObject.toMutableMap().apply {
            put("encrypt", JsonPrimitive(scannedCode.reveal()))
        }.let(::JsonObject)

        return when (
            val result = transport.postJson(
                AuthenticatedKlasEndpoint.QR_CHECKIN,
                session,
                userAgent,
                body,
            )
        ) {
            is KlasAuthenticatedResult.Success -> mapCheckInResponse(result.body as? JsonObject)
            KlasAuthenticatedResult.SessionExpired -> QrCheckInResult.SessionExpired
            KlasAuthenticatedResult.Timeout -> QrCheckInResult.Timeout
            KlasAuthenticatedResult.NetworkFailure -> QrCheckInResult.NetworkFailure
            is KlasAuthenticatedResult.HttpFailure -> QrCheckInResult.HttpFailure(result.statusCode)
            KlasAuthenticatedResult.EmptyResponse -> QrCheckInResult.EmptyResponse
            KlasAuthenticatedResult.MalformedResponse -> QrCheckInResult.MalformedResponse
        }
    }

    private fun mapCheckInResponse(body: JsonObject?): QrCheckInResult {
        body ?: return QrCheckInResult.MalformedResponse
        val fieldErrors = body["fieldErrors"] ?: return QrCheckInResult.Success
        val errors = fieldErrors as? JsonArray ?: return QrCheckInResult.MalformedResponse
        val messages = errors.mapNotNull { element ->
            (element as? JsonObject)
                ?.get("message")
                ?.let { it as? JsonPrimitive }
                ?.contentOrNull
                ?.trim()
                ?.takeIf(String::isNotEmpty)
        }
        return if (messages.isEmpty()) QrCheckInResult.Success else QrCheckInResult.Rejected(messages)
    }

    private fun JsonObject.toPayload(subjectId: String): QrAttendancePayload? {
        return QrAttendancePayload(
            selectYear = string("thisYear") ?: return null,
            selectHakgi = string("hakgi") ?: return null,
            openMajorCode = string("openMajorCode") ?: return null,
            openGrade = string("openGrade") ?: return null,
            openGwamokNo = string("openGwamokNo") ?: return null,
            bunbanNo = string("bunbanNo") ?: return null,
            gwamokKname = string("gwamokKname") ?: return null,
            codeName1 = string("codeName1") ?: return null,
            hakjumNum = string("hakjumNum") ?: return null,
            sisuNum = string("sisuNum") ?: return null,
            memberName = string("memberName") ?: return null,
            currentNum = string("currentNum") ?: return null,
            yoil = string("yoil") ?: return null,
            subj = subjectId,
        )
    }

    private fun JsonObject.string(key: String): String? =
        (get(key) as? JsonPrimitive)?.contentOrNull

    private fun KlasAuthenticatedResult.toPreparationFailure(): QrPreparationResult = when (this) {
        KlasAuthenticatedResult.SessionExpired -> QrPreparationResult.SessionExpired
        KlasAuthenticatedResult.Timeout -> QrPreparationResult.Timeout
        KlasAuthenticatedResult.NetworkFailure -> QrPreparationResult.NetworkFailure
        is KlasAuthenticatedResult.HttpFailure -> QrPreparationResult.HttpFailure(statusCode)
        KlasAuthenticatedResult.EmptyResponse -> QrPreparationResult.EmptyResponse
        KlasAuthenticatedResult.MalformedResponse -> QrPreparationResult.MalformedResponse
        is KlasAuthenticatedResult.Success -> QrPreparationResult.MalformedResponse
    }
}

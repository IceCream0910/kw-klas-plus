package com.icecream.kwklasplus.core.bridge

enum class BridgeSurface {
    BOARD, HOME, LECTURE_PLAN, LECTURE, LINK_VIEW, SETTINGS, VIDEO,
}

enum class BridgeArgumentType {
    STRING, NULLABLE_STRING, BOOLEAN,
}

data class LegacyBridgeMethod(
    val name: String,
    val arguments: List<BridgeArgumentType> = emptyList(),
    val minimumArgumentCount: Int = arguments.size,
    val synchronousReturn: Boolean = false,
) {
    init {
        require(name.isNotBlank())
        require(minimumArgumentCount in 0..arguments.size)
    }
}

sealed interface BridgeValue {
    data class Text(val value: String) : BridgeValue
    data class BooleanValue(val value: Boolean) : BridgeValue
    data class NumberValue(val value: Double) : BridgeValue {
        init {
            require(value.isFinite())
        }
    }
    data class ObjectValue(val value: Map<String, BridgeValue>) : BridgeValue
    data class ListValue(val value: List<BridgeValue>) : BridgeValue
    data object Null : BridgeValue
}

data class BridgeRequest(
    val version: Int,
    val id: String,
    val method: String,
    val arguments: List<BridgeValue>,
)

enum class BridgeEventId(val wireName: String) {
    SESSION_TOKEN("session.token"),
    DEADLINE_DATA("deadline.data"),
    TIMETABLE_DATA("timetable.data"),
    LECTURE_DATA("lecture.data"),
    BOARD_DATA("board.data"),
    YEAR_SEMESTER("semester.selected"),
    THEME("settings.theme"),
    VERSION("app.version"),
    APP_LOCK_SETTING_CHANGED("settings.appLockChanged"),
    BIOMETRIC_SETTING_CHANGED("settings.biometricChanged"),
    ID_CARD_QR_VALUE("profile.idCardQr"),
    SUBJECT_LIST("lecture.subjectList"),
    CLOSE_WEB_VIEW_BOTTOM_SHEET("modal.close"),
}

data class BridgeEvent(
    val version: Int = BridgeValidator.CURRENT_VERSION,
    val id: String,
    val event: BridgeEventId,
    val payload: BridgeValue = BridgeValue.Null,
) {
    init {
        require(id.isNotBlank() && id.length <= BridgeValidator.MAXIMUM_REQUEST_ID_LENGTH)
    }
}

data class BridgeContext(
    val surface: BridgeSurface,
    val origin: String,
    val isMainFrame: Boolean,
    val payloadSizeBytes: Int,
)

enum class BridgeRejection {
    UNSUPPORTED_VERSION,
    INVALID_REQUEST_ID,
    UNTRUSTED_ORIGIN,
    NOT_MAIN_FRAME,
    PAYLOAD_TOO_LARGE,
    UNKNOWN_METHOD,
    INVALID_ARGUMENT_COUNT,
    INVALID_ARGUMENT_TYPE,
}

sealed interface BridgeValidationResult {
    data class Accepted(val method: LegacyBridgeMethod) : BridgeValidationResult
    data class Rejected(val reason: BridgeRejection) : BridgeValidationResult
}

package com.icecream.kwklasplus.core.notification

import com.icecream.kwklasplus.core.bridge.*

object ReminderBridgeContract {
    val methods=listOf("getNotificationCapabilities","openDeadlineNotificationSettings","getDeadlineNotificationState","setDeadlineNotificationsEnabled")
}
class ReminderBridgeHandler(private val consent: DeadlineNotificationConsent,private val ui: ReminderSettingsUi,private val fallback: BridgeCommandHandler): BridgeCommandHandler {
    override suspend fun handle(command: ValidatedBridgeCommand): BridgeHandlerResult {
        if(command.method.name !in ReminderBridgeContract.methods)return fallback.handle(command)
        val value=when(command.method.name) {
            "getNotificationCapabilities" -> mapOf(
                "available" to BridgeValue.BooleanValue(true),"feature" to BridgeValue.Text("deadlineReminders"),
                "schemaVersion" to BridgeValue.NumberValue(1.0),"consentFlow" to BridgeValue.Text("nativePermissionSheet"),
                "supportedMethods" to BridgeValue.ListValue(ReminderBridgeContract.methods.map(BridgeValue::Text)),
            )
            "getDeadlineNotificationState" -> stateValue(consent.state())
            "setDeadlineNotificationsEnabled" -> stateValue(consent.request((command.arguments.single() as BridgeValue.BooleanValue).value,ui))
            else -> mapOf("opened" to BridgeValue.BooleanValue(consent.request(true,ui).pending))
        }
        return BridgeHandlerResult.Success(BridgeValue.ObjectValue(value))
    }
    private fun stateValue(state: DeadlineConsentState)=mapOf(
        "enabled" to BridgeValue.BooleanValue(state.enabled),"pending" to BridgeValue.BooleanValue(state.pending),
        "permission" to BridgeValue.Text(state.permission),"status" to BridgeValue.Text(state.status),"ready" to BridgeValue.BooleanValue(state.ready),
    )
}

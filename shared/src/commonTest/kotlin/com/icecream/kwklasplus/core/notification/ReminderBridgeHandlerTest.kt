package com.icecream.kwklasplus.core.notification

import com.icecream.kwklasplus.core.bridge.*
import com.icecream.kwklasplus.core.session.Clock
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class ReminderBridgeHandlerTest {
    @Test fun webToggleOpensNativeConsentAndRejectsRemovedCalendarAndPermissionCommands()=runBlocking {
        var opened=0
        val engine=ReminderEngine(object: ReminderStore {
            var raw: String?=null
            override suspend fun read()=raw
            override suspend fun write(value: String) { raw=value }
        },object: ReminderPlatform {
            override fun hash(value: String)=value
            override suspend fun permission(kind: String)="denied"
            override suspend fun postDetailed(id: String,kind: String,generation: Long,additional: Boolean,message: ReminderMessage)=true
            override suspend fun cancel(id: String) {}
            override suspend fun cancelAll() {}
        },ReminderIdentityProvider { ReminderIdentity("fixture","2026,2",true) },object: ReminderDataSource {
            override suspend fun deadlines(term: String)=ReminderSourceResult.Success(emptyList<ReminderDeadline>())
        },Clock { 1000 })
        val router=BridgeRouter(ReminderBridgeHandler(DeadlineNotificationConsent(engine) {},ReminderSettingsUi { opened++;true },BridgeCommandHandler { BridgeHandlerResult.Failure(BridgeErrorCode.UNKNOWN_METHOD) }))
        val context=BridgeContext(BridgeSurface.SETTINGS,"https://klasplus.yuntae.in",true,0)
        suspend fun call(method: String,args: List<BridgeValue> = emptyList())=router.route(BridgeRequest(1,"test",method,args),context)
        val cap=assertIs<BridgeValue.ObjectValue>(assertIs<BridgeResponse.Success>(call("getNotificationCapabilities")).result).value
        assertEquals(BridgeValue.Text("nativePermissionSheet"),cap["consentFlow"])
        assertEquals(0,opened)
        val on=assertIs<BridgeValue.ObjectValue>(assertIs<BridgeResponse.Success>(call("setDeadlineNotificationsEnabled",listOf(BridgeValue.BooleanValue(true)))).result).value
        assertEquals(BridgeValue.BooleanValue(false),on["enabled"]);assertEquals(BridgeValue.BooleanValue(true),on["pending"])
        assertEquals(1,opened);assertFalse(engine.snapshot().deadlineEnabled)
        assertIs<BridgeResponse.Success>(call("getDeadlineNotificationState"))
        assertIs<BridgeResponse.Success>(call("setDeadlineNotificationsEnabled",listOf(BridgeValue.BooleanValue(false))))
        assertEquals(BridgeErrorCode.INVALID_ARGUMENT_TYPE,assertIs<BridgeResponse.Failure>(call("setDeadlineNotificationsEnabled",listOf(BridgeValue.Text("true")))).error)
        for(method in listOf("setCalendarReminder","prepareCalendarReminderMutation","commitCalendarReminderMutation","abortCalendarReminderMutation","requestNotificationPermission","setDeadlineNotificationPreferences","getNotificationState","openNotificationSettings")) {
            assertEquals(BridgeErrorCode.UNKNOWN_METHOD,assertIs<BridgeResponse.Failure>(call(method)).error)
        }
    }
}

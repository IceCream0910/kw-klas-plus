package com.icecream.kwklasplus.core.demo

import com.icecream.kwklasplus.core.notification.*
import com.icecream.kwklasplus.core.session.Clock

object DemoReminderFactory {
    fun create(platform: ReminderPlatform, clock: Clock): ReminderEngine = ReminderEngine(
        object : ReminderStore {
            private var ledger: String? = null
            override suspend fun read(): String? = ledger
            override suspend fun write(value: String) { ledger = value }
        }, object : ReminderPlatform by platform {
            override suspend fun cancelAll() { }
            override suspend fun cancel(id: String) { platform.cancel("demo:$id") }
            override suspend fun postDetailed(id: String, kind: String, generation: Long, additional: Boolean, message: ReminderMessage) = platform.postDetailed("demo:$id", kind, generation, additional, message)
        },
        ReminderIdentityProvider { ReminderIdentity("demo-${DemoSession.generation}", DemoFixtures.term, DemoSession.active && DemoSession.hasSession) },
        object : ReminderDataSource {
            override suspend fun deadlines(term: String): ReminderSourceResult<List<ReminderDeadline>> = ReminderSourceResult.Success(emptyList())
        }, clock,
    )
}

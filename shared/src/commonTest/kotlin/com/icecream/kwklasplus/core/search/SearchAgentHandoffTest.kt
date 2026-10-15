package com.icecream.kwklasplus.core.search

import kotlinx.coroutines.runBlocking
import kotlin.test.*

class SearchAgentHandoffTest {
    private val payload = """{"question":"중간고사 범위","term":"2026,2","references":[{"kind":"notice","title":"시험 안내","courseId":"course","boardNo":"10","masterNo":"20"}]}"""

    @Test fun contextIsOneShotAndWrongIdDoesNotConsumeIt() = runBlocking {
        val id = assertNotNull(SearchAgentHandoff.offer(payload))
        assertEquals("", SearchAgentHandoff.take("other"))
        assertTrue(SearchAgentHandoff.take(id).contains("시험 안내"))
        assertEquals("", SearchAgentHandoff.take(id))
    }
    @Test fun newHandoffAndLogoutInvalidatePriorContext() = runBlocking {
        val old = assertNotNull(SearchAgentHandoff.offer(payload))
        val next = assertNotNull(SearchAgentHandoff.offer(payload))
        assertEquals("", SearchAgentHandoff.take(old))
        SearchAgentHandoff.clear()
        assertEquals("", SearchAgentHandoff.take(next))
    }
    @Test fun unknownFieldsSecretsAndLargeInputsAreRejected() {
        assertNull(SearchAgentHandoff.decode(payload.dropLast(1) + ""","token":"secret"}"""))
        assertNull(SearchAgentHandoff.decode("x".repeat(8193)))
        assertNotNull(SearchAgentHandoff.decode(payload))
    }
}

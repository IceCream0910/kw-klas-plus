package com.icecream.kwklasplus.core.notification

import kotlin.test.*

class DeadlineReminderMessageTest {
    private val now=ReminderTime.parse("2026-10-02 10:00:00")!!
    private fun item(id: String,subject: String="s",name: String="자료구조",kind: String="task",left: Long=2*3_600_000)=ReminderDeadline(id,null,now+left,subject,name,kind)
    @Test fun summarizesSubjectAndKindWithEarliestDeadlineAsSentence() {
        val message=DeadlineReminderMessage.create(listOf(item("a"),item("b",left=4*3_600_000),item("c",kind="onlineLecture")),now,false)
        assertEquals("하루 안에 마감되는 할 일이 3건 있어요",message.title)
        assertTrue(message.body.contains("자료구조 과제 2건"))
        assertTrue(message.body.contains("자료구조 온라인 강의 1건"))
        assertTrue(message.body.endsWith("가장 빠른 마감은 약 2시간 뒤예요."))
    }
    @Test fun usesRoundedMinutesBelowOneHourAndTwoGroupsWithOmittedItemCount() {
        assertEquals("자료구조 과제 1건이 있어요. 약 1분 뒤 마감돼요.",DeadlineReminderMessage.create(listOf(item("a",left=1)),now,false).body)
        assertTrue(DeadlineReminderMessage.create(listOf(item("a",left=60_001)),now,false).body.endsWith("약 2분 뒤 마감돼요."))
        val items=(1..6).map { item("$it",subject="$it",name="과목$it",left=it*3_600_000L) }
        val message=DeadlineReminderMessage.create(items,now,true)
        assertEquals("곧 마감되는 할 일이 6건 더 생겼어요",message.title)
        assertEquals("과목1 과제 1건, 과목2 과제 1건 외 4건이 있어요. 가장 빠른 마감은 약 1시간 뒤예요.",message.body)
        assertEquals(1,message.body.lines().size)
    }
    @Test fun keepsSubjectIdentityAndRemovesInjectedLineBreaksAndDirectionControls() {
        val message=DeadlineReminderMessage.create(listOf(item("a",subject="1",name="같은\n이름\u202e"),item("b",subject="2",name="같은\n이름\u202e")),now,false)
        assertTrue(message.body.startsWith("같은이름 과제 1건, 같은이름 과제 1건이 있어요."))
        assertEquals(1,message.body.lines().size)
        assertFalse(message.body.contains('\u202e'))
    }
    @Test fun longNamesStayShortWithoutSplittingEmojiAndOmittedCountCountsItems() {
        val name="가".repeat(18)+"😀"+"나".repeat(80)
        val items=listOf(item("a",name=name,left=60_000),item("b",subject="2",name="나".repeat(100),left=120_000),item("c",subject="3"),item("d",subject="3"))
        val body=DeadlineReminderMessage.create(items,now,false).body
        assertTrue(body.startsWith("가".repeat(18)+"… 과제 1건"))
        assertTrue(body.contains("외 2건이 있어요."))
        assertTrue(body.length<=140)
        assertFalse(body.any { it.isSurrogate() })
    }
}

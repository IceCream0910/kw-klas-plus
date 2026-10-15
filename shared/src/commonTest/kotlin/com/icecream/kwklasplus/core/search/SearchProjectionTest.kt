package com.icecream.kwklasplus.core.search

import kotlin.test.*
import kotlinx.serialization.json.*

class SearchProjectionTest {
    @Test fun wireAlwaysIncludesVersionEvenBeforeLearningDataArrives() {
        for (batches in listOf(emptyList(), listOf(SearchBatch("course", "과목", "task", "success", 100, emptyList())))) {
            val wire = Json.parseToJsonElement(SearchSnapshot("opaque", "2026,2", 1, batches).encode()).jsonObject
            assertEquals(1, wire.getValue("schemaVersion").jsonPrimitive.int)
            assertEquals(batches.size, wire.getValue("batches").jsonArray.size)
        }
    }
    @Test fun completedAndExpiredAssignmentsRemainSearchableWithoutPrivateFields() {
        val rows = Json.parseToJsonElement("""[{"taskNo":"1","title":"중간고사 범위","submityn":"Y","expiredate":"202001010000","session":"secret","userNm":"private"}]""") as JsonArray
        val batch = SearchProjection.parse("course", "과목", "task", rows, 100)
        assertEquals("success", batch.status)
        assertEquals("complete", batch.items.single().completionState)
        val wire = SearchSnapshot("opaque", "2026,2", 1, listOf(batch)).encode()
        assertFalse(wire.contains("secret"))
        assertFalse(wire.contains("private"))
        assertTrue(wire.contains("202001010000"))
    }
    @Test fun invalidRowsArePartialInsteadOfSuccessfulEmptyReplacement() {
        val batch = SearchProjection.parse("course", "과목", "task", Json.parseToJsonElement("""[{"taskNo":"missing title"}]""") as JsonArray, 100)
        assertEquals("partial", batch.status)
        assertTrue(batch.items.isEmpty())
    }
    @Test fun completedLectureIsIncludedButOtherEvaluationKindsAreExcluded() {
        val batch = SearchProjection.parse("course", "과목", "onlineLecture", Json.parseToJsonElement("""[{"evltnSe":"lesson","lesson":"1","oid":"2","title":"강의","prog":100},{"evltnSe":"quiz"}]""") as JsonArray, 100)
        assertEquals("1/2", batch.items.single().sourceId)
        assertEquals("complete", batch.items.single().completionState)
    }
    @Test fun existingLectureAndProjectDisplayFieldsRemainSearchable() {
        val lecture = SearchProjection.parse("course", "과목", "onlineLecture", Json.parseToJsonElement("""[{"evltnSe":"lesson","lesson":"1","oid":"2","sbjt":"온라인 강의 제목","prog":20}]""") as JsonArray, 100)
        assertEquals("success", lecture.status)
        assertEquals("온라인 강의 제목", lecture.items.single().title)
        val project = SearchProjection.parse("course", "과목", "teamTask", Json.parseToJsonElement("""[{"title":"팀프로젝트","sdates":"2026-10-01","edates":"2026-10-20","submit":"N"}]""") as JsonArray, 100)
        assertEquals("success", project.status)
        assertEquals("incomplete", project.items.single().completionState)
        assertEquals("2026-10-20", project.items.single().endsAt)
        assertTrue(project.items.single().sourceId.startsWith("metadata:"))
    }
}

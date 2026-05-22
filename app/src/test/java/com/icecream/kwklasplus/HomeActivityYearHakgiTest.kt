package com.icecream.kwklasplus

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeActivityYearHakgiTest {

    @Test
    fun `findYearHakgiIndex returns matched semester index`() {
        val semesters = JSONArray()
            .put(JSONObject().put("value", "2026,3"))
            .put(JSONObject().put("value", "2026,1"))

        val index = findYearHakgiIndex(semesters, "2026,1")

        assertEquals(1, index)
    }

    @Test
    fun `findYearHakgiIndex falls back to first semester when selected not found`() {
        val semesters = JSONArray()
            .put(JSONObject().put("value", "2026,3"))
            .put(JSONObject().put("value", "2026,1"))

        val index = findYearHakgiIndex(semesters, "2025,2")

        assertEquals(0, index)
    }

    @Test
    fun `findYearHakgiIndex returns negative one when semester list empty`() {
        val index = findYearHakgiIndex(JSONArray(), "2026,1")

        assertEquals(-1, index)
    }
}

package com.icecream.kwklasplus.core.platform

import kotlin.test.Test
import kotlin.test.assertEquals

class FilePickerRequestTest {
    @Test
    fun normalizesMimeTypesWithoutChangingOrder() {
        assertEquals(
            listOf("image/*", "application/pdf"),
            FilePickerRequest(
                listOf(" image/* ", "", "invalid", "application/pdf", "image/*"),
            ).normalizedMimeTypes(),
        )
    }

    @Test
    fun rejectsControlCharacters() {
        assertEquals(
            emptyList(),
            FilePickerRequest(listOf("text/plain\napplication/pdf")).normalizedMimeTypes(),
        )
    }
}

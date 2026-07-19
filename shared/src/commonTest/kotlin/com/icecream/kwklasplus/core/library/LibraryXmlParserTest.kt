package com.icecream.kwklasplus.core.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LibraryXmlParserTest {
    private val parser = LibraryXmlParser()

    @Test
    fun extractsSecretAndAuthKeyFromNestedResponse() {
        val xml = """<?xml version="1.0"?><response><sec_key>0123456789abcdef</sec_key><auth_key>a&amp;b</auth_key></response>"""

        assertEquals("0123456789abcdef", parser.parseValue(xml, "sec_key"))
        assertEquals("a&b", parser.parseValue(xml, "auth_key"))
    }

    @Test
    fun mapsQrFieldsWithLegacyLastValueWinsBehavior() {
        val xml = """<response><name>홍길동</name><qr_code>first</qr_code><qr_code>second</qr_code></response>"""

        assertEquals(
            mapOf("name" to "홍길동", "qr_code" to "second"),
            parser.parseFlatValues(xml),
        )
    }

    @Test
    fun extractsLibraryValuesWrappedInCdata() {
        val xml = """<response><!-- library --><sec_key><![CDATA[0123456789abcdef]]></sec_key><auth_key><![CDATA[a&b]]></auth_key><qr_code><![CDATA[qr-value]]></qr_code></response>"""

        assertEquals("0123456789abcdef", parser.parseValue(xml, "sec_key"))
        assertEquals("a&b", parser.parseValue(xml, "auth_key"))
        assertEquals("qr-value", parser.parseValue(xml, "qr_code"))
    }

    @Test
    fun rejectsMalformedOrEntityDeclaringXml() {
        assertTrue(parser.parseFlatValues("<response><name>open</response>").isEmpty())
        assertTrue(parser.parseFlatValues("<response><name><![CDATA[open</name></response>").isEmpty())
        assertTrue(parser.parseFlatValues("<response><!-- open</response>").isEmpty())
        assertTrue(parser.parseFlatValues("<!DOCTYPE x [<!ENTITY secret SYSTEM 'file:///x'>]><x>&secret;</x>").isEmpty())
        assertNull(parser.parseValue("<response/>", "auth_key"))
    }
}

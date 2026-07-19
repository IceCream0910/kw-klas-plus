package com.icecream.kwklasplus.core.library

class LibraryXmlParser {
    fun parseValue(xml: String, tag: String): String? = parseFlatValues(xml)[tag]

    fun parseFlatValues(xml: String): Map<String, String> {
        if (xml.contains("<!DOCTYPE", ignoreCase = true) || xml.contains("<!ENTITY", ignoreCase = true)) {
            return emptyMap()
        }

        val values = linkedMapOf<String, String>()
        val tagStack = mutableListOf<String>()
        var position = 0
        while (position < xml.length) {
            val tagStart = xml.indexOf('<', position)
            if (tagStart < 0) break
            if (tagStart > position && tagStack.isNotEmpty()) {
                putValue(values, tagStack.last(), decodeEntities(xml.substring(position, tagStart)))
            }
            if (xml.startsWith("<![CDATA[", tagStart)) {
                val cdataEnd = xml.indexOf("]]>", tagStart + CDATA_PREFIX.length)
                if (cdataEnd < 0 || tagStack.isEmpty()) return emptyMap()
                putValue(
                    values,
                    tagStack.last(),
                    xml.substring(tagStart + CDATA_PREFIX.length, cdataEnd),
                )
                position = cdataEnd + MARKUP_SUFFIX_LENGTH
                continue
            }
            if (xml.startsWith("<!--", tagStart)) {
                val commentEnd = xml.indexOf("-->", tagStart + COMMENT_PREFIX.length)
                if (commentEnd < 0) return emptyMap()
                position = commentEnd + MARKUP_SUFFIX_LENGTH
                continue
            }
            val tagEnd = xml.indexOf('>', tagStart + 1)
            if (tagEnd < 0) return emptyMap()
            val token = xml.substring(tagStart + 1, tagEnd).trim()
            when {
                token.startsWith("?") || token.startsWith("!") -> Unit
                token.startsWith("/") -> {
                    val closingName = token.drop(1).substringBefore(' ').trim()
                    if (tagStack.lastOrNull() != closingName) return emptyMap()
                    tagStack.removeAt(tagStack.lastIndex)
                }
                token.endsWith("/") -> Unit
                else -> {
                    val name = token.substringBefore(' ').trim()
                    if (!isValidTagName(name)) return emptyMap()
                    tagStack += name
                }
            }
            position = tagEnd + 1
        }
        return if (tagStack.isEmpty()) values else emptyMap()
    }

    private fun putValue(values: MutableMap<String, String>, tag: String, value: String) {
        if (value.isBlank()) return
        values[tag] = value
    }

    private fun isValidTagName(name: String): Boolean =
        name.isNotEmpty() && name.first().let { it.isLetter() || it == '_' } &&
            name.all { it.isLetterOrDigit() || it == '_' || it == '-' || it == '.' || it == ':' }

    private fun decodeEntities(value: String): String = value
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")

    private companion object {
        const val CDATA_PREFIX = "<![CDATA["
        const val COMMENT_PREFIX = "<!--"
        const val MARKUP_SUFFIX_LENGTH = 3
    }
}

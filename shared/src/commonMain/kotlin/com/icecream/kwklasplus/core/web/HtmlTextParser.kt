package com.icecream.kwklasplus.core.web

class HtmlTextParser {
    fun title(html: String): String? = TITLE_PATTERN.find(html)
        ?.groupValues
        ?.get(1)
        ?.let(::plainText)
        ?.takeIf(String::isNotBlank)

    fun plainText(html: String): String = decodeEntities(
        TAG_PATTERN.replace(html, " "),
    ).replace(WHITESPACE_PATTERN, " ").trim()

    private fun decodeEntities(value: String): String = value
        .replace("&lt;", "<", ignoreCase = true)
        .replace("&gt;", ">", ignoreCase = true)
        .replace("&quot;", "\"", ignoreCase = true)
        .replace("&#39;", "'", ignoreCase = true)
        .replace("&apos;", "'", ignoreCase = true)
        .replace("&nbsp;", " ", ignoreCase = true)
        .replace("&amp;", "&", ignoreCase = true)

    private companion object {
        val TITLE_PATTERN = Regex("""<title\b[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val TAG_PATTERN = Regex("""<[^>]*>""")
        val WHITESPACE_PATTERN = Regex("""\s+""")
    }
}

package com.icecream.kwklasplus.core.academic

import kotlinx.serialization.Serializable

@Serializable
data class TimetableThemeColor(
    val background: String,
    val text: String,
)

@Serializable
data class TimetableColor(
    val palette: String,
    val slot: Int,
    val light: TimetableThemeColor,
    val dark: TimetableThemeColor,
)

data class TimetablePalette(
    val id: String,
    val light: List<TimetableThemeColor>,
    val dark: List<TimetableThemeColor>,
) {
    init {
        require(light.isNotEmpty() && light.size == dark.size)
    }
}

object TimetableColorPolicy {
    val defaultPalette = TimetablePalette(
        id = "default-v1",
        light = listOf(
            TimetableThemeColor("#E0F4E8", "#16633D"),
            TimetableThemeColor("#E5EEFC", "#28569A"),
            TimetableThemeColor("#FFEFCF", "#805000"),
            TimetableThemeColor("#F2E6FC", "#713D92"),
            TimetableThemeColor("#FCE6EC", "#9C344B"),
            TimetableThemeColor("#DDF3F4", "#00656E"),
            TimetableThemeColor("#E8EAFB", "#3949AB"),
            TimetableThemeColor("#F3E9E2", "#7A4328"),
        ),
        dark = listOf(
            TimetableThemeColor("#103822", "#54E995"),
            TimetableThemeColor("#192C48", "#91B9FF"),
            TimetableThemeColor("#3B2C12", "#FFC773"),
            TimetableThemeColor("#31203F", "#D3A7F5"),
            TimetableThemeColor("#41222B", "#FF9EB4"),
            TimetableThemeColor("#16383C", "#78DBE4"),
            TimetableThemeColor("#292B55", "#B8C0FF"),
            TimetableThemeColor("#3A2921", "#F2B58E"),
        ),
    )

    fun slot(title: String, palette: TimetablePalette = defaultPalette): Int =
        (normalize(title).hashCode() and Int.MAX_VALUE) % palette.light.size

    fun resolve(title: String, palette: TimetablePalette = defaultPalette): TimetableColor {
        val slot = slot(title, palette)
        return TimetableColor(palette.id, slot, palette.light[slot], palette.dark[slot])
    }

    fun assign(titles: Iterable<String>, palette: TimetablePalette = defaultPalette): Map<String, TimetableColor> {
        val normalizedTitles = titles.map(::normalize).filter(String::isNotEmpty).distinct()
            .sortedWith(compareBy({ slot(it, palette) }, { it }))
        val usage = IntArray(palette.light.size)
        return buildMap {
            normalizedTitles.forEach { title ->
                val start = slot(title, palette)
                val minimumUsage = usage.min()
                val assigned = (0 until palette.light.size).asSequence()
                    .map { (start + it) % palette.light.size }
                    .first { usage[it] == minimumUsage }
                usage[assigned]++
                put(title, TimetableColor(
                    palette.id, assigned, palette.light[assigned], palette.dark[assigned],
                ))
            }
        }
    }

    fun assignedColor(title: String, assignments: Map<String, TimetableColor>, palette: TimetablePalette = defaultPalette) =
        assignments[normalize(title)] ?: resolve(title, palette)

    fun normalize(title: String) = title.trim().replace(Regex("\\s+"), " ")
}

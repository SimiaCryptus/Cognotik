package com.simiacryptus.cognotik.webui.servlet

import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Properties

/**
 * Textual metadata for a gift theme. All visual styling lives in /gifts/gifts.css,
 * keyed on the `data-gift-theme` attribute; all text lives in /gifts/themes.properties.
 */
data class GiftTheme(
    val id: String,
    val displayName: String,
    val emoji: String,
    val bannerContent: String,
    val dividerContent: String,
    val title: String,
    val subtitle: String
)

object GiftThemes {
    private const val RESOURCE = "/gifts/themes.properties"

    private val props: Properties by lazy {
        val stream = GiftThemes::class.java.getResourceAsStream(RESOURCE)
            ?: throw IllegalStateException("Missing resource $RESOURCE")
        InputStreamReader(stream, StandardCharsets.UTF_8).use { reader ->
            Properties().apply { load(reader) }
        }
    }

    val ALL: List<GiftTheme> by lazy {
        props.getProperty("themes", "")
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { id ->
                fun p(key: String) = props.getProperty("$id.$key", "")
                GiftTheme(
                    id = id,
                    displayName = p("displayName").ifBlank { id },
                    emoji = p("emoji"),
                    bannerContent = p("banner"),
                    dividerContent = p("divider"),
                    title = p("title"),
                    subtitle = p("subtitle")
                )
            }
            .also { require(it.isNotEmpty()) { "No themes defined in $RESOURCE" } }
    }

    val DEFAULT: GiftTheme by lazy {
        ALL.firstOrNull { it.id == props.getProperty("default") } ?: ALL.first()
    }

    fun byId(id: String?): GiftTheme = ALL.firstOrNull { it.id == id } ?: DEFAULT
}
package com.ziaee.frenchreader.resources

import androidx.annotation.StringRes
import com.ziaee.frenchreader.R

enum class ResourceCategory(
    val key: String,
    @StringRes val labelRes: Int
) {
    PODCASTS("podcasts", R.string.resource_category_podcasts),
    READING("reading", R.string.resource_category_reading),
    VIDEO("video", R.string.resource_category_video),
    BOOKS("books", R.string.resource_category_books),
    OTHER("other", R.string.resource_category_other);

    companion object {
        fun fromKey(key: String?): ResourceCategory =
            entries.firstOrNull { it.key == key } ?: OTHER
    }
}

data class DefaultResource(
    val title: String,
    val url: String,
    val category: ResourceCategory,
    val createdAtMs: Long
)

val DEFAULT_RESOURCES = listOf(
    DefaultResource(
        title = "Fabulang",
        url = "https://www.fabulang.com/en/fr/",
        category = ResourceCategory.READING,
        createdAtMs = 0
    ),
    DefaultResource(
        title = "InnerFrench",
        url = "https://innerfrench.com/podcast/",
        category = ResourceCategory.PODCASTS,
        createdAtMs = 1
    ),
    DefaultResource(
        title = "Journal en français facile (RFI)",
        url = "https://francaisfacile.rfi.fr/fr/podcasts/journal-en-fran%C3%A7ais-facile/",
        category = ResourceCategory.PODCASTS,
        createdAtMs = 1
    ),
    DefaultResource(
        title = "Coffee Break French",
        url = "https://coffeebreakfrench.com/",
        category = ResourceCategory.PODCASTS,
        createdAtMs = 1
    ),
    DefaultResource(
        title = "Français Authentique",
        url = "https://www.francaisauthentique.com/podcast/",
        category = ResourceCategory.PODCASTS,
        createdAtMs = 1
    )
)

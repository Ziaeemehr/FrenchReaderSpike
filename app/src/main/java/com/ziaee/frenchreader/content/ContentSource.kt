package com.ziaee.frenchreader.content

/** One search hit from any content source (Vikidia, a news source, and
 * whatever ROADMAP.md section 6 adds later): enough to show a result row.
 * [ref] is an opaque per-source reference passed back to [ContentSource.fetchArticle]
 * to actually download the full text -- a Vikidia page id, a news item's
 * article URL, etc. Never interpreted outside the source that produced it. */
data class ContentResult(
    val sourceId: String,
    val sourceLabel: String,
    val title: String,
    val snippet: String,
    val lengthHint: String,
    val ref: String,
    val publishedAtMs: Long? = null
)

/** A fetched full article, ready to become a TextDocument -- see
 * ROADMAP.md section 6's attribution policy for what each field means
 * per source type. */
data class ContentArticle(
    val title: String,
    val text: String,
    val sourceUrl: String,
    val sourceName: String,
    val author: String?,
    val license: String?,
    val publishedAtMs: Long?
)

/** One pluggable place to find reading material -- see ROADMAP.md section 6.
 * The "پیدا کردن مطلب" search sheet queries every registered ContentSource
 * in parallel and merges the results. */
interface ContentSource {
    val id: String
    val label: String
    suspend fun search(query: String, limit: Int = 10): List<ContentResult>
    suspend fun fetchArticle(result: ContentResult): ContentArticle?
}

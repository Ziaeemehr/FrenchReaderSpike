package com.ziaee.frenchreader.watch

internal data class CachedCaptions(val captions: VideoCaptions, val version: Int?, val source: String?)
internal data class CaptionCacheDecision(val captions: VideoCaptions, val source: String, val write: Boolean)

/** Only a missing entry is fetched: a legacy entry without provenance may be an imported file and is never replaced. */
internal fun shouldFetchCaptions(cached: CachedCaptions?): Boolean = cached == null

/** Null [fetched] means fetching failed. Legacy cues are kept and rewritten as version 2 with "file" provenance. */
internal fun chooseCaptions(cached: CachedCaptions?, fetched: VideoCaptions?): CaptionCacheDecision? = when {
    cached?.version == 2 -> CaptionCacheDecision(cached.captions, cached.source ?: "file", false)
    cached != null -> CaptionCacheDecision(cached.captions, "file", true)
    fetched != null && fetched.cues.isNotEmpty() -> CaptionCacheDecision(fetched, "youtube", true)
    fetched != null -> CaptionCacheDecision(fetched, "youtube", false)
    else -> null
}

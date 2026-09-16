package com.ziaee.frenchreader.news

/**
 * A preset RSS feed offered by the "دریافت خبر امروز" (fetch today's news)
 * button on the texts list. See ROADMAP.md section 1 for the full design.
 *
 * Both feed URLs were tracked down and hand-verified as real, currently
 * published feeds (Sep 2026), but this sandbox's network access is itself
 * restricted, so *what the feed's <description> actually contains* could
 * not be confirmed end-to-end from here -- that needs a real on-device
 * fetch. Two things worth knowing before relying on either:
 *
 * - FRANCE_INFO: franceinfo's own general "titres" (headlines) feed --
 *   confirmed as a real, non-paywalled outlet. The original plan also
 *   considered Le Monde for this slot, but most Le Monde articles only
 *   give a free lead paragraph (subscriber paywall beyond that), which
 *   would defeat the point of a "full article" source -- franceinfo is a
 *   public broadcaster with no such paywall, so it took Le Monde's place.
 * - RFI_FACILE: RFI's "Journal en français facile" is fundamentally an
 *   audio bulletin. Its feed URL had to be resolved indirectly (via Apple
 *   Podcasts' public lookup API, since RFI's own site doesn't link it
 *   obviously), and it's technically a *podcast* feed -- whether its
 *   <description> carries the full simplified-French write-up or just a
 *   short episode blurb wasn't verifiable from here. If it turns out to be
 *   too short for reading practice, this is the one to swap out first.
 */
enum class NewsSource(val id: String, val label: String, val feedUrl: String) {
    RFI_FACILE(
        id = "rfi_facile",
        label = "RFI – فرانسهٔ ساده",
        feedUrl = "https://apis.fle.rfi.fr/products/get_product/fle_getpodcast_by_nid_author_rfi" +
            "?token_application=applepodcast_fle&program.entrepriseId=WBMZ39-FLE-FR-20220627"
    ),
    FRANCE_INFO(
        id = "france_info",
        label = "France Info – خبر کامل",
        feedUrl = "https://www.francetvinfo.fr/titres.rss"
    );

    companion object {
        fun fromId(id: String?): NewsSource? = entries.find { it.id == id }
    }
}

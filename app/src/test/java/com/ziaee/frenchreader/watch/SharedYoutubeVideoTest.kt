package com.ziaee.frenchreader.watch

import org.junit.Assert.*
import org.junit.Test

class SharedYoutubeVideoTest {
    private val id = "dQw4w9WgXcQ"

    @Test fun acceptsYoutubeAppTitleAndLink() {
        assertEquals(id, sharedYoutubeVideoId("Title https://youtu.be/$id?si=xyz"))
    }

    @Test fun acceptsPlainUrlAndShorts() {
        assertEquals(id, sharedYoutubeVideoId("  https://www.youtube.com/watch?v=$id \n"))
        assertEquals(id, sharedYoutubeVideoId("https://youtube.com/shorts/$id"))
    }

    @Test fun rejectsLongArticlesAndTwoLinks() {
        assertNull(sharedYoutubeVideoId("Article ".repeat(70) + "https://youtu.be/$id"))
        assertNull(sharedYoutubeVideoId("https://youtu.be/$id https://example.com"))
        assertNull(sharedYoutubeVideoId("https://youtu.be/$id https://youtu.be/$id"))
    }

    @Test fun rejectsNonYoutubeTextAndTrailingProse() {
        listOf("", id, "youtu.be/$id", "https://example.com/$id", "https://youtu.be/$id more text")
            .forEach { assertNull(it, sharedYoutubeVideoId(it)) }
    }

    @Test fun enforcesTrimmedLengthLimit() {
        val url = "https://youtu.be/$id"
        val accepted = "a".repeat(498 - url.length) + " " + url
        assertEquals(499, accepted.length)
        assertEquals(id, sharedYoutubeVideoId("  $accepted  "))
        assertNull(sharedYoutubeVideoId("a$accepted"))
    }
}

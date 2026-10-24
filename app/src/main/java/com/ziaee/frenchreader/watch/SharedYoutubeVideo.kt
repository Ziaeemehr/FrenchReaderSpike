package com.ziaee.frenchreader.watch

private val sharedUrlPattern = Regex("https?://\\S+")

/** A URL alone or the title followed by a URL shared by the YouTube app. */
fun sharedYoutubeVideoId(text: String): String? {
    val value = text.trim()
    if (value.length >= 500) return null
    val url = sharedUrlPattern.findAll(value).singleOrNull() ?: return null
    if (url.range.last != value.lastIndex) return null
    return YoutubeUrl.videoId(url.value)
}

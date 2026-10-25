package com.ziaee.frenchreader.watch

import java.net.URI
import java.net.URLDecoder

object YoutubeUrl {
    private val idPattern = Regex("[A-Za-z0-9_-]{11}")

    fun videoId(input: String): String? {
        val value = input.trim()
        if (idPattern.matches(value)) return value
        return runCatching {
            val uri = URI(if ("://" in value) value else "https://$value")
            if (uri.scheme !in listOf("http", "https")) return null
            val host = uri.host?.lowercase() ?: return null
            val segments = uri.path.orEmpty().trim('/').split('/')
            val id = when (host) {
                "youtu.be", "www.youtu.be" -> segments.singleOrNull()
                "youtube.com", "www.youtube.com", "m.youtube.com", "youtube-nocookie.com", "www.youtube-nocookie.com" -> {
                    if (segments.firstOrNull() in listOf("shorts", "embed", "live") && segments.size == 2) segments[1]
                    else if (uri.path == "/watch") uri.rawQuery.orEmpty().split('&').firstNotNullOfOrNull { pair ->
                        val parts = pair.split('=', limit = 2)
                        if (parts.size == 2 && parts[0] == "v") URLDecoder.decode(parts[1], "UTF-8") else null
                    } else null
                }
                else -> null
            }
            id?.takeIf(idPattern::matches)
        }.getOrNull()
    }

    fun watchUrl(videoId: String): String = "https://www.youtube.com/watch?v=$videoId"
}

package com.ziaee.frenchreader.resources

/**
 * Localized description of a built-in resource. Kept in its own file with no top-level state: when it lived
 * next to DEFAULT_RESOURCES, reading YOUTUBE_CHANNEL_RESOURCES first (as the video picker does) ran that
 * file's initializer mid-way and DEFAULT_RESOURCES saw the YouTube list as null.
 */
internal fun d(en: String, fr: String, fa: String) = mapOf("en" to en, "fr" to fr, "fa" to fa)

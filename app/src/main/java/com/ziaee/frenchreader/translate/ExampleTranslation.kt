package com.ziaee.frenchreader.translate

/** Lazy callbacks keep bundled translations independent of engine setup and network access. */
internal suspend fun resolveExampleTranslation(
    override: () -> String?, bundled: () -> String?, personal: () -> String?,
    live: suspend () -> String?, google: suspend () -> String
): String = override() ?: personal() ?: bundled() ?: live() ?: google()

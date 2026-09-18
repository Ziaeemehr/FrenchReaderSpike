package com.ziaee.frenchreader.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ProcessTextAppsTest {
    @Test
    fun `filterAndSortProcessTextApps drops entries matching own package`() {
        val apps = listOf(
            ProcessTextApp("Reverso Context", "com.reverso.context", "MainActivity", "Reverso Context"),
            ProcessTextApp("Add to French Reader", "com.ziaee.frenchreader", "ProcessTextAlias", "French Reader")
        )

        val result = filterAndSortProcessTextApps(apps, ownPackageName = "com.ziaee.frenchreader")

        assertEquals(listOf("Reverso Context"), result.map { it.label })
    }

    @Test
    fun `filterAndSortProcessTextApps sorts remaining entries by label case-insensitively`() {
        val apps = listOf(
            ProcessTextApp("reverso Context", "com.reverso.context", "MainActivity", "Reverso"),
            ProcessTextApp("Ask Claude", "com.anthropic.claude", "MainActivity", "Claude"),
            ProcessTextApp("Anki Card", "com.ichi2.anki", "MainActivity", "AnkiDroid")
        )

        val result = filterAndSortProcessTextApps(apps, ownPackageName = "com.ziaee.frenchreader")

        assertEquals(listOf("Anki Card", "Ask Claude", "reverso Context"), result.map { it.label })
    }

    @Test
    fun `filterAndSortProcessTextApps disambiguates entries that share the same label with their app name`() {
        val apps = listOf(
            ProcessTextApp("Translate", "com.samsung.android.app.interpreter", "TranslationActivity", "Bixby Interpreter"),
            ProcessTextApp("Translate", "com.google.android.apps.translate", "TapToTranslateActivity", "Translate"),
            ProcessTextApp("Ask Claude", "com.anthropic.claude", "MainActivity", "Claude")
        )

        val result = filterAndSortProcessTextApps(apps, ownPackageName = "com.ziaee.frenchreader")

        assertEquals(
            listOf("Ask Claude", "Translate (Bixby Interpreter)", "Translate (Translate)"),
            result.map { it.label }
        )
    }
}

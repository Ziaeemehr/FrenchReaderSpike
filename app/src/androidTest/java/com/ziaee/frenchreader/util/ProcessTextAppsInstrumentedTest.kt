package com.ziaee.frenchreader.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Regression guard for the `<queries>` package-visibility declaration in
 * AndroidManifest.xml -- without it, Android 11+ hides almost every other
 * app from an ACTION_PROCESS_TEXT PackageManager query regardless of what's
 * actually installed (this app briefly shipped without it and only ever saw
 * ~2 apps on a real device that has many more PROCESS_TEXT handlers). */
@RunWith(AndroidJUnit4::class)
class ProcessTextAppsInstrumentedTest {
    @Test
    fun queryProcessTextAppsSeesMoreThanAHandfulOnARealDevice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val apps = queryProcessTextApps(context)
        assertTrue(
            "expected more than 2 PROCESS_TEXT handlers, got ${apps.size}: ${apps.map { it.label }}",
            apps.size > 2
        )
    }
}

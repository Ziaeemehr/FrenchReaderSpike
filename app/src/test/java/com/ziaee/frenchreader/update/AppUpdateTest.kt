package com.ziaee.frenchreader.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateTest {
    @Test
    fun comparesVersionsNumericallyWithOptionalPrefixAndMissingParts() {
        assertTrue(isNewerVersion("v0.9.0", "0.8.0"))
        assertTrue(isNewerVersion("0.10.0", "0.9.9"))
        assertTrue(isNewerVersion("1.0", "0.99.1"))
        assertFalse(isNewerVersion("v0.8.0", "0.8.0"))
        assertFalse(isNewerVersion("0.8", "0.8.0"))
        assertFalse(isNewerVersion("0.7.5", "0.8.0"))
        assertFalse(isNewerVersion("latest", "0.8.0"))
        assertTrue(isNewerVersion("0.9.0-beta", "0.8.0"))
    }

    @Test
    fun parsesLatestReleaseAndIgnoresDraftsAndPrereleases() {
        val json = """{"tag_name":"v0.9.0","body":"## New\n- **Find** in text","html_url":"https://github.com/x/y/releases/tag/v0.9.0","draft":false,"prerelease":false}"""
        val release = parseLatestRelease(json)!!
        assertEquals("0.9.0", release.version)
        assertEquals("New\n- Find in text", plainReleaseNotes(release.notes))
        assertNull(parseLatestRelease(json.replace("\"prerelease\":false", "\"prerelease\":true")))
        assertNull(parseLatestRelease("""{"tag_name":"nightly"}"""))
        assertEquals("- Import menu", plainReleaseNotes("## What's new\n- Import menu"))
    }

    @Test
    fun autoCheckRunsAtMostDailyAndRespectsSnoozeAndSetting() {
        val day = 24L * 60 * 60 * 1000
        assertTrue(shouldAutoCheck(true, nowMs = 10 * day, lastCheckMs = 0, snoozedUntilMs = 0))
        assertFalse(shouldAutoCheck(true, nowMs = 10 * day, lastCheckMs = 10 * day - 1000, snoozedUntilMs = 0))
        assertFalse(shouldAutoCheck(true, nowMs = 10 * day, lastCheckMs = 0, snoozedUntilMs = 11 * day))
        assertFalse(shouldAutoCheck(false, nowMs = 10 * day, lastCheckMs = 0, snoozedUntilMs = 0))
    }

    @Test
    fun skippedVersionIsNotPromptedButANewerOneIs() {
        val release = ReleaseInfo("0.9.0", "", "")
        assertTrue(shouldPrompt(release, "0.8.0", skippedVersion = null))
        assertFalse(shouldPrompt(release, "0.8.0", skippedVersion = "0.9.0"))
        assertTrue(shouldPrompt(release.copy(version = "0.9.1"), "0.8.0", skippedVersion = "0.9.0"))
        assertFalse(shouldPrompt(release, "0.9.0", skippedVersion = null))
    }
}

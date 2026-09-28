package com.ziaee.frenchreader

import org.junit.Assert.assertEquals
import org.junit.Test

class StartupLanguageActionTest {
    @Test
    fun `configured installs continue without migration or onboarding`() {
        assertEquals(
            StartupLanguageAction.CONTINUE,
            startupLanguageAction(isConfigured = true, hasLegacyData = false),
        )
        assertEquals(
            StartupLanguageAction.CONTINUE,
            startupLanguageAction(isConfigured = true, hasLegacyData = true),
        )
    }

    @Test
    fun `unconfigured legacy installs migrate silently`() {
        assertEquals(
            StartupLanguageAction.MIGRATE_LEGACY,
            startupLanguageAction(isConfigured = false, hasLegacyData = true),
        )
    }

    @Test
    fun `unconfigured fresh installs show onboarding`() {
        assertEquals(
            StartupLanguageAction.SHOW_ONBOARDING,
            startupLanguageAction(isConfigured = false, hasLegacyData = false),
        )
    }
}

package io.github.ggrzesiek.doomstop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Detection rules are the fragile part of the app (they depend on Messenger's UI labels),
 * so they are covered by plain JVM tests that run in CI on every push.
 */
class ConfigTest {

    // ---------- Layer 1: the Facebook entry button in Messenger ----------

    @Test
    fun `entry button labels observed on real devices match`() {
        assertTrue(Config.matchesEntry("Aplikacja Facebook")) // Messenger PL, Galaxy S24, Oct 2026
        assertTrue(Config.matchesEntry("Facebook"))
        assertTrue(Config.matchesEntry("facebook app"))
        assertTrue(Config.matchesEntry("  Open Facebook  "))
    }

    @Test
    fun `chat content mentioning Facebook does not match the entry button`() {
        assertFalse(Config.matchesEntry("Widziałeś to na Facebooku?"))
        assertFalse(Config.matchesEntry("Facebook Marketplace link"))
        assertFalse(Config.matchesEntry(""))
    }

    // ---------- Layer 2a: Facebook opened in Messenger's in-app browser ----------

    @Test
    fun `facebook hosts shown in the in-app browser bar match`() {
        assertTrue(Config.isFbHost("m.facebook.com"))
        assertTrue(Config.isFbHost("www.facebook.com"))
        assertTrue(Config.isFbHost("facebook.com"))
        assertTrue(Config.isFbHost("web.facebook.com"))
        assertTrue(Config.isFbHost("fb.watch"))
    }

    @Test
    fun `full links and look-alike domains do not match`() {
        assertFalse(Config.isFbHost("https://facebook.com/some.post")) // a link sent in a chat
        assertFalse(Config.isFbHost("see facebook.com"))
        assertFalse(Config.isFbHost("notfacebook.com"))
        assertFalse(Config.isFbHost("facebook.com.evil.example"))
        assertFalse(Config.isFbHost("Messenger"))
    }

    @Test
    fun `browser close button labels are normalized`() {
        assertTrue(Config.norm("  Close ") in Config.BROWSER_CLOSE_LABELS)
        assertTrue(Config.norm("Zamknij") in Config.BROWSER_CLOSE_LABELS)
        assertFalse(Config.norm("More options") in Config.BROWSER_CLOSE_LABELS)
    }

    // ---------- Layer 2b: native feed markers ----------

    @Test
    fun `feed markers require the whole group to be visible`() {
        val feedScreen = setOf("lubię to!", "komentarz", "udostępnij", "jan kowalski")
        val chatScreen = setOf("udostępnij", "jan kowalski", "napisz wiadomość")

        assertTrue(Config.FEED_MARKER_GROUPS.any { feedScreen.containsAll(it) })
        assertFalse(Config.FEED_MARKER_GROUPS.any { chatScreen.containsAll(it) })
    }

    @Test
    fun `norm trims and lowercases`() {
        assertEquals("lubię to!", Config.norm("  Lubię to! "))
    }
}

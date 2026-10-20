package io.github.ggrzesiek.doomstop

/**
 * Everything that may need tuning for a specific phone lives here.
 * Meta changes Messenger's UI, so labels differ between app versions and languages.
 * Turn on debug mode in the app and inspect the real labels with:  adb logcat -s DoomStop
 */
object Config {
    const val TAG = "DoomStop"

    const val MESSENGER = "com.facebook.orca"
    val FB_APPS = setOf("com.facebook.katana", "com.facebook.lite")

    /**
     * Layer 1: label (contentDescription or text) of Messenger's "go to Facebook" button.
     * Matches the WHOLE label, case-insensitively, so chat rows that merely mention
     * "Facebook" do not trigger it.
     */
    private val ENTRY_LABEL = Regex(
        "^(facebook|aplikacja facebook|facebook app|open facebook|go to facebook|otwórz facebooka|przejdź do facebooka)$",
        RegexOption.IGNORE_CASE,
    )

    fun matchesEntry(label: CharSequence): Boolean = ENTRY_LABEL.matches(label.toString().trim())

    /**
     * Layer 2a: Messenger's in-app browser (where the Facebook button leads).
     * Its bar has a node with ONLY the host (e.g. "m.facebook.com") and a "Close" button next to it.
     * Both are required, so a Facebook link sent in a chat does not trigger the block.
     */
    private val FB_HOST = Regex(
        "^(?:[a-z0-9-]+\\.)*(?:facebook\\.com|fb\\.com|fb\\.watch)$",
        RegexOption.IGNORE_CASE,
    )

    fun isFbHost(label: CharSequence): Boolean = FB_HOST.matches(label.toString().trim())

    val BROWSER_CLOSE_LABELS = setOf("close", "zamknij", "close browser", "zamknij przeglądarkę")

    /**
     * Layer 2b: a native Facebook screen inside Messenger is recognised when ALL labels of one group
     * are visible at the same time (the action bar under a post or reel). A single word is not enough,
     * someone could simply type it in a chat.
     * Exact comparison after normalisation (trim + lowercase).
     */
    val FEED_MARKER_GROUPS: List<Set<String>> = listOf(
        setOf("lubię to!", "komentarz", "udostępnij"),
        setOf("like", "comment", "share"),
    )

    fun norm(cs: CharSequence): String = cs.toString().trim().lowercase()

    // Timings (ms)
    const val ARM_WINDOW_MS = 3_000L        // how long after the tap a transition counts as "from Messenger"
    const val IN_APP_BACK_DELAY_MS = 900L   // close after this delay if we are still in Messenger
    const val SCAN_DELAY_MS = 600L          // screen scan throttling
    const val ACTION_COOLDOWN_MS = 1_500L   // so two Backs in a row do not exit Messenger
    const val TOAST_COOLDOWN_MS = 2_500L
    const val DEBUG_DUMP_INTERVAL_MS = 3_000L

    const val MAX_NODES = 800               // node limit per tree scan (performance)
}

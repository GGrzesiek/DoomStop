package io.github.ggrzesiek.doomstop

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

/**
 * Three blocking layers:
 *  1. Tap on the Facebook button in Messenger -> "arm" for a few seconds, then close whatever opened
 *     inside Messenger, or return to Messenger if the Facebook app was launched.
 *  2. Messenger screen scan -> the in-app browser showing a Facebook host (tap Close), or the native
 *     post action bar (Like / Comment / Share, press Back). Catches entries other than the button.
 *  3. Facebook app in the foreground -> always if enabled in settings,
 *     otherwise only when it was opened from Messenger.
 */
class DoomStopService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())

    private var armedUntil = 0L
    private var lastActionAt = 0L
    private var lastToastAt = 0L
    private var lastDumpAt = 0L
    private var scanScheduled = false

    private val inAppBackCheck = Runnable { onInAppBackCheck() }
    private val scanRunnable = Runnable { scanMessengerScreen() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(Config.TAG, "DoomStop service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED ->
                if (pkg == Config.MESSENGER) onMessengerClick(event)

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ->
                onWindowStateChanged(pkg)

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ->
                if (pkg == Config.MESSENGER) scheduleScan()
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    // ---------- Layer 1: tap on the Facebook button ----------

    private fun onMessengerClick(event: AccessibilityEvent) {
        val source = event.source
        val labels = buildList<CharSequence> {
            event.contentDescription?.let { add(it) }
            event.text?.forEach { if (it != null) add(it) }
            source?.contentDescription?.let { add(it) }
            source?.text?.let { add(it) }
        }

        if (Prefs.debug(this)) {
            Log.d(
                Config.TAG,
                "CLICK labels=$labels id=${source?.viewIdResourceName} class=${event.className}",
            )
        }

        if (labels.any { Config.matchesEntry(it) }) {
            Log.i(Config.TAG, "Facebook entry tapped: $labels")
            armedUntil = now() + Config.ARM_WINDOW_MS
            handler.removeCallbacks(inAppBackCheck)
            handler.postDelayed(inAppBackCheck, Config.IN_APP_BACK_DELAY_MS)
        }
    }

    private fun onInAppBackCheck() {
        if (!isArmed()) return
        // If the separate Facebook app was launched, onWindowStateChanged handles it.
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != Config.MESSENGER) return
        disarm()
        val close = findFbBrowserClose(root)
        act {
            if (close == null || !close.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
        }
    }

    // ---------- Layer 3: the Facebook app ----------

    private fun onWindowStateChanged(pkg: String) {
        if (pkg in Config.FB_APPS) {
            val fromMessenger = isArmed()
            if (fromMessenger || Prefs.blockFbApp(this)) {
                disarm()
                Log.i(Config.TAG, "Facebook app blocked (from Messenger: $fromMessenger)")
                // No cooldown: every return to Facebook (e.g. from recent apps) gets kicked out again.
                if (fromMessenger) openMessenger() else performGlobalAction(GLOBAL_ACTION_HOME)
                toast()
            }
            return
        }

        if (pkg == Config.MESSENGER) {
            if (Prefs.debug(this)) dumpTree("WINDOW_STATE_CHANGED")
            scheduleScan()
        }
    }

    private fun openMessenger() {
        val intent = packageManager.getLaunchIntentForPackage(Config.MESSENGER)
        if (intent == null) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.w(Config.TAG, "Could not open Messenger", e)
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    // ---------- Layer 2: Messenger screen scan ----------

    private fun scheduleScan() {
        // Throttle, not debounce: with constant changes (video in a feed) a debounce would never fire.
        if (scanScheduled) return
        scanScheduled = true
        handler.postDelayed(scanRunnable, Config.SCAN_DELAY_MS)
    }

    private fun scanMessengerScreen() {
        scanScheduled = false
        if (inCooldown()) return

        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != Config.MESSENGER) return

        if (Prefs.debug(this) && now() - lastDumpAt > Config.DEBUG_DUMP_INTERVAL_MS) {
            dumpTree("SCAN")
        }

        // 2a: Facebook opened in Messenger's in-app browser -> tap "Close".
        // A tap instead of Back, because Back in a WebView navigates the page history first.
        findFbBrowserClose(root)?.let { close ->
            Log.i(Config.TAG, "Facebook detected in Messenger's browser, closing")
            act {
                if (!close.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }
            }
            return
        }

        // 2b: native Facebook screen inside Messenger (action bar under posts).
        val labels = collectLabels(root)
        val hit = Config.FEED_MARKER_GROUPS.firstOrNull { group -> labels.containsAll(group) }
        if (hit != null) {
            Log.i(Config.TAG, "Facebook screen detected in Messenger, markers: $hit")
            act { performGlobalAction(GLOBAL_ACTION_BACK) }
        }
    }

    /** Finds the in-app browser bar showing a Facebook host; returns its close button or null. */
    private fun findFbBrowserClose(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val stack = ArrayList<AccessibilityNodeInfo>()
        stack.add(root)
        var visited = 0
        while (stack.isNotEmpty() && visited < Config.MAX_NODES) {
            val node = stack.removeAt(stack.size - 1)
            visited++
            val isHost = listOfNotNull(node.text, node.contentDescription).any { Config.isFbHost(it) }
            if (isHost) {
                findCloseNear(node)?.let { return it }
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { stack.add(it) }
            }
        }
        return null
    }

    /** The close button sits in the same bar as the host: search the parent, then the grandparent. */
    private fun findCloseNear(hostNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var container = hostNode.parent
        repeat(2) {
            val c = container ?: return null
            findCloseIn(c, depth = 2)?.let { return it }
            container = c.parent
        }
        return null
    }

    private fun findCloseIn(node: AccessibilityNodeInfo, depth: Int): AccessibilityNodeInfo? {
        val label = node.contentDescription ?: node.text
        if (node.isClickable && label != null && Config.norm(label) in Config.BROWSER_CLOSE_LABELS) {
            return node
        }
        if (depth == 0) return null
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child -> findCloseIn(child, depth - 1)?.let { return it } }
        }
        return null
    }

    private fun collectLabels(root: AccessibilityNodeInfo): Set<String> {
        val out = HashSet<String>()
        val stack = ArrayList<AccessibilityNodeInfo>()
        stack.add(root)
        var visited = 0
        while (stack.isNotEmpty() && visited < Config.MAX_NODES) {
            val node = stack.removeAt(stack.size - 1)
            visited++
            node.text?.let { out.add(Config.norm(it)) }
            node.contentDescription?.let { out.add(Config.norm(it)) }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { stack.add(it) }
            }
        }
        return out
    }

    // ---------- Debug: dump the view tree to logcat ----------

    private fun dumpTree(reason: String) {
        lastDumpAt = now()
        val root = rootInActiveWindow ?: return
        Log.d(Config.TAG, "===== DUMP ($reason) pkg=${root.packageName} =====")
        dumpNode(root, 0, intArrayOf(0))
        Log.d(Config.TAG, "===== END DUMP =====")
    }

    private fun dumpNode(node: AccessibilityNodeInfo, depth: Int, counter: IntArray) {
        if (counter[0]++ > Config.MAX_NODES) return
        val text = node.text?.toString()?.take(60)
        val desc = node.contentDescription?.toString()?.take(60)
        val id = node.viewIdResourceName
        if (text != null || desc != null || id != null || node.isClickable) {
            val cls = node.className?.toString()?.substringAfterLast('.')
            Log.d(
                Config.TAG,
                "${"  ".repeat(depth.coerceAtMost(30))}$cls id=$id text=$text desc=$desc click=${node.isClickable}",
            )
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { dumpNode(it, depth + 1, counter) }
        }
    }

    // ---------- Helpers ----------

    private fun act(action: () -> Unit) {
        if (inCooldown()) return
        lastActionAt = now()
        action()
        toast()
    }

    private fun toast() {
        if (now() - lastToastAt < Config.TOAST_COOLDOWN_MS) return
        lastToastAt = now()
        Toast.makeText(this, getString(R.string.blocked_toast), Toast.LENGTH_SHORT).show()
    }

    private fun isArmed() = now() < armedUntil
    private fun disarm() { armedUntil = 0L }
    private fun inCooldown() = now() - lastActionAt < Config.ACTION_COOLDOWN_MS
    private fun now() = SystemClock.elapsedRealtime()
}

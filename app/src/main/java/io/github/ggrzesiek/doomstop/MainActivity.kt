package io.github.ggrzesiek.doomstop

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

private val BG = Color.parseColor("#F6F7F9")
private val CARD = Color.WHITE
private val TEXT = Color.parseColor("#1B1F24")
private val MUTED = Color.parseColor("#5F6B7A")
private val DIVIDER = Color.parseColor("#E6E8EC")
private val ACCENT = Color.parseColor("#0F766E")
private val OK_BG = Color.parseColor("#E7F6EC")
private val OK_FG = Color.parseColor("#1D6B3A")
private val ERR_BG = Color.parseColor("#FDECEC")
private val ERR_FG = Color.parseColor("#A12A2A")

/** Main screen: service status, a shortcut to accessibility settings and the toggles. No libraries. */
class MainActivity : Activity() {

    private lateinit var statusCard: LinearLayout
    private lateinit var statusTitle: TextView
    private lateinit var statusDesc: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(32))
        }

        // Header
        column.addView(label(getString(R.string.app_name), 30f, TEXT, bold = true))
        column.addView(label(getString(R.string.subtitle), 15f, MUTED), margins(top = 2))

        // Status
        statusTitle = label("", 18f, TEXT, bold = true)
        statusDesc = label("", 14f, TEXT)
        statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            addView(statusTitle)
            addView(statusDesc, margins(top = 4))
        }
        column.addView(statusCard, margins(top = 24))

        column.addView(
            primaryButton(getString(R.string.open_settings)) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
            margins(top = 12),
        )

        // Settings
        column.addView(sectionHeader(R.string.section_settings), margins(top = 28))
        column.addView(
            card(
                switchRow(R.string.block_fb_app, R.string.block_fb_app_desc, Prefs.BLOCK_FB_APP),
                switchRow(R.string.debug_mode, R.string.debug_mode_desc, Prefs.DEBUG),
            ),
            margins(top = 8),
        )

        // How it works
        column.addView(sectionHeader(R.string.section_how), margins(top = 28))
        val how = label(getString(R.string.how_it_works), 14f, MUTED).apply {
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setLineSpacing(0f, 1.15f)
        }
        column.addView(card(how), margins(top = 8))

        val scroll = ScrollView(this).apply {
            setBackgroundColor(BG)
            isFillViewport = true
            addView(column)
        }
        applySystemBarInsets(scroll)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        val on = isServiceEnabled()
        statusTitle.text = getString(if (on) R.string.status_on_title else R.string.status_off_title)
        statusDesc.text = getString(if (on) R.string.status_on_desc else R.string.status_off_desc)
        val fg = if (on) OK_FG else ERR_FG
        statusTitle.setTextColor(fg)
        statusDesc.setTextColor(fg)
        statusCard.background = rounded(if (on) OK_BG else ERR_BG)
    }

    /**
     * Android 15+ enforces edge-to-edge for targetSdk 35: content is drawn under the status
     * and navigation bars, so pad the root view by their size.
     */
    private fun applySystemBarInsets(root: View) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        root.setOnApplyWindowInsetsListener { v, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    // ---------- View builders ----------

    private fun label(value: String, sizeSp: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = sizeSp
            setTextColor(color)
            setTypeface(if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT)
        }

    private fun sectionHeader(res: Int) =
        label(getString(res).uppercase(), 12f, MUTED, bold = true).apply {
            letterSpacing = 0.08f
            setPadding(dp(4), 0, 0, 0)
        }

    private fun primaryButton(title: String, onClick: () -> Unit) = Button(this).apply {
        text = title
        isAllCaps = false
        textSize = 16f
        setTextColor(Color.WHITE)
        setTypeface(Typeface.DEFAULT_BOLD)
        stateListAnimator = null
        minHeight = dp(52)
        background = RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), rounded(ACCENT), null)
        setOnClickListener { onClick() }
    }

    private fun card(vararg rows: View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(CARD)
        rows.forEachIndexed { i, row ->
            if (i > 0) {
                val divider = View(this@MainActivity).apply { setBackgroundColor(DIVIDER) }
                addView(divider, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
                    marginStart = dp(16)
                })
            }
            addView(row, margins())
        }
    }

    private fun switchRow(titleRes: Int, descRes: Int, key: String): View {
        val prefs = Prefs.get(this)
        val toggle = Switch(this).apply {
            text = getString(titleRes)
            textSize = 16f
            setTextColor(TEXT)
            isChecked = prefs.getBoolean(key, false)
            setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean(key, checked).apply() }
        }
        val desc = label(getString(descRes), 13f, MUTED).apply {
            setPadding(0, 0, dp(56), 0) // keep clear of the switch thumb
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(14))
            addView(toggle, margins())
            addView(desc, margins(top = 2))
            setOnClickListener { toggle.toggle() }
        }
    }

    private fun rounded(color: Int, radiusDp: Int = 16) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun margins(top: Int = 0) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = dp(top) }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun isServiceEnabled(): Boolean {
        val expected = ComponentName(this, DoomStopService::class.java)
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
    }
}

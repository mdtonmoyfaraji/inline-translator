package com.inlinetranslator

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Settings screen. Same sections as the extension's options page. */
class MainActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var pal: Palette

    private var connDot: View? = null
    private var connStatus: TextView? = null
    private var a11yDot: View? = null
    private var a11yStatus: TextView? = null
    private var sourceBtn: TextView? = null
    private var targetBtn: TextView? = null
    private var favBox: LinearLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        pal = prefs.palette(this)

        window.setBackgroundDrawable(ColorDrawable(pal.bg))
        window.statusBarColor = pal.bg
        window.navigationBarColor = pal.bg
        if (pal === Palette.LIGHT) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(pal.bg)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(20), dp(28), dp(20), dp(56))
        scroll.addView(col, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(scroll)

        col.addView(tv(this, "Inline Translator", 22f, pal.fg, bold = true))
        add(col, tv(this, "Translate inside any text field and insert the result where your cursor was.", 13f, pal.muted), 2)

        connectionSection(col)
        languagesSection(col)
        floatingSection(col)
        privacySection(col)
    }

    override fun onResume() {
        super.onResume()
        refreshConnStatus()
        refreshA11yStatus()
    }

    // ---- small view helpers ------------------------------------------------------------------

    private fun add(parent: LinearLayout, v: View, topDp: Int = 0) {
        parent.addView(
            v,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(topDp) }
        )
    }

    private fun section(col: LinearLayout, title: String) {
        val line = View(this)
        line.setBackgroundColor(pal.line)
        add(col, line, 26)
        (line.layoutParams as LinearLayout.LayoutParams).height = 1
        add(col, tv(this, title, 15f, pal.fg, bold = true), 18)
    }

    private fun edit(hint: String, value: String, type: Int): EditText {
        val e = EditText(this)
        e.setSingleLine(true)
        e.inputType = type
        e.setText(value)
        e.setHint(hint)
        e.setTextColor(pal.fg)
        e.setHintTextColor(pal.muted)
        e.textSize = 14f
        e.background = shape(this, pal.field, 8f, pal.line)
        e.setPadding(dp(10), dp(10), dp(10), dp(10))
        return e
    }

    private fun labeled(parent: LinearLayout, title: String, hint: String?, v: View, topDp: Int = 12) {
        add(parent, tv(this, title, 13f, pal.fg, bold = true), topDp)
        if (hint != null) add(parent, tv(this, hint, 12f, pal.muted), 1)
        add(parent, v, 4)
    }

    private fun button(label: String, primary: Boolean): TextView {
        val b = tv(this, label, 13f, if (primary) pal.accentFg else pal.fg, bold = primary)
        b.gravity = Gravity.CENTER
        b.setPadding(dp(14), dp(11), dp(14), dp(11))
        b.background = if (primary) {
            pressable(this, pal.accent, (pal.accent and 0x00FFFFFF) or 0xCC000000.toInt(), 8f)
        } else {
            pressable(this, Color.TRANSPARENT, pal.hover, 8f, pal.line)
        }
        b.isClickable = true
        return b
    }

    private fun buttonRow(parent: LinearLayout, vararg buttons: TextView, topDp: Int = 12) {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        buttons.forEachIndexed { i, b ->
            row.addView(
                b,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { if (i > 0) leftMargin = dp(8) }
            )
        }
        add(parent, row, topDp)
    }

    private fun note(): TextView {
        val n = TextView(this)
        n.textSize = 12f
        n.setPadding(dp(10), dp(8), dp(10), dp(8))
        n.visibility = View.GONE
        return n
    }

    private fun showNote(n: TextView, kind: String, msg: String) {
        val (fg, bg) = when (kind) {
            "ok" -> pal.ok to pal.okBg
            "err" -> pal.danger to pal.dangerBg
            "warn" -> pal.warn to pal.warnBg
            else -> pal.muted to pal.hover
        }
        n.setTextColor(fg)
        n.background = shape(this, bg, 8f)
        n.text = msg
        n.visibility = View.VISIBLE
    }

    private fun toggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit): View {
        var state = checked
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, dp(8), 0, dp(8))
        val box = tv(this, if (state) "\u2611" else "\u2610", 22f, pal.accent)
        val text = tv(this, label, 13f, pal.fg)
        text.setPadding(dp(10), 0, 0, 0)
        row.addView(box)
        row.addView(text, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.isClickable = true
        row.setOnClickListener {
            state = !state
            box.text = if (state) "\u2611" else "\u2610"
            onChange(state)
        }
        return row
    }

    private fun statusRow(): Triple<LinearLayout, View, TextView> {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.TOP
        val dot = View(this)
        dot.background = oval(pal.muted)
        row.addView(dot, LinearLayout.LayoutParams(dp(9), dp(9)).apply { topMargin = dp(5); rightMargin = dp(8) })
        val t = tv(this, "", 13f, pal.fg)
        row.addView(t, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return Triple(row, dot, t)
    }

    // ---- AI connection --------------------------------------------------------------------------

    private fun connectionSection(col: LinearLayout) {
        section(col, "AI connection")
        val (row, dot, status) = statusRow()
        connDot = dot
        connStatus = status
        add(col, row, 8)

        val url = edit("https://api.openai.com/v1", prefs.baseUrl, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        val model = edit("gpt-4o-mini", prefs.model, InputType.TYPE_CLASS_TEXT)
        val key = edit("sk-\u2026", prefs.apiKey, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        labeled(col, "API Base URL", "Any OpenAI-compatible endpoint. \u201C/v1\u201D and \u201C/chat/completions\u201D are added automatically.", url)
        labeled(col, "Model", "The model identifier your provider expects.", model)
        labeled(col, "API Key", null, key)
        add(col, toggleRow("Show key", false) { show ->
            key.inputType = InputType.TYPE_CLASS_TEXT or
                (if (show) InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD else InputType.TYPE_TEXT_VARIATION_PASSWORD)
            key.setSelection(key.text.length)
        }, 2)

        val msg = note()
        add(col, msg, 8)

        val test = button("Test connection", false)
        val save = button("Save", true)
        buttonRow(col, test, save)

        fun read() = ApiConfig(url.text.toString().trim(), model.text.toString().trim(), key.text.toString().trim())

        test.setOnClickListener {
            val cfg = read()
            val problems = AiClient.validate(cfg)
            if (problems.isNotEmpty()) {
                showNote(msg, "err", problems[0])
                return@setOnClickListener
            }
            test.isEnabled = false
            test.alpha = 0.5f
            showNote(msg, "info", "Testing\u2026")
            Thread {
                val r = AiClient.testConnection(cfg)
                runOnUiThread {
                    test.isEnabled = true
                    test.alpha = 1f
                    showNote(msg, if (r.ok) "ok" else "err", r.message)
                }
            }.start()
        }
        save.setOnClickListener {
            val cfg = read()
            val problems = AiClient.validate(cfg)
            if (problems.isNotEmpty()) {
                showNote(msg, "err", problems.joinToString("\n"))
                return@setOnClickListener
            }
            prefs.baseUrl = cfg.baseUrl
            prefs.model = cfg.model
            prefs.apiKey = cfg.apiKey
            showNote(msg, "ok", "Saved. Use \u201CTest connection\u201D to check that the model answers.")
            refreshConnStatus()
        }

        add(
            col,
            tv(
                this,
                "The key is stored in this app\u2019s private storage on your phone. It is not an encrypted vault: use a key with a spending limit. Nothing you type is sent anywhere until you press the arrow in the translator panel.",
                12f, pal.muted
            ),
            10
        )
    }

    private fun refreshConnStatus() {
        val cfg = prefs.apiConfig()
        val ok = cfg != null && AiClient.validate(cfg).isEmpty()
        connDot?.background = oval(if (ok) pal.ok else pal.danger)
        connStatus?.text = if (ok && cfg != null) {
            "Connected setup saved: ${cfg.model}"
        } else {
            "Not set up yet. Enter your provider details below."
        }
    }

    // ---- Languages ------------------------------------------------------------------------------

    private fun languagesSection(col: LinearLayout) {
        section(col, "Languages")
        val s = button("", false)
        val t = button("", false)
        s.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        t.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        sourceBtn = s
        targetBtn = t
        s.setOnClickListener { pickLanguage("source") }
        t.setOnClickListener { pickLanguage("target") }
        labeled(col, "Translate from", "\u201CDetect language\u201D works for most text.", s, 8)
        labeled(col, "Translate to", null, t)

        add(col, tv(this, "Favorites", 13f, pal.fg, bold = true), 16)
        val fav = LinearLayout(this)
        fav.orientation = LinearLayout.VERTICAL
        favBox = fav
        add(col, fav, 4)

        val clear = button("Clear recent languages", false)
        clear.setOnClickListener { prefs.recent = emptyList() }
        add(col, clear, 12)

        add(col, tv(this, "Theme", 13f, pal.fg, bold = true), 16)
        val themeRow = LinearLayout(this)
        themeRow.orientation = LinearLayout.HORIZONTAL
        listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEachIndexed { i, (id, label) ->
            val b = button(label, prefs.theme == id)
            b.setOnClickListener {
                if (prefs.theme != id) {
                    prefs.theme = id
                    recreate()
                }
            }
            themeRow.addView(
                b,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { if (i > 0) leftMargin = dp(8) }
            )
        }
        add(col, themeRow, 6)

        refreshLanguages()
    }

    private fun refreshLanguages() {
        sourceBtn?.text = Languages.name(prefs.sourceLang) + "  \u25BE"
        targetBtn?.text = Languages.name(prefs.targetLang) + "  \u25BE"
        val box = favBox ?: return
        box.removeAllViews()
        val favs = prefs.favorites
        if (favs.isEmpty()) {
            box.addView(tv(this, "None yet. Tap the star next to a language in the picker.", 12f, pal.muted))
            return
        }
        for (code in favs) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(dp(10), dp(2), dp(4), dp(2))
            row.background = shape(this, pal.field, 8f, pal.line)
            row.addView(tv(this, Languages.name(code), 13f, pal.fg), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val rm = tv(this, "Remove", 12f, pal.accent)
            rm.setPadding(dp(10), dp(10), dp(10), dp(10))
            rm.setOnClickListener {
                prefs.toggleFavorite(code)
                refreshLanguages()
            }
            row.addView(rm)
            box.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
        }
    }

    private fun pickLanguage(mode: String) {
        val dlg = Dialog(this)
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val current = if (mode == "source") prefs.sourceLang else prefs.targetLang
        val pane = LanguagePickerPane(this, pal, prefs, mode, current, { code ->
            if (mode == "source") prefs.sourceLang = code else prefs.targetLang = code
            dlg.dismiss()
            refreshLanguages()
        }, { dlg.dismiss() })
        val frame = FrameLayout(this)
        frame.setPadding(dp(10), dp(10), dp(10), dp(10))
        frame.background = shape(this, pal.bg, 14f, pal.line)
        frame.addView(pane, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        dlg.setContentView(frame)
        dlg.setOnDismissListener { refreshLanguages() }
        dlg.window?.let {
            it.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            it.setLayout((resources.displayMetrics.widthPixels * 0.92f).toInt(), WindowManager.LayoutParams.WRAP_CONTENT)
            it.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        }
        dlg.show()
    }

    // ---- Floating translator --------------------------------------------------------------------

    private fun isServiceEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val full = "$packageName/${TranslatorService::class.java.name}"
        val short = "$packageName/.TranslatorService"
        return flat.split(':').any { it.equals(full, true) || it.equals(short, true) }
    }

    private fun floatingSection(col: LinearLayout) {
        section(col, "Translate in any text field")
        add(
            col,
            tv(
                this,
                "When a text field is focused and the keyboard is open, a small translate button appears above the keyboard. Tap it, type in the panel, press the arrow, and the translation is inserted at your cursor (or replaces the selected text). This needs the Accessibility service so the app can insert text into the focused field.",
                13f, pal.fg
            ),
            8
        )
        val (row, dot, status) = statusRow()
        a11yDot = dot
        a11yStatus = status
        add(col, row, 12)

        val open = button("Open Accessibility settings", true)
        open.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (e: Exception) {
            }
        }
        add(col, open, 10)

        val info = button("App info (Android 13+: \u22EE \u2192 Allow restricted settings)", false)
        info.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            } catch (e: Exception) {
            }
        }
        add(col, info, 8)

        add(col, toggleRow("Show the translate button above the keyboard", prefs.bubbleEnabled) {
            prefs.bubbleEnabled = it
            TranslatorService.refresh()
        }, 8)

        add(
            col,
            tv(
                this,
                "Without the Accessibility service you can still select text in any app, open the menu (\u22EE or More) and choose Inline Translator. Editable text is replaced by the translation.",
                12f, pal.muted
            ),
            6
        )
    }

    private fun refreshA11yStatus() {
        val on = isServiceEnabled()
        a11yDot?.background = oval(if (on) pal.ok else pal.muted)
        a11yStatus?.text = if (on) "Accessibility service is on." else "Accessibility service is off."
    }

    // ---- Privacy ---------------------------------------------------------------------------------

    private fun privacySection(col: LinearLayout) {
        section(col, "Privacy, security & limits")
        val items = listOf(
            "Nothing on screen is read or sent until you press the arrow in the translator panel. Only the text you typed in the panel (or the text you selected and sent to Inline Translator) goes to the API host you configured.",
            "Your API key is kept in this app\u2019s private storage and is excluded from backups. It is not an encrypted vault: anyone with access to an unlocked or rooted phone could read it.",
            "Remote endpoints must use HTTPS. Plain http:// is accepted only for localhost (a model server on this phone).",
            "The Accessibility service ignores password fields. It only looks at which field is focused and, when you press the arrow, writes the translation into it.",
            "Not supported: password fields, apps that block accessibility services, and editors that refuse text insertion. If insertion fails, the translation is shown in the panel with a Copy button."
        )
        items.forEachIndexed { i, s ->
            add(col, tv(this, "\u2022  $s", 12f, pal.muted), if (i == 0) 8 else 6)
        }
    }
}

package com.inlinetranslator

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.os.Handler
import android.os.Looper
import android.app.AlertDialog
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import android.view.inputmethod.EditorInfo
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
    private var toneBtn: TextView? = null
    private var toneFavBox: LinearLayout? = null

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
        tonesSection(col)
        triggerSection(col)
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
        s.setOnClickListener { pickOption("source") }
        t.setOnClickListener { pickOption("target") }
        labeled(col, "Translate from", "\u201CDetect language\u201D works for most text.", s, 8)
        labeled(col, "Translate to", "\u201CNo translation\u201D = only apply a tone to what you wrote.", t)

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
        refreshTones()
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

    private fun pickOption(kind: String) {
        val dlg = Dialog(this)
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val model = if (kind == "tone") toneModel(prefs) else languageModel(prefs, kind)
        val current = when (kind) {
            "source" -> prefs.sourceLang
            "target" -> prefs.targetLang
            else -> prefs.tone
        }
        val pane = OptionPickerPane(this, pal, model, current, { code ->
            when (kind) {
                "source" -> prefs.sourceLang = code
                "target" -> prefs.targetLang = code
                else -> prefs.tone = code
            }
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
            @Suppress("DEPRECATION")
            it.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        }
        dlg.show()
    }

    // ---- Tones ------------------------------------------------------------------------------------

    private fun tonesSection(col: LinearLayout) {
        section(col, "Tones")
        add(
            col,
            tv(
                this,
                "A tone rewrites the result in a style (Formal, Short, Viking \u2026 or your own). Set the target to \u201CNo translation\u201D to only rewrite your text in the same language.",
                12f, pal.muted
            ),
            8
        )
        val t = button("", false)
        t.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        toneBtn = t
        t.setOnClickListener { pickOption("tone") }
        labeled(col, "Default tone", "You can also change it in the panel at any time.", t, 10)

        add(col, tv(this, "Favorite tones", 13f, pal.fg, bold = true), 14)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        toneFavBox = box
        add(col, box, 4)
        val clear = button("Clear recent tones", false)
        clear.setOnClickListener { prefs.toneRecent = emptyList() }
        add(col, clear, 12)
        refreshTones()
    }

    private fun refreshTones() {
        toneBtn?.text = (if (prefs.tone == Tones.DEFAULT) "Default (no tone)" else Tones.name(prefs.tone)) + "  \u25BE"
        val box = toneFavBox ?: return
        box.removeAllViews()
        val favs = prefs.toneFavorites
        if (favs.isEmpty()) {
            box.addView(tv(this, "None yet. Tap the star next to a tone in the picker.", 12f, pal.muted))
            return
        }
        for (code in favs) {
            box.addView(
                removableRow(Tones.name(code)) {
                    prefs.toggleToneFavorite(code)
                    refreshTones()
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) }
            )
        }
    }

    private fun removableRow(label: String, onRemove: () -> Unit): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(10), dp(2), dp(4), dp(2))
        row.background = shape(this, pal.field, 8f, pal.line)
        row.addView(tv(this, label, 13f, pal.fg), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val rm = tv(this, "Remove", 12f, pal.accent)
        rm.setPadding(dp(10), dp(10), dp(10), dp(10))
        rm.setOnClickListener { onRemove() }
        row.addView(rm)
        return row
    }

    // ---- Trigger --------------------------------------------------------------------------------------

    private fun triggerSection(col: LinearLayout) {
        section(col, "Trigger key")
        add(
            col,
            tv(
                this,
                "Open the translator from the keyboard: press one key several times in a row (for example Space twice). The typed trigger characters are removed again and the panel opens. Works in any text field while the Accessibility service is on.",
                12f, pal.muted
            ),
            8
        )

        var cfg = prefs.trigger
        val keyBtn = button("", false)
        keyBtn.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        val countText = tv(this, "", 15f, pal.fg, bold = true)
        countText.gravity = Gravity.CENTER
        val interval = edit("400", cfg.maxIntervalMs.toString(), InputType.TYPE_CLASS_NUMBER)
        val problems = LinearLayout(this)
        problems.orientation = LinearLayout.VERTICAL
        val saveMsg = note()
        val save = button("Save trigger", true)

        // Test field: uses the same detector as the service, so it also works while the service is off.
        val tester = EditText(this)
        tester.hint = "Tap here and try your trigger\u2026"
        tester.setHintTextColor(pal.muted)
        tester.setTextColor(pal.fg)
        tester.textSize = 14f
        tester.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        tester.background = shape(this, pal.field, 8f, pal.line)
        tester.setPadding(dp(10), dp(10), dp(10), dp(10))
        val flash = tv(this, "\u2713 Trigger detected", 13f, pal.ok, bold = true)
        flash.visibility = View.INVISIBLE
        val testDetector = TriggerDetector(cfg.copy(enabled = true))
        val handler = Handler(Looper.getMainLooper())
        var before = ""
        tester.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {
                before = s?.toString() ?: ""
            }

            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val hit = testDetector.onTextChanged(before, s?.toString() ?: "", android.os.SystemClock.uptimeMillis())
                if (hit == Hit.COMPLETED) {
                    flash.visibility = View.VISIBLE
                    handler.removeCallbacksAndMessages(null)
                    handler.postDelayed({ flash.visibility = View.INVISIBLE }, 1400)
                }
            }
        })

        fun readNumbers() {
            val ms = interval.text.toString().trim().toIntOrNull() ?: 400
            cfg = cfg.copy(maxIntervalMs = ms.coerceIn(0, 100000))
        }

        fun refresh() {
            readNumbers()
            keyBtn.text = cfg.label().let { if (cfg.char.isEmpty()) "Not set  (tap to set)" else "$it  (tap to change)" }
            countText.text = cfg.pressCount.toString()
            val v = validateTrigger(cfg.copy(acknowledgedRisk = true))
            problems.removeAllViews()
            for ((kind, list) in listOf("err" to v.errors, "warn" to v.warnings)) {
                if (list.isEmpty()) continue
                val n = note()
                showNote(n, kind, list.joinToString("\n") { "\u2022 $it" })
                problems.addView(n, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
            }
            save.isEnabled = v.errors.isEmpty()
            save.alpha = if (v.errors.isEmpty()) 1f else 0.5f
            testDetector.setConfig(cfg.copy(enabled = cfg.char.isNotEmpty()))
        }

        add(col, toggleRow("Enable the trigger", cfg.enabled) {
            cfg = cfg.copy(enabled = it)
        }, 8)

        labeled(col, "Trigger key", "Tap, then press the key you want on your own keyboard. Space, letters, punctuation and emoji all work.", keyBtn, 6)
        keyBtn.setOnClickListener {
            captureTriggerChar { ch ->
                cfg = cfg.copy(char = ch, acknowledgedRisk = false)
                refresh()
            }
        }

        // presses in a row
        val stepper = LinearLayout(this)
        stepper.orientation = LinearLayout.HORIZONTAL
        stepper.gravity = Gravity.CENTER_VERTICAL
        val minus = button("\u2212", false)
        val plus = button("+", false)
        minus.setOnClickListener {
            cfg = cfg.copy(pressCount = (cfg.pressCount - 1).coerceIn(1, 5))
            refresh()
        }
        plus.setOnClickListener {
            cfg = cfg.copy(pressCount = (cfg.pressCount + 1).coerceIn(1, 5))
            refresh()
        }
        stepper.addView(minus, LinearLayout.LayoutParams(dp(56), ViewGroup.LayoutParams.WRAP_CONTENT))
        stepper.addView(countText, LinearLayout.LayoutParams(dp(56), ViewGroup.LayoutParams.WRAP_CONTENT))
        stepper.addView(plus, LinearLayout.LayoutParams(dp(56), ViewGroup.LayoutParams.WRAP_CONTENT))
        labeled(col, "Presses in a row", "1\u20135. A plain key needs at least 2, otherwise it would trigger whenever you type it.", stepper, 12)

        labeled(col, "Max. time between presses (ms)", "100\u20132000. Default 400.", interval, 12)
        interval.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                readNumbers()
                val v = validateTrigger(cfg.copy(acknowledgedRisk = true))
                save.isEnabled = v.errors.isEmpty()
                save.alpha = if (v.errors.isEmpty()) 1f else 0.5f
            }
        })

        add(col, problems, 4)

        add(col, tv(this, "Test it", 13f, pal.fg, bold = true), 14)
        add(col, tester, 4)
        add(col, flash, 4)

        add(col, saveMsg, 8)
        add(col, save, 8)
        save.setOnClickListener {
            readNumbers()
            val v = validateTrigger(cfg.copy(acknowledgedRisk = true))
            if (v.errors.isNotEmpty()) return@setOnClickListener
            val doSave = {
                prefs.trigger = cfg
                TranslatorService.refresh()
                showNote(
                    saveMsg, "ok",
                    if (cfg.enabled) "Saved. Trigger: ${cfg.label()}. Make sure the Accessibility service is on." else "Saved. The trigger is switched off."
                )
            }
            if (v.plainTypingKey && cfg.enabled && !cfg.acknowledgedRisk) {
                AlertDialog.Builder(this)
                    .setTitle("Plain typing key")
                    .setMessage(
                        "You chose a letter or digit as the trigger. While it is enabled, pressing it ${cfg.pressCount} times in a row opens the translator instead of just typing. " +
                            "The typed characters are removed again, but double letters in normal words can still trigger it by accident.\n\nSpace, punctuation or an emoji is safer. Enable it anyway?"
                    )
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Enable anyway") { _, _ ->
                        cfg = cfg.copy(acknowledgedRisk = true)
                        doSave()
                    }
                    .show()
            } else {
                doSave()
            }
        }
        refresh()

        add(
            col,
            tv(
                this,
                "Not every app reports typed text to the Accessibility service, and some keyboards change what you type (auto-space, auto-correct). Turn off \u201Cdouble-space period\u201D in your keyboard if you use Space as the trigger. Password fields are always ignored.",
                12f, pal.muted
            ),
            10
        )
    }

    /** A real text box with your own keyboard: the first character you type becomes the trigger. */
    private fun captureTriggerChar(onCaptured: (String) -> Unit) {
        val dlg = Dialog(this)
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val frame = LinearLayout(this)
        frame.orientation = LinearLayout.VERTICAL
        frame.setPadding(dp(16), dp(16), dp(16), dp(16))
        frame.background = shape(this, pal.bg, 14f, pal.line)
        frame.addView(tv(this, "Press your trigger key", 16f, pal.fg, bold = true))
        frame.addView(
            tv(this, "Use the keyboard that just opened: type the key you want (Space, a letter, a punctuation mark, an emoji \u2026). It is taken over immediately.", 12f, pal.muted),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) }
        )
        val box = EditText(this)
        box.setSingleLine(true)
        box.textSize = 18f
        box.gravity = Gravity.CENTER
        box.setTextColor(pal.fg)
        box.hint = "\u2026"
        box.setHintTextColor(pal.muted)
        // No suggestions / auto-correct / auto-space: exactly one tap = exactly one character.
        box.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        box.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
        box.background = shape(this, pal.field, 8f, pal.line)
        box.setPadding(dp(10), dp(12), dp(10), dp(12))
        frame.addView(
            box,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) }
        )
        val cancel = button("Cancel", false)
        cancel.setOnClickListener { dlg.dismiss() }
        frame.addView(
            cancel,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) }
        )

        var before = ""
        var done = false
        box.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {
                before = s?.toString() ?: ""
            }

            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (done) return
                val now = s?.toString() ?: ""
                if (now.isEmpty() || now.length <= before.length) return
                // The newly typed/pasted part; use its last character.
                val added = now.substring(before.length.coerceAtMost(now.length))
                val ch = String(Character.toChars(added.codePointBefore(added.length)))
                if (ch.isNotEmpty()) {
                    done = true
                    dlg.dismiss()
                    onCaptured(ch)
                }
            }
        })
        dlg.setContentView(frame)
        dlg.window?.let {
            it.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            it.setLayout((resources.displayMetrics.widthPixels * 0.92f).toInt(), WindowManager.LayoutParams.WRAP_CONTENT)
            @Suppress("DEPRECATION")
            it.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        }
        dlg.show()
        box.requestFocus()
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
                "When a text field is focused and the keyboard is open, a small translate button appears. Tap it to open the panel, type, press the arrow, and the result is inserted at your cursor (or replaces the selected text). This needs the Accessibility service so the app can insert text into the focused field.",
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

        add(col, toggleRow("Show the floating translate button", prefs.bubbleEnabled) {
            prefs.bubbleEnabled = it
            TranslatorService.refresh()
        }, 8)
        add(
            col,
            tv(
                this,
                "Drag the button anywhere (left, right, top, bottom). It stays exactly where you drop it. The panel works the same: drag it by the small bar at its top.",
                12f, pal.muted
            ),
            2
        )
        val reset = button("Reset button and panel position", false)
        reset.setOnClickListener {
            prefs.resetPositions()
            TranslatorService.refresh()
        }
        add(col, reset, 10)

        add(col, toggleRow("Close the panel when I tap outside of it", prefs.closeOutside) {
            prefs.closeOutside = it
        }, 6)
        add(
            col,
            tv(this, "Taps on the keyboard (and, in the recommended typing mode, on the text field) never close it. The keyboard stays open.", 12f, pal.muted),
            0
        )

        add(
            col,
            tv(
                this,
                "Without the Accessibility service you can still select text in any app, open the menu (\u22EE or More) and choose Inline Translator. Editable text is replaced by the result.",
                12f, pal.muted
            ),
            12
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
            "Nothing is sent anywhere until you press the arrow in the panel. Only the text in the panel (or the text you selected and sent to Inline Translator) goes to the API host you configured.",
            "With the trigger switched off (and \u201CIn the panel box\u201D typing), the Accessibility service does not even receive what you type. With the trigger on, it sees text-change events only to count your trigger key; nothing is stored or sent. In \u201CIn the app\u2019s own field\u201D mode it reads the focused field\u2019s text while the panel is open.",
            "Your API key is kept in this app\u2019s private storage and is excluded from backups. It is not an encrypted vault: anyone with access to an unlocked or rooted phone could read it.",
            "Remote endpoints must use HTTPS. Plain http:// is accepted only for localhost (a model server on this phone).",
            "Password fields are always ignored.",
            "Not supported: apps that block accessibility services and editors that refuse text insertion. If insertion fails, the result is shown in the panel with a Copy button."
        )
        items.forEachIndexed { i, s ->
            add(col, tv(this, "\u2022  $s", 12f, pal.muted), if (i == 0) 8 else 6)
        }
    }
}

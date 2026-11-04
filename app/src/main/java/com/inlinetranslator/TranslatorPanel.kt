package com.inlinetranslator

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast

/** Root view of the panel. Handles Back/Escape. */
class PanelFrame(c: Context, private val onBack: () -> Boolean) : FrameLayout(c) {
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_UP &&
            (event.keyCode == KeyEvent.KEYCODE_BACK || event.keyCode == KeyEvent.KEYCODE_ESCAPE)
        ) {
            if (onBack()) return true
        }
        return super.dispatchKeyEvent(event)
    }
}

/**
 * The translator panel: [source v] [swap] [target v] ... [x], a text box with a round arrow button,
 * status/error line and a result box. Same layout and colours as the extension's panel.
 * Where the translation goes is up to the [Host].
 */
class TranslatorPanel(
    private val ctx: Context,
    private val prefs: Prefs,
    private val host: Host
) {
    interface Host {
        /** Put the translation where the user wants it. Return true on success (the host then closes the panel). */
        fun deliver(text: String): Boolean
        fun close()
        fun openSettings()
        /** Shown above the result box when [deliver] returned false. */
        fun fallbackNote(): String
    }

    private val pal = prefs.palette(ctx)
    private val main = Handler(Looper.getMainLooper())
    private var busy = false
    private var closed = false
    private var pickerOpen = false
    private var savedStatusVis = View.GONE
    private var savedResultVis = View.GONE
    private var lastText = ""

    val view: PanelFrame = PanelFrame(ctx) { onBack() }

    private val sourceBtn = TextView(ctx)
    private val targetBtn = TextView(ctx)
    private val swapBtn = TextView(ctx)
    private val pickerHolder = FrameLayout(ctx)
    private val fieldBox = FrameLayout(ctx)
    private val input = EditText(ctx)
    private val goBtn = FrameLayout(ctx)
    private val goArrow = TextView(ctx)
    private val spinner = ProgressBar(ctx, null, android.R.attr.progressBarStyleSmall)
    private val statusRow = LinearLayout(ctx)
    private val statusMsg = TextView(ctx)
    private val statusAction = TextView(ctx)
    private val resultScroll = MaxHeightScrollView(ctx, ctx.dp(140))
    private val resultText = TextView(ctx)

    init {
        val card = LinearLayout(ctx)
        card.orientation = LinearLayout.VERTICAL
        card.setPadding(ctx.dp(8), ctx.dp(8), ctx.dp(8), ctx.dp(8))
        card.background = shape(ctx, pal.bg, 14f, pal.line)
        card.elevation = ctx.dp(8).toFloat()

        // --- top bar -------------------------------------------------------
        val bar = LinearLayout(ctx)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        styleLabel(sourceBtn)
        styleLabel(targetBtn)
        sourceBtn.setOnClickListener { openPicker("source") }
        targetBtn.setOnClickListener { openPicker("target") }
        styleIcon(swapBtn, "\u21C4")
        swapBtn.contentDescription = "Swap languages"
        swapBtn.setOnClickListener { swap() }
        val closeBtn = TextView(ctx)
        styleIcon(closeBtn, "\u2715")
        closeBtn.contentDescription = "Close"
        closeBtn.setOnClickListener { host.close() }

        bar.addView(sourceBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ctx.dp(36)))
        bar.addView(swapBtn, LinearLayout.LayoutParams(ctx.dp(36), ctx.dp(36)))
        bar.addView(targetBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ctx.dp(36)))
        bar.addView(View(ctx), LinearLayout.LayoutParams(0, 1, 1f))
        bar.addView(closeBtn, LinearLayout.LayoutParams(ctx.dp(36), ctx.dp(36)))
        card.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // --- language picker (replaces the text box while open) -------------
        pickerHolder.visibility = View.GONE
        card.addView(pickerHolder, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // --- text box + round arrow button ----------------------------------
        input.setRawInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        input.imeOptions = EditorInfo.IME_ACTION_SEND
        input.setHorizontallyScrolling(false)
        input.maxLines = 6
        input.gravity = Gravity.TOP or Gravity.START
        input.textSize = 14f
        input.minHeight = ctx.dp(62)
        input.setTextColor(pal.fg)
        input.setHintTextColor(pal.muted)
        input.hint = "Type to translate\u2026"
        input.background = shape(ctx, pal.field, 10f, pal.line)
        input.setPadding(ctx.dp(10), ctx.dp(9), ctx.dp(46), ctx.dp(9))
        input.setOnEditorActionListener { _, actionId, event ->
            val enter = event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
            if (actionId == EditorInfo.IME_ACTION_SEND || enter) {
                submit()
                true
            } else {
                false
            }
        }

        goBtn.background = oval(pal.accent)
        goBtn.isClickable = true
        goBtn.contentDescription = "Translate"
        goArrow.text = "\u2191"
        goArrow.textSize = 18f
        goArrow.setTextColor(pal.accentFg)
        goArrow.typeface = android.graphics.Typeface.DEFAULT_BOLD
        goArrow.gravity = Gravity.CENTER
        spinner.isIndeterminate = true
        spinner.indeterminateTintList = ColorStateList.valueOf(pal.accentFg)
        spinner.visibility = View.GONE
        goBtn.addView(goArrow, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        goBtn.addView(
            spinner,
            FrameLayout.LayoutParams(ctx.dp(18), ctx.dp(18), Gravity.CENTER)
        )
        goBtn.setOnClickListener { submit() }

        fieldBox.addView(input, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        fieldBox.addView(
            goBtn,
            FrameLayout.LayoutParams(ctx.dp(34), ctx.dp(34), Gravity.END or Gravity.BOTTOM).apply {
                setMargins(0, 0, ctx.dp(7), ctx.dp(7))
            }
        )
        card.addView(fieldBox, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // --- status line ---------------------------------------------------
        statusRow.orientation = LinearLayout.HORIZONTAL
        statusRow.gravity = Gravity.CENTER_VERTICAL
        statusRow.setPadding(ctx.dp(8), ctx.dp(6), ctx.dp(8), ctx.dp(6))
        statusRow.visibility = View.GONE
        statusMsg.textSize = 12f
        statusAction.textSize = 12f
        statusAction.setPadding(ctx.dp(8), ctx.dp(3), ctx.dp(8), ctx.dp(3))
        statusAction.visibility = View.GONE
        statusRow.addView(statusMsg, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        statusRow.addView(statusAction, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        card.addView(
            statusRow,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = ctx.dp(6) }
        )

        // --- result box (fallback when direct insertion is not possible) ----
        resultText.textSize = 14f
        resultText.setTextColor(pal.fg)
        resultText.setTextIsSelectable(true)
        resultText.setPadding(ctx.dp(8), ctx.dp(8), ctx.dp(8), ctx.dp(8))
        resultScroll.background = shape(ctx, pal.field, 8f, pal.line)
        resultScroll.addView(resultText, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        resultScroll.visibility = View.GONE
        card.addView(
            resultScroll,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = ctx.dp(6) }
        )

        // --- frame: leaves room around the card so its shadow is not clipped -
        val width = minOf(ctx.dp(340), ctx.resources.displayMetrics.widthPixels - ctx.dp(28))
        view.setPadding(ctx.dp(14), ctx.dp(14), ctx.dp(14), ctx.dp(14))
        view.clipToPadding = false
        view.clipChildren = false
        view.addView(card, FrameLayout.LayoutParams(width - ctx.dp(28), ViewGroup.LayoutParams.WRAP_CONTENT))

        refreshLabels()
        if (prefs.apiConfig() == null) {
            input.hint = "Set up your AI connection to translate."
            showNotice("The AI connection isn\u2019t set up yet.", "Open settings", { host.openSettings() }, false)
        }
    }

    // ---- public API ---------------------------------------------------------

    fun prefill(text: String, autoSubmit: Boolean) {
        input.setText(text)
        input.setSelection(input.text.length)
        if (autoSubmit) main.post { submit() }
    }

    fun focusInput() {
        input.requestFocus()
    }

    fun showKeyboard() {
        input.requestFocus()
        ctx.getSystemService(InputMethodManager::class.java)?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
    }

    /** Called by the host when the panel is removed, so late network results are dropped. */
    fun dispose() {
        closed = true
    }

    // ---- language bar ---------------------------------------------------------

    private fun styleLabel(t: TextView) {
        t.textSize = 13f
        t.setTextColor(pal.fg)
        t.typeface = android.graphics.Typeface.DEFAULT_BOLD
        t.gravity = Gravity.CENTER_VERTICAL
        t.setSingleLine(true)
        t.ellipsize = android.text.TextUtils.TruncateAt.END
        t.maxWidth = ctx.dp(112)
        t.setPadding(ctx.dp(8), 0, ctx.dp(8), 0)
        t.background = pressable(ctx, Color.TRANSPARENT, pal.hover, 8f)
        t.isClickable = true
    }

    private fun styleIcon(t: TextView, glyph: String) {
        t.text = glyph
        t.textSize = 16f
        t.setTextColor(pal.muted)
        t.gravity = Gravity.CENTER
        t.background = pressable(ctx, Color.TRANSPARENT, pal.hover, 8f)
        t.isClickable = true
    }

    private fun refreshLabels() {
        sourceBtn.text = Languages.name(prefs.sourceLang) + " \u25BE"
        targetBtn.text = Languages.name(prefs.targetLang) + " \u25BE"
        swapBtn.alpha = if (prefs.sourceLang == Languages.AUTO) 0.35f else 1f
    }

    private fun swap() {
        val s = prefs.sourceLang
        if (s == Languages.AUTO) return // can't translate "into" auto-detect
        val t = prefs.targetLang
        prefs.sourceLang = t
        prefs.targetLang = s
        refreshLabels()
    }

    private fun openPicker(mode: String) {
        closePicker()
        pickerOpen = true
        savedStatusVis = statusRow.visibility
        savedResultVis = resultScroll.visibility
        val current = if (mode == "source") prefs.sourceLang else prefs.targetLang
        val pane = LanguagePickerPane(ctx, pal, prefs, mode, current, { code ->
            if (mode == "source") prefs.sourceLang = code else prefs.targetLang = code
            closePicker()
            refreshLabels()
        }, { closePicker() })
        pickerHolder.removeAllViews()
        pickerHolder.addView(pane, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        pickerHolder.visibility = View.VISIBLE
        fieldBox.visibility = View.GONE
        statusRow.visibility = View.GONE
        resultScroll.visibility = View.GONE
    }

    private fun closePicker() {
        if (!pickerOpen) return
        pickerOpen = false
        pickerHolder.removeAllViews()
        pickerHolder.visibility = View.GONE
        fieldBox.visibility = View.VISIBLE
        statusRow.visibility = savedStatusVis
        resultScroll.visibility = savedResultVis
        input.requestFocus()
    }

    private fun onBack(): Boolean {
        if (pickerOpen) {
            closePicker()
        } else {
            host.close()
        }
        return true
    }

    // ---- status ---------------------------------------------------------------

    private fun clearStatus() {
        statusRow.visibility = View.GONE
        statusMsg.text = ""
        statusAction.visibility = View.GONE
        resultScroll.visibility = View.GONE
        resultText.text = ""
    }

    private fun showNotice(message: String, actionLabel: String?, action: (() -> Unit)?, error: Boolean) {
        statusRow.background = if (error) shape(ctx, pal.dangerBg, 8f) else null
        statusMsg.setTextColor(if (error) pal.danger else pal.muted)
        statusMsg.text = message
        if (actionLabel != null && action != null) {
            statusAction.text = actionLabel
            statusAction.setTextColor(if (error) pal.danger else pal.muted)
            statusAction.background = shape(ctx, Color.TRANSPARENT, 6f, if (error) pal.danger else pal.muted)
            statusAction.setOnClickListener { action() }
            statusAction.visibility = View.VISIBLE
        } else {
            statusAction.visibility = View.GONE
        }
        statusRow.visibility = View.VISIBLE
    }

    private fun setBusy(b: Boolean) {
        busy = b
        goArrow.visibility = if (b) View.GONE else View.VISIBLE
        spinner.visibility = if (b) View.VISIBLE else View.GONE
        clearStatus()
        if (b) showNotice("Translating\u2026", null, null, false)
    }

    private fun showError(message: String, canRetry: Boolean) {
        setBusy(false)
        showNotice(message, if (canRetry) "Retry" else null, if (canRetry) ({ submit() }) else null, true)
        input.requestFocus()
    }

    private fun showResult(text: String, note: String) {
        setBusy(false)
        showNotice(note, "Copy", { copy(text) }, false)
        resultText.text = text
        resultScroll.visibility = View.VISIBLE
    }

    private fun copy(text: String) {
        val cm = ctx.getSystemService(ClipboardManager::class.java)
        cm?.setPrimaryClip(ClipData.newPlainText("translation", text))
        Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
    }

    // ---- translate ----------------------------------------------------------------

    fun submit() {
        if (busy || closed) return
        val cfg = prefs.apiConfig()
        if (cfg == null) {
            showNotice("Set up the AI connection first.", "Open settings", { host.openSettings() }, true)
            return
        }
        val text = input.text.toString().trim()
        if (text.isEmpty()) {
            input.requestFocus()
            return
        }
        lastText = text
        val src = prefs.sourceLang
        val tgt = prefs.targetLang
        setBusy(true)
        Thread {
            var out: String? = null
            var err: ApiException? = null
            try {
                out = AiClient.translate(cfg, text, src, tgt)
            } catch (e: ApiException) {
                err = e
            } catch (e: Exception) {
                err = ApiException("unknown", "Something went wrong: ${e.message}")
            }
            main.post {
                if (closed) return@post
                val e = err
                val o = out
                if (e != null || o == null) {
                    val retry = e == null || e.code in setOf("timeout", "network", "server", "rate_limit", "bad_response", "unknown")
                    showError(e?.message ?: "Translation failed.", retry)
                } else {
                    prefs.pushRecent(tgt)
                    prefs.pushRecent(src)
                    setBusy(false)
                    if (!host.deliver(o)) showResult(o, host.fallbackNote())
                }
            }
        }.start()
    }
}

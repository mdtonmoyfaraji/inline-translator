package com.inlinetranslator

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
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
 * The translator panel, same layout and colours as the extension:
 *   [handle bar ........................ x]
 *   [source v] [swap] [target v] [tone v]
 *   [ text box                         (^) ]
 *   status / error line, result box
 * Where the result goes (and how the panel is positioned) is up to the [Host].
 */
class TranslatorPanel(
    private val ctx: Context,
    private val prefs: Prefs,
    private val host: Host
) {
    interface Host {
        /** Put the result where the user wants it. Return true on success (the host then closes the panel). */
        fun deliver(text: String): Boolean
        fun close()
        fun openSettings()
        /** Shown above the result box when [deliver] returned false. */
        fun fallbackNote(): String

        /** True if the panel can be moved by dragging its top bar. */
        val draggable: Boolean get() = false

        /** True = the user types in the app's own field; the panel only mirrors it and never takes the keyboard. */
        val mirrorMode: Boolean get() = false

        fun dragStart() {}
        fun dragBy(dx: Int, dy: Int) {}
        fun dragEnd() {}

        /** The panel needs the keyboard for its own search box (picker) or no longer needs it. */
        fun setFocusable(focusable: Boolean) {}
    }

    private val pal = prefs.palette(ctx)
    private val main = Handler(Looper.getMainLooper())
    private var busy = false
    private var closed = false
    private var pickerOpen = false
    private var savedStatusVis = View.GONE
    private var savedResultVis = View.GONE

    val view: PanelFrame = PanelFrame(ctx) { onBack() }

    private val sourceBtn = TextView(ctx)
    private val targetBtn = TextView(ctx)
    private val toneBtn = TextView(ctx)
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
        card.setPadding(ctx.dp(8), ctx.dp(2), ctx.dp(8), ctx.dp(8))
        card.background = shape(ctx, pal.bg, 14f, pal.line)
        card.elevation = ctx.dp(8).toFloat()

        // --- drag handle row: small bar in the middle = "you can move me", close button at the right ---
        val handleRow = FrameLayout(ctx)
        if (host.draggable) {
            val grip = View(ctx)
            grip.background = shape(ctx, pal.muted, 3f)
            grip.alpha = 0.55f
            handleRow.addView(grip, FrameLayout.LayoutParams(ctx.dp(40), ctx.dp(5), Gravity.CENTER))
            handleRow.contentDescription = "Drag to move"
            var downX = 0f
            var downY = 0f
            handleRow.setOnTouchListener { _, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = e.rawX
                        downY = e.rawY
                        host.dragStart()
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        host.dragBy((e.rawX - downX).toInt(), (e.rawY - downY).toInt())
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        host.dragEnd()
                        true
                    }
                    else -> false
                }
            }
        }
        val closeBtn = TextView(ctx)
        styleIcon(closeBtn, "\u2715")
        closeBtn.contentDescription = "Close"
        closeBtn.setOnClickListener { host.close() }
        handleRow.addView(closeBtn, FrameLayout.LayoutParams(ctx.dp(36), ctx.dp(30), Gravity.END or Gravity.CENTER_VERTICAL))
        card.addView(handleRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ctx.dp(30)))

        // --- language + tone bar ---------------------------------------------
        val bar = LinearLayout(ctx)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        styleLabel(sourceBtn, 92)
        styleLabel(targetBtn, 92)
        styleLabel(toneBtn, 86)
        sourceBtn.setOnClickListener { openPicker("source") }
        targetBtn.setOnClickListener { openPicker("target") }
        toneBtn.setOnClickListener { openPicker("tone") }
        styleIcon(swapBtn, "\u21C4")
        swapBtn.contentDescription = "Swap languages"
        swapBtn.setOnClickListener { swap() }
        bar.addView(sourceBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ctx.dp(36)))
        bar.addView(swapBtn, LinearLayout.LayoutParams(ctx.dp(30), ctx.dp(36)))
        bar.addView(targetBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ctx.dp(36)))
        bar.addView(View(ctx), LinearLayout.LayoutParams(0, 1, 1f))
        bar.addView(toneBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ctx.dp(36)))
        card.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // --- picker (replaces the text box while open) -------------------------
        pickerHolder.visibility = View.GONE
        card.addView(pickerHolder, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // --- text box + round arrow button -------------------------------------
        input.setRawInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        input.imeOptions = EditorInfo.IME_ACTION_SEND
        input.setHorizontallyScrolling(false)
        input.maxLines = 6
        input.gravity = Gravity.TOP or Gravity.START
        input.textSize = 14f
        input.minHeight = ctx.dp(62)
        input.setTextColor(pal.fg)
        input.setHintTextColor(pal.muted)
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
        if (host.mirrorMode) {
            // The user types in the app's own field; this box only shows that text. It never takes focus,
            // so the on-screen keyboard is not touched at all.
            input.isFocusable = false
            input.isFocusableInTouchMode = false
            input.isCursorVisible = false
            input.setTextIsSelectable(false)
        }

        goBtn.background = oval(pal.accent)
        goBtn.isClickable = true
        goBtn.contentDescription = "Go"
        goArrow.text = "\u2191"
        goArrow.textSize = 18f
        goArrow.setTextColor(pal.accentFg)
        goArrow.typeface = Typeface.DEFAULT_BOLD
        goArrow.gravity = Gravity.CENTER
        spinner.isIndeterminate = true
        spinner.indeterminateTintList = ColorStateList.valueOf(pal.accentFg)
        spinner.visibility = View.GONE
        goBtn.addView(goArrow, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        goBtn.addView(spinner, FrameLayout.LayoutParams(ctx.dp(18), ctx.dp(18), Gravity.CENTER))
        goBtn.setOnClickListener { submit() }

        fieldBox.addView(input, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        fieldBox.addView(
            goBtn,
            FrameLayout.LayoutParams(ctx.dp(34), ctx.dp(34), Gravity.END or Gravity.BOTTOM).apply {
                setMargins(0, 0, ctx.dp(7), ctx.dp(7))
            }
        )
        card.addView(fieldBox, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // --- status line ----------------------------------------------------------
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

        // --- result box (fallback when direct insertion is not possible) -----------
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

        // --- frame: room around the card so its shadow is not clipped -----------------
        val cardWidth = minOf(ctx.dp(332), ctx.resources.displayMetrics.widthPixels - ctx.dp(36))
        view.setPadding(ctx.dp(14), ctx.dp(14), ctx.dp(14), ctx.dp(14))
        view.clipToPadding = false
        view.clipChildren = false
        view.addView(card, FrameLayout.LayoutParams(cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT))

        refreshLabels()
        if (prefs.apiConfig() == null) {
            input.hint = "Set up your AI connection to translate."
            showNotice("The AI connection isn\u2019t set up yet.", "Open settings", { host.openSettings() }, false)
        }
    }

    // ---- public API -----------------------------------------------------------------

    fun prefill(text: String, autoSubmit: Boolean) {
        input.setText(text)
        input.setSelection(input.text.length)
        if (autoSubmit) main.post { submit() }
    }

    /** Mirror mode: show what the user is typing in the app's own field. */
    fun setMirrorText(text: String) {
        if (input.text.toString() != text) input.setText(text)
    }

    fun showKeyboard() {
        input.requestFocus()
        ctx.getSystemService(InputMethodManager::class.java)?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
    }

    /** Called by the host when the panel is removed, so late network results are dropped. */
    fun dispose() {
        closed = true
    }

    // ---- bar --------------------------------------------------------------------------

    private fun styleLabel(t: TextView, maxDp: Int) {
        t.textSize = 13f
        t.setTextColor(pal.fg)
        t.typeface = Typeface.DEFAULT_BOLD
        t.gravity = Gravity.CENTER_VERTICAL
        t.setSingleLine(true)
        t.ellipsize = TextUtils.TruncateAt.END
        t.maxWidth = ctx.dp(maxDp)
        t.setPadding(ctx.dp(7), 0, ctx.dp(7), 0)
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
        val toneOnly = prefs.targetLang == Languages.NONE
        sourceBtn.text = (if (prefs.sourceLang == Languages.AUTO) "Auto-detect" else Languages.name(prefs.sourceLang)) + " \u25BE"
        targetBtn.text = Languages.name(prefs.targetLang) + " \u25BE"
        val toneActive = prefs.tone != Tones.DEFAULT
        toneBtn.text = (if (toneActive) Tones.name(prefs.tone) else "Tone") + " \u25BE"
        toneBtn.setTextColor(if (toneActive) pal.accent else pal.fg)
        toneBtn.background = if (toneActive) {
            pressable(ctx, (pal.accent and 0x00FFFFFF) or 0x22000000, pal.hover, 8f, pal.accent)
        } else {
            pressable(ctx, Color.TRANSPARENT, pal.hover, 8f)
        }
        swapBtn.alpha = if (prefs.sourceLang == Languages.AUTO) 0.35f else 1f
        // Tone-only keeps the input language: source language and swap make no sense then.
        sourceBtn.visibility = if (toneOnly) View.GONE else View.VISIBLE
        swapBtn.visibility = if (toneOnly) View.GONE else View.VISIBLE
        input.hint = if (prefs.apiConfig() == null) {
            "Set up your AI connection to translate."
        } else if (host.mirrorMode) {
            "Type in the text field. It appears here."
        } else if (toneOnly) {
            "Type text to rewrite in the chosen tone\u2026"
        } else {
            "Type to translate\u2026"
        }
    }

    private fun swap() {
        val s = prefs.sourceLang
        if (s == Languages.AUTO) return // can't translate "into" auto-detect
        val t = prefs.targetLang
        if (t == Languages.NONE) return
        prefs.sourceLang = t
        prefs.targetLang = s
        refreshLabels()
    }

    private fun openPicker(kind: String) {
        closePicker()
        pickerOpen = true
        savedStatusVis = statusRow.visibility
        savedResultVis = resultScroll.visibility
        val model = if (kind == "tone") toneModel(prefs) else languageModel(prefs, kind)
        val current = when (kind) {
            "source" -> prefs.sourceLang
            "target" -> prefs.targetLang
            else -> prefs.tone
        }
        val pane = OptionPickerPane(ctx, pal, model, current, { code ->
            when (kind) {
                "source" -> prefs.sourceLang = code
                "target" -> prefs.targetLang = code
                else -> prefs.tone = code
            }
            closePicker()
            refreshLabels()
        }, { closePicker() })
        pickerHolder.removeAllViews()
        pickerHolder.addView(pane, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        pickerHolder.visibility = View.VISIBLE
        fieldBox.visibility = View.GONE
        statusRow.visibility = View.GONE
        resultScroll.visibility = View.GONE
        if (host.mirrorMode) host.setFocusable(true) // the search box needs the keyboard
    }

    private fun closePicker() {
        if (!pickerOpen) return
        pickerOpen = false
        pickerHolder.removeAllViews()
        pickerHolder.visibility = View.GONE
        fieldBox.visibility = View.VISIBLE
        statusRow.visibility = savedStatusVis
        resultScroll.visibility = savedResultVis
        if (host.mirrorMode) host.setFocusable(false) else input.requestFocus()
    }

    private fun onBack(): Boolean {
        if (pickerOpen) closePicker() else host.close()
        return true
    }

    // ---- status -------------------------------------------------------------------------

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
        if (b) showNotice("Working\u2026", null, null, false)
    }

    private fun showError(message: String, canRetry: Boolean) {
        setBusy(false)
        showNotice(message, if (canRetry) "Retry" else null, if (canRetry) ({ submit() }) else null, true)
        if (!host.mirrorMode) input.requestFocus()
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

    // ---- translate ------------------------------------------------------------------------

    fun submit() {
        if (busy || closed || pickerOpen) return
        val cfg = prefs.apiConfig()
        if (cfg == null) {
            showNotice("Set up the AI connection first.", "Open settings", { host.openSettings() }, true)
            return
        }
        val src = prefs.sourceLang
        val tgt = prefs.targetLang
        val tone = prefs.tone
        if (tgt == Languages.NONE && tone == Tones.DEFAULT) {
            showNotice("\u201CNo translation\u201D needs a tone. Choose a tone, or pick a target language.", null, null, true)
            return
        }
        val text = input.text.toString().trim()
        if (text.isEmpty()) {
            if (!host.mirrorMode) input.requestFocus()
            return
        }
        setBusy(true)
        Thread {
            var out: String? = null
            var err: ApiException? = null
            try {
                out = AiClient.translate(cfg, text, src, tgt, tone)
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
                    showError(e?.message ?: "Request failed.", retry)
                } else {
                    prefs.pushRecent(tgt)
                    prefs.pushRecent(src)
                    prefs.pushToneRecent(tone)
                    setBusy(false)
                    if (!host.deliver(o)) showResult(o, host.fallbackNote())
                }
            }
        }.start()
    }
}

package com.inlinetranslator

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.TextView
import kotlin.math.hypot

/**
 * Inline translate for any app:
 *  - a small draggable button; you put it anywhere and it stays exactly there
 *  - tapping it (or your trigger key) opens the panel; the panel is draggable too and remembers its place
 *  - the result is inserted at the cursor (or replaces the selection / the whole text in "field" mode)
 * Nothing is animated, and the button never follows the keyboard.
 */
class TranslatorService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: TranslatorService? = null

        /** Call after a setting changed (bubble on/off, trigger, typing mode ...). */
        fun refresh() {
            instance?.onSettingsChanged()
        }
    }

    private val main = Handler(Looper.getMainLooper())
    private var wm: WindowManager? = null
    private var prefs: Prefs? = null
    private var bubble: TextView? = null
    private var panelView: View? = null
    private var panelLp: WindowManager.LayoutParams? = null
    private var panel: TranslatorPanel? = null
    private var mirrorActive = false
    private var target: AccessibilityNodeInfo? = null
    private var hidePending = false
    private var ignoreEventsUntil = 0L
    private var lastScreenW = 0
    private var lastScreenH = 0
    private val detector = TriggerDetector(TriggerConfig())
    private val refreshTask = Runnable { refreshBubble() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs = Prefs(this)
        applyServiceInfo()
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        cleanup()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    private fun cleanup() {
        instance = null
        main.removeCallbacksAndMessages(null)
        closePanel(refresh = false)
        hideBubble()
        target = null
    }

    fun onSettingsChanged() {
        applyServiceInfo()
        scheduleRefresh(0)
    }

    /**
     * Text-change events are only requested while they are needed (trigger on, or "type in the field" mode),
     * so with both off this service does not even receive what you type.
     */
    private fun applyServiceInfo() {
        val p = prefs ?: return
        val info = serviceInfo ?: return
        val trig = p.trigger
        val triggerOn = trig.enabled && trig.char.isNotEmpty()
        val wantText = triggerOn || (panelView != null && mirrorActive)
        var types = AccessibilityEvent.TYPE_VIEW_FOCUSED or
            AccessibilityEvent.TYPE_WINDOWS_CHANGED or
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        if (wantText) types = types or AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
        info.eventTypes = types
        info.notificationTimeout = if (wantText) 0 else 100 // no throttling, or fast double presses get lost
        serviceInfo = info
        detector.setConfig(if (triggerOn) trig else trig.copy(enabled = false))
    }

    // ---- events ----------------------------------------------------------------------------

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || prefs == null) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> handleTextChanged(event)
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                if (panelView != null) return // our own panel
                if (event.packageName?.toString() == packageName && ProcessTextActivity.active) return
                val n = event.source
                target = if (n != null && n.isEditable && !n.isPassword) n else null
                scheduleRefresh(120)
            }
            else -> if (panelView == null) scheduleRefresh(120)
        }
    }

    private fun handleTextChanged(event: AccessibilityEvent) {
        if (event.isPassword) return
        val now = SystemClock.uptimeMillis()
        if (now < ignoreEventsUntil) return
        if (event.packageName?.toString() == packageName && ProcessTextActivity.active) return

        if (panelView != null) {
            // "Type in the field" mode: show the field's text inside the panel.
            if (mirrorActive) {
                val src = event.source
                if (src != null && src == target) {
                    panel?.setMirrorText(event.text?.firstOrNull()?.toString() ?: "")
                }
            }
            return
        }

        val cfg = detector.cfg
        if (!cfg.enabled) return
        val before = event.beforeText?.toString() ?: return
        val after = event.text?.firstOrNull()?.toString() ?: return
        if (detector.onTextChanged(before, after, now) == Hit.COMPLETED) {
            val src = event.source ?: return
            if (!src.isEditable || src.isPassword) return
            target = src
            eraseTrigger(src, cfg)
            main.postDelayed({ openPanel() }, 80)
        }
    }

    /** Removes the trigger characters that were just typed (they sit right before the cursor). */
    private fun eraseTrigger(node: AccessibilityNodeInfo, cfg: TriggerConfig) {
        try {
            if (!node.refresh()) return
            val text = node.text?.toString() ?: return
            val run = cfg.char.repeat(cfg.pressCount)
            val cursor = node.textSelectionEnd
            val end = if (cursor in 0..text.length) cursor else text.length
            val start = end - run.length
            if (start < 0 || text.substring(start, end) != run) return
            ignoreEventsUntil = SystemClock.uptimeMillis() + 500
            val args = Bundle()
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text.removeRange(start, end))
            if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                setCursor(node, start, start)
            }
        } catch (e: Exception) {
            // leave the typed characters in place
        }
    }

    fun scheduleRefresh(delayMs: Long) {
        main.removeCallbacks(refreshTask)
        main.postDelayed(refreshTask, delayMs)
    }

    // ---- screen helpers -----------------------------------------------------------------------

    private fun screenSize(): Point {
        val m = wm ?: return Point(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
        return if (Build.VERSION.SDK_INT >= 30) {
            val b = m.maximumWindowMetrics.bounds
            Point(b.width(), b.height())
        } else {
            val p = Point()
            @Suppress("DEPRECATION")
            m.defaultDisplay.getRealSize(p)
            p
        }
    }

    private fun imeVisible(): Boolean {
        return try {
            windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
        } catch (e: Exception) {
            false
        }
    }

    // ---- target field ---------------------------------------------------------------------------

    /** The focused, editable, non-password field, or null. */
    private fun currentTarget(): AccessibilityNodeInfo? {
        val t = target
        if (t != null) {
            val ok = try {
                t.refresh() && t.isEditable && t.isFocused
            } catch (e: Exception) {
                false
            }
            if (ok) return t
        }
        val f = try {
            rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        } catch (e: Exception) {
            null
        }
        if (f != null && f.isEditable && !f.isPassword) {
            target = f
            return f
        }
        target = null
        return null
    }

    // ---- floating button ----------------------------------------------------------------------------

    private fun refreshBubble() {
        val p = prefs ?: return
        if (panelView != null || !p.bubbleEnabled) {
            hideBubble()
            return
        }
        val shouldShow = imeVisible() && currentTarget() != null
        if (shouldShow) {
            hidePending = false
            showBubble(p)
        } else if (bubble != null && !hidePending) {
            // Short grace period: the keyboard animation fires many events and the field can lose focus for a moment.
            hidePending = true
            main.postDelayed({
                hidePending = false
                if (panelView != null || !(imeVisible() && currentTarget() != null)) hideBubble()
            }, 450)
        }
    }

    private fun bubblePixelPos(p: Prefs, size: Int): Point {
        val s = screenSize()
        val pos = p.bubblePos() ?: (1f to 0.6f)
        return Point(
            (pos.first * (s.x - size)).toInt().coerceIn(0, (s.x - size).coerceAtLeast(0)),
            (pos.second * (s.y - size)).toInt().coerceIn(0, (s.y - size).coerceAtLeast(0))
        )
    }

    private fun showBubble(p: Prefs) {
        val manager = wm ?: return
        val size = dp(44)
        val s = screenSize()
        val existing = bubble
        if (existing != null) {
            // Keep position; only re-apply it when the screen changed (rotation).
            if (s.x != lastScreenW || s.y != lastScreenH) {
                lastScreenW = s.x
                lastScreenH = s.y
                val lp = existing.layoutParams as WindowManager.LayoutParams
                val pt = bubblePixelPos(p, size)
                lp.x = pt.x
                lp.y = pt.y
                try {
                    manager.updateViewLayout(existing, lp)
                } catch (e: Exception) {
                }
            }
            return
        }
        lastScreenW = s.x
        lastScreenH = s.y
        val pal = p.palette(this)
        val v = TextView(this)
        v.text = "\u6587A"
        v.textSize = 15f
        v.setTextColor(pal.accentFg)
        v.typeface = Typeface.DEFAULT_BOLD
        v.gravity = Gravity.CENTER
        v.background = oval(pal.accent)
        v.contentDescription = "Translate. Drag to move."
        v.setOnClickListener { openPanel() }

        val lp = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.windowAnimations = R.style.NoAnim // appears instantly, no slide/fade
        val pt = bubblePixelPos(p, size)
        lp.x = pt.x
        lp.y = pt.y

        val slop = ViewConfiguration.get(this).scaledTouchSlop
        v.setOnTouchListener(object : View.OnTouchListener {
            var downX = 0f
            var downY = 0f
            var startX = 0
            var startY = 0
            var moved = false

            override fun onTouch(view: View, e: MotionEvent): Boolean {
                val params = view.layoutParams as WindowManager.LayoutParams
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = e.rawX
                        downY = e.rawY
                        startX = params.x
                        startY = params.y
                        moved = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = e.rawX - downX
                        val dy = e.rawY - downY
                        if (!moved && hypot(dx, dy) > slop) {
                            moved = true
                            view.alpha = 0.75f
                        }
                        if (moved) {
                            val sc = screenSize()
                            params.x = (startX + dx).toInt().coerceIn(0, (sc.x - size).coerceAtLeast(0))
                            params.y = (startY + dy).toInt().coerceIn(0, (sc.y - size).coerceAtLeast(0))
                            try {
                                manager.updateViewLayout(view, params)
                            } catch (ex: Exception) {
                            }
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        view.alpha = 1f
                        if (moved) {
                            val sc = screenSize()
                            val rx = (sc.x - size).coerceAtLeast(1)
                            val ry = (sc.y - size).coerceAtLeast(1)
                            p.saveBubblePos(params.x.toFloat() / rx, params.y.toFloat() / ry)
                        } else if (e.actionMasked == MotionEvent.ACTION_UP) {
                            view.performClick()
                        }
                        return true
                    }
                }
                return false
            }
        })
        try {
            manager.addView(v, lp)
            bubble = v
        } catch (e: Exception) {
            // overlay could not be added
        }
    }

    private fun hideBubble() {
        val b = bubble ?: return
        bubble = null
        try {
            wm?.removeView(b)
        } catch (e: Exception) {
        }
    }

    // ---- panel -----------------------------------------------------------------------------------------

    private fun openPanel() {
        val p = prefs ?: return
        val manager = wm ?: return
        if (panelView != null) return
        val node = currentTarget() ?: return
        val mirror = p.typingMode == "field"
        val selection = selectedTextOf(node)
        val fieldText = fieldTextOf(node)
        hideBubble()

        var dragStartX = 0
        var dragStartY = 0
        val host = object : TranslatorPanel.Host {
            override fun deliver(text: String): Boolean {
                val ok = if (mirror) replaceAllText(text) else insertIntoTarget(text)
                if (ok) main.post { closePanel() }
                return ok
            }

            override fun close() {
                closePanel()
            }

            override fun openSettings() {
                closePanel()
                startActivity(Intent(this@TranslatorService, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }

            override fun fallbackNote(): String = "Couldn\u2019t put it into this field. Copy the result:"

            override val draggable: Boolean get() = true
            override val mirrorMode: Boolean get() = mirror

            override fun dragStart() {
                val lp = panelLp ?: return
                dragStartX = lp.x
                dragStartY = lp.y
            }

            override fun dragBy(dx: Int, dy: Int) {
                val lp = panelLp ?: return
                val v = panelView ?: return
                val sc = screenSize()
                lp.x = (dragStartX + dx).coerceIn(0, (sc.x - v.width).coerceAtLeast(0))
                lp.y = (dragStartY + dy).coerceIn(0, (sc.y - v.height).coerceAtLeast(0))
                try {
                    wm?.updateViewLayout(v, lp)
                } catch (e: Exception) {
                }
            }

            override fun dragEnd() {
                val lp = panelLp ?: return
                val v = panelView ?: return
                val sc = screenSize()
                val rx = (sc.x - v.width).coerceAtLeast(1)
                val ry = (sc.y - v.height).coerceAtLeast(1)
                p.savePanelPos(lp.x.toFloat() / rx, lp.y.toFloat() / ry)
            }

            override fun setFocusable(focusable: Boolean) {
                val lp = panelLp ?: return
                val v = panelView ?: return
                lp.flags = if (focusable) {
                    lp.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                } else {
                    lp.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                }
                try {
                    wm?.updateViewLayout(v, lp)
                } catch (e: Exception) {
                }
            }
        }
        val tp = TranslatorPanel(this, p, host)

        // Box mode: the panel window takes the input focus (needed to type in it) but is told to leave the
        // keyboard state alone. Field mode: the panel is not focusable at all, so the keyboard is never touched.
        var flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (mirror) flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.windowAnimations = R.style.NoAnim
        @Suppress("DEPRECATION")
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED or
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING

        tp.view.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val sc = screenSize()
        val w = tp.view.measuredWidth
        val h = tp.view.measuredHeight
        val saved = p.panelPos()
        if (saved != null) {
            lp.x = (saved.first * (sc.x - w)).toInt().coerceIn(0, (sc.x - w).coerceAtLeast(0))
            lp.y = (saved.second * (sc.y - h)).toInt().coerceIn(0, (sc.y - h).coerceAtLeast(0))
        } else {
            lp.x = ((sc.x - w) / 2).coerceAtLeast(0)
            lp.y = (sc.y * 0.12f).toInt()
        }

        try {
            manager.addView(tp.view, lp)
        } catch (e: Exception) {
            tp.dispose()
            scheduleRefresh(0)
            return
        }
        panel = tp
        panelView = tp.view
        panelLp = lp
        mirrorActive = mirror
        if (mirror) {
            applyServiceInfo() // start listening to text changes of the field
            tp.setMirrorText(fieldText)
        } else {
            if (selection.isNotEmpty()) tp.prefill(selection, autoSubmit = false)
            // Only ask for the keyboard if it is not already up; asking again is what makes it flicker.
            if (!imeVisible()) main.postDelayed({ tp.showKeyboard() }, 150)
        }
    }

    private fun closePanel(refresh: Boolean = true) {
        panel?.dispose()
        panel = null
        val v = panelView
        panelView = null
        panelLp = null
        if (v != null) {
            try {
                wm?.removeView(v)
            } catch (e: Exception) {
            }
        }
        if (mirrorActive) {
            mirrorActive = false
            applyServiceInfo()
        }
        if (refresh) scheduleRefresh(400)
    }

    // ---- reading / writing the target field ---------------------------------------------------------------

    private fun fieldTextOf(node: AccessibilityNodeInfo): String {
        return try {
            if (node.isShowingHintText) "" else (node.text?.toString() ?: "")
        } catch (e: Exception) {
            ""
        }
    }

    private fun selectedTextOf(node: AccessibilityNodeInfo): String {
        return try {
            if (node.isShowingHintText) return ""
            val t = node.text?.toString() ?: return ""
            val s = node.textSelectionStart
            val e = node.textSelectionEnd
            if (s in 0 until e && e <= t.length) t.substring(s, e) else ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun setCursor(node: AccessibilityNodeInfo, start: Int, end: Int) {
        val sel = Bundle()
        sel.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, start)
        sel.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, end)
        node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, sel)
    }

    /** Inserts at the cursor, or replaces the selection. Returns false if the app refuses. */
    private fun insertIntoTarget(text: String): Boolean {
        val node = target ?: return false
        try {
            if (!node.refresh()) return false
        } catch (e: Exception) {
            return false
        }
        if (!node.isEditable) return false

        val base = fieldTextOf(node)
        var start = node.textSelectionStart
        var end = node.textSelectionEnd
        if (node.isShowingHintText || start < 0 || end < 0) {
            start = base.length
            end = base.length
        }
        val lo = minOf(start, end).coerceIn(0, base.length)
        val hi = maxOf(start, end).coerceIn(0, base.length)
        return writeText(node, base.substring(0, lo) + text + base.substring(hi), lo + text.length, text, lo, hi)
    }

    /** "Type in the field" mode: the translation replaces everything in the field. */
    private fun replaceAllText(text: String): Boolean {
        val node = target ?: return false
        try {
            if (!node.refresh()) return false
        } catch (e: Exception) {
            return false
        }
        if (!node.isEditable) return false
        val len = fieldTextOf(node).length
        return writeText(node, text, text.length, text, 0, len)
    }

    private fun writeText(node: AccessibilityNodeInfo, newText: String, cursor: Int, pasteText: String, lo: Int, hi: Int): Boolean {
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
        if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
            setCursor(node, cursor, cursor)
            return true
        }
        return insertViaPaste(node, pasteText, lo, hi)
    }

    /** Fallback for editors that ignore ACTION_SET_TEXT. Overwrites the clipboard. */
    private fun insertViaPaste(node: AccessibilityNodeInfo, text: String, lo: Int, hi: Int): Boolean {
        val cm = getSystemService(ClipboardManager::class.java) ?: return false
        cm.setPrimaryClip(ClipData.newPlainText("translation", text))
        setCursor(node, lo, hi)
        return node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
    }
}

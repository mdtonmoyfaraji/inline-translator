package com.inlinetranslator

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.TextView

/**
 * Gboard-style inline translate for any app:
 *  - when a text field is focused and the keyboard is open, a small translate button floats above the keyboard
 *  - tapping it opens the same panel as the browser extension
 *  - after you press the arrow, the translation is inserted at the cursor (or replaces the selection)
 */
class TranslatorService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: TranslatorService? = null

        /** Call after a setting changed (e.g. the bubble was switched off). */
        fun refresh() {
            instance?.scheduleRefresh(0)
        }
    }

    private val main = Handler(Looper.getMainLooper())
    private var wm: WindowManager? = null
    private var prefs: Prefs? = null
    private var bubble: TextView? = null
    private var panelView: View? = null
    private var panel: TranslatorPanel? = null
    private var target: AccessibilityNodeInfo? = null
    private val refreshTask = Runnable { refreshBubble() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs = Prefs(this)
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
        main.removeCallbacks(refreshTask)
        closePanel(refresh = false)
        hideBubble()
        target = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || prefs == null) return
        if (panelView != null) return // our own panel is open
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED) {
            if (event.packageName?.toString() == packageName && ProcessTextActivity.active) return
            val n = event.source
            target = if (n != null && n.isEditable && !n.isPassword) n else null
        }
        scheduleRefresh(120)
    }

    fun scheduleRefresh(delayMs: Long) {
        main.removeCallbacks(refreshTask)
        main.postDelayed(refreshTask, delayMs)
    }

    // ---- floating button ------------------------------------------------------------

    private fun imeTop(): Int? {
        return try {
            val w = windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD } ?: return null
            val r = Rect()
            w.getBoundsInScreen(r)
            r.top
        } catch (e: Exception) {
            null
        }
    }

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

    private fun refreshBubble() {
        val p = prefs ?: return
        if (panelView != null || !p.bubbleEnabled) {
            hideBubble()
            return
        }
        val top = imeTop()
        if (top == null || currentTarget() == null) {
            hideBubble()
            return
        }
        showBubble(top, p)
    }

    private fun showBubble(imeTop: Int, p: Prefs) {
        val manager = wm ?: return
        val size = dp(44)
        val x = resources.displayMetrics.widthPixels - size - dp(12)
        val y = imeTop - size - dp(10)
        val existing = bubble
        if (existing == null) {
            val pal = p.palette(this)
            val v = TextView(this)
            v.text = "\u6587A"
            v.textSize = 15f
            v.setTextColor(pal.accentFg)
            v.typeface = Typeface.DEFAULT_BOLD
            v.gravity = Gravity.CENTER
            v.background = oval(pal.accent)
            v.contentDescription = "Translate"
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
            lp.x = x
            lp.y = y
            try {
                manager.addView(v, lp)
                bubble = v
            } catch (e: Exception) {
                // overlay could not be added; nothing else to do
            }
        } else {
            val lp = existing.layoutParams as WindowManager.LayoutParams
            if (lp.x != x || lp.y != y) {
                lp.x = x
                lp.y = y
                try {
                    manager.updateViewLayout(existing, lp)
                } catch (e: Exception) {
                }
            }
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

    // ---- panel --------------------------------------------------------------------------

    private fun openPanel() {
        val p = prefs ?: return
        val manager = wm ?: return
        if (panelView != null) return
        val node = currentTarget() ?: return
        val selection = selectedTextOf(node)
        hideBubble()

        val host = object : TranslatorPanel.Host {
            override fun deliver(text: String): Boolean {
                val ok = insertIntoTarget(text)
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

            override fun fallbackNote(): String = "Couldn\u2019t insert into this field. Copy the translation:"
        }
        val tp = TranslatorPanel(this, p, host)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        lp.y = (resources.displayMetrics.heightPixels * 0.12f).toInt()
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
        try {
            manager.addView(tp.view, lp)
        } catch (e: Exception) {
            tp.dispose()
            scheduleRefresh(0)
            return
        }
        panel = tp
        panelView = tp.view
        if (selection.isNotEmpty()) tp.prefill(selection, autoSubmit = false)
        main.postDelayed({ tp.showKeyboard() }, 150)
    }

    private fun closePanel(refresh: Boolean = true) {
        panel?.dispose()
        panel = null
        val v = panelView
        panelView = null
        if (v != null) {
            try {
                wm?.removeView(v)
            } catch (e: Exception) {
            }
        }
        if (refresh) scheduleRefresh(400)
    }

    // ---- reading / writing the target field --------------------------------------------

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

    /** Inserts at the cursor, or replaces the selection. Returns false if the app refuses. */
    private fun insertIntoTarget(text: String): Boolean {
        val node = target ?: return false
        try {
            if (!node.refresh()) return false
        } catch (e: Exception) {
            return false
        }
        if (!node.isEditable) return false

        val base = if (node.isShowingHintText) "" else (node.text?.toString() ?: "")
        var start = node.textSelectionStart
        var end = node.textSelectionEnd
        if (node.isShowingHintText || start < 0 || end < 0) {
            start = base.length
            end = base.length
        }
        val lo = minOf(start, end).coerceIn(0, base.length)
        val hi = maxOf(start, end).coerceIn(0, base.length)

        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle()
        args.putCharSequence(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
            base.substring(0, lo) + text + base.substring(hi)
        )
        if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
            val pos = lo + text.length
            val sel = Bundle()
            sel.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, pos)
            sel.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, pos)
            node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, sel)
            return true
        }
        return insertViaPaste(node, text, lo, hi)
    }

    /** Fallback for editors that ignore ACTION_SET_TEXT. Overwrites the clipboard. */
    private fun insertViaPaste(node: AccessibilityNodeInfo, text: String, lo: Int, hi: Int): Boolean {
        val cm = getSystemService(ClipboardManager::class.java) ?: return false
        cm.setPrimaryClip(ClipData.newPlainText("translation", text))
        val sel = Bundle()
        sel.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, lo)
        sel.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, hi)
        node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, sel)
        return node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
    }
}

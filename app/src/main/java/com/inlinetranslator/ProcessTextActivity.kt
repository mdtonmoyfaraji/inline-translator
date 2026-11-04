package com.inlinetranslator

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout

/**
 * Select text in any app -> menu -> "Inline Translator".
 * Editable text is replaced by the translation; read-only text shows the translation with a Copy button.
 */
class ProcessTextActivity : Activity() {
    companion object {
        @Volatile
        var active = false
    }

    private var panel: TranslatorPanel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val selected = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
        val readOnly = intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)
        val prefs = Prefs(this)

        val host = object : TranslatorPanel.Host {
            override fun deliver(text: String): Boolean {
                if (readOnly) return false
                setResult(RESULT_OK, Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, text))
                finish()
                return true
            }

            override fun close() {
                setResult(RESULT_CANCELED)
                finish()
            }

            override fun openSettings() {
                startActivity(Intent(this@ProcessTextActivity, MainActivity::class.java))
                finish()
            }

            override fun fallbackNote(): String =
                if (readOnly) "This text is read-only here. Copy the translation:" else "Couldn\u2019t replace the text. Copy the translation:"
        }

        val p = TranslatorPanel(this, prefs, host)
        panel = p
        p.view.isClickable = true // taps on the panel must not reach the dimmed backdrop

        val backdrop = FrameLayout(this)
        backdrop.setBackgroundColor(0x33000000)
        backdrop.setOnClickListener { host.close() }
        backdrop.addView(
            p.view,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
                .apply { topMargin = dp(40) }
        )
        setContentView(backdrop)

        if (!selected.isNullOrBlank()) {
            p.prefill(selected, autoSubmit = prefs.apiConfig() != null)
        }
    }

    override fun onStart() {
        super.onStart()
        active = true
    }

    override fun onStop() {
        super.onStop()
        active = false
    }

    override fun onDestroy() {
        panel?.dispose()
        super.onDestroy()
    }
}

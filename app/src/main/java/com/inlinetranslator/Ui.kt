package com.inlinetranslator

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.TypedValue
import android.widget.ScrollView
import android.widget.TextView

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

/** Same colours as the extension's panel and options page. */
class Palette(
    val bg: Int, val fg: Int, val muted: Int, val line: Int, val field: Int, val hover: Int,
    val accent: Int, val accentFg: Int, val danger: Int, val dangerBg: Int,
    val ok: Int, val okBg: Int, val warn: Int, val warnBg: Int
) {
    companion object {
        private fun c(s: String) = Color.parseColor(s)

        val LIGHT: Palette by lazy {
            Palette(
                c("#fbfaf8"), c("#1d2024"), c("#686d76"), c("#e2e0db"), c("#ffffff"), c("#f0eee9"),
                c("#0b6b5e"), c("#ffffff"), c("#b42318"), c("#fdecea"),
                c("#11772f"), c("#e5f5ea"), c("#8a5a00"), c("#fff4d6")
            )
        }
        val DARK: Palette by lazy {
            Palette(
                c("#1f2125"), c("#ecebe8"), c("#9aa0a9"), c("#34373d"), c("#17191c"), c("#2b2e33"),
                c("#3fc3ae"), c("#0b1f1b"), c("#ff8a80"), c("#3a1f1d"),
                c("#6fd58b"), c("#173223"), c("#f3c969"), c("#3a3017")
            )
        }
    }
}

fun shape(ctx: Context, fill: Int, radiusDp: Float, stroke: Int = 0, strokeDp: Int = 1): GradientDrawable =
    GradientDrawable().apply {
        setColor(fill)
        cornerRadius = radiusDp * ctx.resources.displayMetrics.density
        if (stroke != 0) setStroke(ctx.dp(strokeDp), stroke)
    }

fun oval(fill: Int): GradientDrawable = GradientDrawable().apply {
    shape = GradientDrawable.OVAL
    setColor(fill)
}

fun pressable(ctx: Context, fill: Int, pressed: Int, radiusDp: Float, stroke: Int = 0): StateListDrawable =
    StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), shape(ctx, pressed, radiusDp, stroke))
        addState(intArrayOf(), shape(ctx, fill, radiusDp, stroke))
    }

fun tv(ctx: Context, s: CharSequence, sp: Float, color: Int, bold: Boolean = false): TextView =
    TextView(ctx).apply {
        text = s
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

/** ScrollView that never grows taller than [maxPx]. */
class MaxHeightScrollView(c: Context, private val maxPx: Int) : ScrollView(c) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(maxPx, MeasureSpec.AT_MOST))
    }
}

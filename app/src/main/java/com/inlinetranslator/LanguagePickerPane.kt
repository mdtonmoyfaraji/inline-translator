package com.inlinetranslator

import android.content.Context
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Searchable language list: Detect (source only), Favorites, Recent, All languages,
 * plus "use any language name" for languages that are not in the list. Star = favorite.
 */
class LanguagePickerPane(
    c: Context,
    private val pal: Palette,
    private val prefs: Prefs,
    private val mode: String, // "source" | "target"
    private val current: String,
    private val onPick: (String) -> Unit,
    private val onCancel: () -> Unit
) : LinearLayout(c) {

    private val search = EditText(c)
    private val list = LinearLayout(c)
    private var firstResult: String? = null

    init {
        orientation = VERTICAL

        search.hint = if (mode == "source") "Translate from\u2026" else "Translate to\u2026"
        search.setSingleLine(true)
        search.textSize = 14f
        search.setTextColor(pal.fg)
        search.setHintTextColor(pal.muted)
        search.inputType = InputType.TYPE_CLASS_TEXT
        search.imeOptions = EditorInfo.IME_ACTION_DONE
        search.background = shape(c, pal.field, 8f, pal.line)
        search.setPadding(c.dp(10), c.dp(8), c.dp(10), c.dp(8))
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                render(s?.toString() ?: "")
            }
        })
        search.setOnEditorActionListener { _, _, _ ->
            firstResult?.let { onPick(it) }
            true
        }
        addView(search, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        list.orientation = VERTICAL
        val scroll = MaxHeightScrollView(c, c.dp(260))
        scroll.addView(list, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(
            scroll,
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = c.dp(6) }
        )

        render("")
        search.post { search.requestFocus() }
    }

    private fun header(title: String) {
        val h = tv(context, title, 11f, pal.muted, bold = true)
        h.setPadding(context.dp(8), context.dp(10), context.dp(8), context.dp(2))
        list.addView(h)
    }

    private fun addRow(code: String) {
        if (firstResult == null) firstResult = code
        val ctx = context
        val fav = code in prefs.favorites
        val selected = code == current

        val row = LinearLayout(ctx)
        row.orientation = HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(ctx.dp(8), ctx.dp(2), ctx.dp(2), ctx.dp(2))
        row.minimumHeight = ctx.dp(44)
        row.background = pressable(ctx, 0x00000000, pal.hover, 7f)
        row.isClickable = true
        row.setOnClickListener { onPick(code) }

        val name = tv(ctx, Languages.name(code), 14f, if (selected) pal.accent else pal.fg, bold = selected)
        name.setSingleLine(true)
        name.ellipsize = android.text.TextUtils.TruncateAt.END
        row.addView(name, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))

        val nat = Languages.nativeName(code)
        if (nat.isNotEmpty() && !nat.equals(Languages.name(code), ignoreCase = true)) {
            val n = tv(ctx, nat, 12f, pal.muted)
            n.setSingleLine(true)
            n.ellipsize = android.text.TextUtils.TruncateAt.END
            n.maxWidth = ctx.dp(110)
            n.setPadding(ctx.dp(6), 0, 0, 0)
            row.addView(n, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        }

        if (code != Languages.AUTO) {
            val star = tv(ctx, if (fav) "\u2605" else "\u2606", 18f, if (fav) pal.accent else pal.muted)
            star.gravity = Gravity.CENTER
            star.contentDescription = if (fav) "Remove ${Languages.name(code)} from favorites" else "Add ${Languages.name(code)} to favorites"
            star.setOnClickListener {
                prefs.toggleFavorite(code)
                render(search.text.toString())
            }
            row.addView(star, LinearLayout.LayoutParams(ctx.dp(40), ctx.dp(40)))
        }
        list.addView(row, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    private fun addCustomRow(query: String) {
        val ctx = context
        val code = Languages.makeCustom(query)
        if (firstResult == null) firstResult = code
        val row = tv(ctx, "Use \u201C${query.trim().take(40)}\u201D as the language", 14f, pal.accent)
        row.setPadding(ctx.dp(8), ctx.dp(12), ctx.dp(8), ctx.dp(12))
        row.background = pressable(ctx, 0x00000000, pal.hover, 7f)
        row.isClickable = true
        row.setOnClickListener { onPick(code) }
        list.addView(row, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    private fun render(q: String) {
        list.removeAllViews()
        firstResult = null
        val query = q.trim()
        val favs = prefs.favorites
        if (query.isEmpty()) {
            if (mode == "source") addRow(Languages.AUTO)
            if (favs.isNotEmpty()) {
                header("Favorites")
                favs.forEach { addRow(it) }
            }
            val recent = prefs.recent.filter { it !in favs }
            if (recent.isNotEmpty()) {
                header("Recent")
                recent.forEach { addRow(it) }
            }
            header("All languages")
            Languages.ALL.forEach { addRow(it.code) }
        } else {
            if (mode == "source" && "detect language".startsWith(query.lowercase())) addRow(Languages.AUTO)
            val res = Languages.search(query)
            res.forEach { addRow(it.code) }
            val exact = res.any { it.name.equals(query, true) || it.native.equals(query, true) }
            if (!exact && query.length <= 40) addCustomRow(query)
        }
    }

    fun cancel() = onCancel()
}

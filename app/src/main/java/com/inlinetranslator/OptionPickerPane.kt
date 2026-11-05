package com.inlinetranslator

import android.content.Context
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout

class PickItem(val code: String, val label: String, val sub: String? = null, val starrable: Boolean = false)

/** Describes one kind of list (source language, target language, tone) for [OptionPickerPane]. */
interface PickerModel {
    val placeholder: String
    val pinned: List<PickItem>
    val all: List<PickItem>
    val allGroupLabel: String
    fun resolve(code: String): PickItem?
    fun search(q: String): List<PickItem>
    fun custom(q: String, found: List<PickItem>): PickItem?
    fun favorites(): List<String>
    fun recent(): List<String>
    fun toggleFavorite(code: String)
}

private fun langItem(l: Language) =
    PickItem(l.code, l.name, if (!l.native.equals(l.name, true)) l.native else null, true)

fun languageModel(prefs: Prefs, mode: String): PickerModel = object : PickerModel {
    override val placeholder = if (mode == "source") "Translate from\u2026" else "Translate to\u2026"
    override val pinned = if (mode == "source") {
        listOf(PickItem(Languages.AUTO, "Detect language"))
    } else {
        listOf(PickItem(Languages.NONE, "No translation", "tone only"))
    }
    override val all = Languages.ALL.map { langItem(it) }
    override val allGroupLabel = "All languages"
    override fun resolve(code: String): PickItem? {
        Languages.find(code)?.let { return langItem(it) }
        return if (code.startsWith(Languages.CUSTOM_PREFIX)) PickItem(code, Languages.name(code), "custom", true) else null
    }
    override fun search(q: String) = Languages.search(q).map { langItem(it) }
    override fun custom(q: String, found: List<PickItem>): PickItem? {
        val exact = found.any { it.label.equals(q, true) }
        return if (!exact && q.length <= 40) PickItem(Languages.makeCustom(q), "Use \u201C$q\u201D", "custom") else null
    }
    override fun favorites() = prefs.favorites
    override fun recent() = prefs.recent
    override fun toggleFavorite(code: String) = prefs.toggleFavorite(code)
}

private fun toneItem(t: Tone) = PickItem(t.code, t.name, t.hint, true)

fun toneModel(prefs: Prefs): PickerModel = object : PickerModel {
    override val placeholder = "Search or type your own tone\u2026"
    override val pinned = listOf(PickItem(Tones.DEFAULT, "Default", "no tone"))
    override val all = Tones.ALL.map { toneItem(it) }
    override val allGroupLabel = "All tones"
    override fun resolve(code: String): PickItem? {
        Tones.find(code)?.let { return toneItem(it) }
        return if (Tones.isCustom(code)) PickItem(code, Tones.name(code), "custom", true) else null
    }
    override fun search(q: String) = Tones.search(q).map { toneItem(it) }
    override fun custom(q: String, found: List<PickItem>): PickItem? {
        val exact = found.any { it.label.equals(q, true) }
        if (exact || q.length > Tones.MAX_CUSTOM) return null
        return PickItem(Tones.makeCustom(q), "Custom tone: \u201C$q\u201D", "custom")
    }
    override fun favorites() = prefs.toneFavorites
    override fun recent() = prefs.toneRecent
    override fun toggleFavorite(code: String) = prefs.toggleToneFavorite(code)
}

/**
 * Searchable list: pinned entry, Favorites, Recent, All. Star = favorite.
 * Typing something that is not in the list offers it as a custom entry (any language / any tone text).
 */
class OptionPickerPane(
    c: Context,
    private val pal: Palette,
    private val model: PickerModel,
    private val current: String,
    private val onPick: (String) -> Unit,
    private val onCancel: () -> Unit,
    /** If set, the search box does not take the keyboard until it is tapped (then this is called first). */
    private val onSearchFocus: (() -> Unit)? = null
) : LinearLayout(c) {

    private val search = EditText(c)
    private val list = LinearLayout(c)
    private var firstResult: String? = null

    init {
        orientation = VERTICAL

        search.hint = model.placeholder
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
        addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        list.orientation = VERTICAL
        val scroll = MaxHeightScrollView(c, c.dp(260))
        scroll.addView(list, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(
            scroll,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = c.dp(6) }
        )

        render("")
        if (onSearchFocus == null) {
            search.post { search.requestFocus() }
        } else {
            // Keyboard stays exactly as it is until you tap the search box.
            search.isFocusable = false
            search.isFocusableInTouchMode = false
            search.isCursorVisible = false
            search.setOnClickListener {
                onSearchFocus.invoke()
                search.isFocusable = true
                search.isFocusableInTouchMode = true
                search.isCursorVisible = true
                search.requestFocus()
                context.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                    ?.showSoftInput(search, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    private fun header(title: String) {
        val h = tv(context, title, 11f, pal.muted, bold = true)
        h.setPadding(context.dp(8), context.dp(10), context.dp(8), context.dp(2))
        list.addView(h)
    }

    private fun addRow(item: PickItem) {
        if (firstResult == null) firstResult = item.code
        val ctx = context
        val fav = item.code in model.favorites()
        val selected = item.code == current

        val row = LinearLayout(ctx)
        row.orientation = HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(ctx.dp(8), ctx.dp(2), ctx.dp(2), ctx.dp(2))
        row.minimumHeight = ctx.dp(44)
        row.background = pressable(ctx, 0x00000000, pal.hover, 7f)
        row.isClickable = true
        row.setOnClickListener { onPick(item.code) }

        val name = tv(ctx, item.label, 14f, if (selected) pal.accent else pal.fg, bold = selected)
        name.setSingleLine(true)
        name.ellipsize = TextUtils.TruncateAt.END
        row.addView(name, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        if (!item.sub.isNullOrEmpty()) {
            val n = tv(ctx, item.sub, 12f, pal.muted)
            n.setSingleLine(true)
            n.ellipsize = TextUtils.TruncateAt.END
            n.maxWidth = ctx.dp(110)
            n.setPadding(ctx.dp(6), 0, 0, 0)
            row.addView(n, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        if (item.starrable) {
            val star = tv(ctx, if (fav) "\u2605" else "\u2606", 18f, if (fav) pal.accent else pal.muted)
            star.gravity = Gravity.CENTER
            star.contentDescription = if (fav) "Remove ${item.label} from favorites" else "Add ${item.label} to favorites"
            star.setOnClickListener {
                model.toggleFavorite(item.code)
                render(search.text.toString())
            }
            row.addView(star, LinearLayout.LayoutParams(ctx.dp(40), ctx.dp(40)))
        }
        list.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun render(q: String) {
        list.removeAllViews()
        firstResult = null
        val query = q.trim()
        if (query.isEmpty()) {
            model.pinned.forEach { addRow(it) }
            val favs = model.favorites()
            val favItems = favs.mapNotNull { model.resolve(it) }
            if (favItems.isNotEmpty()) {
                header("Favorites")
                favItems.forEach { addRow(it) }
            }
            val recentItems = model.recent().filter { it !in favs }.mapNotNull { model.resolve(it) }
            if (recentItems.isNotEmpty()) {
                header("Recent")
                recentItems.forEach { addRow(it) }
            }
            header(model.allGroupLabel)
            model.all.forEach { addRow(it) }
        } else {
            model.pinned.filter { it.label.lowercase().startsWith(query.lowercase()) }.forEach { addRow(it) }
            val found = model.search(query)
            found.forEach { addRow(it) }
            model.custom(query, found)?.let { addRow(it) }
        }
    }

    fun cancel() = onCancel()
}

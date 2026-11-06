package com.inlinetranslator

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import org.json.JSONArray
import java.util.Locale

/** All settings live in app-private storage on this device. */
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("inline_translator_prefs", Context.MODE_PRIVATE)

    // ---- connection (falls back to the keys of the very first app version) ----
    var baseUrl: String
        get() = sp.getString("base_url", null) ?: sp.getString("api_url", null) ?: ""
        set(v) { sp.edit().putString("base_url", v.trim()).apply() }

    var model: String
        get() = sp.getString("model", null) ?: sp.getString("model_name", null) ?: ""
        set(v) { sp.edit().putString("model", v.trim()).apply() }

    var apiKey: String
        get() = sp.getString("api_key", "") ?: ""
        set(v) { sp.edit().putString("api_key", v.trim()).apply() }

    fun apiConfig(): ApiConfig? =
        if (baseUrl.isNotBlank() && model.isNotBlank()) ApiConfig(baseUrl, model, apiKey) else null

    // ---- languages ----
    var sourceLang: String
        get() = sp.getString("source_lang", null)?.takeIf { Languages.isValid(it) } ?: Languages.AUTO
        set(v) { if (Languages.isValid(v)) sp.edit().putString("source_lang", v).apply() }

    /** A language code, or [Languages.NONE] for "tone only, no translation". */
    var targetLang: String
        get() {
            val saved = sp.getString("target_code", null)
            if (saved == Languages.NONE) return saved
            if (saved != null && Languages.isValid(saved) && saved != Languages.AUTO) return saved
            val legacy = sp.getString("target_lang", null)?.trim()
            if (!legacy.isNullOrEmpty()) {
                val hit = Languages.ALL.firstOrNull { it.name.equals(legacy, ignoreCase = true) }
                return hit?.code ?: Languages.makeCustom(legacy)
            }
            return Languages.defaultTargetForLanguage(Locale.getDefault().language)
        }
        set(v) {
            if (v == Languages.NONE || (Languages.isValid(v) && v != Languages.AUTO)) {
                sp.edit().putString("target_code", v).apply()
            }
        }

    var recent: List<String>
        get() = readList("recent", 8) { Languages.isValid(it) && it != Languages.AUTO }
        set(v) { writeList("recent", v.take(8)) }

    var favorites: List<String>
        get() = readList("favorites", 30) { Languages.isValid(it) && it != Languages.AUTO }
        set(v) { writeList("favorites", v.take(30)) }

    fun pushRecent(code: String) {
        if (code == Languages.AUTO || code == Languages.NONE || !Languages.isValid(code)) return
        recent = listOf(code) + recent.filter { it != code }
    }

    fun toggleFavorite(code: String) {
        if (code == Languages.AUTO || code == Languages.NONE || !Languages.isValid(code)) return
        val f = favorites
        favorites = if (code in f) f.filter { it != code } else f + code
    }

    // ---- tones ----
    var tone: String
        get() = sp.getString("tone", null)?.takeIf { Tones.isValid(it) } ?: Tones.DEFAULT
        set(v) { if (Tones.isValid(v)) sp.edit().putString("tone", v).apply() }

    var toneRecent: List<String>
        get() = readList("tone_recent", 8) { Tones.isValid(it) && it != Tones.DEFAULT }
        set(v) { writeList("tone_recent", v.take(8)) }

    var toneFavorites: List<String>
        get() = readList("tone_favorites", 30) { Tones.isValid(it) && it != Tones.DEFAULT }
        set(v) { writeList("tone_favorites", v.take(30)) }

    fun pushToneRecent(code: String) {
        if (code == Tones.DEFAULT || !Tones.isValid(code)) return
        toneRecent = listOf(code) + toneRecent.filter { it != code }
    }

    fun toggleToneFavorite(code: String) {
        if (code == Tones.DEFAULT || !Tones.isValid(code)) return
        val f = toneFavorites
        toneFavorites = if (code in f) f.filter { it != code } else f + code
    }

    // ---- look ----
    /** "system", "light" or "dark" */
    var theme: String
        get() = sp.getString("theme", "system") ?: "system"
        set(v) { sp.edit().putString("theme", v).apply() }

    // ---- floating button / panel ----
    var bubbleEnabled: Boolean
        get() = sp.getBoolean("bubble_enabled", true)
        set(v) { sp.edit().putBoolean("bubble_enabled", v).apply() }

    /**
     * "relay" (default) = you type in the panel box, the keyboard stays bound to the app's field and is never touched;
     *                      typed characters are picked up and the field is put back at once.
     * "field" = you keep typing in the app's own field; the panel shows that text live and the keyboard is never touched.
     * "box" = the panel window takes the keyboard (Android then switches it and may hide it for a moment).
     */
    var typingMode: String
        get() = sp.getString("typing_mode4", "box") ?: "box"
        set(v) { sp.edit().putString("typing_mode4", if (v == "relay" || v == "field") v else "box").apply() }

    /** Tapping outside the panel closes it (the keyboard stays). */
    var closeOutside: Boolean
        get() = sp.getBoolean("close_outside", true)
        set(v) { sp.edit().putBoolean("close_outside", v).apply() }

    /** Position as a fraction (0..1) of the free screen area, so it survives rotation. Null = default. */
    fun bubblePos(): Pair<Float, Float>? = readPos("bubble")
    fun saveBubblePos(xf: Float, yf: Float) = writePos("bubble", xf, yf)
    fun panelPos(): Pair<Float, Float>? = readPos("panel")
    fun savePanelPos(xf: Float, yf: Float) = writePos("panel", xf, yf)

    fun resetPositions() {
        sp.edit().remove("bubble_xf").remove("bubble_yf").remove("panel_xf").remove("panel_yf").apply()
    }

    private fun readPos(k: String): Pair<Float, Float>? {
        if (!sp.contains("${k}_xf") || !sp.contains("${k}_yf")) return null
        return sp.getFloat("${k}_xf", 0f).coerceIn(0f, 1f) to sp.getFloat("${k}_yf", 0f).coerceIn(0f, 1f)
    }

    private fun writePos(k: String, xf: Float, yf: Float) {
        sp.edit().putFloat("${k}_xf", xf.coerceIn(0f, 1f)).putFloat("${k}_yf", yf.coerceIn(0f, 1f)).apply()
    }

    // ---- trigger ----
    var trigger: TriggerConfig
        get() = TriggerConfig.fromJson(sp.getString("trigger", null))
        set(v) { sp.edit().putString("trigger", v.sanitized().toJson()).apply() }

    // ---- helpers ----
    private fun readList(key: String, max: Int, valid: (String) -> Boolean): List<String> {
        val raw = sp.getString(key, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val out = ArrayList<String>()
            for (i in 0 until arr.length()) {
                val c = arr.optString(i)
                if (valid(c) && c !in out) out.add(c)
            }
            out.take(max)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun writeList(key: String, list: List<String>) {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        sp.edit().putString(key, arr.toString()).apply()
    }

    fun palette(context: Context): Palette {
        val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        return when (theme) {
            "dark" -> Palette.DARK
            "light" -> Palette.LIGHT
            else -> if (night) Palette.DARK else Palette.LIGHT
        }
    }
}

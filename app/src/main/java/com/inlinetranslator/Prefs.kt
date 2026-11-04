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

    // Connection. Falls back to the keys used by the very first version of the app.
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

    // Languages
    var sourceLang: String
        get() = sp.getString("source_lang", null)?.takeIf { Languages.isValid(it) } ?: Languages.AUTO
        set(v) { if (Languages.isValid(v)) sp.edit().putString("source_lang", v).apply() }

    var targetLang: String
        get() {
            val saved = sp.getString("target_code", null)
            if (saved != null && Languages.isValid(saved) && saved != Languages.AUTO) return saved
            val legacy = sp.getString("target_lang", null)?.trim()
            if (!legacy.isNullOrEmpty()) {
                val hit = Languages.ALL.firstOrNull { it.name.equals(legacy, ignoreCase = true) }
                return hit?.code ?: Languages.makeCustom(legacy)
            }
            return Languages.defaultTargetForLanguage(Locale.getDefault().language)
        }
        set(v) { if (Languages.isValid(v) && v != Languages.AUTO) sp.edit().putString("target_code", v).apply() }

    var recent: List<String>
        get() = readList("recent", 8)
        set(v) { writeList("recent", v.take(8)) }

    var favorites: List<String>
        get() = readList("favorites", 30)
        set(v) { writeList("favorites", v.take(30)) }

    fun pushRecent(code: String) {
        if (code == Languages.AUTO || !Languages.isValid(code)) return
        recent = listOf(code) + recent.filter { it != code }
    }

    fun toggleFavorite(code: String) {
        if (code == Languages.AUTO || !Languages.isValid(code)) return
        val f = favorites
        favorites = if (code in f) f.filter { it != code } else f + code
    }

    /** "system", "light" or "dark" */
    var theme: String
        get() = sp.getString("theme", "system") ?: "system"
        set(v) { sp.edit().putString("theme", v).apply() }

    var bubbleEnabled: Boolean
        get() = sp.getBoolean("bubble_enabled", true)
        set(v) { sp.edit().putBoolean("bubble_enabled", v).apply() }

    private fun readList(key: String, max: Int): List<String> {
        val raw = sp.getString(key, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val out = ArrayList<String>()
            for (i in 0 until arr.length()) {
                val c = arr.optString(i)
                if (Languages.isValid(c) && c != Languages.AUTO && c !in out) out.add(c)
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

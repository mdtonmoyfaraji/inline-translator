package com.inlinetranslator

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("inline_translator_prefs", Context.MODE_PRIVATE)

    var apiUrl: String
        get() = prefs.getString("api_url", "https://api.openai.com/v1/chat/completions") ?: ""
        set(value) = prefs.edit().putString("api_url", value).apply()

    var apiKey: String
        get() = prefs.getString("api_key", "") ?: ""
        set(value) = prefs.edit().putString("api_key", value).apply()

    var modelName: String
        get() = prefs.getString("model_name", "gpt-4o-mini") ?: ""
        set(value) = prefs.edit().putString("model_name", value).apply()

    var targetLang: String
        get() = prefs.getString("target_lang", "English") ?: ""
        set(value) = prefs.edit().putString("target_lang", value).apply()
}

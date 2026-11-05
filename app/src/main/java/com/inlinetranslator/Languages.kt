package com.inlinetranslator

import java.text.Normalizer

data class Language(val code: String, val name: String, val native: String)

/** Same list, same search and same naming rules as the Chrome extension. */
object Languages {
    const val AUTO = "auto"
    const val CUSTOM_PREFIX = "custom:"

    /** Target meaning "do not translate, only apply the tone". */
    const val NONE = "none"

    val ALL: List<Language> = listOf(
        Language("af", "Afrikaans", "Afrikaans"),
        Language("sq", "Albanian", "Shqip"),
        Language("am", "Amharic", "አማርኛ"),
        Language("ar", "Arabic", "العربية"),
        Language("hy", "Armenian", "Հայերեն"),
        Language("as", "Assamese", "অসমীয়া"),
        Language("az", "Azerbaijani", "Azərbaycan"),
        Language("eu", "Basque", "Euskara"),
        Language("be", "Belarusian", "Беларуская"),
        Language("bn", "Bengali", "বাংলা"),
        Language("bs", "Bosnian", "Bosanski"),
        Language("bg", "Bulgarian", "Български"),
        Language("my", "Burmese", "မြန်မာ"),
        Language("ca", "Catalan", "Català"),
        Language("ceb", "Cebuano", "Cebuano"),
        Language("zh-CN", "Chinese (Simplified)", "简体中文"),
        Language("zh-TW", "Chinese (Traditional)", "繁體中文"),
        Language("hr", "Croatian", "Hrvatski"),
        Language("cs", "Czech", "Čeština"),
        Language("da", "Danish", "Dansk"),
        Language("nl", "Dutch", "Nederlands"),
        Language("en", "English", "English"),
        Language("eo", "Esperanto", "Esperanto"),
        Language("et", "Estonian", "Eesti"),
        Language("fil", "Filipino", "Filipino"),
        Language("fi", "Finnish", "Suomi"),
        Language("fr", "French", "Français"),
        Language("gl", "Galician", "Galego"),
        Language("ka", "Georgian", "ქართული"),
        Language("de", "German", "Deutsch"),
        Language("el", "Greek", "Ελληνικά"),
        Language("gu", "Gujarati", "ગુજરાતી"),
        Language("ht", "Haitian Creole", "Kreyòl ayisyen"),
        Language("ha", "Hausa", "Hausa"),
        Language("haw", "Hawaiian", "ʻŌlelo Hawaiʻi"),
        Language("he", "Hebrew", "עברית"),
        Language("hi", "Hindi", "हिन्दी"),
        Language("hu", "Hungarian", "Magyar"),
        Language("is", "Icelandic", "Íslenska"),
        Language("ig", "Igbo", "Igbo"),
        Language("id", "Indonesian", "Bahasa Indonesia"),
        Language("ga", "Irish", "Gaeilge"),
        Language("it", "Italian", "Italiano"),
        Language("ja", "Japanese", "日本語"),
        Language("jv", "Javanese", "Basa Jawa"),
        Language("kn", "Kannada", "ಕನ್ನಡ"),
        Language("kk", "Kazakh", "Қазақша"),
        Language("km", "Khmer", "ខ្មែរ"),
        Language("rw", "Kinyarwanda", "Kinyarwanda"),
        Language("ko", "Korean", "한국어"),
        Language("ku", "Kurdish", "Kurdî"),
        Language("ky", "Kyrgyz", "Кыргызча"),
        Language("lo", "Lao", "ລາວ"),
        Language("la", "Latin", "Latina"),
        Language("lv", "Latvian", "Latviešu"),
        Language("lt", "Lithuanian", "Lietuvių"),
        Language("lb", "Luxembourgish", "Lëtzebuergesch"),
        Language("mk", "Macedonian", "Македонски"),
        Language("mg", "Malagasy", "Malagasy"),
        Language("ms", "Malay", "Bahasa Melayu"),
        Language("ml", "Malayalam", "മലയാളം"),
        Language("mt", "Maltese", "Malti"),
        Language("mi", "Maori", "Māori"),
        Language("mr", "Marathi", "मराठी"),
        Language("mn", "Mongolian", "Монгол"),
        Language("ne", "Nepali", "नेपाली"),
        Language("no", "Norwegian", "Norsk"),
        Language("or", "Odia", "ଓଡ଼ିଆ"),
        Language("ps", "Pashto", "پښتو"),
        Language("fa", "Persian", "فارسی"),
        Language("pl", "Polish", "Polski"),
        Language("pt-BR", "Portuguese (Brazil)", "Português (Brasil)"),
        Language("pt-PT", "Portuguese (Portugal)", "Português (Portugal)"),
        Language("pa", "Punjabi", "ਪੰਜਾਬੀ"),
        Language("ro", "Romanian", "Română"),
        Language("ru", "Russian", "Русский"),
        Language("sm", "Samoan", "Gagana Samoa"),
        Language("gd", "Scottish Gaelic", "Gàidhlig"),
        Language("sr", "Serbian", "Српски"),
        Language("sn", "Shona", "chiShona"),
        Language("sd", "Sindhi", "سنڌي"),
        Language("si", "Sinhala", "සිංහල"),
        Language("sk", "Slovak", "Slovenčina"),
        Language("sl", "Slovenian", "Slovenščina"),
        Language("so", "Somali", "Soomaali"),
        Language("es", "Spanish", "Español"),
        Language("su", "Sundanese", "Basa Sunda"),
        Language("sw", "Swahili", "Kiswahili"),
        Language("sv", "Swedish", "Svenska"),
        Language("tg", "Tajik", "Тоҷикӣ"),
        Language("ta", "Tamil", "தமிழ்"),
        Language("tt", "Tatar", "Татарча"),
        Language("te", "Telugu", "తెలుగు"),
        Language("th", "Thai", "ไทย"),
        Language("tr", "Turkish", "Türkçe"),
        Language("tk", "Turkmen", "Türkmen"),
        Language("uk", "Ukrainian", "Українська"),
        Language("ur", "Urdu", "اردو"),
        Language("ug", "Uyghur", "ئۇيغۇرچە"),
        Language("uz", "Uzbek", "Oʻzbek"),
        Language("vi", "Vietnamese", "Tiếng Việt"),
        Language("cy", "Welsh", "Cymraeg"),
        Language("xh", "Xhosa", "isiXhosa"),
        Language("yi", "Yiddish", "ייִדיש"),
        Language("yo", "Yoruba", "Yorùbá"),
        Language("zu", "Zulu", "isiZulu")
    )

    private val CODE_RE = Regex("^(auto|[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})*|custom:.{1,40})$")
    private val MARKS = Regex("\\p{M}+")

    fun isValid(code: String?): Boolean = code != null && CODE_RE.matches(code)

    fun makeCustom(name: String): String = CUSTOM_PREFIX + name.trim().take(40)

    fun find(code: String): Language? = ALL.firstOrNull { it.code == code }

    /** English display name (this is also what is sent to the model). */
    fun name(code: String): String = when {
        code == AUTO -> "Detect language"
        code == NONE -> "No translation"
        code.startsWith(CUSTOM_PREFIX) -> code.removePrefix(CUSTOM_PREFIX)
        else -> find(code)?.name ?: code
    }

    fun nativeName(code: String): String = find(code)?.native ?: ""

    private fun norm(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(MARKS, "").lowercase()

    fun search(query: String, list: List<Language> = ALL): List<Language> {
        val q = norm(query.trim())
        if (q.isEmpty()) return list
        val starts = ArrayList<Language>()
        val contains = ArrayList<Language>()
        for (l in list) {
            val name = norm(l.name)
            val native = norm(l.native)
            val code = l.code.lowercase()
            if (name.startsWith(q) || native.startsWith(q) || code == q) starts.add(l)
            else if (name.contains(q) || native.contains(q) || code.startsWith(q)) contains.add(l)
        }
        return starts + contains
    }

    /** English locales default to Spanish, everything else to English (same as the extension). */
    fun defaultTargetForLanguage(lang: String?): String =
        if ((lang ?: "en").lowercase() == "en") "es" else "en"
}

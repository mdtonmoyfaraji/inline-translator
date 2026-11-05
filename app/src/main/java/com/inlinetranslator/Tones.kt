package com.inlinetranslator

data class Tone(val code: String, val name: String, val hint: String, val instruction: String)

/** Same tones, same prompts and same search as the Chrome extension. */
object Tones {
    const val DEFAULT = "default"
    const val CUSTOM_PREFIX = "custom:"
    const val MAX_CUSTOM = 80

    val ALL: List<Tone> = listOf(
        Tone("improve", "Improve", "Fix & polish",
            "Improve the writing: fix grammar, spelling and punctuation, and make it clearer and smoother. Keep the meaning, the voice and roughly the same length."),
        Tone("advanced", "Advanced", "Richer vocabulary",
            "Rewrite it with a more sophisticated, advanced vocabulary and polished sentence structure, as an articulate, well-read writer would. Keep the meaning."),
        Tone("formal", "Formal", "Professional",
            "Write it in a formal, professional register: no slang, no contractions, courteous and precise."),
        Tone("short", "Short", "Concise",
            "Make it as short and concise as possible while keeping the key meaning. Remove filler."),
        Tone("tribal", "Tribal", "Elders by the fire",
            "Write it in a tribal, ancestral voice, as an elder speaking around a fire: earthy, communal imagery (the land, the ancestors, the spirits, the hunt). Keep the meaning."),
        Tone("corp", "Corp", "Corporate speak",
            "Write it in corporate business jargon (synergy, stakeholders, bandwidth, leverage, circle back, align on deliverables). Keep the meaning."),
        Tone("biblical", "Biblical", "Scripture style",
            "Write it in the solemn style of the King James Bible: archaic phrasing (thee, thou, verily, unto), rhythmic and reverent. Keep the meaning."),
        Tone("viking", "Viking", "Norse warrior",
            "Write it as a bold, boastful Viking warrior would: fierce and proud, with references to Odin, Valhalla, glory, ships and battle. Keep the meaning."),
        Tone("zen", "Zen", "Calm & minimal",
            "Write it in a calm, serene, minimalist Zen tone: spare, mindful, peaceful, with gentle imagery. Keep the meaning."),
        Tone("emojify", "Emojify", "Add emojis",
            "Keep the text but add fitting emojis after key phrases and at the end of sentences. Do not overdo it.")
    )

    fun isCustom(code: String): Boolean = code.startsWith(CUSTOM_PREFIX)

    fun makeCustom(text: String): String =
        CUSTOM_PREFIX + text.trim().replace(Regex("\\s+"), " ").take(MAX_CUSTOM)

    fun isValid(code: String?): Boolean {
        if (code == null) return false
        if (code == DEFAULT) return true
        if (ALL.any { it.code == code }) return true
        return isCustom(code) && code.length > CUSTOM_PREFIX.length && code.length <= CUSTOM_PREFIX.length + MAX_CUSTOM
    }

    fun find(code: String): Tone? = ALL.firstOrNull { it.code == code }

    fun name(code: String): String = when {
        code == DEFAULT -> "Default"
        isCustom(code) -> code.removePrefix(CUSTOM_PREFIX)
        else -> find(code)?.name ?: code
    }

    /** (label, instruction) for the model prompt, or null when no tone applies. */
    fun instruction(code: String): Pair<String, String>? {
        if (code == DEFAULT) return null
        if (isCustom(code)) {
            val text = code.removePrefix(CUSTOM_PREFIX)
            return "Custom" to "Write it in this custom tone/style, described by the user as: \"$text\". Keep the meaning."
        }
        val t = find(code) ?: return null
        return t.name to t.instruction
    }

    fun search(query: String): List<Tone> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return ALL
        val starts = ALL.filter { it.name.lowercase().startsWith(q) }
        val contains = ALL.filter {
            !it.name.lowercase().startsWith(q) && (it.name.lowercase().contains(q) || it.hint.lowercase().contains(q))
        }
        return starts + contains
    }
}

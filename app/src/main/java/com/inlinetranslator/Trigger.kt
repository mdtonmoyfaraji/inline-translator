package com.inlinetranslator

import org.json.JSONObject

/**
 * In-page trigger, Android version of the extension's "In-page trigger" setting.
 * The trigger is ONE character that you type on your normal keyboard (space, "j", "/", an emoji ...),
 * pressed [pressCount] times in a row within [maxIntervalMs] each.
 * The typed trigger characters are removed again and the translator opens.
 */
data class TriggerConfig(
    val enabled: Boolean = false,
    val char: String = "",
    val pressCount: Int = 2,
    val maxIntervalMs: Int = 400,
    val acknowledgedRisk: Boolean = false
) {
    fun sanitized(): TriggerConfig = copy(
        pressCount = pressCount.coerceIn(1, 5),
        maxIntervalMs = maxIntervalMs.coerceIn(100, 2000)
    )

    fun toJson(): String = JSONObject()
        .put("enabled", enabled).put("char", char).put("pressCount", pressCount)
        .put("maxIntervalMs", maxIntervalMs).put("acknowledgedRisk", acknowledgedRisk).toString()

    /** "Space ×2", "j ×2", "Not set" */
    fun label(): String {
        if (char.isEmpty()) return "Not set"
        return charLabel(char) + " \u00D7" + pressCount
    }

    companion object {
        fun fromJson(raw: String?): TriggerConfig {
            if (raw.isNullOrBlank()) return TriggerConfig()
            return try {
                val o = JSONObject(raw)
                TriggerConfig(
                    enabled = o.optBoolean("enabled", false),
                    char = firstCodePoint(o.optString("char", "")),
                    pressCount = o.optInt("pressCount", 2),
                    maxIntervalMs = o.optInt("maxIntervalMs", 400),
                    acknowledgedRisk = o.optBoolean("acknowledgedRisk", false)
                ).sanitized()
            } catch (e: Exception) {
                TriggerConfig()
            }
        }

        fun firstCodePoint(s: String): String =
            if (s.isEmpty()) "" else String(Character.toChars(s.codePointAt(0)))

        fun charLabel(c: String): String = when (c) {
            " " -> "Space"
            "\u00A0" -> "Non-breaking space"
            "\t" -> "Tab"
            "\n" -> "Enter"
            else -> "\u201C$c\u201D"
        }
    }
}

class TriggerValidation(val errors: List<String>, val warnings: List<String>, val plainTypingKey: Boolean)

fun validateTrigger(cfg: TriggerConfig): TriggerValidation {
    val errors = ArrayList<String>()
    val warnings = ArrayList<String>()
    var plain = false
    if (cfg.char.isEmpty()) {
        errors.add("Choose a trigger key.")
    } else {
        if (cfg.char == "\n") errors.add("Enter can't be used as the trigger. Choose another key.")
        if (cfg.pressCount < 2) {
            errors.add("A single press of a plain key would trigger whenever you type it. Use at least 2 presses.")
        }
        val cp = cfg.char.codePointAt(0)
        if (Character.isLetterOrDigit(cp)) {
            plain = true
            warnings.add("Double letters or digits are common in normal words and can trigger it by accident. Space, a punctuation mark or an emoji is safer.")
        }
        if (cfg.char == " ") {
            warnings.add("If your keyboard turns a double space into \u201C. \u201D (Gboard: Text correction \u2192 Double-space period), switch that off or the trigger won\u2019t fire.")
        }
    }
    if (cfg.maxIntervalMs < 100 || cfg.maxIntervalMs > 2000) errors.add("The time between presses must be 100\u20132000 ms.")
    return TriggerValidation(errors, warnings, plain)
}

enum class Hit { NONE, PROGRESS, COMPLETED }

/**
 * Counts consecutive presses of the trigger character. Same rules as the extension's SequenceDetector:
 * any other text change resets the count, and presses must follow each other within the interval.
 * A press = exactly one trigger character inserted into the text (compared before/after).
 */
class TriggerDetector(var cfg: TriggerConfig) {
    private var count = 0
    private var last = Long.MIN_VALUE / 2

    fun reset() {
        count = 0
        last = Long.MIN_VALUE / 2
    }

    fun setConfig(c: TriggerConfig) {
        cfg = c
        reset()
    }

    fun onTextChanged(before: String, after: String, now: Long): Hit {
        val c = cfg.char
        if (!cfg.enabled || c.isEmpty()) return Hit.NONE
        if (after.length == before.length + c.length) {
            var i = 0
            while (i < before.length && before[i] == after[i]) i++
            if (i + c.length <= after.length &&
                after.regionMatches(i, c, 0, c.length) &&
                after.removeRange(i, i + c.length) == before
            ) {
                return register(now)
            }
        }
        reset()
        return Hit.NONE
    }

    private fun register(now: Long): Hit {
        if (now - last > cfg.maxIntervalMs) count = 0
        count += 1
        last = now
        if (count >= cfg.pressCount) {
            reset()
            return Hit.COMPLETED
        }
        return Hit.PROGRESS
    }
}

/** One contiguous edit between two texts: [removed] characters were replaced by [inserted]. */
class TextEdit(val removed: Int, val inserted: String)

object TextDiff {
    fun between(before: String, after: String): TextEdit {
        val max = minOf(before.length, after.length)
        var p = 0
        while (p < max && before[p] == after[p]) p++
        var s = 0
        while (s < max - p && before[before.length - 1 - s] == after[after.length - 1 - s]) s++
        return TextEdit(before.length - p - s, after.substring(p, after.length - s))
    }

    /** Applies an edit that happened in a field to the panel's own box text (typing/backspace at the end). */
    fun applyToBox(box: StringBuilder, e: TextEdit) {
        if (e.removed > 0) box.setLength((box.length - e.removed).coerceAtLeast(0))
        if (e.inserted.isNotEmpty()) box.append(e.inserted)
    }
}

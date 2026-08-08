package com.retrofrost.lattice.privacy

import com.retrofrost.lattice.model.LatticeSettings
import com.retrofrost.lattice.telegram.TelegramMessageItem

object ContentFilterEngine {
    enum class Action { SHOW, COLLAPSE, HIDE_PAID }

    data class Decision(
        val action: Action,
        val displayText: String,
        val reason: String? = null,
        val allowShowOnce: Boolean = false
    )

    fun evaluate(message: TelegramMessageItem, settings: LatticeSettings): Decision {
        val raw = message.text.trim()
        val lower = raw.lowercase()
        val kind = message.contentKind.lowercase()

        if (settings.hidePaidMedia && (
                kind.contains("paidmedia") ||
                kind.contains("paid_media") ||
                lower == "paid media" ||
                lower == "stars media"
            )
        ) {
            return Decision(
                Action.HIDE_PAID,
                "Paid media hidden",
                "Lattice hides Telegram Stars media without showing a teaser.",
                false
            )
        }

        if (!settings.showSexualMaterial && looksSexual(lower, kind, settings)) {
            return Decision(Action.COLLAPSE, "Sexual content hidden", "Hidden locally by Sexual content filters.", true)
        }

        if (settings.goreFilter && looksGraphic(lower, kind, settings)) {
            return Decision(Action.COLLAPSE, "Graphic content hidden", "Hidden locally by Gore filters.", true)
        }

        if (settings.spamFilter && looksSpam(lower)) {
            return Decision(Action.COLLAPSE, "Suspected spam hidden", "Collapsed locally. The Telegram message was not deleted or reported.", true)
        }

        if (settings.swearingFilter && containsSwearing(lower)) {
            return if (settings.censorSwearingInsteadOfHide) {
                Decision(Action.SHOW, censorSwearing(raw))
            } else {
                Decision(Action.COLLAPSE, "Message hidden by swearing filter", "Swearing filtering is enabled on this device.", true)
            }
        }

        return Decision(Action.SHOW, raw.ifBlank { fallbackForKind(kind) })
    }

    private fun looksSexual(lower: String, kind: String, settings: LatticeSettings): Boolean {
        if (SENSITIVE_MARKERS.any(lower::contains)) return true
        if (settings.hideSuggestiveEmojis && SUGGESTIVE_COMBINATIONS.any(lower::contains)) return true
        if (settings.hideSexualStickersGifs && (kind.contains("sticker") || kind.contains("animation"))) {
            return SUGGESTIVE_COMBINATIONS.any(lower::contains)
        }
        return false
    }

    private fun looksGraphic(lower: String, kind: String, settings: LatticeSettings): Boolean {
        if (GRAPHIC_MARKERS.any(lower::contains)) return true
        return settings.hideGoreStickersGifs &&
            (kind.contains("sticker") || kind.contains("animation")) &&
            GRAPHIC_MARKERS.any(lower::contains)
    }

    private fun looksSpam(lower: String): Boolean {
        val score = SPAM_PHRASES.count(lower::contains)
        val hasUrl = lower.contains("http://") || lower.contains("https://") || lower.contains("t.me/")
        val excessiveUrgency = listOf("urgent", "immediately", "act now", "limited time").count(lower::contains) >= 2
        return score >= 2 || (score >= 1 && hasUrl) || (hasUrl && excessiveUrgency)
    }

    private fun containsSwearing(lower: String): Boolean = SWEAR_WORDS.any { containsWord(lower, it) }

    private fun censorSwearing(input: String): String {
        var output = input
        SWEAR_WORDS.forEach { word ->
            val replacement = if (word.length <= 2) "*".repeat(word.length) else word.first() + "*".repeat(word.length - 2) + word.last()
            output = Regex("(?i)\\b${Regex.escape(word)}\\b").replace(output, replacement)
        }
        return output
    }

    private fun containsWord(text: String, word: String): Boolean =
        Regex("(?:^|[^a-z0-9])${Regex.escape(word)}(?:$|[^a-z0-9])").containsMatchIn(text)

    private fun fallbackForKind(kind: String): String = when {
        kind.contains("photo") -> "Photo"
        kind.contains("video") -> "Video"
        kind.contains("animation") -> "GIF"
        kind.contains("sticker") -> "Sticker"
        kind.contains("voice") -> "Voice message"
        kind.contains("audio") -> "Audio"
        kind.contains("document") -> "File"
        else -> "Telegram message"
    }

    private val SENSITIVE_MARKERS = listOf("nsfw", "18+", "explicit content", "adult content")
    private val SUGGESTIVE_COMBINATIONS = listOf("🍆💦", "🍑💦", "🍆🍑", "👅🍆", "👅🍑")
    private val GRAPHIC_MARKERS = listOf("graphic injury", "graphic violence", "gore", "severe injury", "graphic scene")
    private val SPAM_PHRASES = listOf(
        "guaranteed profit", "double your money", "claim your prize", "free crypto", "crypto investment",
        "verify your account now", "wallet verification", "recovery phrase", "send a small fee", "exclusive airdrop"
    )
    private val SWEAR_WORDS = listOf("fuck", "fucking", "motherfuck", "motherfucker", "shit", "bitch", "dick")
}

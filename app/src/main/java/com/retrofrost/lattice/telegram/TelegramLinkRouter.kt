package com.retrofrost.lattice.telegram

import android.net.Uri

sealed interface TelegramLinkTarget {
    data class Username(val username: String, val messageId: Long? = null) : TelegramLinkTarget
    data class Invite(val token: String) : TelegramLinkTarget
    data class PrivatePost(val channelId: Long, val messageId: Long) : TelegramLinkTarget
    data class TelegramScheme(val uri: String) : TelegramLinkTarget
}

object TelegramLinkRouter {
    fun parse(raw: String?): TelegramLinkTarget? {
        if (raw.isNullOrBlank()) return null
        val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return null

        if (uri.scheme.equals("tg", ignoreCase = true)) {
            return TelegramLinkTarget.TelegramScheme(raw)
        }

        val host = uri.host?.lowercase() ?: return null
        if (host != "t.me" && host != "telegram.me") return null

        val segments = uri.pathSegments.filter { it.isNotBlank() }
        if (segments.isEmpty()) return null

        val first = segments.first()
        if (first.startsWith("+")) {
            return TelegramLinkTarget.Invite(first.removePrefix("+"))
        }
        if (first == "joinchat" && segments.size >= 2) {
            return TelegramLinkTarget.Invite(segments[1])
        }
        if (first == "c" && segments.size >= 3) {
            val channelId = segments[1].toLongOrNull() ?: return null
            val messageId = segments[2].toLongOrNull() ?: return null
            return TelegramLinkTarget.PrivatePost(channelId, messageId)
        }

        val messageId = segments.getOrNull(1)?.toLongOrNull()
        return TelegramLinkTarget.Username(first, messageId)
    }
}

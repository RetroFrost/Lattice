package com.retrofrost.lattice.telegram

/**
 * Boundary between the UI and Telegram.
 *
 * The first build intentionally keeps TDLib behind this interface so native binaries,
 * authentication, storage encryption, proxies and update handling can be integrated
 * without coupling the Compose UI directly to TDLib classes.
 */
interface TelegramRepository {
    val isConfigured: Boolean
    fun start()
    fun stop()
    fun openLink(rawLink: String): TelegramLinkTarget?
}

class BootstrapTelegramRepository : TelegramRepository {
    override val isConfigured: Boolean = false
    override fun start() = Unit
    override fun stop() = Unit
    override fun openLink(rawLink: String): TelegramLinkTarget? = TelegramLinkRouter.parse(rawLink)
}

package com.retrofrost.lattice.telegram

import kotlinx.coroutines.flow.StateFlow

interface TelegramRepository {
    val isConfigured: Boolean
    val state: StateFlow<TelegramUiState>

    fun start()
    fun stop()
    fun openLink(rawLink: String): TelegramLinkTarget?
    fun openTelegramLink(rawLink: String)
    fun joinPendingInvite()
    fun dismissPendingInvite()

    fun setApiCredentials(apiId: Int, apiHash: String)
    fun retryPrivacyRoute()
    fun submitPhoneNumber(phoneNumber: String)
    fun requestQrLogin()
    fun submitCode(code: String)
    fun submitPassword(password: String)
    fun submitEmailAddress(email: String)
    fun submitEmailCode(code: String)
    fun register(firstName: String, lastName: String)

    fun refreshChats()
    fun openChat(chatId: Long)
    fun closeChat()
    fun sendTextMessage(chatId: Long, text: String)
}

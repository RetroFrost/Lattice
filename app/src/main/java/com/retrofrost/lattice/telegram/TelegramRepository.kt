package com.retrofrost.lattice.telegram

import kotlinx.coroutines.flow.StateFlow

interface TelegramRepository {
    val isConfigured: Boolean
    val state: StateFlow<TelegramUiState>

    fun start()
    fun stop()
    fun openLink(rawLink: String): TelegramLinkTarget?

    fun setApiCredentials(apiId: Int, apiHash: String)
    fun submitPhoneNumber(phoneNumber: String)
    fun requestQrLogin()
    fun submitCode(code: String)
    fun submitPassword(password: String)
    fun submitEmailAddress(email: String)
    fun submitEmailCode(code: String)
    fun register(firstName: String, lastName: String)
    fun refreshChats()
}

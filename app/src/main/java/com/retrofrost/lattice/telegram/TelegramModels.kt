package com.retrofrost.lattice.telegram

sealed interface TelegramAuthStage {
    data object Initializing : TelegramAuthStage
    data object NeedApiCredentials : TelegramAuthStage
    data class WaitPrivacyRoute(val orbotInstalled: Boolean) : TelegramAuthStage
    data object WaitPhoneNumber : TelegramAuthStage
    data class WaitCode(val codeLength: Int? = null) : TelegramAuthStage
    data object WaitPassword : TelegramAuthStage
    data object WaitEmailAddress : TelegramAuthStage
    data object WaitEmailCode : TelegramAuthStage
    data object WaitRegistration : TelegramAuthStage
    data class WaitOtherDeviceConfirmation(val link: String) : TelegramAuthStage
    data object Ready : TelegramAuthStage
    data object Closing : TelegramAuthStage
    data object Closed : TelegramAuthStage
    data class Error(val message: String) : TelegramAuthStage
}

data class TelegramChatSummary(
    val id: Long,
    val title: String,
    val preview: String,
    val unreadCount: Int,
    val order: Long,
    val kind: Kind
) {
    enum class Kind { PRIVATE, GROUP, CHANNEL_OR_SUPERGROUP, SECRET, UNKNOWN }
}

data class TelegramMessageItem(
    val id: Long,
    val chatId: Long,
    val text: String,
    val isOutgoing: Boolean,
    val date: Int,
    val contentKind: String
)

data class TelegramUiState(
    val authStage: TelegramAuthStage = TelegramAuthStage.Initializing,
    val connectionLabel: String = "Starting TDLib…",
    val chats: List<TelegramChatSummary> = emptyList(),
    val activeChatId: Long? = null,
    val activeMessages: List<TelegramMessageItem> = emptyList(),
    val messagesLoading: Boolean = false,
    val lastError: String? = null
)

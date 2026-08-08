package com.retrofrost.lattice.telegram

import android.content.Context
import android.os.Build
import com.retrofrost.lattice.BuildConfig
import com.retrofrost.lattice.privacy.TorSupport
import io.xbot.tdlib.TdLib
import java.io.Closeable
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class TdlibTelegramRepository(
    context: Context
) : TelegramRepository, Closeable {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = AtomicBoolean(false)
    private val chats = LinkedHashMap<Long, TelegramChatSummary>()

    private var clientId: Int = 0
    private var apiId: Int = BuildConfig.TELEGRAM_API_ID
    private var apiHash: String = BuildConfig.TELEGRAM_API_HASH
    private var waitingForParameters = false
    private var privacyProxyConfigured = false
    private var privacyProxyRequestPending = false

    private val _state = MutableStateFlow(TelegramUiState())
    override val state: StateFlow<TelegramUiState> = _state.asStateFlow()

    override val isConfigured: Boolean
        get() = apiId > 0 && apiHash.isNotBlank()

    override fun start() {
        if (!running.compareAndSet(false, true)) return
        clientId = TdLib.createClientId()
        _state.value = _state.value.copy(connectionLabel = "TDLib started — network locked")
        scope.launch { receiveLoop() }
    }

    override fun stop() {
        if (!running.compareAndSet(true, false)) return
        if (clientId != 0) {
            send(JSONObject().put("@type", "close"))
        }
    }

    override fun close() {
        stop()
        scope.cancel()
    }

    override fun openLink(rawLink: String): TelegramLinkTarget? = TelegramLinkRouter.parse(rawLink)

    override fun setApiCredentials(apiId: Int, apiHash: String) {
        if (apiId <= 0 || apiHash.isBlank()) {
            setError("A valid Telegram API ID and API hash are required.")
            return
        }
        this.apiId = apiId
        this.apiHash = apiHash.trim()
        if (waitingForParameters) prepareInitialization()
    }

    override fun retryPrivacyRoute() {
        if (waitingForParameters) prepareInitialization()
    }

    override fun submitPhoneNumber(phoneNumber: String) {
        send(
            JSONObject()
                .put("@type", "setAuthenticationPhoneNumber")
                .put("phone_number", phoneNumber.trim())
                .put("settings", JSONObject.NULL)
        )
    }

    override fun requestQrLogin() {
        send(
            JSONObject()
                .put("@type", "requestQrCodeAuthentication")
                .put("other_user_ids", JSONArray())
        )
    }

    override fun submitCode(code: String) {
        send(JSONObject().put("@type", "checkAuthenticationCode").put("code", code.trim()))
    }

    override fun submitPassword(password: String) {
        send(JSONObject().put("@type", "checkAuthenticationPassword").put("password", password))
    }

    override fun submitEmailAddress(email: String) {
        send(JSONObject().put("@type", "setAuthenticationEmailAddress").put("email_address", email.trim()))
    }

    override fun submitEmailCode(code: String) {
        send(
            JSONObject()
                .put("@type", "checkAuthenticationEmailCode")
                .put(
                    "code",
                    JSONObject()
                        .put("@type", "emailAddressAuthenticationCode")
                        .put("code", code.trim())
                )
        )
    }

    override fun register(firstName: String, lastName: String) {
        send(
            JSONObject()
                .put("@type", "registerUser")
                .put("first_name", firstName.trim())
                .put("last_name", lastName.trim())
                .put("disable_notification", true)
        )
    }

    override fun refreshChats() {
        send(
            JSONObject()
                .put("@type", "loadChats")
                .put("chat_list", JSONObject().put("@type", "chatListMain"))
                .put("limit", 100)
        )
    }

    private fun receiveLoop() {
        while (running.get()) {
            val raw = runCatching { TdLib.receive(1.0) }
                .onFailure { setError("TDLib receive failed: ${it.message ?: it.javaClass.simpleName}") }
                .getOrNull()
                ?: continue

            runCatching { handle(JSONObject(raw)) }
                .onFailure { setError("TDLib update parse failed: ${it.message ?: it.javaClass.simpleName}") }
        }
    }

    private fun handle(objectJson: JSONObject) {
        val responseClientId = objectJson.optInt("@client_id", clientId)
        if (responseClientId != clientId) return

        if (objectJson.optString("@extra") == TOR_PROXY_EXTRA) {
            privacyProxyRequestPending = false
            if (objectJson.optString("@type") == "addedProxy") {
                privacyProxyConfigured = true
                _state.value = _state.value.copy(connectionLabel = "Tor proxy enabled — direct fallback blocked", lastError = null)
                sendTdlibParameters()
            } else {
                updateAuth(
                    TelegramAuthStage.WaitPrivacyRoute(TorSupport.isOrbotInstalled(appContext)),
                    "Tor proxy setup failed — Telegram remains paused"
                )
                setError(objectJson.optString("message", "TDLib could not enable the Tor SOCKS5 proxy."))
            }
            return
        }

        when (objectJson.optString("@type")) {
            "updateAuthorizationState" -> handleAuthorizationState(objectJson.getJSONObject("authorization_state"))
            "updateConnectionState" -> handleConnectionState(objectJson.optJSONObject("state"))
            "updateNewChat" -> objectJson.optJSONObject("chat")?.let(::upsertChat)
            "updateChatTitle" -> updateTitle(objectJson.optLong("chat_id"), objectJson.optString("title"))
            "updateChatLastMessage" -> updateLastMessage(objectJson)
            "updateChatReadInbox" -> updateUnread(objectJson.optLong("chat_id"), objectJson.optInt("unread_count"))
            "error" -> setError(objectJson.optString("message", "Telegram returned an unknown error."))
        }
    }

    private fun handleAuthorizationState(auth: JSONObject) {
        when (auth.optString("@type")) {
            "authorizationStateWaitTdlibParameters" -> {
                waitingForParameters = true
                prepareInitialization()
            }
            "authorizationStateWaitPhoneNumber" -> updateAuth(TelegramAuthStage.WaitPhoneNumber, "Ready to log in through Tor")
            "authorizationStateWaitCode" -> {
                val length = auth.optJSONObject("code_info")
                    ?.optJSONObject("type")
                    ?.optInt("length")
                    ?.takeIf { it > 0 }
                updateAuth(TelegramAuthStage.WaitCode(length), "Authentication code required")
            }
            "authorizationStateWaitPassword" -> updateAuth(TelegramAuthStage.WaitPassword, "Two-step verification required")
            "authorizationStateWaitEmailAddress" -> updateAuth(TelegramAuthStage.WaitEmailAddress, "Email address required")
            "authorizationStateWaitEmailCode" -> updateAuth(TelegramAuthStage.WaitEmailCode, "Email code required")
            "authorizationStateWaitRegistration" -> updateAuth(TelegramAuthStage.WaitRegistration, "Registration details required")
            "authorizationStateWaitOtherDeviceConfirmation" -> {
                updateAuth(
                    TelegramAuthStage.WaitOtherDeviceConfirmation(auth.optString("link")),
                    "Scan with another Telegram device"
                )
            }
            "authorizationStateReady" -> {
                waitingForParameters = false
                updateAuth(TelegramAuthStage.Ready, "Connected through privacy proxy")
                refreshChats()
            }
            "authorizationStateLoggingOut", "authorizationStateClosing" -> updateAuth(TelegramAuthStage.Closing, "Closing Telegram session")
            "authorizationStateClosed" -> updateAuth(TelegramAuthStage.Closed, "TDLib closed")
        }
    }

    private fun prepareInitialization() {
        if (!isConfigured) {
            updateAuth(TelegramAuthStage.NeedApiCredentials, "Telegram API credentials required — network locked")
            return
        }
        if (!TorSupport.isOrbotInstalled(appContext)) {
            updateAuth(TelegramAuthStage.WaitPrivacyRoute(false), "Orbot required — Telegram network is paused")
            return
        }
        if (privacyProxyConfigured) {
            sendTdlibParameters()
            return
        }
        if (!privacyProxyRequestPending) configureTorProxy()
    }

    private fun configureTorProxy() {
        privacyProxyRequestPending = true
        updateAuth(TelegramAuthStage.WaitPrivacyRoute(true), "Enabling Tor SOCKS5 route…")
        val proxy = JSONObject()
            .put("@type", "proxy")
            .put("server", TorSupport.DEFAULT_SOCKS_HOST)
            .put("port", TorSupport.DEFAULT_SOCKS_PORT)
            .put(
                "type",
                JSONObject()
                    .put("@type", "proxyTypeSocks5")
                    .put("username", "")
                    .put("password", "")
            )
        send(
            JSONObject()
                .put("@type", "addProxy")
                .put("proxy", proxy)
                .put("enable", true)
                .put("@extra", TOR_PROXY_EXTRA)
        )
    }

    private fun handleConnectionState(stateJson: JSONObject?) {
        val label = when (stateJson?.optString("@type")) {
            "connectionStateWaitingForNetwork" -> "Waiting for network through Tor"
            "connectionStateConnectingToProxy" -> "Connecting to Tor"
            "connectionStateConnecting" -> "Connecting to Telegram through Tor"
            "connectionStateUpdating" -> "Syncing Telegram through Tor"
            "connectionStateReady" -> "Connected through privacy proxy"
            else -> return
        }
        _state.value = _state.value.copy(connectionLabel = label)
    }

    private fun sendTdlibParameters() {
        if (!isConfigured || !privacyProxyConfigured) return
        waitingForParameters = false
        val dbDir = appContext.filesDir.resolve("tdlib/database").apply { mkdirs() }.absolutePath
        val filesDir = appContext.filesDir.resolve("tdlib/files").apply { mkdirs() }.absolutePath
        val request = JSONObject()
            .put("@type", "setTdlibParameters")
            .put("use_test_dc", false)
            .put("database_directory", dbDir)
            .put("files_directory", filesDir)
            .put("database_encryption_key", "")
            .put("use_file_database", true)
            .put("use_chat_info_database", true)
            .put("use_message_database", true)
            .put("use_secret_chats", true)
            .put("api_id", apiId)
            .put("api_hash", apiHash)
            .put("system_language_code", Locale.getDefault().toLanguageTag())
            .put("device_model", "${Build.MANUFACTURER} ${Build.MODEL}".trim())
            .put("system_version", Build.VERSION.RELEASE ?: "Android")
            .put("application_version", BuildConfig.VERSION_NAME)
        send(request)
    }

    private fun send(request: JSONObject) {
        if (clientId == 0) return
        runCatching { TdLib.send(clientId, request.toString()) }
            .onFailure { setError("TDLib send failed: ${it.message ?: it.javaClass.simpleName}") }
    }

    private fun upsertChat(chat: JSONObject) {
        val id = chat.optLong("id")
        if (id == 0L) return
        val summary = TelegramChatSummary(
            id = id,
            title = chat.optString("title", "Telegram chat"),
            preview = previewFor(chat.optJSONObject("last_message")),
            unreadCount = chat.optInt("unread_count"),
            order = chat.optJSONArray("positions")?.optJSONObject(0)?.optLong("order") ?: 0L,
            kind = kindFor(chat.optJSONObject("type"))
        )
        synchronized(chats) { chats[id] = summary }
        publishChats()
    }

    private fun updateTitle(chatId: Long, title: String) {
        synchronized(chats) {
            val current = chats[chatId] ?: return
            chats[chatId] = current.copy(title = title)
        }
        publishChats()
    }

    private fun updateLastMessage(update: JSONObject) {
        val chatId = update.optLong("chat_id")
        synchronized(chats) {
            val current = chats[chatId] ?: return
            val order = update.optJSONArray("positions")?.optJSONObject(0)?.optLong("order") ?: current.order
            chats[chatId] = current.copy(preview = previewFor(update.optJSONObject("last_message")), order = order)
        }
        publishChats()
    }

    private fun updateUnread(chatId: Long, unread: Int) {
        synchronized(chats) {
            val current = chats[chatId] ?: return
            chats[chatId] = current.copy(unreadCount = unread)
        }
        publishChats()
    }

    private fun publishChats() {
        val ordered = synchronized(chats) {
            chats.values.sortedWith(compareByDescending<TelegramChatSummary> { it.order }.thenByDescending { it.id })
        }
        _state.value = _state.value.copy(chats = ordered)
    }

    private fun kindFor(type: JSONObject?): TelegramChatSummary.Kind = when (type?.optString("@type")) {
        "chatTypePrivate" -> TelegramChatSummary.Kind.PRIVATE
        "chatTypeBasicGroup" -> TelegramChatSummary.Kind.GROUP
        "chatTypeSupergroup" -> TelegramChatSummary.Kind.CHANNEL_OR_SUPERGROUP
        "chatTypeSecret" -> TelegramChatSummary.Kind.SECRET
        else -> TelegramChatSummary.Kind.UNKNOWN
    }

    private fun previewFor(message: JSONObject?): String {
        val content = message?.optJSONObject("content") ?: return ""
        return when (content.optString("@type")) {
            "messageText" -> content.optJSONObject("text")?.optString("text").orEmpty()
            "messagePhoto" -> caption(content, "Photo")
            "messageVideo" -> caption(content, "Video")
            "messageAnimation" -> caption(content, "GIF")
            "messageDocument" -> caption(content, "Document")
            "messageAudio" -> caption(content, "Audio")
            "messageVoiceNote" -> "Voice message"
            "messageVideoNote" -> "Video message"
            "messageSticker" -> "Sticker"
            "messageContact" -> "Contact"
            "messageLocation" -> "Location"
            else -> "Telegram message"
        }
    }

    private fun caption(content: JSONObject, fallback: String): String {
        val text = content.optJSONObject("caption")?.optString("text").orEmpty()
        return text.ifBlank { fallback }
    }

    private fun updateAuth(stage: TelegramAuthStage, label: String) {
        _state.value = _state.value.copy(authStage = stage, connectionLabel = label, lastError = null)
    }

    private fun setError(message: String) {
        _state.value = _state.value.copy(lastError = message)
    }

    private companion object {
        const val TOR_PROXY_EXTRA = "lattice_tor_proxy_setup"
    }
}

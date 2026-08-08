package com.retrofrost.lattice.telegram

import android.content.Context
import android.os.Build
import com.retrofrost.lattice.BuildConfig
import com.retrofrost.lattice.privacy.TorSupport
import com.retrofrost.lattice.security.TdlibDatabaseKeyStore
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
    private val activeMessages = LinkedHashMap<Long, TelegramMessageItem>()

    private var clientId: Int = 0
    private var apiId: Int = BuildConfig.TELEGRAM_API_ID
    private var apiHash: String = BuildConfig.TELEGRAM_API_HASH
    private var waitingForParameters = false
    private var privacyProxyConfigured = false
    private var privacyProxyRequestPending = false
    private var pendingInviteLink: String? = null

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
        if (clientId != 0) send(JSONObject().put("@type", "close"))
    }

    override fun close() {
        stop()
        scope.cancel()
    }

    override fun openLink(rawLink: String): TelegramLinkTarget? = TelegramLinkRouter.parse(rawLink)

    override fun openTelegramLink(rawLink: String) {
        if (_state.value.authStage != TelegramAuthStage.Ready) {
            setError("Telegram links can be opened after login is complete.")
            return
        }
        when (val target = openLink(rawLink)) {
            is TelegramLinkTarget.Username -> if (target.messageId == null) requestPublicChat(target.username) else requestMessageLink(rawLink)
            is TelegramLinkTarget.PrivatePost -> requestMessageLink(rawLink)
            is TelegramLinkTarget.Invite -> requestInvite(rawLink)
            is TelegramLinkTarget.TelegramScheme -> requestInternalLink(rawLink)
            null -> setError("This isn't a supported Telegram link.")
        }
    }

    override fun joinPendingInvite() {
        val inviteLink = pendingInviteLink ?: _state.value.pendingInvite?.inviteLink ?: return
        send(JSONObject().put("@type", "joinChatByInviteLink").put("invite_link", inviteLink).put("@extra", LINK_JOIN_EXTRA))
    }

    override fun dismissPendingInvite() {
        pendingInviteLink = null
        _state.value = _state.value.copy(pendingInvite = null, lastError = null)
    }

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
        send(JSONObject().put("@type", "setAuthenticationPhoneNumber").put("phone_number", phoneNumber.trim()).put("settings", JSONObject.NULL))
    }

    override fun requestQrLogin() {
        send(JSONObject().put("@type", "requestQrCodeAuthentication").put("other_user_ids", JSONArray()))
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
                .put("code", JSONObject().put("@type", "emailAddressAuthenticationCode").put("code", code.trim()))
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
        send(JSONObject().put("@type", "loadChats").put("chat_list", JSONObject().put("@type", "chatListMain")).put("limit", 100))
    }

    override fun openChat(chatId: Long) {
        openChatInternal(chatId, 0)
    }

    override fun closeChat() {
        val chatId = _state.value.activeChatId ?: return
        send(JSONObject().put("@type", "closeChat").put("chat_id", chatId))
        synchronized(activeMessages) { activeMessages.clear() }
        _state.value = _state.value.copy(activeChatId = null, activeMessages = emptyList(), messagesLoading = false, lastError = null)
    }

    override fun sendTextMessage(chatId: Long, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val formattedText = JSONObject().put("@type", "formattedText").put("text", trimmed).put("entities", JSONArray())
        val linkPreviewOptions = JSONObject()
            .put("@type", "linkPreviewOptions")
            .put("is_disabled", true)
            .put("url", "")
            .put("force_small_media", false)
            .put("force_large_media", false)
            .put("show_above_text", false)
        val inputContent = JSONObject()
            .put("@type", "inputMessageText")
            .put("text", formattedText)
            .put("link_preview_options", linkPreviewOptions)
            .put("clear_draft", true)
        send(
            JSONObject()
                .put("@type", "sendMessage")
                .put("chat_id", chatId)
                .put("topic_id", JSONObject.NULL)
                .put("reply_to", JSONObject.NULL)
                .put("options", JSONObject.NULL)
                .put("reply_markup", JSONObject.NULL)
                .put("input_message_content", inputContent)
        )
    }

    override fun downloadFile(fileId: Int) {
        if (fileId <= 0) return
        send(
            JSONObject()
                .put("@type", "downloadFile")
                .put("file_id", fileId)
                .put("priority", 16)
                .put("offset", 0)
                .put("limit", 0)
                .put("synchronous", false)
        )
    }

    override fun cancelDownload(fileId: Int) {
        if (fileId <= 0) return
        send(JSONObject().put("@type", "cancelDownloadFile").put("file_id", fileId).put("only_if_pending", false))
    }

    private fun requestPublicChat(username: String) {
        send(JSONObject().put("@type", "searchPublicChat").put("username", username).put("@extra", LINK_PUBLIC_CHAT_EXTRA))
    }

    private fun requestMessageLink(url: String) {
        send(JSONObject().put("@type", "getMessageLinkInfo").put("url", url).put("@extra", LINK_MESSAGE_EXTRA))
    }

    private fun requestInvite(inviteLink: String) {
        pendingInviteLink = inviteLink
        _state.value = _state.value.copy(pendingInvite = null, lastError = null)
        send(JSONObject().put("@type", "checkChatInviteLink").put("invite_link", inviteLink).put("@extra", LINK_INVITE_EXTRA))
    }

    private fun requestInternalLink(link: String) {
        send(JSONObject().put("@type", "getInternalLinkType").put("link", link).put("@extra", LINK_INTERNAL_EXTRA))
    }

    private fun openChatInternal(chatId: Long, fromMessageId: Long) {
        synchronized(activeMessages) { activeMessages.clear() }
        _state.value = _state.value.copy(activeChatId = chatId, activeMessages = emptyList(), messagesLoading = true, pendingInvite = null, lastError = null)
        send(JSONObject().put("@type", "openChat").put("chat_id", chatId))
        send(
            JSONObject()
                .put("@type", "getChatHistory")
                .put("chat_id", chatId)
                .put("from_message_id", fromMessageId)
                .put("offset", if (fromMessageId == 0L) 0 else -20)
                .put("limit", 50)
                .put("only_local", false)
                .put("@extra", "$HISTORY_EXTRA_PREFIX$chatId")
        )
    }

    private fun receiveLoop() {
        while (running.get()) {
            val raw = runCatching { TdLib.receive(1.0) }
                .onFailure { setError("TDLib receive failed: ${it.message ?: it.javaClass.simpleName}") }
                .getOrNull() ?: continue
            runCatching { handle(JSONObject(raw)) }
                .onFailure { setError("TDLib update parse failed: ${it.message ?: it.javaClass.simpleName}") }
        }
    }

    private fun handle(objectJson: JSONObject) {
        val responseClientId = objectJson.optInt("@client_id", clientId)
        if (responseClientId != clientId) return
        val extra = objectJson.optString("@extra")
        when {
            extra == TOR_PROXY_EXTRA -> { handleTorProxyResponse(objectJson); return }
            extra.startsWith(HISTORY_EXTRA_PREFIX) -> { handleHistoryResponse(extra, objectJson); return }
            extra == LINK_PUBLIC_CHAT_EXTRA -> { handlePublicChatResponse(objectJson); return }
            extra == LINK_MESSAGE_EXTRA -> { handleMessageLinkResponse(objectJson); return }
            extra == LINK_INVITE_EXTRA -> { handleInviteResponse(objectJson); return }
            extra == LINK_JOIN_EXTRA -> { handleJoinResponse(objectJson); return }
            extra == LINK_INTERNAL_EXTRA -> { handleInternalLinkResponse(objectJson); return }
        }

        when (objectJson.optString("@type")) {
            "updateAuthorizationState" -> handleAuthorizationState(objectJson.getJSONObject("authorization_state"))
            "updateConnectionState" -> handleConnectionState(objectJson.optJSONObject("state"))
            "updateNewChat" -> objectJson.optJSONObject("chat")?.let(::upsertChat)
            "updateChatTitle" -> updateTitle(objectJson.optLong("chat_id"), objectJson.optString("title"))
            "updateChatPhoto" -> updateChatPhoto(objectJson)
            "updateChatPosition" -> updatePosition(objectJson)
            "updateChatLastMessage" -> updateLastMessage(objectJson)
            "updateChatReadInbox" -> updateUnread(objectJson.optLong("chat_id"), objectJson.optInt("unread_count"))
            "updateNewMessage" -> objectJson.optJSONObject("message")?.let(::upsertActiveMessage)
            "updateMessageContent" -> updateMessageContent(objectJson)
            "updateDeleteMessages" -> removeMessages(objectJson)
            "updateFile" -> objectJson.optJSONObject("file")?.let(::updateFile)
            "error" -> setError(objectJson.optString("message", "Telegram returned an unknown error."))
        }
    }

    private fun handleTorProxyResponse(objectJson: JSONObject) {
        privacyProxyRequestPending = false
        if (objectJson.optString("@type") == "addedProxy") {
            privacyProxyConfigured = true
            _state.value = _state.value.copy(connectionLabel = "Tor proxy enabled — direct fallback blocked", lastError = null)
            sendTdlibParameters()
        } else {
            updateAuth(TelegramAuthStage.WaitPrivacyRoute(TorSupport.isOrbotInstalled(appContext)), "Tor proxy setup failed — Telegram remains paused")
            setError(objectJson.optString("message", "TDLib could not enable the Tor SOCKS5 proxy."))
        }
    }

    private fun handleHistoryResponse(extra: String, response: JSONObject) {
        val chatId = extra.removePrefix(HISTORY_EXTRA_PREFIX).toLongOrNull() ?: return
        if (_state.value.activeChatId != chatId) return
        if (response.optString("@type") == "error") {
            _state.value = _state.value.copy(messagesLoading = false)
            setError(response.optString("message", "Unable to load chat history."))
            return
        }
        val items = response.optJSONArray("messages") ?: JSONArray()
        synchronized(activeMessages) {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                parseMessage(item)?.let { activeMessages[it.id] = it }
            }
        }
        publishActiveMessages(loading = false)
    }

    private fun handlePublicChatResponse(response: JSONObject) {
        if (response.optString("@type") == "error") { setError(response.optString("message", "Public Telegram chat wasn't found.")); return }
        if (response.optString("@type") != "chat") { setError("Telegram returned an unexpected public-chat response."); return }
        upsertChat(response)
        response.optLong("id").takeIf { it != 0L }?.let(::openChat)
    }

    private fun handleMessageLinkResponse(response: JSONObject) {
        if (response.optString("@type") == "error") { setError(response.optString("message", "Telegram message link couldn't be opened.")); return }
        val chatId = response.optLong("chat_id")
        if (chatId == 0L) { setError("The linked Telegram chat isn't accessible."); return }
        val linkedMessage = response.optJSONObject("message")
        openChatInternal(chatId, linkedMessage?.optLong("id") ?: 0L)
        linkedMessage?.let(::upsertActiveMessage)
    }

    private fun handleInviteResponse(response: JSONObject) {
        if (response.optString("@type") == "error") { setError(response.optString("message", "Telegram invite link is invalid or unavailable.")); return }
        val inviteLink = pendingInviteLink ?: return
        val chatId = response.optLong("chat_id")
        if (chatId != 0L) {
            pendingInviteLink = null
            _state.value = _state.value.copy(pendingInvite = null)
            openChat(chatId)
            return
        }
        _state.value = _state.value.copy(
            pendingInvite = TelegramInvitePreview(
                inviteLink = inviteLink,
                title = response.optString("title", "Telegram chat"),
                description = response.optString("description"),
                memberCount = response.optInt("member_count"),
                createsJoinRequest = response.optBoolean("creates_join_request"),
                requiresSubscription = response.optJSONObject("subscription_info") != null
            ),
            lastError = null
        )
    }

    private fun handleJoinResponse(response: JSONObject) {
        if (response.optString("@type") == "error") { setError(response.optString("message", "Unable to join this Telegram chat.")); return }
        if (response.optString("@type") != "chat") { setError("Telegram returned an unexpected join response."); return }
        pendingInviteLink = null
        _state.value = _state.value.copy(pendingInvite = null, lastError = null)
        upsertChat(response)
        response.optLong("id").takeIf { it != 0L }?.let(::openChat)
    }

    private fun handleInternalLinkResponse(response: JSONObject) {
        if (response.optString("@type") == "error") { setError(response.optString("message", "Telegram link couldn't be resolved.")); return }
        when (response.optString("@type")) {
            "internalLinkTypePublicChat" -> requestPublicChat(response.optString("chat_username"))
            "internalLinkTypeMessage" -> requestMessageLink(response.optString("url"))
            "internalLinkTypeChatInvite" -> requestInvite(response.optString("invite_link"))
            else -> setError("This Telegram link type isn't supported by Lattice yet.")
        }
    }

    private fun handleAuthorizationState(auth: JSONObject) {
        when (auth.optString("@type")) {
            "authorizationStateWaitTdlibParameters" -> { waitingForParameters = true; prepareInitialization() }
            "authorizationStateWaitPhoneNumber" -> updateAuth(TelegramAuthStage.WaitPhoneNumber, "Ready to log in through Tor")
            "authorizationStateWaitCode" -> {
                val length = auth.optJSONObject("code_info")?.optJSONObject("type")?.optInt("length")?.takeIf { it > 0 }
                updateAuth(TelegramAuthStage.WaitCode(length), "Authentication code required")
            }
            "authorizationStateWaitPassword" -> updateAuth(TelegramAuthStage.WaitPassword, "Two-step verification required")
            "authorizationStateWaitEmailAddress" -> updateAuth(TelegramAuthStage.WaitEmailAddress, "Email address required")
            "authorizationStateWaitEmailCode" -> updateAuth(TelegramAuthStage.WaitEmailCode, "Email code required")
            "authorizationStateWaitRegistration" -> updateAuth(TelegramAuthStage.WaitRegistration, "Registration details required")
            "authorizationStateWaitOtherDeviceConfirmation" -> updateAuth(TelegramAuthStage.WaitOtherDeviceConfirmation(auth.optString("link")), "Scan with another Telegram device")
            "authorizationStateReady" -> { waitingForParameters = false; updateAuth(TelegramAuthStage.Ready, "Connected through privacy proxy"); refreshChats() }
            "authorizationStateLoggingOut", "authorizationStateClosing" -> updateAuth(TelegramAuthStage.Closing, "Closing Telegram session")
            "authorizationStateClosed" -> updateAuth(TelegramAuthStage.Closed, "TDLib closed")
        }
    }

    private fun prepareInitialization() {
        if (!isConfigured) { updateAuth(TelegramAuthStage.NeedApiCredentials, "Telegram API credentials required — network locked"); return }
        if (!TorSupport.isOrbotInstalled(appContext)) { updateAuth(TelegramAuthStage.WaitPrivacyRoute(false), "Orbot required — Telegram network is paused"); return }
        if (privacyProxyConfigured) { sendTdlibParameters(); return }
        if (!privacyProxyRequestPending) configureTorProxy()
    }

    private fun configureTorProxy() {
        privacyProxyRequestPending = true
        updateAuth(TelegramAuthStage.WaitPrivacyRoute(true), "Enabling Tor SOCKS5 route…")
        val proxy = JSONObject()
            .put("@type", "proxy")
            .put("server", TorSupport.DEFAULT_SOCKS_HOST)
            .put("port", TorSupport.DEFAULT_SOCKS_PORT)
            .put("type", JSONObject().put("@type", "proxyTypeSocks5").put("username", "").put("password", ""))
        send(JSONObject().put("@type", "addProxy").put("proxy", proxy).put("enable", true).put("@extra", TOR_PROXY_EXTRA))
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
        val databaseKey = runCatching { TdlibDatabaseKeyStore.getOrCreateBase64Key(appContext) }
            .onFailure { setError("Local database security setup failed: ${it.message ?: it.javaClass.simpleName}") }
            .getOrNull() ?: return
        waitingForParameters = false
        val dbDir = appContext.filesDir.resolve("tdlib/database").apply { mkdirs() }.absolutePath
        val filesDir = appContext.filesDir.resolve("tdlib/files").apply { mkdirs() }.absolutePath
        send(
            JSONObject()
                .put("@type", "setTdlibParameters")
                .put("use_test_dc", false)
                .put("database_directory", dbDir)
                .put("files_directory", filesDir)
                .put("database_encryption_key", databaseKey)
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
        )
    }

    private fun send(request: JSONObject) {
        if (clientId == 0) return
        runCatching { TdLib.send(clientId, request.toString()) }
            .onFailure { setError("TDLib send failed: ${it.message ?: it.javaClass.simpleName}") }
    }

    private fun upsertChat(chat: JSONObject) {
        val id = chat.optLong("id")
        if (id == 0L) return
        val photo = chat.optJSONObject("photo")?.optJSONObject("small")
        val summary = TelegramChatSummary(
            id = id,
            title = chat.optString("title", "Telegram chat"),
            preview = previewFor(chat.optJSONObject("last_message")),
            unreadCount = chat.optInt("unread_count"),
            order = chat.optJSONArray("positions")?.optJSONObject(0)?.optLong("order") ?: 0L,
            kind = kindFor(chat.optJSONObject("type")),
            photoFileId = fileId(photo),
            photoPath = filePath(photo)
        )
        synchronized(chats) { chats[id] = summary }
        publishChats()
    }

    private fun updateTitle(chatId: Long, title: String) {
        synchronized(chats) { val current = chats[chatId] ?: return; chats[chatId] = current.copy(title = title) }
        publishChats()
    }

    private fun updateChatPhoto(update: JSONObject) {
        val chatId = update.optLong("chat_id")
        val file = update.optJSONObject("photo")?.optJSONObject("small")
        synchronized(chats) {
            val current = chats[chatId] ?: return
            chats[chatId] = current.copy(photoFileId = fileId(file), photoPath = filePath(file))
        }
        publishChats()
    }

    private fun updatePosition(update: JSONObject) {
        val chatId = update.optLong("chat_id")
        val position = update.optJSONObject("position") ?: return
        synchronized(chats) { val current = chats[chatId] ?: return; chats[chatId] = current.copy(order = position.optLong("order")) }
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
        synchronized(chats) { val current = chats[chatId] ?: return; chats[chatId] = current.copy(unreadCount = unread) }
        publishChats()
    }

    private fun updateFile(file: JSONObject) {
        val id = file.optInt("id")
        if (id <= 0) return
        val path = filePath(file)
        val local = file.optJSONObject("local")
        val active = local?.optBoolean("is_downloading_active") ?: false
        val complete = local?.optBoolean("is_downloading_completed") ?: false
        val size = file.optLong("size").takeIf { it > 0 } ?: file.optLong("expected_size")
        var chatChanged = false
        synchronized(chats) {
            chats.keys.toList().forEach { chatId ->
                val current = chats[chatId] ?: return@forEach
                if (current.photoFileId == id) {
                    chats[chatId] = current.copy(photoPath = path)
                    chatChanged = true
                }
            }
        }
        var messageChanged = false
        synchronized(activeMessages) {
            activeMessages.keys.toList().forEach { messageId ->
                val current = activeMessages[messageId] ?: return@forEach
                if (current.mediaFileId == id) {
                    activeMessages[messageId] = current.copy(
                        mediaPath = path,
                        mediaSize = if (size > 0) size else current.mediaSize,
                        mediaDownloadActive = active,
                        mediaDownloadComplete = complete
                    )
                    messageChanged = true
                }
            }
        }
        if (chatChanged) publishChats()
        if (messageChanged) publishActiveMessages(loading = false)
    }

    private fun publishChats() {
        val ordered = synchronized(chats) { chats.values.sortedWith(compareByDescending<TelegramChatSummary> { it.order }.thenByDescending { it.id }) }
        _state.value = _state.value.copy(chats = ordered)
    }

    private fun upsertActiveMessage(message: JSONObject) {
        val parsed = parseMessage(message) ?: return
        if (_state.value.activeChatId != parsed.chatId) return
        synchronized(activeMessages) { activeMessages[parsed.id] = parsed }
        publishActiveMessages(loading = false)
    }

    private fun updateMessageContent(update: JSONObject) {
        val chatId = update.optLong("chat_id")
        val messageId = update.optLong("message_id")
        if (_state.value.activeChatId != chatId) return
        val newContent = update.optJSONObject("new_content") ?: return
        synchronized(activeMessages) {
            val current = activeMessages[messageId] ?: return
            val (text, kind) = messageContent(newContent)
            val media = primaryMediaFile(newContent)
            activeMessages[messageId] = current.copy(
                text = text,
                contentKind = kind,
                mediaFileId = fileId(media),
                mediaPath = filePath(media),
                mediaSize = media?.optLong("size") ?: 0L,
                mediaDownloadActive = media?.optJSONObject("local")?.optBoolean("is_downloading_active") ?: false,
                mediaDownloadComplete = media?.optJSONObject("local")?.optBoolean("is_downloading_completed") ?: false
            )
        }
        publishActiveMessages(loading = false)
    }

    private fun removeMessages(update: JSONObject) {
        val chatId = update.optLong("chat_id")
        if (_state.value.activeChatId != chatId) return
        val ids = update.optJSONArray("message_ids") ?: return
        synchronized(activeMessages) { for (index in 0 until ids.length()) activeMessages.remove(ids.optLong(index)) }
        publishActiveMessages(loading = false)
    }

    private fun publishActiveMessages(loading: Boolean) {
        val ordered = synchronized(activeMessages) { activeMessages.values.sortedWith(compareBy<TelegramMessageItem> { it.date }.thenBy { it.id }) }
        _state.value = _state.value.copy(activeMessages = ordered, messagesLoading = loading)
    }

    private fun parseMessage(message: JSONObject): TelegramMessageItem? {
        val id = message.optLong("id")
        val chatId = message.optLong("chat_id")
        if (id == 0L || chatId == 0L) return null
        val content = message.optJSONObject("content")
        val (text, kind) = messageContent(content)
        val media = primaryMediaFile(content)
        val local = media?.optJSONObject("local")
        return TelegramMessageItem(
            id = id,
            chatId = chatId,
            text = text,
            isOutgoing = message.optBoolean("is_outgoing"),
            date = message.optInt("date"),
            contentKind = kind,
            mediaFileId = fileId(media),
            mediaPath = filePath(media),
            mediaSize = media?.optLong("size") ?: 0L,
            mediaDownloadActive = local?.optBoolean("is_downloading_active") ?: false,
            mediaDownloadComplete = local?.optBoolean("is_downloading_completed") ?: false
        )
    }

    private fun messageContent(content: JSONObject?): Pair<String, String> {
        val type = content?.optString("@type").orEmpty()
        val text = when (type) {
            "messageText" -> content?.optJSONObject("text")?.optString("text").orEmpty()
            "messagePhoto" -> caption(content, "Photo")
            "messageVideo" -> caption(content, "Video")
            "messageAnimation" -> caption(content, "GIF")
            "messageDocument" -> caption(content, "Document")
            "messageAudio" -> caption(content, "Audio")
            "messageVoiceNote" -> "Voice message"
            "messageVideoNote" -> "Video message"
            "messageSticker" -> "Sticker"
            "messagePaidMedia" -> "Paid media"
            "messageContact" -> "Contact"
            "messageLocation" -> "Location"
            "messagePoll" -> content?.optJSONObject("poll")?.optJSONObject("question")?.optString("text") ?: "Poll"
            else -> "Telegram message"
        }
        return text to type.ifBlank { "unknown" }
    }

    private fun primaryMediaFile(content: JSONObject?): JSONObject? = when (content?.optString("@type")) {
        "messagePhoto" -> largestPhotoFile(content.optJSONObject("photo")?.optJSONArray("sizes"))
        "messageVideo" -> content.optJSONObject("video")?.optJSONObject("video")
        "messageAnimation" -> content.optJSONObject("animation")?.optJSONObject("animation")
        "messageDocument" -> content.optJSONObject("document")?.optJSONObject("document")
        "messageAudio" -> content.optJSONObject("audio")?.optJSONObject("audio")
        "messageVoiceNote" -> content.optJSONObject("voice_note")?.optJSONObject("voice")
        "messageVideoNote" -> content.optJSONObject("video_note")?.optJSONObject("video")
        "messageSticker" -> content.optJSONObject("sticker")?.optJSONObject("sticker")
        else -> null
    }

    private fun largestPhotoFile(sizes: JSONArray?): JSONObject? {
        if (sizes == null) return null
        var best: JSONObject? = null
        var bestArea = -1L
        for (index in 0 until sizes.length()) {
            val size = sizes.optJSONObject(index) ?: continue
            val area = size.optLong("width") * size.optLong("height")
            if (area >= bestArea) {
                bestArea = area
                best = size.optJSONObject("photo")
            }
        }
        return best
    }

    private fun fileId(file: JSONObject?): Int? = file?.optInt("id")?.takeIf { it > 0 }

    private fun filePath(file: JSONObject?): String? = file
        ?.optJSONObject("local")
        ?.optString("path")
        ?.takeIf { it.isNotBlank() }

    private fun kindFor(type: JSONObject?): TelegramChatSummary.Kind = when (type?.optString("@type")) {
        "chatTypePrivate" -> TelegramChatSummary.Kind.PRIVATE
        "chatTypeBasicGroup" -> TelegramChatSummary.Kind.GROUP
        "chatTypeSupergroup" -> TelegramChatSummary.Kind.CHANNEL_OR_SUPERGROUP
        "chatTypeSecret" -> TelegramChatSummary.Kind.SECRET
        else -> TelegramChatSummary.Kind.UNKNOWN
    }

    private fun previewFor(message: JSONObject?): String = messageContent(message?.optJSONObject("content")).first

    private fun caption(content: JSONObject?, fallback: String): String {
        val text = content?.optJSONObject("caption")?.optString("text").orEmpty()
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
        const val HISTORY_EXTRA_PREFIX = "lattice_history:"
        const val LINK_PUBLIC_CHAT_EXTRA = "lattice_link_public_chat"
        const val LINK_MESSAGE_EXTRA = "lattice_link_message"
        const val LINK_INVITE_EXTRA = "lattice_link_invite"
        const val LINK_JOIN_EXTRA = "lattice_link_join"
        const val LINK_INTERNAL_EXTRA = "lattice_link_internal"
    }
}

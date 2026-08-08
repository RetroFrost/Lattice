package com.retrofrost.lattice.ui

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Message
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.retrofrost.lattice.model.LatticeSettings
import com.retrofrost.lattice.model.LatticeSettingsStore
import com.retrofrost.lattice.privacy.ContentFilterEngine
import com.retrofrost.lattice.telegram.TdlibTelegramRepository
import com.retrofrost.lattice.telegram.TelegramAuthStage
import com.retrofrost.lattice.telegram.TelegramChatSummary
import com.retrofrost.lattice.telegram.TelegramMessageItem
import com.retrofrost.lattice.telegram.TelegramUiState

private data class HomeTab(val title: String, val icon: ImageVector)

@Composable
fun LatticeApp(incomingLink: String?) {
    val context = LocalContext.current
    val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(context)
    } else {
        darkColorScheme()
    }
    val repository = remember { TdlibTelegramRepository(context.applicationContext) }
    val telegramState by repository.state.collectAsStateWithLifecycle()
    val settingsStore = remember { LatticeSettingsStore(context.applicationContext) }

    DisposableEffect(repository) {
        repository.start()
        onDispose { repository.close() }
    }

    LaunchedEffect(incomingLink, telegramState.authStage) {
        if (telegramState.authStage == TelegramAuthStage.Ready && !incomingLink.isNullOrBlank()) {
            repository.openTelegramLink(incomingLink)
        }
    }

    MaterialTheme(colorScheme = colors) {
        var settings by remember { mutableStateOf(settingsStore.load()) }
        var showSettings by remember { mutableStateOf(false) }
        val activeChatId = telegramState.activeChatId

        if (telegramState.authStage != TelegramAuthStage.Ready) {
            AuthScreen(state = telegramState, repository = repository)
        } else if (showSettings) {
            SettingsScreen(
                settings = settings,
                onSettingsChange = { updated ->
                    settings = updated
                    settingsStore.save(updated)
                },
                onBack = { showSettings = false }
            )
        } else if (activeChatId != null) {
            val chat = telegramState.chats.firstOrNull { it.id == activeChatId }
            ChatScreen(
                title = chat?.title ?: "Telegram chat",
                state = telegramState,
                settings = settings,
                onBack = repository::closeChat,
                onSend = { repository.sendTextMessage(activeChatId, it) }
            )
        } else {
            HomeScreen(
                state = telegramState,
                settings = settings,
                onOpenSettings = { showSettings = true },
                onRefresh = repository::refreshChats,
                onOpenChat = repository::openChat,
                onJoinInvite = repository::joinPendingInvite,
                onDismissInvite = repository::dismissPendingInvite
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    state: TelegramUiState,
    settings: LatticeSettings,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit,
    onOpenChat: (Long) -> Unit,
    onJoinInvite: () -> Unit,
    onDismissInvite: () -> Unit
) {
    val tabs = listOf(
        HomeTab("Chats", Icons.Outlined.Message),
        HomeTab("Updates", Icons.Outlined.Update),
        HomeTab("Groups", Icons.Outlined.Groups),
        HomeTab("Calls", Icons.Outlined.Call)
    )
    var selected by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Lattice")
                        Text(state.connectionLabel, style = MaterialTheme.typography.labelSmall)
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Outlined.Update, contentDescription = "Refresh chats")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selected == index,
                        onClick = { selected = index },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.title) }
                    )
                }
            }
        }
    ) { padding ->
        val title = tabs[selected].title
        val visibleChats = chatsForTab(title, state.chats)

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(16.dp)) {
                        Icon(Icons.Outlined.Lock, contentDescription = null)
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            Text("Maximum Privacy", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (settings.dndEnabled) "Do Not Disturb active • privacy route enforced"
                                else "Privacy route enforced • optional data disabled",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            state.pendingInvite?.let { invite ->
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Telegram invite", style = MaterialTheme.typography.titleLarge)
                            Text(invite.title, style = MaterialTheme.typography.titleMedium)
                            if (invite.memberCount > 0) Text("${invite.memberCount} members")
                            if (invite.description.isNotBlank()) Text(invite.description)
                            if (invite.requiresSubscription) {
                                Text("This invite requires a Telegram subscription payment.", style = MaterialTheme.typography.bodySmall)
                            } else if (invite.createsJoinRequest) {
                                Text("Joining sends a request to the chat administrators.", style = MaterialTheme.typography.bodySmall)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(onClick = onDismissInvite) { Text("Cancel") }
                                Button(onClick = onJoinInvite, enabled = !invite.requiresSubscription) {
                                    Text(if (invite.createsJoinRequest) "Request to join" else "Join")
                                }
                            }
                        }
                    }
                }
            }

            state.lastError?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
            item { Text(title, style = MaterialTheme.typography.headlineSmall) }

            if (title == "Calls") {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Calls", style = MaterialTheme.typography.titleMedium)
                            Text("P2P is disabled by Maximum Privacy; relay-only call support is still being wired.")
                        }
                    }
                }
            } else if (visibleChats.isEmpty()) {
                item { Text("No chats loaded in this section yet.") }
            } else {
                items(visibleChats, key = { it.id }) { chat ->
                    ChatRow(chat = chat, settings = settings, onClick = { onOpenChat(chat.id) })
                }
            }
        }
    }
}

@Composable
private fun ChatRow(chat: TelegramChatSummary, settings: LatticeSettings, onClick: () -> Unit) {
    val previewDecision = ContentFilterEngine.evaluate(
        TelegramMessageItem(-1L, chat.id, chat.preview, false, 0, "preview"),
        settings
    )
    val hideBadge = settings.dndEnabled && settings.dndHideUnreadBadges

    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(chat.title, style = MaterialTheme.typography.titleMedium)
            if (chat.preview.isNotBlank()) {
                Text(previewDecision.displayText, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
            }
            if (chat.unreadCount > 0 && !hideBadge) {
                Text("${chat.unreadCount} unread", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

private fun chatsForTab(tab: String, chats: List<TelegramChatSummary>): List<TelegramChatSummary> = when (tab) {
    "Chats" -> chats
    "Updates" -> chats.filter { it.kind == TelegramChatSummary.Kind.CHANNEL_OR_SUPERGROUP }
    "Groups" -> chats.filter {
        it.kind == TelegramChatSummary.Kind.GROUP || it.kind == TelegramChatSummary.Kind.CHANNEL_OR_SUPERGROUP
    }
    else -> emptyList()
}

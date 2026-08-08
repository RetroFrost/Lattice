package com.retrofrost.lattice.ui

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
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.retrofrost.lattice.model.LatticeSettings
import com.retrofrost.lattice.privacy.ContentFilterEngine
import com.retrofrost.lattice.telegram.TelegramMessageItem
import com.retrofrost.lattice.telegram.TelegramUiState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    title: String,
    state: TelegramUiState,
    settings: LatticeSettings,
    onBack: () -> Unit,
    onSend: (String) -> Unit
) {
    var draft by remember(state.activeChatId) { mutableStateOf("") }
    var revealedMessages by remember(state.activeChatId) { mutableStateOf(emptySet<Long>()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title)
                        Text(state.connectionLabel, style = MaterialTheme.typography.labelSmall)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 2.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.fillMaxWidth(0.84f),
                        placeholder = { Text("Message") },
                        maxLines = 5
                    )
                    IconButton(
                        onClick = {
                            val message = draft.trim()
                            if (message.isNotEmpty()) {
                                onSend(message)
                                draft = ""
                            }
                        },
                        enabled = draft.isNotBlank()
                    ) {
                        Icon(Icons.Outlined.Send, contentDescription = "Send")
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (state.messagesLoading) {
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator()
                    }
                }
            }

            state.lastError?.let { error ->
                item { Text(error, color = MaterialTheme.colorScheme.error) }
            }

            if (!state.messagesLoading && state.activeMessages.isEmpty()) {
                item { Text("No messages in this chat yet.") }
            }

            items(state.activeMessages, key = { it.id }) { message ->
                val decision = ContentFilterEngine.evaluate(message, settings)
                MessageBubble(
                    message = message,
                    decision = decision,
                    revealed = message.id in revealedMessages,
                    onShowOnce = {
                        revealedMessages = revealedMessages + message.id
                    }
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: TelegramMessageItem,
    decision: ContentFilterEngine.Decision,
    revealed: Boolean,
    onShowOnce: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isOutgoing) Arrangement.End else Arrangement.Start
    ) {
        Card(modifier = Modifier.fillMaxWidth(0.82f)) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                val showRaw = revealed && decision.action == ContentFilterEngine.Action.COLLAPSE
                Text(
                    if (showRaw) message.text else decision.displayText,
                    style = MaterialTheme.typography.bodyLarge
                )

                if (!showRaw && decision.reason != null) {
                    Text(decision.reason, style = MaterialTheme.typography.bodySmall)
                }

                if (!showRaw && decision.allowShowOnce) {
                    OutlinedButton(onClick = onShowOnce) {
                        Text("Show once")
                    }
                }

                if (isMediaKind(message.contentKind)) {
                    Text(
                        mediaPolicyLabel(message.contentKind),
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                Text(
                    formatMessageTime(message.date),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

private fun isMediaKind(kind: String): Boolean {
    val lower = kind.lowercase()
    return lower.contains("photo") || lower.contains("video") || lower.contains("animation") ||
        lower.contains("document") || lower.contains("audio") || lower.contains("voice") || lower.contains("sticker")
}

private fun mediaPolicyLabel(kind: String): String = when {
    kind.contains("paid", ignoreCase = true) -> "Stars media • hidden by Maximum Privacy"
    kind.contains("photo", ignoreCase = true) -> "Photo • manual download policy"
    kind.contains("video", ignoreCase = true) -> "Video • manual download / autoplay off"
    kind.contains("animation", ignoreCase = true) -> "GIF • autoplay off"
    kind.contains("voice", ignoreCase = true) -> "Voice • manual download policy"
    kind.contains("document", ignoreCase = true) -> "File • manual download policy"
    else -> "Media • privacy controls active"
}

private fun formatMessageTime(unixSeconds: Int): String {
    if (unixSeconds <= 0) return ""
    return MESSAGE_TIME_FORMAT.format(Instant.ofEpochSecond(unixSeconds.toLong()))
}

private val MESSAGE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter
    .ofPattern("HH:mm")
    .withZone(ZoneId.systemDefault())

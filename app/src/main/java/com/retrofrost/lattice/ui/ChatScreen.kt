package com.retrofrost.lattice.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.retrofrost.lattice.model.LatticeSettings
import com.retrofrost.lattice.privacy.ContentFilterEngine
import com.retrofrost.lattice.telegram.TelegramMessageItem
import com.retrofrost.lattice.telegram.TelegramUiState
import java.io.File
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
    onSend: (String) -> Unit,
    onDownloadFile: (Int) -> Unit,
    onCancelDownload: (Int) -> Unit
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
            state.lastError?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
            if (!state.messagesLoading && state.activeMessages.isEmpty()) item { Text("No messages in this chat yet.") }

            items(state.activeMessages, key = { it.id }) { message ->
                val decision = ContentFilterEngine.evaluate(message, settings)
                val shouldAutoDownload = shouldAutoDownload(message, settings)
                LaunchedEffect(message.mediaFileId, message.mediaDownloadComplete, shouldAutoDownload, decision.action) {
                    val fileId = message.mediaFileId
                    if (
                        fileId != null &&
                        shouldAutoDownload &&
                        !message.mediaDownloadComplete &&
                        !message.mediaDownloadActive &&
                        decision.action != ContentFilterEngine.Action.HIDE_PAID &&
                        decision.action != ContentFilterEngine.Action.COLLAPSE
                    ) {
                        onDownloadFile(fileId)
                    }
                }
                MessageBubble(
                    message = message,
                    decision = decision,
                    revealed = message.id in revealedMessages,
                    onShowOnce = { revealedMessages = revealedMessages + message.id },
                    onDownloadFile = onDownloadFile,
                    onCancelDownload = onCancelDownload
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
    onShowOnce: () -> Unit,
    onDownloadFile: (Int) -> Unit,
    onCancelDownload: (Int) -> Unit
) {
    val showRaw = revealed && decision.action == ContentFilterEngine.Action.COLLAPSE
    val mediaAllowed = decision.action == ContentFilterEngine.Action.SHOW || showRaw

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isOutgoing) Arrangement.End else Arrangement.Start
    ) {
        Card(modifier = Modifier.fillMaxWidth(0.82f)) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                if (mediaAllowed) {
                    LocalMediaPreview(message)
                }

                Text(if (showRaw) message.text else decision.displayText, style = MaterialTheme.typography.bodyLarge)

                if (!showRaw && decision.reason != null) Text(decision.reason, style = MaterialTheme.typography.bodySmall)
                if (!showRaw && decision.allowShowOnce) {
                    OutlinedButton(onClick = onShowOnce) { Text("Show once") }
                }

                if (mediaAllowed) {
                    MediaDownloadControls(message, onDownloadFile, onCancelDownload)
                }

                Text(formatMessageTime(message.date), style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.End))
            }
        }
    }
}

@Composable
private fun LocalMediaPreview(message: TelegramMessageItem) {
    if (!message.mediaDownloadComplete || !isImageKind(message.contentKind)) return
    val path = message.mediaPath ?: return
    val bitmap = remember(path) {
        runCatching {
            if (File(path).isFile) BitmapFactory.decodeFile(path) else null
        }.getOrNull()
    } ?: return
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = null,
        modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
        contentScale = ContentScale.Fit
    )
}

@Composable
private fun MediaDownloadControls(
    message: TelegramMessageItem,
    onDownloadFile: (Int) -> Unit,
    onCancelDownload: (Int) -> Unit
) {
    val fileId = message.mediaFileId ?: return
    when {
        message.mediaDownloadComplete -> {
            val size = formatSize(message.mediaSize)
            Text(if (size.isBlank()) "Downloaded privately" else "Downloaded privately • $size", style = MaterialTheme.typography.labelSmall)
        }
        message.mediaDownloadActive -> {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator()
                OutlinedButton(onClick = { onCancelDownload(fileId) }) { Text("Cancel") }
            }
        }
        else -> {
            OutlinedButton(onClick = { onDownloadFile(fileId) }) { Text("Download") }
            val size = formatSize(message.mediaSize)
            Text(if (size.isBlank()) "Manual download through privacy route" else "$size • manual download through privacy route", style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun shouldAutoDownload(message: TelegramMessageItem, settings: LatticeSettings): Boolean = when {
    message.contentKind.equals("messagePhoto", true) -> settings.autoDownloadPhotos
    message.contentKind.equals("messageVideo", true) -> settings.autoDownloadVideos
    message.contentKind.equals("messageAnimation", true) -> settings.autoDownloadVideos
    message.contentKind.equals("messageVoiceNote", true) -> settings.autoDownloadVoice
    message.contentKind.equals("messageVideoNote", true) -> settings.autoDownloadVideoMessages
    message.contentKind.equals("messageDocument", true) -> settings.autoDownloadFiles
    else -> false
}

private fun isImageKind(kind: String): Boolean =
    kind.equals("messagePhoto", true) || kind.equals("messageSticker", true)

private fun formatSize(bytes: Long): String = when {
    bytes <= 0L -> ""
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
    else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
}

private fun formatMessageTime(unixSeconds: Int): String {
    if (unixSeconds <= 0) return ""
    return MESSAGE_TIME_FORMAT.format(Instant.ofEpochSecond(unixSeconds.toLong()))
}

private val MESSAGE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter
    .ofPattern("HH:mm")
    .withZone(ZoneId.systemDefault())

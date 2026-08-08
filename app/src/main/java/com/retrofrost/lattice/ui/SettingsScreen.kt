package com.retrofrost.lattice.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.retrofrost.lattice.model.LatticeSettings

private val settingsTabs = listOf("Privacy", "Media", "Sexual", "Gore", "Spam", "Swearing", "DND")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: LatticeSettings,
    onSettingsChange: (LatticeSettings) -> Unit,
    onBack: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Maximum Privacy") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
                ScrollableTabRow(selectedTabIndex = selectedTab) {
                    settingsTabs.forEachIndexed { index, label ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (settingsTabs[selectedTab]) {
                "Privacy" -> {
                    item { StatusCard("Only data required for Telegram functionality is sent.") }
                    item { Toggle("Send optional data to Telegram", "Off by default", settings.sendOptionalDataToTelegram) { onSettingsChange(settings.copy(sendOptionalDataToTelegram = it)) } }
                    item { Toggle("Hide my IP", "Privacy routing policy", settings.hideIp) { onSettingsChange(settings.copy(hideIp = it)) } }
                    item { Toggle("Use Tor for Telegram", "Orbot / SOCKS5 setup", settings.useTor) { onSettingsChange(settings.copy(useTor = it)) } }
                    item { Toggle("Block direct fallback", "Pause Telegram if the privacy route fails", settings.blockDirectFallback) { onSettingsChange(settings.copy(blockDirectFallback = it)) } }
                    item { Toggle("Disable P2P calls", "Prevents exposing your direct IP to callers", settings.disableP2PCalls) { onSettingsChange(settings.copy(disableP2PCalls = it)) } }
                    item { Toggle("Sync contacts", "Never upload contacts unless enabled", settings.contactSync) { onSettingsChange(settings.copy(contactSync = it)) } }
                    item { Toggle("Link previews", "Avoid automatic preview requests", settings.linkPreviews) { onSettingsChange(settings.copy(linkPreviews = it)) } }
                    item { Toggle("Show notification content", "Sender/message text stays hidden when off", settings.notificationContent) { onSettingsChange(settings.copy(notificationContent = it)) } }
                }
                "Media" -> {
                    item { Toggle("Auto-download photos", "Tap to download when off", settings.autoDownloadPhotos) { onSettingsChange(settings.copy(autoDownloadPhotos = it)) } }
                    item { Toggle("Auto-download videos", "Tap to download when off", settings.autoDownloadVideos) { onSettingsChange(settings.copy(autoDownloadVideos = it)) } }
                    item { Toggle("Auto-download files", "Manual by default", settings.autoDownloadFiles) { onSettingsChange(settings.copy(autoDownloadFiles = it)) } }
                    item { Toggle("Autoplay videos", "Off by default", settings.autoplayVideo) { onSettingsChange(settings.copy(autoplayVideo = it)) } }
                    item { Toggle("Autoplay GIFs", "Off by default", settings.autoplayGifs) { onSettingsChange(settings.copy(autoplayGifs = it)) } }
                    item { Toggle("Load profile pictures", "PFP thumbnails remain visible", settings.loadProfilePictures) { onSettingsChange(settings.copy(loadProfilePictures = it)) } }
                    item { Toggle("Save media to Gallery", "Keep Telegram media out of Gallery when off", settings.saveToGallery) { onSettingsChange(settings.copy(saveToGallery = it)) } }
                    item { Toggle("Strip location metadata", "Remove location metadata before sending", settings.stripLocationMetadata) { onSettingsChange(settings.copy(stripLocationMetadata = it)) } }
                    item { Toggle("Hide Stars / paid media", "No blurred paid-media teaser", settings.hidePaidMedia) { onSettingsChange(settings.copy(hidePaidMedia = it)) } }
                }
                "Sexual" -> {
                    item { Toggle("Show sexual material", "Off by default", settings.showSexualMaterial) { onSettingsChange(settings.copy(showSexualMaterial = it)) } }
                    item { Toggle("Hide suggestive emojis", "Context-aware local filtering", settings.hideSuggestiveEmojis) { onSettingsChange(settings.copy(hideSuggestiveEmojis = it)) } }
                    item { Toggle("Hide suggestive bot messages", "Collapse locally instead of deleting", settings.hideSuggestiveBotMessages) { onSettingsChange(settings.copy(hideSuggestiveBotMessages = it)) } }
                    item { Toggle("Block adult-site previews", "Do not load thumbnails/titles for blocked domains", settings.blockAdultSitePreviews) { onSettingsChange(settings.copy(blockAdultSitePreviews = it)) } }
                }
                "Gore" -> {
                    item { Toggle("Gore filter", "Hide suspected graphic media", settings.goreFilter) { onSettingsChange(settings.copy(goreFilter = it)) } }
                    item { Toggle("Hide graphic bot messages", "Collapse graphic bot content", settings.hideGraphicBotMessages) { onSettingsChange(settings.copy(hideGraphicBotMessages = it)) } }
                }
                "Spam" -> {
                    item { Toggle("Spam filter", "Local filtering is aggressive; messages are not deleted", settings.spamFilter) { onSettingsChange(settings.copy(spamFilter = it)) } }
                    item { Toggle("Hide suspicious bot DMs", "Collapse likely unsolicited bot spam", settings.hideSuspiciousBotDms) { onSettingsChange(settings.copy(hideSuspiciousBotDms = it)) } }
                    item { Toggle("Scam-link warnings", "Warn before opening suspicious domains", settings.scamLinkWarnings) { onSettingsChange(settings.copy(scamLinkWarnings = it)) } }
                    item { Toggle("Auto-report suspected spam", "Off by default because reports leave the device", settings.autoReportSpam) { onSettingsChange(settings.copy(autoReportSpam = it)) } }
                }
                "Swearing" -> {
                    item { Toggle("Swearing filter", "Optional; off by default", settings.swearingFilter) { onSettingsChange(settings.copy(swearingFilter = it)) } }
                }
                else -> {
                    item { Toggle("Do Not Disturb", "Mute Lattice notifications", settings.dndEnabled) { onSettingsChange(settings.copy(dndEnabled = it)) } }
                    item { Toggle("Hide unread badges", "Quiet mode while DND is active", settings.dndHideUnreadBadges) { onSettingsChange(settings.copy(dndHideUnreadBadges = it)) } }
                    item { Toggle("Allow pinned chats", "Pinned chats can bypass DND", settings.dndAllowPinnedChats) { onSettingsChange(settings.copy(dndAllowPinnedChats = it)) } }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(text: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(text, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Toggle(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

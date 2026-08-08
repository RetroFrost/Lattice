package com.retrofrost.lattice.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
                        Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(label) })
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
                    item { StatusCard("Maximum Privacy is always active. Only data required for Telegram functionality is sent unless you explicitly enable an exception.") }
                    item { Toggle("Send optional data to Telegram", "Off by default", settings.sendOptionalDataToTelegram) { onSettingsChange(settings.copy(sendOptionalDataToTelegram = it)) } }
                    item { Toggle("Hide my IP", "Privacy routing policy", settings.hideIp) { onSettingsChange(settings.copy(hideIp = it)) } }
                    item { Toggle("Use Tor for Telegram", "Orbot / SOCKS5 route", settings.useTor) { onSettingsChange(settings.copy(useTor = it)) } }
                    item { Toggle("Block direct fallback", "Pause Telegram if Tor/privacy routing fails", settings.blockDirectFallback) { onSettingsChange(settings.copy(blockDirectFallback = it)) } }
                    item { Toggle("Disable P2P calls", "Do not expose your direct IP to another caller", settings.disableP2PCalls) { onSettingsChange(settings.copy(disableP2PCalls = it)) } }
                    item { Toggle("Sync contacts", "Never upload contacts unless enabled", settings.contactSync) { onSettingsChange(settings.copy(contactSync = it)) } }
                    item { Toggle("Link previews", "Avoid automatic preview requests", settings.linkPreviews) { onSettingsChange(settings.copy(linkPreviews = it)) } }
                    item { Toggle("Quiet UI", "Reduce attention-grabbing badges/effects", settings.quietUi) { onSettingsChange(settings.copy(quietUi = it)) } }
                    item { Toggle("Notifications", "Master Lattice notification switch", settings.notificationsEnabled) { onSettingsChange(settings.copy(notificationsEnabled = it)) } }
                    item { Toggle("Show notification content", "Keep sender/message text hidden when off", settings.notificationContent) { onSettingsChange(settings.copy(notificationContent = it)) } }
                    item { Toggle("Notification sound", "Silent by default", settings.notificationSound) { onSettingsChange(settings.copy(notificationSound = it)) } }
                    item { Toggle("Notification vibration", "Off by default", settings.notificationVibration) { onSettingsChange(settings.copy(notificationVibration = it)) } }
                }
                "Media" -> {
                    item { StatusCard("Normal media remains usable. Automatic downloads and autoplay stay off by default; paid Stars media is hidden separately.") }
                    item { Toggle("Auto-download photos", "Tap-to-download policy when off", settings.autoDownloadPhotos) { onSettingsChange(settings.copy(autoDownloadPhotos = it)) } }
                    item { Toggle("Auto-download videos", "Manual by default", settings.autoDownloadVideos) { onSettingsChange(settings.copy(autoDownloadVideos = it)) } }
                    item { Toggle("Auto-download voice", "Manual by default", settings.autoDownloadVoice) { onSettingsChange(settings.copy(autoDownloadVoice = it)) } }
                    item { Toggle("Auto-download video messages", "Manual by default", settings.autoDownloadVideoMessages) { onSettingsChange(settings.copy(autoDownloadVideoMessages = it)) } }
                    item { Toggle("Auto-download files", "Never automatic by default", settings.autoDownloadFiles) { onSettingsChange(settings.copy(autoDownloadFiles = it)) } }
                    item { Toggle("Autoplay videos", "Off by default", settings.autoplayVideo) { onSettingsChange(settings.copy(autoplayVideo = it)) } }
                    item { Toggle("Autoplay GIFs", "Off by default", settings.autoplayGifs) { onSettingsChange(settings.copy(autoplayGifs = it)) } }
                    item { Toggle("Animate stickers", "Off by default", settings.animateStickers) { onSettingsChange(settings.copy(animateStickers = it)) } }
                    item { Toggle("Load profile pictures", "Normal PFP thumbnails remain available", settings.loadProfilePictures) { onSettingsChange(settings.copy(loadProfilePictures = it)) } }
                    item { Toggle("Save media to Gallery", "Keep Telegram media out of Gallery when off", settings.saveToGallery) { onSettingsChange(settings.copy(saveToGallery = it)) } }
                    item { Toggle("Preserve manual downloads", "Cache cleanup will not remove files you explicitly saved", settings.preserveManualDownloads) { onSettingsChange(settings.copy(preserveManualDownloads = it)) } }
                    item { Toggle("Strip location metadata", "Remove location metadata before sending where supported", settings.stripLocationMetadata) { onSettingsChange(settings.copy(stripLocationMetadata = it)) } }
                    item { Toggle("Hide Stars / paid media", "No blurred teaser or unlock bait", settings.hidePaidMedia) { onSettingsChange(settings.copy(hidePaidMedia = it)) } }
                    item { Toggle("Hide paid reactions", "Suppress Stars reaction prompts", settings.hidePaidReactions) { onSettingsChange(settings.copy(hidePaidReactions = it)) } }
                    item { Toggle("Hide Stars upsells", "Suppress buy-Stars/promotional surfaces", settings.hideStarUpsells) { onSettingsChange(settings.copy(hideStarUpsells = it)) } }
                }
                "Sexual" -> {
                    item { Toggle("Show sexual material", "Off by default; enable to display it", settings.showSexualMaterial) { onSettingsChange(settings.copy(showSexualMaterial = it)) } }
                    item { Toggle("Blur suspected sexual media", "Local rendering policy", settings.blurSuspectedSexualMedia) { onSettingsChange(settings.copy(blurSuspectedSexualMedia = it)) } }
                    item { Toggle("Hide sexual stickers/GIFs", "Collapse locally", settings.hideSexualStickersGifs) { onSettingsChange(settings.copy(hideSexualStickersGifs = it)) } }
                    item { Toggle("Hide suggestive emojis", "Context-aware combination filtering", settings.hideSuggestiveEmojis) { onSettingsChange(settings.copy(hideSuggestiveEmojis = it)) } }
                    item { Toggle("Hide suggestive bot messages", "Collapse locally instead of deleting", settings.hideSuggestiveBotMessages) { onSettingsChange(settings.copy(hideSuggestiveBotMessages = it)) } }
                    item { Toggle("Block adult-site previews", "Do not load previews for blocked adult links", settings.blockAdultSitePreviews) { onSettingsChange(settings.copy(blockAdultSitePreviews = it)) } }
                    item { Toggle("Hide explicit chat previews", "Keep filtered text out of the inbox", settings.hideExplicitChatPreviews) { onSettingsChange(settings.copy(hideExplicitChatPreviews = it)) } }
                }
                "Gore" -> {
                    item { Toggle("Gore filter", "Strong graphic-content filter; no sensitivity slider", settings.goreFilter) { onSettingsChange(settings.copy(goreFilter = it)) } }
                    item { Toggle("Blur suspected gore", "Hide graphic imagery before rendering", settings.blurSuspectedGore) { onSettingsChange(settings.copy(blurSuspectedGore = it)) } }
                    item { Toggle("Hide gore stickers/GIFs", "Collapse graphic animated media", settings.hideGoreStickersGifs) { onSettingsChange(settings.copy(hideGoreStickersGifs = it)) } }
                    item { Toggle("Hide graphic bot messages", "Collapse graphic bot content", settings.hideGraphicBotMessages) { onSettingsChange(settings.copy(hideGraphicBotMessages = it)) } }
                    item { Toggle("Block shock-site previews", "Do not load shock-site thumbnails", settings.blockShockSites) { onSettingsChange(settings.copy(blockShockSites = it)) } }
                }
                "Spam" -> {
                    item { Toggle("Spam filter", "Local filtering; messages are not silently deleted", settings.spamFilter) { onSettingsChange(settings.copy(spamFilter = it)) } }
                    item { Toggle("Collapse repeated messages", "Reduce repeated promotional noise", settings.collapseRepeatedMessages) { onSettingsChange(settings.copy(collapseRepeatedMessages = it)) } }
                    item { Toggle("Hide suspicious bot DMs", "Collapse likely unsolicited bot spam", settings.hideSuspiciousBotDms) { onSettingsChange(settings.copy(hideSuspiciousBotDms = it)) } }
                    item { Toggle("Scam-link warnings", "Warn before suspicious links", settings.scamLinkWarnings) { onSettingsChange(settings.copy(scamLinkWarnings = it)) } }
                    item { Toggle("Auto-report suspected spam", "Off by default because reporting sends an action to Telegram", settings.autoReportSpam) { onSettingsChange(settings.copy(autoReportSpam = it)) } }
                }
                "Swearing" -> {
                    item { StatusCard("Swearing filtering is optional and stays off by default.") }
                    item { Toggle("Swearing filter", "Off by default", settings.swearingFilter) { onSettingsChange(settings.copy(swearingFilter = it)) } }
                    item { Toggle("Censor instead of hide", "Replace profanity rather than collapsing the whole message", settings.censorSwearingInsteadOfHide) { onSettingsChange(settings.copy(censorSwearingInsteadOfHide = it)) } }
                }
                else -> {
                    item { Toggle("Do Not Disturb", "Mute Lattice attention surfaces", settings.dndEnabled) { onSettingsChange(settings.copy(dndEnabled = it)) } }
                    item { Toggle("Hide unread badges", "Quiet mode while DND is active", settings.dndHideUnreadBadges) { onSettingsChange(settings.copy(dndHideUnreadBadges = it)) } }
                    item { Toggle("Hide notification content", "Use generic notifications during DND", settings.dndHideNotificationContent) { onSettingsChange(settings.copy(dndHideNotificationContent = it)) } }
                    item { Toggle("Disable vibration", "No vibration while DND is active", settings.dndDisableVibration) { onSettingsChange(settings.copy(dndDisableVibration = it)) } }
                    item { Toggle("Disable sounds", "No notification sounds while DND is active", settings.dndDisableSounds) { onSettingsChange(settings.copy(dndDisableSounds = it)) } }
                    item { Toggle("Allow pinned chats", "Pinned chats may bypass DND when notification service supports exceptions", settings.dndAllowPinnedChats) { onSettingsChange(settings.copy(dndAllowPinnedChats = it)) } }
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

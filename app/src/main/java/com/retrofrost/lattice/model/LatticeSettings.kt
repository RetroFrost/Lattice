package com.retrofrost.lattice.model

data class LatticeSettings(
    val sendOptionalDataToTelegram: Boolean = false,
    val hideIp: Boolean = true,
    val useTor: Boolean = false,
    val blockDirectFallback: Boolean = true,
    val disableP2PCalls: Boolean = true,
    val contactSync: Boolean = false,
    val linkPreviews: Boolean = false,
    val screenshotProtection: Boolean = true,
    val notificationContent: Boolean = false,

    val autoDownloadPhotos: Boolean = false,
    val autoDownloadVideos: Boolean = false,
    val autoDownloadFiles: Boolean = false,
    val autoplayVideo: Boolean = false,
    val autoplayGifs: Boolean = false,
    val loadProfilePictures: Boolean = true,
    val saveToGallery: Boolean = false,
    val stripLocationMetadata: Boolean = true,
    val hidePaidMedia: Boolean = true,

    val showSexualMaterial: Boolean = false,
    val hideSuggestiveEmojis: Boolean = true,
    val hideSuggestiveBotMessages: Boolean = true,
    val blockAdultSitePreviews: Boolean = true,

    val goreFilter: Boolean = true,
    val hideGraphicBotMessages: Boolean = true,

    val spamFilter: Boolean = true,
    val hideSuspiciousBotDms: Boolean = true,
    val scamLinkWarnings: Boolean = true,
    val autoReportSpam: Boolean = false,

    val swearingFilter: Boolean = false,

    val dndEnabled: Boolean = false,
    val dndHideUnreadBadges: Boolean = false,
    val dndAllowPinnedChats: Boolean = true
)

package com.retrofrost.lattice.model

import android.content.Context

class LatticeSettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): LatticeSettings = LatticeSettings(
        sendOptionalDataToTelegram = bool("send_optional_data", false),
        hideIp = bool("hide_ip", true),
        useTor = bool("use_tor", true),
        blockDirectFallback = bool("block_direct_fallback", true),
        disableP2PCalls = bool("disable_p2p_calls", true),
        contactSync = bool("contact_sync", false),
        linkPreviews = bool("link_previews", false),
        screenshotProtection = bool("screenshot_protection", true),
        notificationContent = bool("notification_content", false),
        notificationsEnabled = bool("notifications_enabled", true),
        notificationSound = bool("notification_sound", false),
        notificationVibration = bool("notification_vibration", false),
        quietUi = bool("quiet_ui", true),
        autoDownloadPhotos = bool("auto_download_photos", false),
        autoDownloadVideos = bool("auto_download_videos", false),
        autoDownloadFiles = bool("auto_download_files", false),
        autoDownloadVoice = bool("auto_download_voice", false),
        autoDownloadVideoMessages = bool("auto_download_video_messages", false),
        autoplayVideo = bool("autoplay_video", false),
        autoplayGifs = bool("autoplay_gifs", false),
        animateStickers = bool("animate_stickers", false),
        loadProfilePictures = bool("load_profile_pictures", true),
        saveToGallery = bool("save_to_gallery", false),
        stripLocationMetadata = bool("strip_location_metadata", true),
        preserveManualDownloads = bool("preserve_manual_downloads", true),
        hidePaidMedia = bool("hide_paid_media", true),
        hidePaidReactions = bool("hide_paid_reactions", true),
        hideStarUpsells = bool("hide_star_upsells", true),
        showSexualMaterial = bool("show_sexual_material", false),
        blurSuspectedSexualMedia = bool("blur_suspected_sexual_media", true),
        hideSexualStickersGifs = bool("hide_sexual_stickers_gifs", true),
        hideSuggestiveEmojis = bool("hide_suggestive_emojis", true),
        hideSuggestiveBotMessages = bool("hide_suggestive_bot_messages", true),
        blockAdultSitePreviews = bool("block_adult_site_previews", true),
        hideExplicitChatPreviews = bool("hide_explicit_chat_previews", true),
        goreFilter = bool("gore_filter", true),
        blurSuspectedGore = bool("blur_suspected_gore", true),
        hideGoreStickersGifs = bool("hide_gore_stickers_gifs", true),
        hideGraphicBotMessages = bool("hide_graphic_bot_messages", true),
        blockShockSites = bool("block_shock_sites", true),
        spamFilter = bool("spam_filter", true),
        collapseRepeatedMessages = bool("collapse_repeated_messages", true),
        hideSuspiciousBotDms = bool("hide_suspicious_bot_dms", true),
        scamLinkWarnings = bool("scam_link_warnings", true),
        autoReportSpam = bool("auto_report_spam", false),
        swearingFilter = bool("swearing_filter", false),
        censorSwearingInsteadOfHide = bool("censor_swearing_instead_of_hide", true),
        dndEnabled = bool("dnd_enabled", false),
        dndHideUnreadBadges = bool("dnd_hide_unread_badges", true),
        dndHideNotificationContent = bool("dnd_hide_notification_content", true),
        dndDisableVibration = bool("dnd_disable_vibration", true),
        dndDisableSounds = bool("dnd_disable_sounds", true),
        dndAllowPinnedChats = bool("dnd_allow_pinned_chats", true)
    )

    fun save(value: LatticeSettings) {
        prefs.edit()
            .putBoolean("send_optional_data", value.sendOptionalDataToTelegram)
            .putBoolean("hide_ip", value.hideIp)
            .putBoolean("use_tor", value.useTor)
            .putBoolean("block_direct_fallback", value.blockDirectFallback)
            .putBoolean("disable_p2p_calls", value.disableP2PCalls)
            .putBoolean("contact_sync", value.contactSync)
            .putBoolean("link_previews", value.linkPreviews)
            .putBoolean("screenshot_protection", value.screenshotProtection)
            .putBoolean("notification_content", value.notificationContent)
            .putBoolean("notifications_enabled", value.notificationsEnabled)
            .putBoolean("notification_sound", value.notificationSound)
            .putBoolean("notification_vibration", value.notificationVibration)
            .putBoolean("quiet_ui", value.quietUi)
            .putBoolean("auto_download_photos", value.autoDownloadPhotos)
            .putBoolean("auto_download_videos", value.autoDownloadVideos)
            .putBoolean("auto_download_files", value.autoDownloadFiles)
            .putBoolean("auto_download_voice", value.autoDownloadVoice)
            .putBoolean("auto_download_video_messages", value.autoDownloadVideoMessages)
            .putBoolean("autoplay_video", value.autoplayVideo)
            .putBoolean("autoplay_gifs", value.autoplayGifs)
            .putBoolean("animate_stickers", value.animateStickers)
            .putBoolean("load_profile_pictures", value.loadProfilePictures)
            .putBoolean("save_to_gallery", value.saveToGallery)
            .putBoolean("strip_location_metadata", value.stripLocationMetadata)
            .putBoolean("preserve_manual_downloads", value.preserveManualDownloads)
            .putBoolean("hide_paid_media", value.hidePaidMedia)
            .putBoolean("hide_paid_reactions", value.hidePaidReactions)
            .putBoolean("hide_star_upsells", value.hideStarUpsells)
            .putBoolean("show_sexual_material", value.showSexualMaterial)
            .putBoolean("blur_suspected_sexual_media", value.blurSuspectedSexualMedia)
            .putBoolean("hide_sexual_stickers_gifs", value.hideSexualStickersGifs)
            .putBoolean("hide_suggestive_emojis", value.hideSuggestiveEmojis)
            .putBoolean("hide_suggestive_bot_messages", value.hideSuggestiveBotMessages)
            .putBoolean("block_adult_site_previews", value.blockAdultSitePreviews)
            .putBoolean("hide_explicit_chat_previews", value.hideExplicitChatPreviews)
            .putBoolean("gore_filter", value.goreFilter)
            .putBoolean("blur_suspected_gore", value.blurSuspectedGore)
            .putBoolean("hide_gore_stickers_gifs", value.hideGoreStickersGifs)
            .putBoolean("hide_graphic_bot_messages", value.hideGraphicBotMessages)
            .putBoolean("block_shock_sites", value.blockShockSites)
            .putBoolean("spam_filter", value.spamFilter)
            .putBoolean("collapse_repeated_messages", value.collapseRepeatedMessages)
            .putBoolean("hide_suspicious_bot_dms", value.hideSuspiciousBotDms)
            .putBoolean("scam_link_warnings", value.scamLinkWarnings)
            .putBoolean("auto_report_spam", value.autoReportSpam)
            .putBoolean("swearing_filter", value.swearingFilter)
            .putBoolean("censor_swearing_instead_of_hide", value.censorSwearingInsteadOfHide)
            .putBoolean("dnd_enabled", value.dndEnabled)
            .putBoolean("dnd_hide_unread_badges", value.dndHideUnreadBadges)
            .putBoolean("dnd_hide_notification_content", value.dndHideNotificationContent)
            .putBoolean("dnd_disable_vibration", value.dndDisableVibration)
            .putBoolean("dnd_disable_sounds", value.dndDisableSounds)
            .putBoolean("dnd_allow_pinned_chats", value.dndAllowPinnedChats)
            .apply()
    }

    private fun bool(key: String, default: Boolean) = prefs.getBoolean(key, default)

    private companion object {
        const val PREFS = "lattice_privacy_settings_v1"
    }
}

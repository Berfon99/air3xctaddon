package com.xc.air3xctaddon

data class TelegramChat(
    val chatId: String,
    val title: String,
    val isGroup: Boolean,
    val isBotMember: Boolean = false,
    val isBotActive: Boolean = false,
    val isUserMember: Boolean = false
)

data class TelegramBotInfo(
    val username: String,
    val botName: String,
    val id: String
)
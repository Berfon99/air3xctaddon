package com.xc.air3xctaddon

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class TelegramChatManager(
    private val context: Context,
    private val telegramBotHelper: TelegramBotHelper
) {
    private val client = OkHttpClient()

    fun fetchChats(
        onResult: (List<TelegramChat>) -> Unit,
        onError: (String?) -> Unit,
        retryCount: Int = 0,
        maxRetries: Int = 3
    ) {
        if (retryCount > maxRetries) {
            Log.e("TelegramChatManager", "Max retries ($maxRetries) exceeded for fetching chats")
            onError(context.getString(R.string.failed_to_fetch_groups_retries, maxRetries))
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            Log.d("TelegramChatManager", "Fetching chats, attempt ${retryCount + 1}/$maxRetries")
            delay(500L) // Reduced delay for faster retries
            val userId = SettingsRepository.getUserId()
            if (userId == null) {
                Log.e("TelegramChatManager", "User ID not found")
                withContext(Dispatchers.Main) {
                    onError(context.getString(R.string.user_id_not_found_prompt))
                }
                return@launch
            }
            fetchChatsWithOffset(0, { rawChats ->
                Log.d("TelegramChatManager", "Fetched ${rawChats.size} raw chats")
                validateChats(rawChats, { validatedChats ->
                    CoroutineScope(Dispatchers.IO).launch {
                        val cachedChats = SettingsRepository.getCachedChats()
                        val allChats = (validatedChats + cachedChats)
                            .distinctBy { it.chatId }
                            .filter { it.isUserMember || !it.isGroup }
                        Log.d("TelegramChatManager", "Saving ${allChats.size} chats to repository")
                        SettingsRepository.saveChats(allChats)
                        withContext(Dispatchers.Main) {
                            onResult(allChats.sortedBy { it.title })
                        }
                    }
                }, { error ->
                    Log.e("TelegramChatManager", "Error validating chats: $error")
                    CoroutineScope(Dispatchers.IO).launch {
                        if (retryCount < maxRetries) {
                            delay(1000L * (retryCount + 1))
                            fetchChats(onResult, onError, retryCount + 1, maxRetries)
                        } else {
                            withContext(Dispatchers.Main) {
                                onError(context.getString(R.string.failed_to_fetch_groups_error, error ?: "Unknown error"))
                            }
                        }
                    }
                })
            }, { error ->
                Log.e("TelegramChatManager", "Error fetching chats with offset: $error")
                CoroutineScope(Dispatchers.IO).launch {
                    if (retryCount < maxRetries) {
                        delay(1000L * (retryCount + 1))
                        fetchChats(onResult, onError, retryCount + 1, maxRetries)
                    } else {
                        withContext(Dispatchers.Main) {
                            onError(context.getString(R.string.failed_to_fetch_groups_error, error ?: "Unknown error"))
                        }
                    }
                }
            })
        }
    }

    fun handleChatSelection(
        isAddingNewChat: Boolean,
        pendingGroupChat: TelegramChat?,
        fetchedChats: List<TelegramChat>,
        currentChatId: String,
        onChatSelected: (TelegramChat) -> Unit,
        onChatNotFound: () -> Unit
    ) {
        Log.d("TelegramChatManager", "Handling chat selection: isAddingNewChat=$isAddingNewChat, pendingGroupChat=${pendingGroupChat?.title}, currentChatId=$currentChatId")
        when {
            pendingGroupChat != null -> {
                // Check if the pending group chat exists in fetched chats and is a group
                fetchedChats.find { it.chatId == pendingGroupChat.chatId && it.isGroup }?.let { chat ->
                    Log.d("TelegramChatManager", "Selected pending group chat: ${chat.title} (${chat.chatId})")
                    telegramBotHelper.checkBotAccess(
                        chatId = chat.chatId,
                        onResult = { isBotMember, isBotActive, isUserMember ->
                            val updatedChat = chat.copy(
                                isBotMember = isBotMember,
                                isBotActive = isBotActive,
                                isUserMember = isUserMember
                            )
                            onChatSelected(updatedChat)
                        },
                        onError = { error ->
                            Log.e("TelegramChatManager", "Error checking bot access for pending chat ${chat.title}: $error")
                            onChatNotFound()
                        }
                    )
                } ?: run {
                    Log.d("TelegramChatManager", "Pending group chat ${pendingGroupChat.chatId} not found or not a group")
                    onChatNotFound()
                }
            }
            isAddingNewChat -> {
                // Prioritize group chats where the bot is a member
                val groupChat = fetchedChats
                    .filter { it.isGroup && it.isBotMember }
                    .maxByOrNull { it.chatId.toLongOrNull() ?: 0L }
                if (groupChat != null) {
                    Log.d("TelegramChatManager", "Selected newest group chat with bot: ${groupChat.title} (${groupChat.chatId})")
                    telegramBotHelper.checkBotAccess(
                        chatId = groupChat.chatId,
                        onResult = { isBotMember, isBotActive, isUserMember ->
                            val updatedChat = groupChat.copy(
                                isBotMember = isBotMember,
                                isBotActive = isBotActive,
                                isUserMember = isUserMember
                            )
                            onChatSelected(updatedChat)
                        },
                        onError = { error ->
                            Log.e("TelegramChatManager", "Error checking bot access for group chat ${groupChat.title}: $error")
                            onChatNotFound()
                        }
                    )
                } else {
                    Log.d("TelegramChatManager", "No group chats with bot found")
                    onChatNotFound()
                }
            }
            currentChatId.isNotEmpty() -> {
                // Check if the current chat ID corresponds to a group chat
                fetchedChats.find { it.chatId == currentChatId && it.isGroup }?.let { chat ->
                    Log.d("TelegramChatManager", "Selected current group chat: ${chat.title} (${chat.chatId})")
                    telegramBotHelper.checkBotAccess(
                        chatId = chat.chatId,
                        onResult = { isBotMember, isBotActive, isUserMember ->
                            val updatedChat = chat.copy(
                                isBotMember = isBotMember,
                                isBotActive = isBotActive,
                                isUserMember = isUserMember
                            )
                            onChatSelected(updatedChat)
                        },
                        onError = { error ->
                            Log.e("TelegramChatManager", "Error checking bot access for current chat ${chat.title}: $error")
                            onChatNotFound()
                        }
                    )
                } ?: run {
                    Log.d("TelegramChatManager", "Current chat ID $currentChatId not found or not a group")
                    onChatNotFound()
                }
            }
            else -> {
                Log.d("TelegramChatManager", "No chat selection criteria met")
                onChatNotFound()
            }
        }
    }

    fun checkBotInSelectedChat(
        chat: TelegramChat,
        onResult: (Boolean, Boolean, Boolean) -> Unit = { _, _, _ -> },
        onError: (String?) -> Unit = {}
    ) {
        Log.d("TelegramChatManager", "Checking bot access for chat: ${chat.title} (${chat.chatId})")
        telegramBotHelper.checkBotAccess(
            chatId = chat.chatId,
            onResult = onResult,
            onError = onError
        )
    }

    private fun fetchChatsWithOffset(
        offset: Int,
        onResult: (List<TelegramChat>) -> Unit,
        onError: (String?) -> Unit
    ) {
        val botToken = telegramBotHelper.botToken
        val url = "https://api.telegram.org/bot$botToken/getUpdates?offset=$offset&timeout=10&limit=1000"
        Log.d("TelegramChatManager", "Fetching chats with URL: ${url.replace(botToken, "<REDACTED>")}")
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("TelegramChatManager", "Failed to fetch chats: ${e.message}", e)
                onError(e.message ?: "Network error")
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string()
                    Log.e("TelegramChatManager", "Error fetching chats: ${response.message}, code: ${response.code}, body: $errorBody")
                    onError("HTTP error: ${response.message} (code: ${response.code})")
                    response.close()
                    return
                }

                val json = response.body?.string() ?: run {
                    Log.e("TelegramChatManager", "Response body is null")
                    onError("Response body is null")
                    response.close()
                    return
                }

                Log.d("TelegramChatManager", "Raw response: $json")
                try {
                    val jsonObject = JSONObject(json)
                    if (!jsonObject.getBoolean("ok")) {
                        val errorDescription = jsonObject.optString("description", "Unknown error")
                        Log.e("TelegramChatManager", "Telegram API error: $errorDescription")
                        onError("Telegram API error: $errorDescription")
                        response.close()
                        return
                    }

                    val updates = jsonObject.getJSONArray("result")
                    val chats = mutableListOf<TelegramChat>()
                    val seenChatIds = mutableSetOf<String>()
                    var maxUpdateId = offset

                    // Prioritize group chats
                    val groupChats = mutableListOf<TelegramChat>()
                    val privateChats = mutableListOf<TelegramChat>()

                    for (i in 0 until updates.length()) {
                        val update = updates.getJSONObject(i)
                        val updateId = update.optInt("update_id", 0)
                        if (updateId > maxUpdateId) maxUpdateId = updateId

                        if (update.has("message") && update.getJSONObject("message").has("chat")) {
                            val chat = update.getJSONObject("message").getJSONObject("chat")
                            val chatId = chat.getLong("id").toString()
                            val chatType = chat.getString("type")
                            if (chatId !in seenChatIds) {
                                val isGroup = chatType in listOf(
                                    context.getString(R.string.telegram_group_type),
                                    context.getString(R.string.telegram_supergroup_type)
                                )
                                val title = if (isGroup) {
                                    chat.optString("title", context.getString(R.string.unknown_group))
                                } else {
                                    val firstName = chat.optString("first_name", "")
                                    val lastName = chat.optString("last_name", "")
                                    (firstName + " " + lastName).trim().ifEmpty { context.getString(R.string.unknown_user) }
                                }
                                val telegramChat = TelegramChat(chatId, title, isGroup)
                                if (isGroup) {
                                    groupChats.add(telegramChat)
                                } else {
                                    privateChats.add(telegramChat)
                                }
                                seenChatIds.add(chatId)
                                Log.d("TelegramChatManager", "Added chat: $title ($chatId, isGroup=$isGroup)")
                            }
                        }
                    }

                    // Combine group chats first, then private chats
                    chats.addAll(groupChats)
                    chats.addAll(privateChats)

                    if (updates.length() == 1000) {
                        Log.d("TelegramChatManager", "More updates available, fetching with offset ${maxUpdateId + 1}")
                        fetchChatsWithOffset(maxUpdateId + 1, { newChats ->
                            onResult(chats + newChats)
                        }, onError)
                    } else {
                        Log.d("TelegramChatManager", "Finished fetching chats, total: ${chats.size} (groups: ${groupChats.size}, private: ${privateChats.size})")
                        onResult(chats)
                    }
                } catch (e: Exception) {
                    Log.e("TelegramChatManager", "Error parsing chats: ${e.message}", e)
                    onError("Parsing error: ${e.message}")
                } finally {
                    response.close()
                }
            }
        })
    }

    private fun fetchGroupChat(
        chatId: String,
        onResult: (TelegramChat?) -> Unit,
        onError: (String?) -> Unit
    ) {
        val botToken = telegramBotHelper.botToken
        val url = "https://api.telegram.org/bot$botToken/getChat?chat_id=$chatId"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("TelegramChatManager", "Failed to fetch group chat $chatId: ${e.message}")
                onError(e.message)
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string()
                    Log.e("TelegramChatManager", "Error fetching group chat $chatId: ${response.message}, code: ${response.code}, body: $errorBody")
                    onError("HTTP error: ${response.message} (code: ${response.code})")
                    response.close()
                    return
                }

                val json = response.body?.string() ?: run {
                    Log.e("TelegramChatManager", "Response body is null for chat $chatId")
                    onError("Response body is null")
                    response.close()
                    return
                }

                try {
                    val jsonObject = JSONObject(json)
                    if (!jsonObject.getBoolean("ok")) {
                        val errorDescription = jsonObject.optString("description", "Unknown error")
                        Log.e("TelegramChatManager", "Telegram API error for chat $chatId: $errorDescription")
                        onError("Telegram API error: $errorDescription")
                        response.close()
                        return
                    }

                    val chat = jsonObject.getJSONObject("result")
                    val chatId = chat.getLong("id").toString()
                    val chatType = chat.getString("type")
                    val isGroup = chatType in listOf(
                        context.getString(R.string.telegram_group_type),
                        context.getString(R.string.telegram_supergroup_type)
                    )
                    val title = if (isGroup) {
                        chat.optString("title", context.getString(R.string.unknown_group))
                    } else {
                        val firstName = chat.optString("first_name", "")
                        val lastName = chat.optString("last_name", "")
                        (firstName + " " + lastName).trim().ifEmpty { context.getString(R.string.unknown_user) }
                    }
                    onResult(TelegramChat(chatId, title, isGroup))
                } catch (e: Exception) {
                    Log.e("TelegramChatManager", "Error parsing group chat $chatId: ${e.message}")
                    onError("Parsing error: ${e.message}")
                } finally {
                    response.close()
                }
            }
        })
    }

    private fun validateChats(
        rawChats: List<TelegramChat>,
        onResult: (List<TelegramChat>) -> Unit,
        onError: (String?) -> Unit
    ) {
        Log.d("TelegramChatManager", "Validating ${rawChats.size} chats")
        if (rawChats.isEmpty()) {
            // Try fetching cached chats or known group chats
            val cachedChats = SettingsRepository.getCachedChats().filter { it.isUserMember || !it.isGroup }
            Log.d("TelegramChatManager", "No raw chats, checking ${cachedChats.size} cached chats")
            if (cachedChats.isEmpty()) {
                onResult(emptyList())
                return
            }

            val validatedChats = mutableListOf<TelegramChat>()
            var chatsProcessed = 0
            cachedChats.forEach { chat ->
                if (chat.isGroup) {
                    fetchGroupChat(
                        chatId = chat.chatId,
                        onResult = { fetchedChat ->
                            if (fetchedChat != null) {
                                telegramBotHelper.checkBotAccess(
                                    chatId = fetchedChat.chatId,
                                    onResult = { isBotMember, isBotActive, isUserMember ->
                                        Log.d("TelegramChatManager", "Validated cached chat ${fetchedChat.title}: isBotMember=$isBotMember, isBotActive=$isBotActive, isUserMember=$isUserMember")
                                        if (isBotMember && (isUserMember || !fetchedChat.isGroup)) {
                                            validatedChats.add(fetchedChat.copy(isBotMember = isBotMember, isBotActive = isBotActive, isUserMember = isUserMember))
                                        }
                                        chatsProcessed++
                                        if (chatsProcessed == cachedChats.size) {
                                            Log.d("TelegramChatManager", "Validation complete, returning ${validatedChats.size} chats")
                                            onResult(validatedChats.sortedBy { it.title })
                                        }
                                    },
                                    onError = { error ->
                                        Log.e("TelegramChatManager", "Error validating cached chat ${chat.title}: $error")
                                        chatsProcessed++
                                        if (chatsProcessed == cachedChats.size) {
                                            Log.d("TelegramChatManager", "Validation complete with errors, returning ${validatedChats.size} chats")
                                            onResult(validatedChats.sortedBy { it.title })
                                        }
                                    }
                                )
                            } else {
                                chatsProcessed++
                                if (chatsProcessed == cachedChats.size) {
                                    Log.d("TelegramChatManager", "Validation complete, returning ${validatedChats.size} chats")
                                    onResult(validatedChats.sortedBy { it.title })
                                }
                            }
                        },
                        onError = { error ->
                            Log.e("TelegramChatManager", "Error fetching cached chat ${chat.chatId}: $error")
                            chatsProcessed++
                            if (chatsProcessed == cachedChats.size) {
                                Log.d("TelegramChatManager", "Validation complete with errors, returning ${validatedChats.size} chats")
                                onResult(validatedChats.sortedBy { it.title })
                            }
                        }
                    )
                } else {
                    telegramBotHelper.checkBotAccess(
                        chatId = chat.chatId,
                        onResult = { isBotMember, isBotActive, isUserMember ->
                            Log.d("TelegramChatManager", "Validated cached chat ${chat.title}: isBotMember=$isBotMember, isBotActive=$isBotActive, isUserMember=$isUserMember")
                            if (isBotMember && (isUserMember || !chat.isGroup)) {
                                validatedChats.add(chat.copy(isBotMember = isBotMember, isBotActive = isBotActive, isUserMember = isUserMember))
                            }
                            chatsProcessed++
                            if (chatsProcessed == cachedChats.size) {
                                Log.d("TelegramChatManager", "Validation complete, returning ${validatedChats.size} chats")
                                onResult(validatedChats.sortedBy { it.title })
                            }
                        },
                        onError = { error ->
                            Log.e("TelegramChatManager", "Error validating cached chat ${chat.title}: $error")
                            chatsProcessed++
                            if (chatsProcessed == cachedChats.size) {
                                Log.d("TelegramChatManager", "Validation complete with errors, returning ${validatedChats.size} chats")
                                onResult(validatedChats.sortedBy { it.title })
                            }
                        }
                    )
                }
            }
            return
        }

        val validatedChats = mutableListOf<TelegramChat>()
        var chatsProcessed = 0

        rawChats.forEach { chat ->
            telegramBotHelper.checkBotAccess(
                chatId = chat.chatId,
                onResult = { isBotMember: Boolean, isBotActive: Boolean, isUserMember: Boolean ->
                    Log.d("TelegramChatManager", "Validated chat ${chat.title}: isBotMember=$isBotMember, isBotActive=$isBotActive, isUserMember=$isUserMember")
                    if (isBotMember && (isUserMember || !chat.isGroup)) {
                        validatedChats.add(chat.copy(isBotMember = isBotMember, isBotActive = isBotActive, isUserMember = isUserMember))
                    }
                    chatsProcessed++
                    if (chatsProcessed == rawChats.size) {
                        Log.d("TelegramChatManager", "Validation complete, returning ${validatedChats.size} chats")
                        onResult(validatedChats.sortedBy { it.title })
                    }
                },
                onError = { error: String? ->
                    Log.e("TelegramChatManager", "Error validating chat ${chat.title}: $error")
                    chatsProcessed++
                    if (chatsProcessed == rawChats.size) {
                        Log.d("TelegramChatManager", "Validation complete with errors, returning ${validatedChats.size} chats")
                        onResult(validatedChats.sortedBy { it.title })
                    }
                }
            )
        }
    }
}
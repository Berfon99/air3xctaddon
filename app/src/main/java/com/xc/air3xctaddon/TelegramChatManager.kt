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
            onError(context.getString(R.string.failed_to_fetch_groups_retries, maxRetries))
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            delay(2000L)
            val userId = SettingsRepository.getUserId()
            if (userId == null) {
                withContext(Dispatchers.Main) {
                    onError(context.getString(R.string.user_id_not_found_prompt))
                }
                return@launch
            }
            fetchChatsWithOffset(0, { rawChats ->
                validateChats(rawChats, { validatedChats ->
                    CoroutineScope(Dispatchers.IO).launch {
                        val cachedChats = SettingsRepository.getCachedChats()
                        val allChats = (validatedChats + cachedChats)
                            .distinctBy { it.chatId }
                            .filter { it.isUserMember || !it.isGroup }
                        SettingsRepository.saveChats(allChats)
                        withContext(Dispatchers.Main) {
                            onResult(allChats.sortedBy { it.title })
                        }
                    }
                }, { error ->
                    CoroutineScope(Dispatchers.IO).launch {
                        if (retryCount < maxRetries) {
                            delay(1000L * (retryCount + 1))
                            fetchChats(onResult, onError, retryCount + 1, maxRetries)
                        } else {
                            withContext(Dispatchers.Main) {
                                onError(context.getString(R.string.failed_to_fetch_groups_error, error))
                            }
                        }
                    }
                })
            }, { error ->
                CoroutineScope(Dispatchers.IO).launch {
                    if (retryCount < maxRetries) {
                        delay(1000L * (retryCount + 1))
                        fetchChats(onResult, onError, retryCount + 1, maxRetries)
                    } else {
                        withContext(Dispatchers.Main) {
                            onError(context.getString(R.string.failed_to_fetch_groups_error, error))
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
        when {
            isAddingNewChat && pendingGroupChat != null -> {
                fetchedChats.find { it.chatId == pendingGroupChat.chatId }?.let { chat ->
                    onChatSelected(chat)
                }
            }
            isAddingNewChat && fetchedChats.isNotEmpty() -> {
                fetchedChats.maxByOrNull { it.chatId.toLongOrNull() ?: Long.MAX_VALUE }?.let { chat ->
                    onChatSelected(chat)
                }
            }
            currentChatId.isNotEmpty() && fetchedChats.none { it.chatId == currentChatId } -> {
                onChatNotFound()
            }
        }
    }

    fun checkBotInSelectedChat(
        chat: TelegramChat,
        onResult: (Boolean, Boolean, Boolean) -> Unit = { _, _, _ -> },
        onError: (String?) -> Unit = {}
    ) {
        telegramBotHelper.checkBotAccess(
            chatId = chat.chatId,
            isGroup = chat.isGroup,
            onResult = onResult,
            onError = onError
        )
    }

    private fun fetchChatsWithOffset(
        offset: Int,
        onResult: (List<TelegramChat>) -> Unit,
        onError: (String?) -> Unit
    ) {
        val url = "${context.getString(R.string.telegram_api_base_url)}${context.getString(R.string.telegram_get_updates_endpoint)}?offset=$offset&timeout=10&limit=1000"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("TelegramChatManager", context.getString(R.string.log_failed_fetch_chats, e.message ?: ""))
                onError(context.getString(R.string.failed_to_fetch_groups_error, e.message ?: ""))
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.e("TelegramChatManager", context.getString(R.string.log_error_fetching_chats, response.message))
                    onError(context.getString(R.string.failed_to_fetch_groups_error, response.message))
                    response.close()
                    return
                }

                val json = response.body?.string() ?: run {
                    Log.e("TelegramChatManager", context.getString(R.string.error_response_body_null))
                    onError(context.getString(R.string.error_response_body_null))
                    response.close()
                    return
                }

                try {
                    val jsonObject = JSONObject(json)
                    val updates = jsonObject.getJSONArray("result")
                    val chats = mutableListOf<TelegramChat>()
                    val seenChatIds = mutableSetOf<String>()
                    var maxUpdateId = offset

                    for (i in 0 until updates.length()) {
                        val update = updates.getJSONObject(i)
                        val updateId = update.optInt("update_id", 0)
                        if (updateId > maxUpdateId) maxUpdateId = updateId

                        if (update.has("message")) {
                            val message = update.getJSONObject("message")
                            val chat = message.getJSONObject("chat")
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
                                chats.add(TelegramChat(chatId, title, isGroup))
                                seenChatIds.add(chatId)
                            }
                        }
                    }

                    if (updates.length() == 1000) {
                        fetchChatsWithOffset(maxUpdateId + 1, { newChats ->
                            onResult(chats + newChats)
                        }, onError)
                    } else {
                        onResult(chats)
                    }
                } catch (e: Exception) {
                    Log.e("TelegramChatManager", context.getString(R.string.log_error_parsing_chats, e.message ?: ""))
                    onError(context.getString(R.string.failed_to_fetch_groups_error, e.message ?: ""))
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
        if (rawChats.isEmpty()) {
            onResult(SettingsRepository.getCachedChats().filter { it.isUserMember || !it.isGroup })
            return
        }

        val validatedChats = mutableListOf<TelegramChat>()
        var chatsProcessed = 0

        rawChats.forEach { chat ->
            telegramBotHelper.checkBotAccess(
                chatId = chat.chatId,
                isGroup = chat.isGroup,
                onResult = { isBotMember: Boolean, isBotActive: Boolean, isUserMember: Boolean ->
                    if (isBotMember && (isUserMember || !chat.isGroup)) {
                        validatedChats.add(chat.copy(isBotMember = isBotMember, isBotActive = isBotActive, isUserMember = isUserMember))
                    }
                    chatsProcessed++
                    if (chatsProcessed == rawChats.size) {
                        onResult(validatedChats.sortedBy { it.title })
                    }
                },
                onError = { error: String? ->
                    chatsProcessed++
                    if (chatsProcessed == rawChats.size) {
                        onResult(validatedChats.sortedBy { it.title })
                    }
                }
            )
        }
    }
}
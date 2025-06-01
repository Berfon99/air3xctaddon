package com.xc.air3xctaddon

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.io.IOException

data class TelegramChat(
    val chatId: String,
    val title: String,
    val isGroup: Boolean,
    val isBotMember: Boolean = false,
    val isBotActive: Boolean = false
)

data class TelegramBotInfo(
    val username: String,
    val botName: String,
    val id: Long
)

class TelegramBotHelper(
    private val context: Context,
    private val botToken: String,
    private val fusedLocationClient: FusedLocationProviderClient,
    private val settingsRepository: SettingsRepository
) {
    private val client = OkHttpClient()

    fun sendLiveLocation(
        chatId: String,
        latitude: Double,
        longitude: Double,
        livePeriod: Int = 3600
    ) {
        val url = context.getString(R.string.telegram_api_base_url) + botToken + context.getString(R.string.telegram_send_location_endpoint)
        val pilotName = settingsRepository.getPilotName()
        val json = JSONObject()
            .put("chat_id", chatId)
            .put("latitude", latitude)
            .put("longitude", longitude)
            .put("live_period", livePeriod)
            .apply {
                if (!pilotName.isNullOrEmpty()) {
                    put("caption", context.getString(R.string.telegram_position_from_pilot, pilotName))
                }
            }
            .toString()
            .also { Log.d("TelegramBotHelper", context.getString(R.string.log_sending_live_location_json, it, pilotName ?: "")) }

        val requestBody = RequestBody.create(
            context.getString(R.string.telegram_content_type).toMediaType(),
            json
        )

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("TelegramBotHelper", context.getString(R.string.log_failed_send_live_location, chatId, pilotName ?: "", e.message ?: ""))
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    Log.d("TelegramBotHelper", context.getString(R.string.log_live_location_sent, chatId, pilotName ?: ""))
                } else {
                    Log.e("TelegramBotHelper", context.getString(R.string.log_error_sending_live_location, chatId, pilotName ?: "", response.message, response.code))
                    response.body?.string()?.let { body ->
                        Log.e("TelegramBotHelper", context.getString(R.string.log_response_body, body))
                    }
                }
                response.close()
            }
        })
    }

    fun sendLocationMessage(
        chatId: String,
        latitude: Double,
        longitude: Double,
        event: String? = null
    ) {
        val url = context.getString(R.string.telegram_api_base_url) + botToken + context.getString(R.string.telegram_send_message_endpoint)
        val pilotName = settingsRepository.getPilotName()
        val mapsLink = context.getString(R.string.telegram_maps_link_format, latitude, longitude)
        val messageText = when {
            !pilotName.isNullOrEmpty() && !event.isNullOrEmpty() -> {
                context.getString(R.string.telegram_position_from_pilot_event, pilotName, event, mapsLink)
            }
            !pilotName.isNullOrEmpty() -> {
                context.getString(R.string.telegram_position_from_pilot, pilotName) + ": " + mapsLink
            }
            !event.isNullOrEmpty() -> {
                context.getString(R.string.telegram_position_event, event, mapsLink)
            }
            else -> {
                context.getString(R.string.telegram_position_only, mapsLink)
            }
        }
        val json = JSONObject()
            .put("chat_id", chatId)
            .put("text", messageText)
            .toString()
            .also { Log.d("TelegramBotHelper", context.getString(R.string.log_sending_location_message_json, it, pilotName ?: "", event ?: "")) }

        val requestBody = RequestBody.create(
            context.getString(R.string.telegram_content_type).toMediaType(),
            json
        )

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("TelegramBotHelper", context.getString(R.string.log_failed_send_location_message, chatId, pilotName ?: "", event ?: "", e.message ?: ""))
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    Log.d("TelegramBotHelper", context.getString(R.string.log_location_message_sent, chatId, pilotName ?: "", event ?: ""))
                } else {
                    Log.e("TelegramBotHelper", context.getString(R.string.log_error_sending_location_message, chatId, pilotName ?: "", event ?: "", response.message, response.code))
                    response.body?.string()?.let { body ->
                        Log.e("TelegramBotHelper", context.getString(R.string.log_response_body, body))
                    }
                }
                response.close()
            }
        })
    }

    fun getCurrentLocation(onResult: (latitude: Double, longitude: Double) -> Unit, onError: (String) -> Unit) {
        try {
            fusedLocationClient.getCurrentLocation(LocationRequest.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        onResult(location.latitude, location.longitude)
                    } else {
                        onError(context.getString(R.string.error_location_null))
                    }
                }
                .addOnFailureListener { e ->
                    onError(context.getString(R.string.error_failed_get_location, e.message ?: ""))
                }
        } catch (e: SecurityException) {
            onError(context.getString(R.string.error_location_permission_not_granted))
        }
    }

    fun getBotInfo(onResult: (TelegramBotInfo) -> Unit, onError: (String) -> Unit) {
        val url = context.getString(R.string.telegram_api_base_url) + botToken + context.getString(R.string.telegram_get_me_endpoint)
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("TelegramBotHelper", context.getString(R.string.log_failed_get_bot_info, e.message ?: ""))
                onError(context.getString(R.string.failed_to_get_bot_info, e.message ?: ""))
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.e("TelegramBotHelper", context.getString(R.string.log_error_getting_bot_info, response.message))
                    onError(context.getString(R.string.failed_to_get_bot_info, response.message))
                    response.close()
                    return
                }

                val json = response.body?.string() ?: run {
                    Log.e("TelegramBotHelper", context.getString(R.string.error_response_body_null))
                    onError(context.getString(R.string.error_response_body_null))
                    response.close()
                    return
                }

                try {
                    val jsonObject = JSONObject(json)
                    val result = jsonObject.getJSONObject("result")
                    val username = result.getString("username")
                    val firstName = result.getString("first_name")
                    val id = result.getLong("id")
                    Log.d("TelegramBotHelper", context.getString(R.string.log_bot_info_fetched, username, id))
                    onResult(TelegramBotInfo(username, firstName, id))
                } catch (e: Exception) {
                    Log.e("TelegramBotHelper", context.getString(R.string.log_error_parsing_bot_info, e.message ?: ""))
                    onError(context.getString(R.string.failed_to_get_bot_info, e.message ?: ""))
                } finally {
                    response.close()
                }
            }
        })
    }

    fun checkBotInGroup(chatId: String, onResult: (Boolean, Boolean) -> Unit, onError: (String) -> Unit) {
        getBotInfo(
            onResult = { botInfo ->
                val url = context.getString(R.string.telegram_api_base_url) + botToken + context.getString(R.string.telegram_get_chat_member_endpoint) + "?chat_id=$chatId&user_id=${botInfo.id}"
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()

                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e("TelegramBotHelper", context.getString(R.string.log_failed_check_bot_status, chatId, e.message ?: ""))
                        onError(context.getString(R.string.failed_to_check_bot_status, e.message ?: ""))
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val json = response.body?.string() ?: ""
                        response.close()

                        if (!response.isSuccessful) {
                            Log.d("TelegramBotHelper", context.getString(R.string.log_bot_not_in_group, chatId, response.message, response.code))
                            onResult(false, false)
                            return
                        }

                        try {
                            val jsonObject = JSONObject(json)
                            if (jsonObject.getBoolean("ok")) {
                                val result = jsonObject.getJSONObject("result")
                                val status = result.getString("status")
                                val validStatuses = listOf(
                                    context.getString(R.string.telegram_member_status),
                                    context.getString(R.string.telegram_admin_status),
                                    context.getString(R.string.telegram_creator_status)
                                )
                                val isMember = status in validStatuses
                                Log.d("TelegramBotHelper", context.getString(R.string.log_bot_status_in_chat, chatId, isMember, status))
                                onResult(isMember, isMember)
                            } else {
                                Log.d("TelegramBotHelper", context.getString(R.string.log_bot_not_in_group_response_not_ok, chatId, json))
                                onResult(false, false)
                            }
                        } catch (e: Exception) {
                            Log.e("TelegramBotHelper", context.getString(R.string.log_error_parsing_bot_status, chatId, e.message ?: "", json))
                            onResult(false, false)
                        }
                    }
                })
            },
            onError = { error ->
                Log.e("TelegramBotHelper", context.getString(R.string.log_failed_get_bot_info_for_check, error))
                onError(context.getString(R.string.failed_to_get_bot_info, error))
            }
        )
    }

    fun sendStartCommand(chatId: String, onResult: () -> Unit, onError: (String) -> Unit) {
        val url = context.getString(R.string.telegram_api_base_url) + botToken + context.getString(R.string.telegram_send_message_endpoint)
        val json = JSONObject()
            .put("chat_id", chatId)
            .put("text", context.getString(R.string.telegram_start_command))
            .toString()

        val requestBody = RequestBody.create(
            context.getString(R.string.telegram_content_type).toMediaType(),
            json
        )

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("TelegramBotHelper", context.getString(R.string.log_failed_send_start_command, e.message ?: ""))
                onError(context.getString(R.string.failed_to_activate_bot, e.message ?: ""))
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    Log.d("TelegramBotHelper", context.getString(R.string.log_start_command_sent, chatId))
                    onResult()
                } else {
                    Log.e("TelegramBotHelper", context.getString(R.string.log_error_sending_start_command, response.message))
                    onError(context.getString(R.string.failed_to_activate_bot, response.message))
                }
                response.close()
            }
        })
    }

    fun openTelegramToAddBot(context: Context, botUsername: String, groupTitle: String? = null) {
        val cleanUsername = botUsername.removePrefix("@")
        try {
            // Open group picker with /start prefilled
            val uri = Uri.parse("tg://resolve?domain=$cleanUsername&startgroup")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.setPackage(context.getString(R.string.telegram_package_name))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Log.d("TelegramBotHelper", context.getString(R.string.log_opening_telegram, botUsername))
        } catch (e: Exception) {
            Log.e("TelegramBotHelper", context.getString(R.string.log_failed_open_telegram, e.message ?: ""))
            try {
                // Fallback to HTTPS link
                val fallbackUri = Uri.parse("https://t.me/$cleanUsername?startgroup")
                val fallbackIntent = Intent(Intent.ACTION_VIEW, fallbackUri)
                fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(fallbackIntent)
            } catch (e: Exception) {
                Log.e("TelegramBotHelper", context.getString(R.string.log_failed_open_telegram_fallback, e.message ?: ""))
            }
        }
    }

    fun shareBotLink(context: Context, botUsername: String) {
        val cleanUsername = botUsername.removePrefix("@")
        try {
            // Open chat picker to share bot's link
            val intent = Intent(Intent.ACTION_SEND)
            intent.type = "text/plain"
            intent.putExtra(Intent.EXTRA_TEXT, "https://t.me/$cleanUsername")
            intent.setPackage(context.getString(R.string.telegram_package_name))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Log.d("TelegramBotHelper", context.getString(R.string.log_sharing_bot_link, botUsername))
        } catch (e: Exception) {
            Log.e("TelegramBotHelper", context.getString(R.string.log_failed_share_bot_link, e.message ?: ""))
            try {
                // Fallback to generic share
                val fallbackIntent = Intent(Intent.ACTION_SEND)
                fallbackIntent.type = "text/plain"
                fallbackIntent.putExtra(Intent.EXTRA_TEXT, "https://t.me/$cleanUsername")
                fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(Intent.createChooser(fallbackIntent, "Share bot link"))
            } catch (e: Exception) {
                Log.e("TelegramBotHelper", context.getString(R.string.log_failed_share_bot_link_fallback, e.message ?: ""))
            }
        }
    }

    fun fetchRecentChats(onResult: (List<TelegramChat>) -> Unit, onError: (String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            delay(2000L)
            fetchChatsWithOffset(0, { rawChats ->
                Log.d("TelegramBotHelper", context.getString(R.string.log_raw_chats_fetched, rawChats.map { it.title }.toString()))
                validateChats(rawChats, { validatedChats ->
                    Log.d("TelegramBotHelper", context.getString(R.string.log_validated_chats, validatedChats.map { it.title }.toString()))
                    // Merge with cached chats
                    val cachedChats = settingsRepository.getCachedChats()
                    val allChats = (validatedChats + cachedChats).distinctBy { it.chatId }
                    settingsRepository.saveChats(allChats)
                    onResult(allChats.sortedBy { it.title })
                }, onError)
            }, onError)
        }
    }

    private fun validateChats(
        rawChats: List<TelegramChat>,
        onResult: (List<TelegramChat>) -> Unit,
        onError: (String) -> Unit
    ) {
        if (rawChats.isEmpty()) {
            Log.d("TelegramBotHelper", context.getString(R.string.log_no_raw_chats_validate))
            onResult(settingsRepository.getCachedChats())
            return
        }

        val validatedChats = mutableListOf<TelegramChat>()
        var chatsProcessed = 0

        rawChats.forEach { chat ->
            checkBotAccess(
                chatId = chat.chatId,
                isGroup = chat.isGroup,
                onResult = { isMember, isActive ->
                    Log.d("TelegramBotHelper", context.getString(R.string.log_validated_chat, chat.title, isMember, isActive))
                    if (isMember) {
                        validatedChats.add(chat.copy(isBotMember = isMember, isBotActive = isActive))
                    }
                    chatsProcessed++
                    if (chatsProcessed == rawChats.size) {
                        onResult(validatedChats.sortedBy { it.title })
                    }
                },
                onError = { error ->
                    Log.w("TelegramBotHelper", context.getString(R.string.log_error_validating_chat, chat.title, error))
                    chatsProcessed++
                    if (chatsProcessed == rawChats.size) {
                        onResult(validatedChats.sortedBy { it.title })
                    }
                }
            )
        }
    }

    fun checkBotAccess(chatId: String, isGroup: Boolean, onResult: (Boolean, Boolean) -> Unit, onError: (String) -> Unit) {
        if (!isGroup) {
            testBotAccessToPrivateChat(chatId, onResult, onError)
        } else {
            checkBotInGroup(chatId, onResult, onError)
        }
    }

    private fun testBotAccessToPrivateChat(chatId: String, onResult: (Boolean, Boolean) -> Unit, onError: (String) -> Unit) {
        val url = context.getString(R.string.telegram_api_base_url) + botToken + "/getChat?chat_id=$chatId"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("TelegramBotHelper", context.getString(R.string.log_failed_check_bot_status, chatId, e.message ?: ""))
                onError(context.getString(R.string.failed_to_check_bot_status, e.message ?: ""))
            }

            override fun onResponse(call: Call, response: Response) {
                val json = response.body?.string() ?: ""
                response.close()

                if (response.isSuccessful) {
                    try {
                        val jsonObject = JSONObject(json)
                        if (jsonObject.getBoolean("ok")) {
                            Log.d("TelegramBotHelper", "Bot has access to private chat: $chatId")
                            onResult(true, true)
                        } else {
                            Log.d("TelegramBotHelper", "Bot doesn't have access to private chat: $chatId")
                            onResult(false, false)
                        }
                    } catch (e: Exception) {
                        Log.e("TelegramBotHelper", "Error parsing private chat access response: ${e.message}")
                        onResult(false, false)
                    }
                } else {
                    Log.d("TelegramBotHelper", "Bot doesn't have access to private chat: $chatId (HTTP ${response.code})")
                    onResult(false, false)
                }
            }
        })
    }

    fun sendMessage(
        chatId: String,
        message: String
    ) {
        val url = context.getString(R.string.telegram_api_base_url) + botToken + context.getString(R.string.telegram_send_message_endpoint)
        val pilotName = settingsRepository.getPilotName()
        val messageText = if (!pilotName.isNullOrEmpty()) {
            context.getString(R.string.telegram_message_from_pilot, pilotName, message)
        } else {
            message
        }
        val json = JSONObject()
            .put("chat_id", chatId)
            .put("text", messageText)
            .toString()
            .also { Log.d("TelegramBotHelper", context.getString(R.string.log_sending_message_json, it, pilotName ?: "")) }

        val requestBody = RequestBody.create(
            context.getString(R.string.telegram_content_type).toMediaType(),
            json
        )

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("TelegramBotHelper", context.getString(R.string.log_failed_send_message, chatId, pilotName ?: "", e.message ?: ""))
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    Log.d("TelegramBotHelper", context.getString(R.string.log_message_sent, chatId, pilotName ?: ""))
                } else {
                    Log.e("TelegramBotHelper", context.getString(R.string.log_error_sending_message, chatId, pilotName ?: "", response.message, response.code))
                    response.body?.string()?.let { body ->
                        Log.e("TelegramBotHelper", context.getString(R.string.log_response_body, body))
                    }
                }
                response.close()
            }
        })
    }

    private fun fetchChatsWithOffset(
        offset: Int,
        onResult: (List<TelegramChat>) -> Unit,
        onError: (String) -> Unit
    ) {
        val url = context.getString(R.string.telegram_api_base_url) + botToken + context.getString(R.string.telegram_get_updates_endpoint) + "?offset=$offset&timeout=10&limit=1000"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("TelegramBotHelper", context.getString(R.string.log_failed_fetch_chats, e.message ?: ""))
                onError(context.getString(R.string.failed_to_fetch_groups_error, e.message ?: ""))
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.e("TelegramBotHelper", context.getString(R.string.log_error_fetching_chats, response.message))
                    onError(context.getString(R.string.failed_to_fetch_groups_error, response.message))
                    response.close()
                    return
                }

                val json = response.body?.string() ?: run {
                    Log.e("TelegramBotHelper", context.getString(R.string.error_response_body_null))
                    onError(context.getString(R.string.error_response_body_null))
                    response.close()
                    return
                }

                Log.d("TelegramBotHelper", context.getString(R.string.log_getupdates_response, json))

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
                                    (firstName + " " + lastName).trim().takeIf { it.isNotEmpty() }
                                        ?: context.getString(R.string.unknown_user)
                                }
                                chats.add(TelegramChat(chatId, title, isGroup))
                                seenChatIds.add(chatId)
                                Log.d("TelegramBotHelper", context.getString(R.string.log_found_chat, title, chatId, chatType))
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
                    Log.e("TelegramBotHelper", context.getString(R.string.log_error_parsing_chats, e.message ?: ""))
                    onError(context.getString(R.string.failed_to_fetch_groups_error, e.message ?: ""))
                } finally {
                    response.close()
                }
            }
        })
    }
}
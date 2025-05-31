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

data class TelegramGroup(
    val chatId: String,
    val title: String,
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
        val deepLink = context.getString(R.string.telegram_deeplink_format, cleanUsername)
        Log.d("TelegramBotHelper", context.getString(R.string.log_opening_telegram_deeplink, deepLink))
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(deepLink))
            intent.setPackage(context.getString(R.string.telegram_package_name))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("TelegramBotHelper", context.getString(R.string.error_failed_open_telegram, e.message ?: ""))
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(deepLink))
            webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(webIntent)
        }
    }

    fun fetchGroups(onResult: (List<TelegramGroup>) -> Unit, onError: (String) -> Unit) {
        // Add delay to ensure Telegram processes new group
        CoroutineScope(Dispatchers.IO).launch {
            delay(2000L) // Wait 2s for Telegram to process
            fetchGroupsWithOffset(0, { rawGroups ->
                Log.d("TelegramBotHelper", context.getString(R.string.log_raw_groups_fetched, rawGroups.map { it.title }.toString()))
                validateGroups(rawGroups, { validatedGroups ->
                    Log.d("TelegramBotHelper", context.getString(R.string.log_validated_groups, validatedGroups.map { it.title }.toString()))
                    onResult(validatedGroups)
                }, onError)
            }, onError)
        }
    }

    private fun validateGroups(
        rawGroups: List<TelegramGroup>,
        onResult: (List<TelegramGroup>) -> Unit,
        onError: (String) -> Unit
    ) {
        if (rawGroups.isEmpty()) {
            Log.d("TelegramBotHelper", context.getString(R.string.log_no_raw_groups_validate))
            onResult(emptyList())
            return
        }

        val validatedGroups = mutableListOf<TelegramGroup>()
        var groupsProcessed = 0

        rawGroups.forEach { group ->
            checkBotInGroup(
                chatId = group.chatId,
                onResult = { isMember, isActive ->
                    Log.d("TelegramBotHelper", context.getString(R.string.log_validated_group, group.title, isMember, isActive))
                    if (isMember) {
                        validatedGroups.add(group.copy(isBotMember = isMember, isBotActive = isActive))
                    }
                    groupsProcessed++
                    if (groupsProcessed == rawGroups.size) {
                        onResult(validatedGroups.sortedBy { it.title })
                    }
                },
                onError = { error ->
                    Log.w("TelegramBotHelper", context.getString(R.string.log_error_validating_group, group.title, error))
                    groupsProcessed++
                    if (groupsProcessed == rawGroups.size) {
                        onResult(validatedGroups.sortedBy { it.title })
                    }
                }
            )
        }
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

    private fun fetchGroupsWithOffset(
        offset: Int,
        onResult: (List<TelegramGroup>) -> Unit,
        onError: (String) -> Unit
    ) {
        val url = context.getString(R.string.telegram_api_base_url) + botToken + context.getString(R.string.telegram_get_updates_endpoint) + "?offset=$offset"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("TelegramBotHelper", context.getString(R.string.log_failed_fetch_groups, e.message ?: ""))
                onError(context.getString(R.string.failed_to_fetch_groups_error, e.message ?: ""))
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.e("TelegramBotHelper", context.getString(R.string.log_error_fetching_groups, response.message))
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
                    val groups = mutableListOf<TelegramGroup>()
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
                            val validGroupTypes = listOf(
                                context.getString(R.string.telegram_group_type),
                                context.getString(R.string.telegram_supergroup_type)
                            )
                            if (chatType in validGroupTypes && chatId !in seenChatIds) {
                                val title = chat.optString("title", context.getString(R.string.unknown_group))
                                groups.add(TelegramGroup(chatId, title))
                                seenChatIds.add(chatId)
                                Log.d("TelegramBotHelper", context.getString(R.string.log_found_group, title, chatId))
                            }
                        }
                    }

                    if (updates.length() == 100) {
                        fetchGroupsWithOffset(maxUpdateId + 1, { newGroups ->
                            onResult(groups + newGroups)
                        }, onError)
                    } else {
                        onResult(groups)
                    }
                } catch (e: Exception) {
                    Log.e("TelegramBotHelper", context.getString(R.string.log_error_parsing_groups, e.message ?: ""))
                    onError(context.getString(R.string.failed_to_fetch_groups_error, e.message ?: ""))
                } finally {
                    response.close()
                }
            }
        })
    }
}
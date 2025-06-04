package com.xc.air3xctaddon

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.tasks.CancellationTokenSource
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.io.IOException

class TelegramBotHelper(
    private val context: Context,
    private val botToken: String,
    private val fusedLocationClient: FusedLocationProviderClient
) {
    private val client = OkHttpClient()

    fun openTelegramToAddBot(context: Context, botUsername: String, groupTitle: String? = null) {
        val cleanUsername = botUsername.removePrefix("@")
        try {
            val uri = if (groupTitle != null) {
                Uri.parse("tg://resolve?domain=$cleanUsername&startgroup")
            } else {
                Uri.parse("tg://resolve?domain=$cleanUsername")
            }
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.setPackage("org.telegram.messenger")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Log.d("TelegramBotHelper", "Opening Telegram for bot: $botUsername")
        } catch (e: Exception) {
            Log.e("TelegramBotHelper", "Failed to open Telegram: ${e.message}")
            try {
                val fallbackUri = Uri.parse("https://t.me/$cleanUsername${if (groupTitle != null) "?startgroup" else ""}")
                val fallbackIntent = Intent(Intent.ACTION_VIEW, fallbackUri)
                fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(fallbackIntent)
            } catch (e: Exception) {
                Log.e("TelegramBotHelper", "Failed to open Telegram fallback: ${e.message}")
            }
        }
    }

    fun shareBotLink(context: Context, botUsername: String) {
        val cleanUsername = botUsername.removePrefix("@")
        try {
            val botLink = "https://t.me/$cleanUsername"
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, botLink)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Share Bot Link"))
            Log.d("TelegramBotHelper", "Sharing bot link for: $botUsername")
        } catch (e: Exception) {
            Log.e("TelegramBotHelper", "Failed to share bot link: ${e.message}")
            try {
                val fallbackUri = Uri.parse("https://t.me/$cleanUsername")
                val fallbackIntent = Intent(Intent.ACTION_VIEW, fallbackUri)
                fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(Intent.createChooser(fallbackIntent, "Share Bot"))
            } catch (e: Exception) {
                Log.e("TelegramBotHelper", "Failed to share bot link fallback: ${e.message}")
            }
        }
    }

    fun sendLocationMessage(
        chatId: String,
        latitude: Double,
        longitude: Double,
        event: String? = null
    ) {
        val url = context.getString(R.string.telegram_api_base_url) + botToken + context.getString(R.string.telegram_send_message_endpoint)
        val pilotName = SettingsRepository.getPilotName()
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

    fun sendMessage(
        chatId: String,
        message: String
    ) {
        val url = context.getString(R.string.telegram_api_base_url) + botToken + context.getString(R.string.telegram_send_message_endpoint)
        val pilotName = SettingsRepository.getPilotName()
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
                    Log.d("TelegramBotHelper", "Message sent to $chatId, pilot: ${pilotName ?: ""}")
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
        if (!botToken.matches(Regex("\\d+:[A-Za-z0-9_-]+"))) {
            val error = "Invalid bot token format: $botToken"
            Log.e("TelegramBotHelper", error)
            onError(context.getString(R.string.failed_to_get_bot_info, error))
            return
        }

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
                    if (!jsonObject.getBoolean("ok")) {
                        val description = jsonObject.optString("description", "Unknown error")
                        Log.e("TelegramBotHelper", "API error: $description")
                        onError(context.getString(R.string.failed_to_get_bot_info, description))
                        response.close()
                        return
                    }

                    val result = jsonObject.getJSONObject("result")
                    val username = result.getString("username")
                    val firstName = result.getString("first_name")
                    val id = result.getLong("id").toString()
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

    /**
     * Checks if the bot has access to the specified chat and determines bot/user membership status
     * @param chatId The chat ID to check
     * @param isGroup Whether the chat is a group chat
     * @param onResult Callback with (isBotMember, isBotActive, isUserMember) status
     * @param onError Callback for error handling
     */
    fun checkBotAccess(
        chatId: String,
        isGroup: Boolean,
        onResult: (Boolean, Boolean, Boolean) -> Unit,
        onError: (String?) -> Unit
    ) {
        // For private chats, assume user is member and bot can send messages
        if (!isGroup) {
            onResult(true, true, true)
            return
        }

        // For group chats, check if bot is a member by trying to get chat info
        val url = context.getString(R.string.telegram_api_base_url) + botToken + "/getChat"
        val json = JSONObject()
            .put("chat_id", chatId)
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
                Log.e("TelegramBotHelper", "Failed to check bot access for chat $chatId: ${e.message}")
                onError(e.message)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val json = response.body?.string() ?: run {
                        Log.e("TelegramBotHelper", "Empty response body when checking bot access")
                        onError("Empty response body")
                        response.close()
                        return
                    }

                    val jsonObject = JSONObject(json)
                    val isSuccess = jsonObject.getBoolean("ok")

                    if (isSuccess) {
                        // Bot can access chat info, so it's likely a member
                        // Check if bot can send messages by attempting to get chat member info
                        checkBotMembershipStatus(chatId, onResult, onError)
                    } else {
                        // Bot cannot access chat info, likely not a member or kicked
                        val errorCode = jsonObject.optInt("error_code", 0)
                        val description = jsonObject.optString("description", "Unknown error")

                        Log.d("TelegramBotHelper", "Bot access check failed for chat $chatId: $description (code: $errorCode)")

                        // Common error codes:
                        // 400: Bad Request (chat not found, bot not in chat)
                        // 403: Forbidden (bot was kicked or chat is private)
                        when (errorCode) {
                            400, 403 -> onResult(false, false, false) // Bot not in chat
                            else -> onResult(false, false, true) // Assume user is member but bot has issues
                        }
                    }
                } catch (e: Exception) {
                    Log.e("TelegramBotHelper", "Error parsing bot access response: ${e.message}")
                    onError(e.message)
                } finally {
                    response.close()
                }
            }
        })
    }

    private fun checkBotMembershipStatus(
        chatId: String,
        onResult: (Boolean, Boolean, Boolean) -> Unit,
        onError: (String?) -> Unit
    ) {
        // Get bot info to get bot user ID
        getBotInfo({ botInfo ->
            val url = context.getString(R.string.telegram_api_base_url) + botToken + "/getChatMember"
            val json = JSONObject()
                .put("chat_id", chatId)
                .put("user_id", botInfo.id)
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
                    Log.e("TelegramBotHelper", "Failed to check bot membership status: ${e.message}")
                    // Fallback: assume bot is member but status unknown
                    onResult(true, false, true)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val json = response.body?.string() ?: run {
                            Log.e("TelegramBotHelper", "Empty response body when checking bot membership")
                            onResult(true, false, true)
                            response.close()
                            return
                        }

                        val jsonObject = JSONObject(json)
                        val isSuccess = jsonObject.getBoolean("ok")

                        if (isSuccess) {
                            val result = jsonObject.getJSONObject("result")
                            val status = result.getString("status")

                            val isBotMember = status in listOf("member", "administrator", "creator")
                            val isBotActive = status in listOf("member", "administrator", "creator")

                            Log.d("TelegramBotHelper", "Bot status in chat $chatId: $status")

                            // For group chats, we assume user is member since they can see the chat
                            // In a real implementation, you might want to check user membership too
                            onResult(isBotMember, isBotActive, true)
                        } else {
                            val description = jsonObject.optString("description", "Unknown error")
                            Log.e("TelegramBotHelper", "Failed to get bot membership status: $description")
                            // Fallback: assume not member
                            onResult(false, false, true)
                        }
                    } catch (e: Exception) {
                        Log.e("TelegramBotHelper", "Error parsing bot membership response: ${e.message}")
                        onResult(true, false, true) // Fallback
                    } finally {
                        response.close()
                    }
                }
            })
        }, { error ->
            Log.e("TelegramBotHelper", "Failed to get bot info for membership check: $error")
            onResult(true, false, true) // Fallback
        })
    }
}
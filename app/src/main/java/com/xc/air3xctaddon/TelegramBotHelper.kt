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
import org.json.JSONArray
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
    val botName: String
)

class TelegramBotHelper(
    private val botToken: String,
    private val fusedLocationClient: FusedLocationProviderClient
) {
    private val client = OkHttpClient()

    fun sendLiveLocation(chatId: String, latitude: Double, longitude: Double, livePeriod: Int = 3600) {
        val url = "https://api.telegram.org/bot$botToken/sendLocation"
        val json = JSONObject()
            .put("chat_id", chatId)
            .put("latitude", latitude)
            .put("longitude", longitude)
            .put("live_period", livePeriod)
            .toString()

        val requestBody = RequestBody.create(
            "application/json; charset=utf-8".toMediaType(),
            json
        )

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("TelegramBotHelper", "Failed to send location: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    Log.d("TelegramBotHelper", "Location sent to chat $chatId")
                } else {
                    Log.e("TelegramBotHelper", "Error sending location: ${response.message}")
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
                        onError("Location is null")
                    }
                }
                .addOnFailureListener { e ->
                    onError("Failed to get location: ${e.message}")
                }
        } catch (e: SecurityException) {
            onError("Location permission not granted")
        }
    }

    fun getBotInfo(onResult: (TelegramBotInfo) -> Unit, onError: (String) -> Unit) {
        val url = "https://api.telegram.org/bot$botToken/getMe"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onError("Failed to get bot info: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    onError("Error getting bot info: ${response.message}")
                    response.close()
                    return
                }

                val json = response.body?.string() ?: run {
                    onError("Response body is null")
                    response.close()
                    return
                }

                try {
                    val jsonObject = JSONObject(json)
                    val result = jsonObject.getJSONObject("result")
                    val username = result.getString("username")
                    val firstName = result.getString("first_name")
                    onResult(TelegramBotInfo(username, firstName))
                } catch (e: Exception) {
                    onError("Error parsing bot info: ${e.message}")
                } finally {
                    response.close()
                }
            }
        })
    }

    fun checkBotInGroup(chatId: String, onResult: (Boolean, Boolean) -> Unit, onError: (String) -> Unit) {
        val url = "https://api.telegram.org/bot$botToken/getChatMember?chat_id=$chatId&user_id=${getBotUserId()}"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onError("Failed to check bot status: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val json = response.body?.string() ?: ""
                response.close()

                if (!response.isSuccessful) {
                    // Bot is not in the group
                    onResult(false, false)
                    return
                }

                try {
                    val jsonObject = JSONObject(json)
                    if (jsonObject.getBoolean("ok")) {
                        val result = jsonObject.getJSONObject("result")
                        val status = result.getString("status")
                        val isMember = status in listOf("member", "administrator", "creator")
                        // For this use case, if bot is a member, we consider it active
                        onResult(isMember, isMember)
                    } else {
                        onResult(false, false)
                    }
                } catch (e: Exception) {
                    onResult(false, false)
                }
            }
        })
    }

    fun sendStartCommand(chatId: String, onResult: () -> Unit, onError: (String) -> Unit) {
        val url = "https://api.telegram.org/bot$botToken/sendMessage"
        val json = JSONObject()
            .put("chat_id", chatId)
            .put("text", "/start")
            .toString()

        val requestBody = RequestBody.create(
            "application/json; charset=utf-8".toMediaType(),
            json
        )

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onError("Failed to send start command: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    Log.d("TelegramBotHelper", "Start command sent to chat $chatId")
                    onResult()
                } else {
                    onError("Error sending start command: ${response.message}")
                }
                response.close()
            }
        })
    }

    fun openTelegramToAddBot(context: Context, botUsername: String, groupTitle: String? = null) {
        val intent = if (groupTitle != null) {
            // Try to open Telegram with a deep link to add bot to a specific group
            Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/$botUsername?startgroup=true"))
        } else {
            // Open bot in Telegram
            Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/$botUsername"))
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback to web browser
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/$botUsername"))
            webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(webIntent)
        }
    }

    private fun getBotUserId(): String {
        // Extract bot user ID from token (it's the part before the colon)
        return botToken.substringBefore(":")
    }

    fun fetchGroups(onResult: (List<TelegramGroup>) -> Unit, onError: (String) -> Unit) {
        fetchGroupsWithOffset(0, onResult, onError)
    }

    private fun fetchGroupsWithOffset(offset: Int, onResult: (List<TelegramGroup>) -> Unit, onError: (String) -> Unit) {
        val url = "https://api.telegram.org/bot$botToken/getUpdates?offset=$offset"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onError("Failed to fetch groups: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    onError("Error fetching groups: ${response.message}")
                    response.close()
                    return
                }

                val json = response.body?.string() ?: run {
                    onError("Response body is null")
                    response.close()
                    return
                }

                Log.d("TelegramBotHelper", "getUpdates response: $json")

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
                            val chatId = chat.getString("id")
                            val chatType = chat.getString("type")
                            if ((chatType == "group" || chatType == "supergroup") && chatId !in seenChatIds) {
                                val title = chat.optString("title", "Unknown Group")
                                groups.add(TelegramGroup(chatId, title, true, true)) // If we see it in updates, bot is active
                                seenChatIds.add(chatId)
                            }
                        }
                    }

                    if (updates.length() == 100) {
                        // More updates may be available, fetch next batch
                        fetchGroupsWithOffset(maxUpdateId + 1, { newGroups ->
                            onResult(groups + newGroups)
                        }, onError)
                    } else {
                        onResult(groups)
                    }
                } catch (e: Exception) {
                    onError("Error parsing groups: ${e.message}")
                } finally {
                    response.close()
                }
            }
        })
    }
}
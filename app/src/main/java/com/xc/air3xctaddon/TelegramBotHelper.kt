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
    private val botToken: String,
    private val fusedLocationClient: FusedLocationProviderClient
) {
    private val client = OkHttpClient()

    fun sendLiveLocation(
        chatId: String,
        latitude: Double,
        longitude: Double,
        livePeriod: Int = 3600,
        username: String? = null
    ) {
        val url = "https://api.telegram.org/bot$botToken/sendLocation"
        val json = JSONObject()
            .put("chat_id", chatId)
            .put("latitude", latitude)
            .put("longitude", longitude)
            .put("live_period", livePeriod)
            .apply {
                if (!username.isNullOrEmpty()) {
                    put("caption", "Position from $username")
                }
            }
            .toString()
            .also { Log.d("TelegramBotHelper", "Sending live location JSON: $it, username=$username") }

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
                Log.e("TelegramBotHelper", "Failed to send live location to chat $chatId, username=$username: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    Log.d("TelegramBotHelper", "Live location sent to chat $chatId, username=$username")
                } else {
                    Log.e("TelegramBotHelper", "Error sending live location to chat $chatId, username=$username: ${response.message}, code=${response.code}")
                    response.body?.string()?.let { body ->
                        Log.e("TelegramBotHelper", "Response body: $body")
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
                Log.e("TelegramBotHelper", "Failed to get bot info: ${e.message}")
                onError("Failed to get bot info: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.e("TelegramBotHelper", "Error getting bot info: ${response.message}")
                    onError("Error getting bot info: ${response.message}")
                    response.close()
                    return
                }

                val json = response.body?.string() ?: run {
                    Log.e("TelegramBotHelper", "Response body is null")
                    onError("Response body is null")
                    response.close()
                    return
                }

                try {
                    val jsonObject = JSONObject(json)
                    val result = jsonObject.getJSONObject("result")
                    val username = result.getString("username")
                    val firstName = result.getString("first_name")
                    val id = result.getLong("id")
                    Log.d("TelegramBotHelper", "Bot info fetched: username=$username, id=$id")
                    onResult(TelegramBotInfo(username, firstName, id))
                } catch (e: Exception) {
                    Log.e("TelegramBotHelper", "Error parsing bot info: ${e.message}")
                    onError("Error parsing bot info: ${e.message}")
                } finally {
                    response.close()
                }
            }
        })
    }

    fun checkBotInGroup(chatId: String, onResult: (Boolean, Boolean) -> Unit, onError: (String) -> Unit) {
        getBotInfo(
            onResult = { botInfo ->
                val url = "https://api.telegram.org/bot$botToken/getChatMember?chat_id=$chatId&user_id=${botInfo.id}"
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()

                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e("TelegramBotHelper", "Failed to check bot status for chat $chatId: ${e.message}")
                        onError("Failed to check bot status: ${e.message}")
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val json = response.body?.string() ?: ""
                        response.close()

                        if (!response.isSuccessful) {
                            Log.d("TelegramBotHelper", "Bot not in group $chatId: ${response.message} (code=${response.code})")
                            onResult(false, false)
                            return
                        }

                        try {
                            val jsonObject = JSONObject(json)
                            if (jsonObject.getBoolean("ok")) {
                                val result = jsonObject.getJSONObject("result")
                                val status = result.getString("status")
                                val isMember = status in listOf("member", "administrator", "creator")
                                Log.d("TelegramBotHelper", "Bot status in chat $chatId: isMember=$isMember, status=$status")
                                onResult(isMember, isMember)
                            } else {
                                Log.d("TelegramBotHelper", "Bot not in group $chatId: Response not ok, json=$json")
                                onResult(false, false)
                            }
                        } catch (e: Exception) {
                            Log.e("TelegramBotHelper", "Error parsing bot status for chat $chatId: ${e.message}, json=$json")
                            onResult(false, false)
                        }
                    }
                })
            },
            onError = { error ->
                Log.e("TelegramBotHelper", "Failed to get bot info for checkBotInGroup: $error")
                onError("Failed to get bot info: $error")
            }
        )
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
                Log.e("TelegramBotHelper", "Failed to send start command: ${e.message}")
                onError("Failed to send start command: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    Log.d("TelegramBotHelper", "Start command sent to chat $chatId")
                    onResult()
                } else {
                    Log.e("TelegramBotHelper", "Error sending start command: ${response.message}")
                    onError("Error sending start command: ${response.message}")
                }
                response.close()
            }
        })
    }

    fun openTelegramToAddBot(context: Context, botUsername: String, groupTitle: String? = null) {
        val deepLink = "https://t.me/${botUsername.removePrefix("@")}?startgroup=true"
        Log.d("TelegramBotHelper", "Opening Telegram with deep link: $deepLink")
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(deepLink))
            intent.setPackage("org.telegram.messenger")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("TelegramBotHelper", "Failed to open Telegram: ${e.message}")
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
                Log.d("TelegramBotHelper", "Raw groups fetched: ${rawGroups.map { it.title }}")
                validateGroups(rawGroups, { validatedGroups ->
                    Log.d("TelegramBotHelper", "Validated groups: ${validatedGroups.map { it.title }}")
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
            Log.d("TelegramBotHelper", "No raw groups to validate")
            onResult(emptyList())
            return
        }

        val validatedGroups = mutableListOf<TelegramGroup>()
        var groupsProcessed = 0

        rawGroups.forEach { group ->
            checkBotInGroup(
                chatId = group.chatId,
                onResult = { isMember, isActive ->
                    Log.d("TelegramBotHelper", "Validated group ${group.title}: isMember=$isMember, isActive=$isActive")
                    if (isMember) {
                        validatedGroups.add(group.copy(isBotMember = isMember, isBotActive = isActive))
                    }
                    groupsProcessed++
                    if (groupsProcessed == rawGroups.size) {
                        onResult(validatedGroups.sortedBy { it.title })
                    }
                },
                onError = { error ->
                    Log.w("TelegramBotHelper", "Error validating group ${group.title}: $error")
                    groupsProcessed++
                    if (groupsProcessed == rawGroups.size) {
                        onResult(validatedGroups.sortedBy { it.title })
                    }
                }
            )
        }
    }

    private fun fetchGroupsWithOffset(
        offset: Int,
        onResult: (List<TelegramGroup>) -> Unit,
        onError: (String) -> Unit
    ) {
        val url = "https://api.telegram.org/bot$botToken/getUpdates?offset=$offset"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("TelegramBotHelper", "Failed to fetch groups: ${e.message}")
                onError("Failed to fetch groups: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.e("TelegramBotHelper", "Error fetching groups: ${response.message}")
                    onError("Error fetching groups: ${response.message}")
                    response.close()
                    return
                }

                val json = response.body?.string() ?: run {
                    Log.e("TelegramBotHelper", "Response body is null")
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
                                groups.add(TelegramGroup(chatId, title))
                                seenChatIds.add(chatId)
                                Log.d("TelegramBotHelper", "Found group: $title, chatId=$chatId")
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
                    Log.e("TelegramBotHelper", "Error parsing groups: ${e.message}")
                    onError("Error parsing groups: ${e.message}")
                } finally {
                    response.close()
                }
            }
        })
    }
}
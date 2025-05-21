package com.xc.air3xctaddon

import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.tasks.CancellationTokenSource
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

data class TelegramGroup(val chatId: String, val title: String)

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
            "application/json; charset=utf-8".toMediaType(), // Fixed: Replaced MediaType.parse
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

    fun fetchGroups(onResult: (List<TelegramGroup>) -> Unit, onError: (String) -> Unit) {
        val url = "https://api.telegram.org/bot$botToken/getUpdates"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onError("Failed to fetch groups: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val json = response.body?.string() ?: run {
                        onError("Response body is null")
                        response.close()
                        return
                    }
                    val groups = mutableListOf<TelegramGroup>()
                    try {
                        val updates = JSONObject(json).getJSONArray("result")
                        val seenChatIds = mutableSetOf<String>()
                        for (i in 0 until updates.length()) {
                            val update = updates.getJSONObject(i)
                            if (update.has("message")) {
                                val message = update.getJSONObject("message")
                                val chat = message.getJSONObject("chat")
                                val chatId = chat.getString("id")
                                val chatType = chat.getString("type")
                                if ((chatType == "group" || chatType == "supergroup") && chatId !in seenChatIds) {
                                    val title = chat.optString("title", "Unknown Group")
                                    groups.add(TelegramGroup(chatId, title))
                                    seenChatIds.add(chatId)
                                }
                            }
                        }
                        onResult(groups)
                    } catch (e: Exception) {
                        onError("Error parsing groups: ${e.message}")
                    }
                } else {
                    onError("Error fetching groups: ${response.message}")
                }
                response.close()
            }
        })
    }
}
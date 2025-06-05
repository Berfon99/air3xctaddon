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
    val botToken: String,
    private val fusedLocationClient: FusedLocationProviderClient
) {
    private val client = OkHttpClient()

    fun openTelegramToSelectGroup(context: Context) {
        try {
            // Try to open Telegram without specifying package first (let system choose)
            val uri = Uri.parse("tg://")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Log.d("TelegramBotHelper", "Opening Telegram for group selection")
        } catch (e: Exception) {
            Log.e("TelegramBotHelper", "Failed to open Telegram with tg:// scheme: ${e.message}")
            try {
                // Fallback - try with specific package
                val uri = Uri.parse("tg://")
                val intent = Intent(Intent.ACTION_VIEW, uri)
                intent.setPackage("org.telegram.messenger")
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Log.d("TelegramBotHelper", "Opened Telegram with specific package")
            } catch (e: Exception) {
                Log.e("TelegramBotHelper", "Failed with specific package: ${e.message}")
                try {
                    // Try alternative Telegram package names
                    val packages = listOf("org.telegram.messenger", "org.telegram.plus", "org.thunderdog.challegram")
                    var success = false
                    for (packageName in packages) {
                        try {
                            val packageManager = context.packageManager
                            val telegramIntent = packageManager.getLaunchIntentForPackage(packageName)
                            if (telegramIntent != null) {
                                telegramIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(telegramIntent)
                                Log.d("TelegramBotHelper", "Opened Telegram app directly with package: $packageName")
                                success = true
                                break
                            }
                        } catch (e: Exception) {
                            Log.d("TelegramBotHelper", "Package $packageName not found or failed to launch")
                        }
                    }
                    if (!success) {
                        Log.e("TelegramBotHelper", "No Telegram app found")
                    }
                } catch (e: Exception) {
                    Log.e("TelegramBotHelper", "Failed to launch any Telegram app: ${e.message}")
                }
            }
        }
    }

    fun openTelegramToAddBot(context: Context, botUsername: String) {
        val cleanUsername = botUsername.removePrefix("@")
        try {
            val uri = Uri.parse("tg://resolve?domain=$cleanUsername&startgroup")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.setPackage("org.telegram.messenger")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Log.d("TelegramBotHelper", "Opening Telegram to add bot to group: $botUsername")
        } catch (e: Exception) {
            Log.e("TelegramBotHelper", "Failed to open Telegram: ${e.message}")
            try {
                val fallbackUri = Uri.parse("https://t.me/$cleanUsername?startgroup")
                val fallbackIntent = Intent(Intent.ACTION_VIEW, fallbackUri)
                fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(fallbackIntent)
            } catch (e: Exception) {
                Log.e("TelegramBotHelper", "Failed to open Telegram fallback: ${e.message}")
            }
        }
    }

    fun sendLocationMessage(
        chatId: String,
        latitude: Double,
        longitude: Double,
        event: String? = null
    ) {
        val url = "https://api.telegram.org/bot" + botToken + "/sendMessage"
        val pilotName = SettingsRepository.getPilotName()
        val mapsLink = "https://maps.google.com/?q=$latitude,$longitude"
        val messageText = when {
            !pilotName.isNullOrEmpty() && !event.isNullOrEmpty() -> {
                "$pilotName ($event): $mapsLink"
            }
            !pilotName.isNullOrEmpty() -> {
                "Position from $pilotName: $mapsLink"
            }
            !event.isNullOrEmpty() -> {
                "Position ($event): $mapsLink"
            }
            else -> {
                "Position: $mapsLink"
            }
        }
        val json = JSONObject()
            .put("chat_id", chatId)
            .put("text", messageText)
            .toString()
            .also { Log.d("TelegramBotHelper", "Sending location message JSON: $it, pilotName=${pilotName ?: ""}, event=${event ?: ""}") }

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
                Log.e("TelegramBotHelper", "Failed to send location message to chat $chatId, pilotName=${pilotName ?: ""}, event=${event ?: ""}: ${e.message ?: ""}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    Log.d("TelegramBotHelper", "Location message sent to chat $chatId, pilotName=${pilotName ?: ""}, event=${event ?: ""}")
                } else {
                    Log.e("TelegramBotHelper", "Error sending location message to chat $chatId, pilotName=${pilotName ?: ""}, event=${event ?: ""}: ${response.message}, code=${response.code}")
                    response.body?.string()?.let { body ->
                        Log.e("TelegramBotHelper", "Response body: $body")
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
        val url = "https://api.telegram.org/bot" + botToken + "/sendMessage"
        val pilotName = SettingsRepository.getPilotName()
        val messageText = if (!pilotName.isNullOrEmpty()) {
            "Message from $pilotName: $message"
        } else {
            message
        }
        val json = JSONObject()
            .put("chat_id", chatId)
            .put("text", messageText)
            .toString()
            .also { Log.d("TelegramBotHelper", "Sending message JSON: $it, pilot: ${pilotName ?: ""}") }

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
                Log.e("TelegramBotHelper", "Failed to send message to chat $chatId, pilot: ${pilotName ?: ""}, error: ${e.message ?: ""}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    Log.d("TelegramBotHelper", "Message sent to chat $chatId, pilot: ${pilotName ?: ""}")
                } else {
                    Log.e("TelegramBotHelper", "Error sending message to chat $chatId, pilot: ${pilotName ?: ""}, message: ${response.message}, code: ${response.code}")
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
                    onError("Failed to get location: ${e.message ?: ""}")
                }
        } catch (e: SecurityException) {
            onError("Location permission not granted")
        }
    }

    fun getBotInfo(onResult: (TelegramBotInfo) -> Unit, onError: (String) -> Unit) {
        if (!botToken.matches(Regex("\\d+:[A-Za-z0-9_-]+"))) {
            val error = "Invalid bot token format: $botToken"
            Log.e("TelegramBotHelper", error)
            onError(error)
            return
        }

        val url = "https://api.telegram.org/bot" + botToken + "/getMe"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("TelegramBotHelper", "Failed to get bot info: ${e.message ?: ""}")
                onError("Failed to get bot info: ${e.message ?: ""}")
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
                    if (!jsonObject.getBoolean("ok")) {
                        val description = jsonObject.optString("description", "Unknown error")
                        Log.e("TelegramBotHelper", "API error: $description")
                        onError(description)
                        response.close()
                        return
                    }

                    val result = jsonObject.getJSONObject("result")
                    val username = result.getString("username")
                    val firstName = result.getString("first_name")
                    val id = result.getLong("id").toString()
                    Log.d("TelegramBotHelper", "Bot info fetched: $username, $id")
                    onResult(TelegramBotInfo(username, firstName, id))
                } catch (e: Exception) {
                    Log.e("TelegramBotHelper", "Error parsing bot info: ${e.message ?: ""}")
                    onError("Error parsing bot info: ${e.message ?: ""}")
                } finally {
                    response.close()
                }
            }
        })
    }

    fun sendStartCommand(chatId: String, onResult: () -> Unit, onError: (String) -> Unit) {
        val url = "https://api.telegram.org/bot" + botToken + "/sendMessage"
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
                Log.e("TelegramBotHelper", "Failed to send start command: ${e.message ?: ""}")
                onError("Failed to send start command: ${e.message ?: ""}")
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

    fun checkBotAccess(
        chatId: String,
        onResult: (Boolean, Boolean, Boolean) -> Unit,
        onError: (String?) -> Unit
    ) {
        val url = "https://api.telegram.org/bot" + botToken + "/getChat"
        val json = JSONObject()
            .put("chat_id", chatId)
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
                        checkBotMembershipStatus(chatId, onResult, onError)
                    } else {
                        val errorCode = jsonObject.optInt("error_code", 0)
                        val description = jsonObject.optString("description", "Unknown error")
                        Log.d("TelegramBotHelper", "Bot access check failed for chat $chatId: $description (code: $errorCode)")
                        when (errorCode) {
                            400, 403 -> onResult(false, false, false)
                            else -> onResult(false, false, true)
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
        getBotInfo({ botInfo ->
            val url = "https://api.telegram.org/bot" + botToken + "/getChatMember"
            val json = JSONObject()
                .put("chat_id", chatId)
                .put("user_id", botInfo.id)
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
                    Log.e("TelegramBotHelper", "Failed to check bot membership status: ${e.message}")
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
                            onResult(isBotMember, isBotActive, true)
                        } else {
                            val description = jsonObject.optString("description", "Unknown error")
                            Log.e("TelegramBotHelper", "Failed to get bot membership status: $description")
                            onResult(false, false, true)
                        }
                    } catch (e: Exception) {
                        Log.e("TelegramBotHelper", "Error parsing bot membership response: ${e.message}")
                        onResult(true, false, true)
                    } finally {
                        response.close()
                    }
                }
            })
        }, { error ->
            Log.e("TelegramBotHelper", "Failed to get bot info for membership check: $error")
            onResult(true, false, true)
        })
    }
}

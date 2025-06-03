package com.xc.air3xctaddon

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class TelegramValidation(
    private val context: Context,
    private val botToken: String,
    private val settingsRepository: SettingsRepository
) {
    private val client = OkHttpClient()

    fun fetchUserId(
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
        retryCount: Int = 0,
        maxRetries: Int = 3,
        offset: Long? = null
    ) {
        if (retryCount > maxRetries) {
            Log.e("TelegramValidation", "Max retries reached for fetchUserId")
            onError(context.getString(R.string.user_id_not_found_prompt))
            return
        }

        // Clear previous validation state only on first attempt
        if (retryCount == 0) {
            settingsRepository.clearUserId()
            settingsRepository.clearTelegramValidated()
        }

        val urlBuilder = StringBuilder(context.getString(R.string.telegram_api_base_url) + botToken + context.getString(R.string.telegram_get_updates_endpoint) + "?timeout=10&limit=100")
        offset?.let { urlBuilder.append("&offset=$it") }
        val url = urlBuilder.toString()
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("TelegramValidation", "Failed to fetch user ID (retry $retryCount): ${e.message}")
                CoroutineScope(Dispatchers.IO).launch {
                    delay(2000L * (retryCount + 1))
                    fetchUserId(onResult, onError, retryCount + 1, maxRetries, offset)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.e("TelegramValidation", "Error fetching user ID (retry $retryCount): ${response.message}, code: ${response.code}")
                    response.body?.string()?.let { body ->
                        Log.e("TelegramValidation", "Response body: $body")
                    }
                    CoroutineScope(Dispatchers.IO).launch {
                        delay(2000L * (retryCount + 1))
                        fetchUserId(onResult, onError, retryCount + 1, maxRetries, offset)
                    }
                    response.close()
                    return
                }

                val json = response.body?.string() ?: run {
                    Log.e("TelegramValidation", "Response body is null")
                    onError(context.getString(R.string.error_response_body_null))
                    response.close()
                    return
                }

                Log.d("TelegramValidation", "getUpdates response: $json")
                try {
                    val jsonObject = JSONObject(json)
                    if (!jsonObject.getBoolean("ok")) {
                        val description = jsonObject.optString("description", "Unknown error")
                        Log.e("TelegramValidation", "API error fetching user ID (retry $retryCount): $description")
                        CoroutineScope(Dispatchers.IO).launch {
                            delay(2000L * (retryCount + 1))
                            fetchUserId(onResult, onError, retryCount + 1, maxRetries, offset)
                        }
                        response.close()
                        return
                    }

                    val updates = jsonObject.getJSONArray("result")
                    var latestUpdateId: Long? = null
                    val currentTime = System.currentTimeMillis() / 1000 // Current time in seconds
                    val timeThreshold = currentTime - 60 // Check messages from last 60 seconds

                    for (i in 0 until updates.length()) {
                        val update = updates.getJSONObject(i)
                        val updateId = update.getLong("update_id")
                        latestUpdateId = maxOf(latestUpdateId ?: updateId, updateId)

                        if (update.has("message")) {
                            val message = update.getJSONObject("message")
                            val date = message.getLong("date")
                            if (date < timeThreshold) continue // Skip old messages

                            val chat = message.getJSONObject("chat")
                            val chatType = chat.getString("type")
                            if (chatType == "private" && message.has("text") && message.getString("text") == "/start") {
                                val user = message.getJSONObject("from")
                                val userId = user.getLong("id").toString()
                                settingsRepository.saveUserId(userId)
                                settingsRepository.setTelegramValidated(true)
                                Log.d("TelegramValidation", "Fetched user ID: $userId")
                                onResult(userId)
                                response.close()
                                return
                            }
                        }
                    }

                    // No valid /start found, retry with the latest update ID as offset
                    CoroutineScope(Dispatchers.IO).launch {
                        delay(2000L * (retryCount + 1))
                        fetchUserId(onResult, onError, retryCount + 1, maxRetries, latestUpdateId?.plus(1))
                    }
                } catch (e: Exception) {
                    Log.e("TelegramValidation", "Error parsing user ID (retry $retryCount): ${e.message}")
                    onError(context.getString(R.string.failed_to_fetch_user_id, e.message ?: ""))
                } finally {
                    response.close()
                }
            }
        })
    }

    fun openTelegramChat(botUsername: String, prefillText: String? = null) {
        val cleanUsername = botUsername.removePrefix("@")
        try {
            val uriString = if (prefillText != null) {
                "tg://msg?to=$cleanUsername&text=${Uri.encode(prefillText)}"
            } else {
                "tg://resolve?domain=$cleanUsername"
            }
            val uri = Uri.parse(uriString)
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.setPackage(context.getString(R.string.telegram_package_name))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Log.d("TelegramValidation", "Opening Telegram for bot: $botUsername, prefill: $prefillText")
        } catch (e: Exception) {
            Log.e("TelegramValidation", "Failed to open Telegram: ${e.message}")
            try {
                val fallbackUrl = if (prefillText != null) {
                    "https://t.me/$cleanUsername?text=${Uri.encode(prefillText)}"
                } else {
                    "https://t.me/$cleanUsername"
                }
                val fallbackUri = Uri.parse(fallbackUrl)
                val fallbackIntent = Intent(Intent.ACTION_VIEW, fallbackUri)
                fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(fallbackIntent)
            } catch (e: Exception) {
                Log.e("TelegramValidation", "Failed to open Telegram fallback: ${e.message}")
            }
        }
    }
}

@Composable
fun TelegramValidationDialog(
    botUsername: String,
    onDismiss: () -> Unit,
    onValidationSuccess: (String) -> Unit,
    telegramValidation: TelegramValidation,
    settingsRepository: SettingsRepository,
    onValidationStarted: ((String) -> Unit) -> Unit
) {
    val context = LocalContext.current
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isValidating by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isValidating) onDismiss() },
        title = { Text(stringResource(R.string.telegram_validation_title)) },
        text = {
            Column {
                Text(stringResource(R.string.telegram_validation_initial_message, botUsername))
                Spacer(modifier = Modifier.height(8.dp))
                if (isValidating) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.waiting_for_validation))
                }
                errorMessage?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = it,
                        color = MaterialTheme.colors.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    telegramValidation.openTelegramChat(botUsername, "/start")
                    isValidating = true
                    onValidationStarted { userId ->
                        onValidationSuccess(userId)
                        isValidating = false
                    }
                    telegramValidation.fetchUserId(
                        onResult = { userId ->
                            onValidationSuccess(userId)
                            isValidating = false
                        },
                        onError = { error ->
                            errorMessage = error
                            isValidating = false
                        }
                    )
                },
                enabled = !isValidating
            ) {
                Text(stringResource(R.string.open_telegram_with_start))
            }
        },
        dismissButton = {
            Button(
                onClick = { if (!isValidating) onDismiss() },
                enabled = !isValidating
            ) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}
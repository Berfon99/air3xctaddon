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
    private var validationStartTime: Long = 0

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

        // Only clear on first attempt and set validation start time
        if (retryCount == 0) {
            settingsRepository.clearUserId()
            settingsRepository.clearTelegramValidated()
            validationStartTime = System.currentTimeMillis() / 1000
            Log.d("TelegramValidation", "Starting new validation session at: $validationStartTime")
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

                    for (i in 0 until updates.length()) {
                        val update = updates.getJSONObject(i)
                        val updateId = update.getLong("update_id")
                        latestUpdateId = maxOf(latestUpdateId ?: updateId, updateId)

                        if (update.has("message")) {
                            val message = update.getJSONObject("message")
                            val messageDate = message.getLong("date")

                            // Only process messages sent AFTER validation started
                            if (messageDate < validationStartTime) {
                                Log.d("TelegramValidation", "Skipping old message from: $messageDate (validation started: $validationStartTime)")
                                continue
                            }

                            val chat = message.getJSONObject("chat")
                            val chatType = chat.getString("type")
                            if (chatType == "private" && message.has("text") && message.getString("text") == "/start") {
                                val user = message.getJSONObject("from")
                                val userId = user.getLong("id").toString()
                                val username = user.optString("username", "")
                                val firstName = user.optString("first_name", "")

                                Log.d("TelegramValidation", "Found valid /start from user: $userId (username: $username, name: $firstName) at: $messageDate")

                                settingsRepository.saveUserId(userId)
                                settingsRepository.setTelegramValidated(true)
                                onResult(userId)
                                response.close()
                                return
                            }
                        }
                    }

                    // No valid /start found, retry with the latest update ID as offset
                    Log.d("TelegramValidation", "No valid /start found in this batch, retrying...")
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

        // Try multiple approaches in order of preference
        val approaches = if (prefillText != null) {
            listOf(
                // Approach 1: Direct resolve with start parameter (most reliable for bots)
                "tg://resolve?domain=$cleanUsername&start=${Uri.encode(prefillText.removePrefix("/"))}",
                // Approach 2: Message with text
                "tg://msg?to=$cleanUsername&text=${Uri.encode(prefillText)}",
                // Approach 3: Simple resolve
                "tg://resolve?domain=$cleanUsername"
            )
        } else {
            listOf(
                // Simple resolve for no prefill
                "tg://resolve?domain=$cleanUsername"
            )
        }

        var success = false

        for (uriString in approaches) {
            try {
                Log.d("TelegramValidation", "Trying Telegram URI: $uriString")
                val uri = Uri.parse(uriString)
                val intent = Intent(Intent.ACTION_VIEW, uri)
                intent.setPackage(context.getString(R.string.telegram_package_name))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                // Check if Telegram can handle this intent
                val packageManager = context.packageManager
                if (intent.resolveActivity(packageManager) != null) {
                    context.startActivity(intent)
                    Log.d("TelegramValidation", "Successfully opened Telegram with: $uriString")
                    success = true
                    break
                }
            } catch (e: Exception) {
                Log.w("TelegramValidation", "Failed to open Telegram with $uriString: ${e.message}")
                continue
            }
        }

        // If all deep link approaches failed, try web fallback
        if (!success) {
            Log.w("TelegramValidation", "All Telegram deep links failed, trying web fallback")
            try {
                val fallbackUrl = if (prefillText != null) {
                    "https://t.me/$cleanUsername?start=${Uri.encode(prefillText.removePrefix("/"))}"
                } else {
                    "https://t.me/$cleanUsername"
                }
                Log.d("TelegramValidation", "Trying web fallback: $fallbackUrl")
                val fallbackUri = Uri.parse(fallbackUrl)
                val fallbackIntent = Intent(Intent.ACTION_VIEW, fallbackUri)
                fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(fallbackIntent)
                Log.d("TelegramValidation", "Opened web fallback successfully")
            } catch (e: Exception) {
                Log.e("TelegramValidation", "All approaches failed: ${e.message}")
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
    var hasAttemptedValidation by remember { mutableStateOf(false) }

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
                if (!isValidating && hasAttemptedValidation && settingsRepository.getUserId().isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.start_command_required),
                        color = MaterialTheme.colors.error
                    )
                }
            }
        },
        confirmButton = {
            if (isValidating) {
                // Show "Stop" button when validating
                Button(
                    onClick = {
                        isValidating = false
                        errorMessage = "Validation stopped by user"
                        settingsRepository.clearUserId()
                        settingsRepository.clearTelegramValidated()
                    }
                ) {
                    Text("Stop")
                }
            } else {
                // Show "Open Telegram" button when not validating
                Button(
                    onClick = {
                        hasAttemptedValidation = true
                        isValidating = true
                        errorMessage = null

                        // Clear any existing state before starting new validation
                        settingsRepository.clearUserId()
                        settingsRepository.clearTelegramValidated()

                        telegramValidation.openTelegramChat(botUsername, "/start")
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
                    }
                ) {
                    Text(stringResource(R.string.open_telegram_with_start))
                }
            }
        },
        dismissButton = {
            Button(
                onClick = {
                    if (!isValidating) {
                        // Clear user ID if validation is incomplete
                        settingsRepository.clearUserId()
                        settingsRepository.clearTelegramValidated()
                        onDismiss()
                    }
                },
                enabled = !isValidating
            ) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}
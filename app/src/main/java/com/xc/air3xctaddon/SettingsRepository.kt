package com.xc.air3xctaddon

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object SettingsRepository {
    private lateinit var prefs: SharedPreferences
    private val gson = Gson()

    fun initialize(context: Context) {
        prefs = context.getSharedPreferences("air3xctaddon_prefs", Context.MODE_PRIVATE)
    }

    fun savePilotName(pilotName: String) {
        prefs.edit().putString("pilot_name", pilotName).apply()
        Log.d("SettingsRepository", "Saved pilotName: $pilotName")
    }

    fun getPilotName(): String? {
        val pilotName = prefs.getString("pilot_name", null)
        Log.d("SettingsRepository", "Retrieved pilotName: $pilotName")
        return pilotName
    }

    fun clearPilotName() {
        prefs.edit().remove("pilot_name").apply()
        Log.d("SettingsRepository", "Cleared pilotName")
    }

    fun saveUserId(userId: String) {
        prefs.edit().putString("user_id", userId).apply()
        Log.d("SettingsRepository", "Saved userId: $userId")
    }

    fun getUserId(): String? {
        val userId = prefs.getString("user_id", null)
        Log.d("SettingsRepository", "Retrieved userId: $userId")
        return userId
    }

    fun clearUserId() {
        prefs.edit().remove("user_id").apply()
        Log.d("SettingsRepository", "Cleared userId")
    }

    fun saveChats(chats: List<TelegramChat>) {
        val json = gson.toJson(chats)
        prefs.edit().putString("cached_chats", json).apply()
        Log.d("SettingsRepository", "Saved chats: $json")
    }

    fun getCachedChats(): List<TelegramChat> {
        val json = prefs.getString("cached_chats", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<Array<TelegramChat>>() {}.type
            val chatsArray = gson.fromJson<Array<TelegramChat>>(json, type)
            chatsArray.toList()
        } catch (e: Exception) {
            clearCachedChats()
            Log.e("SettingsRepository", "Failed to parse cached chats: ${e.message}")
            emptyList()
        }
    }

    fun clearCachedChats() {
        prefs.edit().remove("cached_chats").apply()
        Log.d("SettingsRepository", "Cleared cached chats")
    }

    fun setTelegramValidated(isValidated: Boolean) {
        prefs.edit().putBoolean("telegram_validated", isValidated).apply()
        Log.d("SettingsRepository", "Set telegram_validated: $isValidated")
    }

    fun isTelegramValidated(): Boolean {
        val isValidated = prefs.getBoolean("telegram_validated", false)
        Log.d("SettingsRepository", "Retrieved telegram_validated: $isValidated")
        return isValidated
    }

    fun clearTelegramValidated() {
        prefs.edit().remove("telegram_validated").apply()
        Log.d("SettingsRepository", "Cleared telegram_validated")
    }

    fun debugLogUserId(context: String) {
        val userId = getUserId()
        Log.d("SettingsRepository", "$context - Current userId: $userId")
    }
}
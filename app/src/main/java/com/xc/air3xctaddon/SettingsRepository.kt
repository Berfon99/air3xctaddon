package com.xc.air3xctaddon

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("air3xctaddon_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun savePilotName(pilotName: String) {
        prefs.edit { putString("pilot_name", pilotName) }
    }

    fun getPilotName(): String? {
        return prefs.getString("pilot_name", null)
    }

    fun clearPilotName() {
        prefs.edit { remove("pilot_name") }
    }

    fun saveUserId(userId: String) {
        prefs.edit { putString("user_id", userId) }
    }

    fun getUserId(): String? {
        return prefs.getString("user_id", null)
    }

    fun clearUserId() {
        prefs.edit { remove("user_id") }
    }

    fun saveChats(chats: List<TelegramChat>) {
        val json = gson.toJson(chats)
        prefs.edit().putString("cached_chats", json).apply()
    }

    fun getCachedChats(): List<TelegramChat> {
        val json = prefs.getString("cached_chats", null) ?: return emptyList()
        return try {
            val chatsArray = gson.fromJson(json, Array<TelegramChat>::class.java)
            chatsArray?.toList() ?: emptyList()
        } catch (e: Exception) {
            clearCachedChats()
            emptyList()
        }
    }

    fun clearCachedChats() {
        prefs.edit().remove("cached_chats").apply()
    }

    fun setTelegramValidated(isValidated: Boolean) {
        prefs.edit { putBoolean("telegram_validated", isValidated) }
    }

    fun isTelegramValidated(): Boolean {
        return prefs.getBoolean("telegram_validated", false)
    }

    fun clearTelegramValidated() {
        prefs.edit { remove("telegram_validated") }
    }
}
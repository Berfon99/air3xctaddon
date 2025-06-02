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

    fun saveChats(chats: List<TelegramChat>) {
        val json = gson.toJson(chats)
        prefs.edit().putString("cached_chats", json).apply()
    }

    fun getCachedChats(): List<TelegramChat> {
        val json = prefs.getString("cached_chats", null) ?: return emptyList()

        return try {
            // Use Array instead of List to avoid TypeToken
            val chatsArray = gson.fromJson(json, Array<TelegramChat>::class.java)
            chatsArray?.toList() ?: emptyList()
        } catch (e: Exception) {
            // If parsing fails, clear cache and return empty list
            clearCachedChats()
            emptyList()
        }
    }

    fun clearCachedChats() {
        prefs.edit().remove("cached_chats").apply()
    }
}
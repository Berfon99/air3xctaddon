package com.xc.air3xctaddon

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

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
        val type = object : TypeToken<List<TelegramChat>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    fun clearCachedChats() {
        prefs.edit().remove("cached_chats").apply()
    }
}
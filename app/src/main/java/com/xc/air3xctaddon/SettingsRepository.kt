package com.xc.air3xctaddon

import android.content.Context
import androidx.core.content.edit

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("air3xctaddon_prefs", Context.MODE_PRIVATE)

    fun savePilotName(pilotName: String) {
        prefs.edit { putString("pilot_name", pilotName) }
    }

    fun getPilotName(): String? {
        return prefs.getString("pilot_name", null)
    }

    fun clearPilotName() {
        prefs.edit { remove("pilot_name") }
    }
}
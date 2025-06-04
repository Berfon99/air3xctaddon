package com.xc.air3xctaddon

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.util.Log
import com.xc.air3xctaddon.SettingsRepository
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.io.IOException


fun isOnline(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    return connectivityManager.activeNetwork != null
}
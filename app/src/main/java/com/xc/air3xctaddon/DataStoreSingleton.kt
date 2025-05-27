package com.xc.air3xctaddon

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

object DataStoreSingleton {
    private const val DATASTORE_NAME = "air3xct_settings"
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = DATASTORE_NAME)
    private var appContext: Context? = null

    fun initialize(context: Context) {
        if (appContext == null) {
            synchronized(this) {
                if (appContext == null) {
                    appContext = context.applicationContext
                }
            }
        }
    }

    fun getDataStore(): DataStore<Preferences> {
        return appContext?.dataStore
            ?: throw IllegalStateException("DataStore not initialized. Call initialize(context) first.")
    }
}
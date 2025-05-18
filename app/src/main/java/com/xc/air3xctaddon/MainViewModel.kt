package com.xc.air3xctaddon

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val dao = db.eventConfigDao()
    private val _configs = MutableStateFlow<List<EventConfig>>(emptyList())
    val configs: StateFlow<List<EventConfig>> = _configs.asStateFlow()

    companion object {
        // List of all XCTrack events for sound mapping
        private val ALL_EVENTS = listOf(
            "BATTERY50", "BATTERY40", "BATTERY30", "BATTERY20", "BATTERY10",
            "BATTERY5", "BATTERY_CHARGING", "BATTERY_DISCHARGING",
            "TAKEOFF", "LANDING", "START_THERMALING", "STOP_THERMALING",
            "COMP_SSS_CROSSED", "COMP_TURNPOINT_CROSSED", "COMP_ESS_CROSSED",
            "COMP_GOAL_CROSSED", "SYSTEM_GPS_OK", "AIRSPACE_CROSSED",
            "AIRSPACE_RED_WARN", "AIRSPACE_ORANGE_WARN", "BT_OK", "BT_KO",
            "LIVETRACK_MESSAGE", "AIRSPACE_CROSSED_SOON", "AIRSPACE_OBSTACLE",
            "CALL_REJECTED", "COMP_TURNPOINT_PREV", "LIVETRACK_ENABLED",
            "TEST", "_LANDING_CONFIRMATION_NEEDED", "BUTTON_CLICK"
        )
    }

    init {
        viewModelScope.launch {
            try {
                // Check if database is empty
                val currentConfigs = dao.getAllConfigsSync()
                Log.d("MainViewModel", "Initial configs: $currentConfigs")
                if (currentConfigs.isEmpty()) {
                    // Add a default configuration
                    val defaultEvent = ALL_EVENTS.firstOrNull()
                    if (defaultEvent != null) {
                        val defaultConfig = EventConfig(
                            id = 0,
                            event = defaultEvent,
                            soundFile = "beep.mp3",
                            volumeType = VolumeType.SYSTEM,
                            volumePercentage = 100,
                            playCount = 1,
                            position = 0
                        )
                        dao.insert(defaultConfig)
                        Log.d("MainViewModel", "Added default config: $defaultEvent")
                    } else {
                        Log.w("MainViewModel", "No events available for default config")
                    }
                }

                // Collect configurations
                dao.getAllConfigs().collect { configs ->
                    _configs.value = configs
                    Log.d("MainViewModel", "Configs updated: ${configs.map { it.event }}")
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error initializing configs", e)
            }
        }
    }

    fun addConfig(event: String, soundFile: String, volumeType: VolumeType, volumePercentage: Int, playCount: Int) {
        viewModelScope.launch {
            try {
                val position = _configs.value.size
                val config = EventConfig(
                    id = 0,
                    event = event,
                    soundFile = soundFile,
                    volumeType = volumeType,
                    volumePercentage = volumePercentage,
                    playCount = playCount,
                    position = position
                )
                dao.insert(config)
                Log.d("MainViewModel", "Added config for event: $event")
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error adding config", e)
            }
        }
    }

    fun updateConfig(config: EventConfig) {
        viewModelScope.launch {
            try {
                dao.update(config)
                Log.d("MainViewModel", "Updated config: ${config.event}")
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error updating config", e)
            }
        }
    }

    fun deleteConfig(config: EventConfig) {
        viewModelScope.launch {
            try {
                dao.delete(config)
                _configs.value.forEachIndexed { index, cfg ->
                    if (cfg.position != index) {
                        dao.updatePosition(cfg.id, index)
                    }
                }
                Log.d("MainViewModel", "Deleted config: ${config.event}")
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error deleting config", e)
            }
        }
    }

    fun reorderConfigs(from: Int, to: Int) {
        viewModelScope.launch {
            try {
                val list = _configs.value.toMutableList()
                val item = list.removeAt(from)
                list.add(to, item)
                list.forEachIndexed { index, config ->
                    if (config.position != index) {
                        dao.updatePosition(config.id, index)
                    }
                }
                Log.d("MainViewModel", "Reordered configs from $from to $to")
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error reordering configs", e)
            }
        }
    }

    fun getAvailableEvents(): List<String> {
        val usedEvents = _configs.value.map { it.event }
        val availableEvents = ALL_EVENTS.filter { it !in usedEvents }
        Log.d("MainViewModel", "Available events: $availableEvents")
        return availableEvents
    }
}
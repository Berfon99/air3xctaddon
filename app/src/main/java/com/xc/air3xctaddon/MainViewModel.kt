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

    sealed class EventItem {
        data class Category(val name: String) : EventItem()
        data class Event(val name: String) : EventItem()
    }

    companion object {
        private val CATEGORIZED_EVENTS = listOf(
            EventItem.Category("Battery"),
            EventItem.Event("BATTERY50"),
            EventItem.Event("BATTERY40"),
            EventItem.Event("BATTERY30"),
            EventItem.Event("BATTERY20"),
            EventItem.Event("BATTERY10"),
            EventItem.Event("BATTERY5"),
            EventItem.Event("BATTERY_CHARGING"),
            EventItem.Event("BATTERY_DISCHARGING"),
            EventItem.Category("Flight"),
            EventItem.Event("TAKEOFF"),
            EventItem.Event("LANDING"),
            EventItem.Event("_LANDING_CONFIRMATION_NEEDED"),
            EventItem.Event("START_THERMALING"),
            EventItem.Event("STOP_THERMALING"),
            EventItem.Category("Competition"),
            EventItem.Event("COMP_SSS_CROSSED"),
            EventItem.Event("COMP_TURNPOINT_CROSSED"),
            EventItem.Event("COMP_ESS_CROSSED"),
            EventItem.Event("COMP_GOAL_CROSSED"),
            EventItem.Event("COMP_TURNPOINT_PREV"),
            EventItem.Category("Airspace"),
            EventItem.Event("AIRSPACE_CROSSED"),
            EventItem.Event("AIRSPACE_RED_WARN"),
            EventItem.Event("AIRSPACE_ORANGE_WARN"),
            EventItem.Event("AIRSPACE_CROSSED_SOON"),
            EventItem.Event("AIRSPACE_OBSTACLE"),
            EventItem.Category("Others"),
            EventItem.Event("LIVETRACK_MESSAGE"),
            EventItem.Event("LIVETRACK_ENABLED"),
            EventItem.Event("BUTTON_CLICK"),
            EventItem.Event("CALL_REJECTED"),
            EventItem.Event("SYSTEM_GPS_OK"),
            EventItem.Event("BT_OK"),
            EventItem.Event("BT_KO"),
            EventItem.Event("TEST")
        )

        private val ALL_EVENTS = CATEGORIZED_EVENTS
            .filterIsInstance<EventItem.Event>()
            .map { it.name }
    }

    init {
        viewModelScope.launch {
            try {
                val currentConfigs = dao.getAllConfigsSync()
                Log.d("MainViewModel", "Initial configs: $currentConfigs")
                if (currentConfigs.isEmpty()) {
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
                Log.e("MainViewModel", "Expecting binder but got null!")
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

    fun getAvailableEvents(): List<EventItem> {
        val usedEvents = _configs.value.map { it.event }.toSet()
        val availableEvents = CATEGORIZED_EVENTS.filter { item ->
            when (item) {
                is EventItem.Category -> true
                is EventItem.Event -> item.name !in usedEvents
            }
        }
        Log.d("MainViewModel", "Available events: ${availableEvents.filterIsInstance<EventItem.Event>().map { it.name }}")
        return availableEvents
    }
}
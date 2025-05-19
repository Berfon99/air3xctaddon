package com.xc.air3xctaddon

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val configDao = db.eventConfigDao()
    private val eventDao = db.eventDao()

    val configs: StateFlow<List<EventConfig>> = configDao.getAllConfigs()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val events: StateFlow<List<EventItem>> = eventDao.getAllEvents()
        .map { entities ->
            entities.map { entity ->
                when (entity.type) {
                    "category" -> EventItem.Category(entity.name)
                    "event" -> EventItem.Event(entity.name)
                    else -> throw IllegalArgumentException("Unknown event type: ${entity.type}")
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

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
            // Initialize configs
            val currentConfigs = configDao.getAllConfigsSync()
            if (currentConfigs.isEmpty()) {
                val defaultEvent = ALL_EVENTS.firstOrNull()
                if (defaultEvent != null) {
                    val defaultConfig = EventConfig(
                        id = 0,
                        event = defaultEvent,
                        soundFile = "Goodresult.mp3",
                        volumeType = VolumeType.SYSTEM,
                        volumePercentage = 100,
                        playCount = 1,
                        position = 0
                    )
                    configDao.insert(defaultConfig)
                    Log.d("MainViewModel", "Added default config: $defaultEvent")
                } else {
                    Log.w("MainViewModel", "No events available for default config")
                }
            }

            // Initialize events if empty
            val currentEvents = eventDao.getAllEvents().first()
            if (currentEvents.isEmpty()) {
                CATEGORIZED_EVENTS.forEach { item ->
                    val entity = when (item) {
                        is EventItem.Category -> EventEntity(type = "category", name = item.name)
                        is EventItem.Event -> EventEntity(type = "event", name = item.name)
                    }
                    eventDao.insert(entity)
                }
                Log.d("MainViewModel", "Initialized events with CATEGORIZED_EVENTS")
            }
        }
    }

    fun addConfig(event: String, soundFile: String, volumeType: VolumeType, volumePercentage: Int, playCount: Int) {
        viewModelScope.launch {
            try {
                val position = configs.value.size
                val config = EventConfig(
                    id = 0,
                    event = event,
                    soundFile = soundFile,
                    volumeType = volumeType,
                    volumePercentage = volumePercentage,
                    playCount = playCount,
                    position = position
                )
                configDao.insert(config)
                Log.d("MainViewModel", "Added config for event: $event")
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error adding config", e)
            }
        }
    }

    fun updateConfig(config: EventConfig) {
        viewModelScope.launch {
            try {
                configDao.update(config)
                Log.d("MainViewModel", "Updated config: ${config.event}")
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error updating config", e)
            }
        }
    }

    fun deleteConfig(config: EventConfig) {
        viewModelScope.launch {
            try {
                configDao.delete(config)
                configs.value.forEachIndexed { index, cfg ->
                    if (cfg.position != index) {
                        configDao.updatePosition(cfg.id, index)
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
                val list = configs.value.toMutableList()
                val item = list.removeAt(from)
                list.add(to, item)
                list.forEachIndexed { index, config ->
                    if (config.position != index) {
                        configDao.updatePosition(config.id, index)
                    }
                }
                Log.d("MainViewModel", "Reordered configs from $from to $to")
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error reordering configs", e)
            }
        }
    }

    fun getAvailableEvents(): List<EventItem> {
        val usedEvents = configs.value.map { it.event }.toSet()
        val availableEvents = events.value.filter { item ->
            when (item) {
                is EventItem.Category -> true
                is EventItem.Event -> item.name !in usedEvents
            }
        }
        Log.d("MainViewModel", "Available events: ${availableEvents.filterIsInstance<EventItem.Event>().map { it.name }}")
        return availableEvents
    }

    fun addEvent(category: String, eventName: String) {
        viewModelScope.launch {
            // Check if event name already exists
            val existingEvent = events.value.any { it is EventItem.Event && it.name == eventName }
            if (existingEvent) {
                Log.w("MainViewModel", "Event '$eventName' already exists")
                return@launch
            }
            // Find category
            val categoryExists = events.value.any { it is EventItem.Category && it.name == category }
            if (categoryExists) {
                val newEvent = EventEntity(type = "event", name = eventName)
                eventDao.insert(newEvent)
                Log.d("MainViewModel", "Added event '$eventName' to category '$category'")
            } else {
                Log.w("MainViewModel", "Category '$category' not found")
            }
        }
    }
}
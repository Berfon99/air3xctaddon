package com.xc.air3xctaddon

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val configDao = AppDatabase.getDatabase(application).eventConfigDao()
    private val eventDao = AppDatabase.getDatabase(application).eventDao()

    private val _configs = MutableStateFlow<List<EventConfig>>(emptyList())
    val configs: StateFlow<List<EventConfig>> = _configs.asStateFlow()

    private val _events = MutableStateFlow<List<EventItem>>(emptyList())
    val events: StateFlow<List<EventItem>> = _events.asStateFlow()

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
    }

    init {
        viewModelScope.launch {
            // Load configs
            configDao.getAllConfigs().collect { configs ->
                _configs.value = configs
                Log.d("MainViewModel", "Loaded configs: ${configs.size}")
            }
        }
        viewModelScope.launch {
            // Initialize with CATEGORIZED_EVENTS
            _events.value = CATEGORIZED_EVENTS
            Log.d("MainViewModel", "Initialized with CATEGORIZED_EVENTS: ${CATEGORIZED_EVENTS.size}, Categories: ${CATEGORIZED_EVENTS.filterIsInstance<EventItem.Category>().size}")

            // Merge database events
            eventDao.getAllEvents().collect { dbEvents ->
                val updatedItems = mutableListOf<EventItem>()
                var currentCategory: String? = null

                // Add predefined categories and events
                CATEGORIZED_EVENTS.forEach { item ->
                    if (item is EventItem.Category) {
                        currentCategory = item.name
                        updatedItems.add(item)
                    } else if (item is EventItem.Event && dbEvents.none { it.type == "event" && it.name == item.name }) {
                        updatedItems.add(item)
                    }
                }

                // Add custom events from database under their categories
                dbEvents.filter { it.type == "event" }.forEach { dbEvent ->
                    if (dbEvent.name !in CATEGORIZED_EVENTS.filterIsInstance<EventItem.Event>().map { it.name }) {
                        val targetCategory = dbEvent.category ?: "Others"
                        val insertIndex = updatedItems.indexOfFirst { it is EventItem.Category && it.name == targetCategory }
                            .takeIf { it >= 0 }?.let { it + 1 } ?: updatedItems.size
                        updatedItems.add(insertIndex, EventItem.Event(dbEvent.name))
                    }
                }

                _events.value = updatedItems
                Log.d("MainViewModel", "Updated events: ${updatedItems.size}, Categories: ${updatedItems.filterIsInstance<EventItem.Category>().size}, DB events: ${dbEvents.size}")
                Log.d("MainViewModel", "Event list: ${updatedItems.joinToString { when (it) { is EventItem.Category -> "Category: ${it.name}"; is EventItem.Event -> "Event: ${it.name}" } }}")
            }
        }
    }

    fun getAvailableEvents(): List<EventItem> {
        val usedEvents = _configs.value.map { it.event }.toSet()
        val available = _events.value.filter { item ->
            when (item) {
                is EventItem.Category -> true
                is EventItem.Event -> item.name !in usedEvents
            }
        }
        Log.d("MainViewModel", "Available events: ${available.size}, Categories: ${available.filterIsInstance<EventItem.Category>().size}")
        return available
    }

    fun addEvent(category: String, eventName: String) {
        viewModelScope.launch {
            if (_events.value.any { it is EventItem.Event && it.name == eventName }) {
                Log.w("MainViewModel", "Event '$eventName' already exists, skipping")
                return@launch
            }
            val newEvent = EventEntity(type = "event", name = eventName, category = category)
            eventDao.insert(newEvent)
            Log.d("MainViewModel", "Added event '$eventName' to category '$category'")
        }
    }

    fun addConfig(event: String, soundFile: String, volumeType: VolumeType, volumePercentage: Int, playCount: Int) {
        viewModelScope.launch {
            val maxPosition = _configs.value.maxOfOrNull { it.position } ?: -1
            val newConfig = EventConfig(
                id = UUID.randomUUID().hashCode(),
                event = event,
                soundFile = soundFile,
                volumeType = volumeType,
                volumePercentage = volumePercentage,
                playCount = playCount,
                position = maxPosition + 1
            )
            configDao.insert(newConfig)
            Log.d("MainViewModel", "Added config: event=$event, soundFile=$soundFile")
        }
    }

    fun updateConfig(config: EventConfig) {
        viewModelScope.launch {
            configDao.update(config)
            Log.d("MainViewModel", "Updated config: id=${config.id}")
        }
    }

    fun deleteConfig(config: EventConfig) {
        viewModelScope.launch {
            configDao.delete(config)
            _configs.value.filter { it.id != config.id }
                .sortedBy { it.position }
                .forEachIndexed { index, eventConfig ->
                    configDao.updatePosition(eventConfig.id, index)
                }
            Log.d("MainViewModel", "Deleted config: id=${config.id}")
        }
    }

    fun reorderConfigs(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            val configs = _configs.value.toMutableList()
            if (fromIndex in configs.indices && toIndex in configs.indices) {
                val item = configs.removeAt(fromIndex)
                configs.add(toIndex, item)
                configs.forEachIndexed { index, config ->
                    configDao.updatePosition(config.id, index)
                }
                _configs.value = configs
                Log.d("MainViewModel", "Reordered configs: from=$fromIndex, to=$toIndex")
            }
        }
    }
}
package com.xc.air3xctaddon

import android.app.Application
import android.util.Log
import android.view.KeyEvent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val configDao = AppDatabase.getDatabase(application).eventConfigDao()
    private val eventDao = AppDatabase.getDatabase(application).eventDao()
    private val taskDao = AppDatabase.getDatabase(application).taskDao()
    private val context = application.applicationContext

    private val _configs = MutableStateFlow<List<EventConfig>>(emptyList())
    val configs: StateFlow<List<EventConfig>> = _configs.asStateFlow()

    private val _events = MutableStateFlow<List<EventItem>>(emptyList())
    val events: StateFlow<List<EventItem>> = _events.asStateFlow()

    sealed class EventItem {
        data class Category(val name: String, val level: Int = 0) : EventItem()
        data class Event(
            val name: String,
            val displayName: String = name,
            val level: Int = 0
        ) : EventItem()
    }

    companion object {
        private val XCTRACK_EVENTS = mutableListOf<EventItem>().apply {
            add(EventItem.Category("Battery"))
            add(EventItem.Event("BATTERY50"))
            add(EventItem.Event("BATTERY40"))
            add(EventItem.Event("BATTERY30"))
            add(EventItem.Event("BATTERY20"))
            add(EventItem.Event("BATTERY10"))
            add(EventItem.Event("BATTERY5"))
            add(EventItem.Event("BATTERY_CHARGING"))
            add(EventItem.Event("BATTERY_DISCHARGING"))
            add(EventItem.Category("Flight"))
            add(EventItem.Event("TAKEOFF"))
            add(EventItem.Event("LANDING"))
            add(EventItem.Event("_LANDING_CONFIRMATION_NEEDED"))
            add(EventItem.Event("START_THERMALING"))
            add(EventItem.Event("STOP_THERMALING"))
            add(EventItem.Category("Competition"))
            add(EventItem.Event("COMP_SSS_CROSSED"))
            add(EventItem.Event("COMP_TURNPOINT_CROSSED"))
            add(EventItem.Event("COMP_ESS_CROSSED"))
            add(EventItem.Event("COMP_GOAL_CROSSED"))
            add(EventItem.Event("COMP_TURNPOINT_PREV"))
            add(EventItem.Category("Airspace"))
            add(EventItem.Event("AIRSPACE_CROSSED"))
            add(EventItem.Event("AIRSPACE_RED_WARN"))
            add(EventItem.Event("AIRSPACE_ORANGE_WARN"))
            add(EventItem.Event("AIRSPACE_CROSSED_SOON"))
            add(EventItem.Event("AIRSPACE_OBSTACLE"))
            add(EventItem.Category("Others"))
            add(EventItem.Event("LIVETRACK_MESSAGE"))
            add(EventItem.Event("LIVETRACK_ENABLED"))
            add(EventItem.Event("BUTTON_CLICK"))
            add(EventItem.Event("CALL_REJECTED"))
            add(EventItem.Event("SYSTEM_GPS_OK"))
            add(EventItem.Event("BT_OK"))
            add(EventItem.Event("BT_KO"))
            add(EventItem.Event("TEST"))
        }
    }

    init {
        DataStoreSingleton.initialize(context)
        viewModelScope.launch {
            configDao.getAllConfigs().collect { configs ->
                _configs.value = configs
                Log.d("MainViewModel", "Loaded configs: ${configs.size}")
            }
        }
        viewModelScope.launch {
            updateEvents()
            eventDao.getAllEvents().collect { dbEvents ->
                updateEvents(dbEvents)
            }
        }
    }

    private suspend fun updateEvents(dbEvents: List<EventEntity>? = null) {
        val actualDbEvents = dbEvents ?: eventDao.getAllEvents().first()
        val dataStore = DataStoreSingleton.getDataStore()
        val updatedItems = mutableListOf<EventItem>()

// Add XCTrack events
        updatedItems.add(EventItem.Category("XCTrack events", level = 0))
        var currentCategory: String? = null
        XCTRACK_EVENTS.forEach { item ->
            if (item is EventItem.Category) {
                currentCategory = item.name
                updatedItems.add(EventItem.Category(item.name, level = 1))
            } else if (item is EventItem.Event && actualDbEvents.none { it.type == "event" && it.name == item.name }) {
                updatedItems.add(EventItem.Event(item.name, level = 2))
            }
        }

// Add custom XCTrack events from database (not in XCTRACK_EVENTS, not BUTTON_)
        actualDbEvents.filter { it.type == "event" && !it.name.startsWith("BUTTON_") && it.name !in XCTRACK_EVENTS.filterIsInstance<EventItem.Event>().map { it.name } }
            .forEach { dbEvent ->
                val targetCategory = dbEvent.category ?: "Others"
                val insertIndex = updatedItems.indexOfFirst { it is EventItem.Category && it.name == targetCategory }
                    .takeIf { it >= 0 }?.let { it + 1 } ?: updatedItems.size
                updatedItems.add(insertIndex, EventItem.Event(dbEvent.name, level = 2))
            }

// Add Button events
        updatedItems.add(EventItem.Category("Button events", level = 0))
        actualDbEvents.filter { it.type == "event" && it.name.startsWith("BUTTON_") }
            .forEach { event ->
                val eventName = event.name
                val isChecked = dataStore.data.first()[booleanPreferencesKey("${eventName}_isChecked")] ?: false
                if (isChecked) {
                    val keyCode = eventName.removePrefix("BUTTON_").toIntOrNull()
                    val designation = getButtonDesignation(keyCode)
                    val comment = dataStore.data.first()[stringPreferencesKey("${eventName}_comment")] ?: ""

                    val displayName = buildString {
                        append(eventName)
                        if (designation.isNotEmpty()) {
                            append(" ($designation)")
                        }
                        if (comment.isNotEmpty()) {
                            append(" - $comment")
                        }
                    }

                    updatedItems.add(EventItem.Event(eventName, displayName, level = 2))
                    Log.d("MainViewModel", "Button event: name=$eventName, designation=$designation, comment=$comment, displayName=$displayName")
                }
            }

        _events.value = updatedItems
        Log.d("MainViewModel", "Updated events: ${updatedItems.size}, Categories: ${updatedItems.filterIsInstance<EventItem.Category>().size}, DB events: ${actualDbEvents.size}")
    }

    private fun getButtonDesignation(keyCode: Int?): String {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> "Volume +"
            KeyEvent.KEYCODE_VOLUME_DOWN -> "Volume -"
            KeyEvent.KEYCODE_BACK -> "Back"
            KeyEvent.KEYCODE_MENU -> "Menu"
            KeyEvent.KEYCODE_POWER -> "Power"
            else -> if (keyCode != null) "Button $keyCode" else ""
        }
    }

    fun getAvailableEvents(): List<EventItem> {
        val available = _events.value
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
            updateEvents()
        }
    }

    fun deleteEvent(eventName: String) {
        viewModelScope.launch {
            val allEvents = eventDao.getAllEvents().first()
            val event = allEvents.find { it.name == eventName && it.type == "event" }
            if (event != null) {
                eventDao.delete(event)
                Log.d("MainViewModel", "Deleted event '$eventName'")
                updateEvents()
            } else {
                Log.w("MainViewModel", "Event '$eventName' not found for deletion")
            }
        }
    }

    fun addConfig(
        event: String,
        taskType: String,
        taskData: String,
        volumeType: VolumeType,
        volumePercentage: Int,
        playCount: Int,
        telegramChatId: String? = null,
        telegramGroupName: String? = null
    ) {
        viewModelScope.launch {
            val maxPosition = _configs.value.maxByOrNull { it.position }?.position ?: -1
            val newConfig = EventConfig(
                id = UUID.randomUUID().hashCode(),
                event = event,
                taskType = taskType,
                taskData = taskData,
                volumeType = volumeType,
                volumePercentage = volumePercentage,
                playCount = playCount,
                position = maxPosition + 1,
                telegramChatId = if (taskType == "SendTelegramPosition" || taskType == "SendTelegramMessage") telegramChatId else null,
                telegramGroupName = if (taskType == "SendTelegramPosition" || taskType == "SendTelegramMessage") telegramGroupName else null
            )
            configDao.insert(newConfig)
            Log.d("MainViewModel", "Added config: event=$event, taskType=$taskType, taskData=$taskData, telegramChatId=$telegramChatId, telegramGroupName=$telegramGroupName")
        }
    }

    fun updateConfig(config: EventConfig) {
        viewModelScope.launch {
            configDao.update(config)
            Log.d("MainViewModel", "Updated config: id=${config.id}, event=${config.event}, taskType=${config.taskType}, taskData=${config.taskData}, telegramChatId=${config.telegramChatId}, telegramGroupName=${config.telegramGroupName}")
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
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
    private val context = application.applicationContext

    private val _configs = MutableStateFlow<List<EventConfig>>(emptyList())
    val configs: StateFlow<List<EventConfig>> = _configs.asStateFlow()

    private val _events = MutableStateFlow<List<EventItem>>(emptyList())
    val events: StateFlow<List<EventItem>> = _events.asStateFlow()

    sealed class EventItem {
        data class Category(val name: String) : EventItem()
        data class Event(val name: String) : EventItem()
    }

    companion object {
        private val CATEGORIZED_EVENTS = mutableListOf<EventItem>().apply {
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
        viewModelScope.launch {
            configDao.getAllConfigs().collect { configs ->
                _configs.value = configs
                Log.d("MainViewModel", context.getString(R.string.log_loaded_configs, configs.size))
            }
        }
        viewModelScope.launch {
            _events.value = CATEGORIZED_EVENTS
            Log.d("MainViewModel", context.getString(
                R.string.log_initialized_events,
                CATEGORIZED_EVENTS.size,
                CATEGORIZED_EVENTS.filterIsInstance<EventItem.Category>().size
            ))

            eventDao.getAllEvents().collect { dbEvents ->
                val updatedItems = mutableListOf<EventItem>()
                var currentCategory: String? = null

                CATEGORIZED_EVENTS.forEach { item ->
                    if (item is EventItem.Category) {
                        currentCategory = item.name
                        updatedItems.add(item)
                    } else if (item is EventItem.Event && dbEvents.none { it.type == "event" && it.name == item.name }) {
                        updatedItems.add(item)
                    }
                }

                dbEvents.filter { it.type == "event" }.forEach { dbEvent ->
                    if (dbEvent.name !in CATEGORIZED_EVENTS.filterIsInstance<EventItem.Event>().map { it.name }) {
                        val targetCategory = dbEvent.category ?: context.getString(R.string.category_others)
                        val insertIndex = updatedItems.indexOfFirst { it is EventItem.Category && it.name == targetCategory }
                            .takeIf { it >= 0 }?.let { it + 1 } ?: updatedItems.size
                        updatedItems.add(insertIndex, EventItem.Event(dbEvent.name))
                    }
                }

                _events.value = updatedItems
                Log.d("MainViewModel", context.getString(
                    R.string.log_updated_events,
                    updatedItems.size,
                    updatedItems.filterIsInstance<EventItem.Category>().size,
                    dbEvents.size
                ))
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
        Log.d("MainViewModel", context.getString(
            R.string.log_available_events,
            available.size,
            available.filterIsInstance<EventItem.Category>().size
        ))
        return available
    }

    fun addEvent(category: String, eventName: String) {
        viewModelScope.launch {
            if (_events.value.any { it is EventItem.Event && it.name == eventName }) {
                Log.w("MainViewModel", context.getString(R.string.log_event_exists, eventName))
                return@launch
            }
            val newEvent = EventEntity(type = "event", name = eventName, category = category)
            eventDao.insert(newEvent)
            Log.d("MainViewModel", context.getString(R.string.log_added_event, eventName, category))
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
                telegramChatId = telegramChatId,
                telegramGroupName = telegramGroupName
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
            Log.d("MainViewModel", context.getString(R.string.log_deleted_config, config.id))
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
                Log.d("MainViewModel", context.getString(R.string.log_reordered_configs, fromIndex, toIndex))
            }
        }
    }
}
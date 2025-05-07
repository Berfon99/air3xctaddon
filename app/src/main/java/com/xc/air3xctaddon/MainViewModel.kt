package com.xc.air3xctaddon

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val db = Room.databaseBuilder(
        application,
        AppDatabase::class.java,
        "air3xctaddon-db"
    ).build()

    private val dao = db.eventConfigDao()
    private val _configs = MutableStateFlow<List<EventConfig>>(emptyList())
    val configs: StateFlow<List<EventConfig>> = _configs.asStateFlow()

    init {
        viewModelScope.launch {
            dao.getAllConfigs().collect { configs ->
                _configs.value = configs
            }
        }
    }

    fun addConfig(event: Event, soundFile: String, volumeType: VolumeType, volumePercentage: Int, playCount: Int) {
        viewModelScope.launch {
            val position = _configs.value.size
            val config = EventConfig(
                event = event,
                soundFile = soundFile,
                volumeType = volumeType,
                volumePercentage = volumePercentage,
                playCount = playCount,
                position = position
            )
            dao.insert(config)
        }
    }

    fun updateConfig(config: EventConfig) {
        viewModelScope.launch {
            dao.update(config)
        }
    }

    fun deleteConfig(config: EventConfig) {
        viewModelScope.launch {
            dao.delete(config)
            // Update positions
            _configs.value.forEachIndexed { index, cfg ->
                if (cfg.position != index) {
                    dao.updatePosition(cfg.id, index)
                }
            }
        }
    }

    fun reorderConfigs(from: Int, to: Int) {
        viewModelScope.launch {
            val list = _configs.value.toMutableList()
            val item = list.removeAt(from)
            list.add(to, item)
            list.forEachIndexed { index, config ->
                if (config.position != index) {
                    dao.updatePosition(config.id, index)
                }
            }
        }
    }

    fun getAvailableEvents(): List<Event> {
        val usedEvents = _configs.value.map { it.event }
        return Event.values().filter { it !in usedEvents }
    }
}
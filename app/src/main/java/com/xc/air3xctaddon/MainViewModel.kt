package com.xc.air3xctaddon

import android.app.Application
import android.util.Log
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
            try {
                // Vérifier si la base de données est vide
                val currentConfigs = dao.getAllConfigsSync()
                Log.d("MainViewModel", "Initial configs: $currentConfigs")
                if (currentConfigs.isEmpty()) {
                    // Ajouter une configuration par défaut
                    val defaultEvent = Event.values().firstOrNull()
                    if (defaultEvent != null) {
                        val defaultConfig = EventConfig(
                            id = 0, // Room générera automatiquement l'ID
                            event = defaultEvent,
                            soundFile = "beep.mp3",
                            volumeType = VolumeType.SYSTEM,
                            volumePercentage = 100,
                            playCount = 1,
                            position = 0
                        )
                        dao.insert(defaultConfig)
                        Log.d("MainViewModel", "Added default config: ${defaultConfig.event.name}")
                    } else {
                        Log.w("MainViewModel", "No events available for default config")
                    }
                }

                // Collecter les configurations
                dao.getAllConfigs().collect { configs ->
                    _configs.value = configs
                    Log.d("MainViewModel", "Configs updated: ${configs.map { it.event.name }}")
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error initializing configs", e)
            }
        }
    }

    fun addConfig(event: Event, soundFile: String, volumeType: VolumeType, volumePercentage: Int, playCount: Int) {
        viewModelScope.launch {
            try {
                val position = _configs.value.size
                val config = EventConfig(
                    id = 0, // Room générera automatiquement l'ID
                    event = event,
                    soundFile = soundFile,
                    volumeType = volumeType,
                    volumePercentage = volumePercentage,
                    playCount = playCount,
                    position = position
                )
                dao.insert(config)
                Log.d("MainViewModel", "Added config for event: ${event.name}")
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error adding config", e)
            }
        }
    }

    fun updateConfig(config: EventConfig) {
        viewModelScope.launch {
            try {
                dao.update(config)
                Log.d("MainViewModel", "Updated config: ${config.event.name}")
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error updating config", e)
            }
        }
    }

    fun deleteConfig(config: EventConfig) {
        viewModelScope.launch {
            try {
                dao.delete(config)
                // Update positions
                _configs.value.forEachIndexed { index, cfg ->
                    if (cfg.position != index) {
                        dao.updatePosition(cfg.id, index)
                    }
                }
                Log.d("MainViewModel", "Deleted config: ${config.event.name}")
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

    fun getAvailableEvents(): List<Event> {
        val usedEvents = _configs.value.map { it.event }
        val availableEvents = Event.values().filter { it !in usedEvents }
        Log.d("MainViewModel", "Available events: ${availableEvents.map { it.name }}")
        return availableEvents
    }
}
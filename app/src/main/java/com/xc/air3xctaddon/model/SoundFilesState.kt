package com.xc.air3xctaddon.model

sealed class SoundFilesState {
    object Loading : SoundFilesState()
    data class Success(val files: List<String>) : SoundFilesState()
    object Empty : SoundFilesState()
    data class Error(val message: String) : SoundFilesState()
}
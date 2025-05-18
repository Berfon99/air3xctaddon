package com.xc.air3xctaddon.utils

import android.content.res.AssetManager
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

private const val TAG = "SoundFilesUtils"
private const val ASSET_SOUNDS_PATH = "sounds" // Match lowercase folder name

fun AssetManager.copySoundFilesFromAssets(externalFilesDir: File?): Boolean {
    if (externalFilesDir == null) {
        Log.e(TAG, "External files directory is null")
        return false
    }

    val soundsDir = File(externalFilesDir, "Sounds")
    try {
        if (!soundsDir.exists() && !soundsDir.mkdirs()) {
            Log.e(TAG, "Failed to create sounds directory: ${soundsDir.absolutePath}")
            return false
        }
        Log.d(TAG, "Sounds directory: ${soundsDir.absolutePath}, exists: ${soundsDir.exists()}")

        // Check for existing files
        val existingFiles = soundsDir.listFiles()
            ?.map { it.name }
            ?.filter { it.endsWith(".mp3", true) || it.endsWith(".wav", true) }
            ?.sorted() ?: emptyList()
        Log.d(TAG, "Existing sound files: $existingFiles")
        if (existingFiles.isNotEmpty()) {
            Log.d(TAG, "Skipping copy, sound files already exist")
            return true // Return true since files exist
        }

        // List asset files
        val assetFiles = list(ASSET_SOUNDS_PATH)
            ?.filter { it.endsWith(".mp3", true) || it.endsWith(".wav", true) }
            ?.sorted() ?: emptyList()
        Log.d(TAG, "Sound files in assets/$ASSET_SOUNDS_PATH: $assetFiles")
        if (assetFiles.isEmpty()) {
            Log.w(TAG, "No .mp3 or .wav files found in assets/$ASSET_SOUNDS_PATH")
            return false
        }

        // Copy files
        assetFiles.forEach { fileName ->
            try {
                open("$ASSET_SOUNDS_PATH/$fileName").use { input ->
                    val outputFile = File(soundsDir, fileName)
                    FileOutputStream(outputFile).use { output ->
                        input.copyTo(output)
                    }
                    Log.d(TAG, "Copied sound file: $fileName to ${outputFile.absolutePath}")
                }
            } catch (e: IOException) {
                Log.e(TAG, "Failed to copy sound file: $fileName", e)
                return false
            }
        }

        // Log directory contents
        logDirectoryContents(soundsDir)
        return true
    } catch (e: Exception) {
        Log.e(TAG, "Error copying sound files from assets", e)
        return false
    }
}

fun logDirectoryContents(directory: File) {
    if (!directory.exists()) {
        Log.d(TAG, "Directory does not exist: ${directory.absolutePath}")
        return
    }

    val files = directory.listFiles()?.filter { it.isFile && it.canRead() } ?: emptyList()
    if (files.isEmpty()) {
        Log.d(TAG, "Directory is empty: ${directory.absolutePath}")
        return
    }

    Log.d(TAG, "Contents of ${directory.absolutePath}:")
    files.forEach { file ->
        Log.d(TAG, "  - ${file.name} (${file.length()} bytes, readable: ${file.canRead()})")
    }
}
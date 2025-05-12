package com.xc.air3xctaddon.utils

import android.content.res.AssetManager
import android.util.Log
import java.io.File
import java.io.FileOutputStream

fun AssetManager.copySoundFilesFromAssets(externalFilesDir: File?) {
    val soundsDir = File(externalFilesDir, "Sounds")
    try {
        soundsDir.mkdirs()
        Log.d("AssetUtils", "Sounds directory for assets: ${soundsDir.absolutePath}, exists: ${soundsDir.exists()}")
        val existingFiles = soundsDir.listFiles()?.map { it.name }?.filter { it.endsWith(".mp3") || it.endsWith(".wav") }?.sorted() ?: emptyList()
        Log.d("AssetUtils", "Existing sound files: $existingFiles")
        if (existingFiles.isNotEmpty()) {
            Log.d("AssetUtils", "Skipping copy, sound files already exist")
            return
        }
        val assetFiles = list("sounds")?.filter { it.endsWith(".mp3") || it.endsWith(".wav") }?.sorted() ?: emptyList()
        Log.d("AssetUtils", "Sound files in assets/sounds: $assetFiles")
        if (assetFiles.isEmpty()) {
            Log.w("AssetUtils", "No .mp3 or .wav files found in assets/sounds")
            return
        }
        assetFiles.forEach { fileName ->
            val inputStream = open("sounds/$fileName")
            val outputFile = File(soundsDir, fileName)
            FileOutputStream(outputFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
            inputStream.close()
            Log.d("AssetUtils", "Copied sound file: $fileName to ${outputFile.absolutePath}")
        }
    } catch (e: Exception) {
        Log.e("AssetUtils", "Error copying sound files from assets", e)
    }
}
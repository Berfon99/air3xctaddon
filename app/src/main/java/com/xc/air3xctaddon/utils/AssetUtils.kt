package com.xc.air3xctaddon.utils

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import com.xc.air3xctaddon.R
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

private const val TAG = "SoundFilesUtils"

fun AssetManager.copySoundFilesFromAssets(context: Context, externalFilesDir: File?): Boolean {
    if (externalFilesDir == null) {
        Log.e(TAG, "External files directory is null")
        return false
    }

    val soundsDir = externalFilesDir
    try {
        if (!soundsDir.exists() && !soundsDir.mkdirs()) {
            Log.e(TAG, "Failed to create Sounds directory: ${soundsDir.absolutePath}")
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
            Log.d(TAG, "Skipping copy as sound files already exist")
            return true // Return true since files exist
        }

        // List asset files
        val assetSoundsPath = context.getString(R.string.asset_sounds_path)
        val assetFiles = list(assetSoundsPath)
            ?.filter { it.endsWith(".mp3", true) || it.endsWith(".wav", true) }
            ?.sorted() ?: emptyList()
        Log.d(TAG, "Asset sound files in $assetSoundsPath: $assetFiles")
        if (assetFiles.isEmpty()) {
            Log.w(TAG, "No sound files found in assets path: $assetSoundsPath")
            return false
        }

        // Copy files
        assetFiles.forEach { fileName ->
            try {
                open("$assetSoundsPath/$fileName").use { input ->
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
        logDirectoryContents(context, soundsDir)
        return true
    } catch (e: Exception) {
        Log.e(TAG, "Error copying sound files", e)
        return false
    }
}

fun logDirectoryContents(context: Context, directory: File) {
    if (!directory.exists()) {
        Log.d(TAG, "Directory does not exist: ${directory.absolutePath}")
        return
    }

    val files = directory.listFiles()?.filter { it.isFile && it.canRead() } ?: emptyList()
    if (files.isEmpty()) {
        Log.d(TAG, "Directory is empty: ${directory.absolutePath}")
        return
    }

    Log.d(TAG, "Directory contents: ${directory.absolutePath}")
    files.forEach { file ->
        Log.d(TAG, "File: ${file.name}, size: ${file.length()}, readable: ${file.canRead()}")
    }
}
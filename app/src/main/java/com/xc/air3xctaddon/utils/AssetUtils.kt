package com.xc.air3xctaddon.utils

import android.content.res.AssetManager
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import android.content.Context
import java.io.IOException

private const val TAG = "SoundFilesUtils"

fun AssetManager.copySoundFilesFromAssets(externalDir: File?) {
    try {
        val soundsDir = File(externalDir, "Sounds").apply {
            if (!exists()) {
                mkdirs()
                Log.d(TAG, "Created sounds directory: $absolutePath")
            }
        }

        val internalSoundsDir = File(File(externalDir?.parent, "files"), "Sounds").apply {
            if (!exists()) {
                mkdirs()
                Log.d(TAG, "Created internal sounds directory: $absolutePath")
            }
        }

        val soundFiles = list("Sounds") ?: emptyArray()
        Log.d(TAG, "Found ${soundFiles.size} sound files in assets")

        for (fileName in soundFiles) {
            val externalFile = File(soundsDir, fileName)
            val internalFile = File(internalSoundsDir, fileName)

            // Copy to external storage
            if (!externalFile.exists()) {
                try {
                    open("Sounds/$fileName").use { input ->
                        FileOutputStream(externalFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.d(TAG, "Copied $fileName to external: ${externalFile.absolutePath}")
                } catch (e: IOException) {
                    Log.e(TAG, "Failed to copy $fileName to external location", e)
                }
            }

            // Copy to internal storage
            if (!internalFile.exists()) {
                try {
                    open("Sounds/$fileName").use { input ->
                        FileOutputStream(internalFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.d(TAG, "Copied $fileName to internal: ${internalFile.absolutePath}")
                } catch (e: IOException) {
                    Log.e(TAG, "Failed to copy $fileName to internal location", e)
                }
            }
        }

        // Log the contents of the directories for debugging
        logDirectoryContents(soundsDir)
        logDirectoryContents(internalSoundsDir)
    } catch (e: Exception) {
        Log.e(TAG, "Error copying sound files from assets", e)
    }
}

/**
 * Logs the contents of a directory for debugging
 */
fun logDirectoryContents(directory: File) {
    if (!directory.exists()) {
        Log.d(TAG, "Directory does not exist: ${directory.absolutePath}")
        return
    }

    val files = directory.listFiles()
    if (files == null || files.isEmpty()) {
        Log.d(TAG, "Directory is empty: ${directory.absolutePath}")
        return
    }

    Log.d(TAG, "Contents of ${directory.absolutePath}:")
    files.forEach { file ->
        Log.d(TAG, "  - ${file.name} (${file.length()} bytes, readable: ${file.canRead()})")
    }
}
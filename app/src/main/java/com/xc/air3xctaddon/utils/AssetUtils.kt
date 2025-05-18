package com.xc.air3xctaddon.utils

import android.content.res.AssetManager
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

private const val TAG = "SoundFilesUtils"
private const val ASSET_SOUNDS_PATH = "sounds" // Adjust to "sounds" if the folder name is lowercase

fun AssetManager.copySoundFilesFromAssets(destDir: File): Boolean {
    try {
        // Create destination directory if it doesn't exist
        if (!destDir.exists()) {
            if (!destDir.mkdirs()) {
                Log.e(TAG, "Failed to create directory: ${destDir.absolutePath}")
                return false
            }
            Log.d(TAG, "Created directory: ${destDir.absolutePath}")
        }

        // List sound files in assets
        val soundFiles = list(ASSET_SOUNDS_PATH) ?: emptyArray()
        if (soundFiles.isEmpty()) {
            Log.w(TAG, "No sound files found in assets/$ASSET_SOUNDS_PATH")
            return false
        }
        Log.d(TAG, "Found ${soundFiles.size} sound files in assets/$ASSET_SOUNDS_PATH")

        // Copy each file
        for (fileName in soundFiles) {
            val destFile = File(destDir, fileName)
            if (!destFile.exists()) {
                try {
                    open("$ASSET_SOUNDS_PATH/$fileName").use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.d(TAG, "Copied $fileName to ${destFile.absolutePath}")
                } catch (e: IOException) {
                    Log.e(TAG, "Failed to copy $fileName", e)
                    return false
                }
            } else {
                Log.d(TAG, "File $fileName already exists at ${destFile.absolutePath}")
            }
        }

        // Log directory contents
        logDirectoryContents(destDir)
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
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
        Log.e(TAG, context.getString(R.string.log_external_files_dir_null))
        return false
    }

    val soundsDir = externalFilesDir
    try {
        if (!soundsDir.exists() && !soundsDir.mkdirs()) {
            Log.e(TAG, context.getString(R.string.log_failed_create_sounds_dir, soundsDir.absolutePath))
            return false
        }
        Log.d(TAG, context.getString(R.string.log_sounds_dir_status, soundsDir.absolutePath, soundsDir.exists()))

        // Check for existing files
        val existingFiles = soundsDir.listFiles()
            ?.map { it.name }
            ?.filter { it.endsWith(".mp3", true) || it.endsWith(".wav", true) }
            ?.sorted() ?: emptyList()
        Log.d(TAG, context.getString(R.string.log_existing_sound_files_list, existingFiles.toString()))
        if (existingFiles.isNotEmpty()) {
            Log.d(TAG, context.getString(R.string.log_skip_copy_files_exist))
            return true // Return true since files exist
        }

        // List asset files
        val assetSoundsPath = context.getString(R.string.asset_sounds_path)
        val assetFiles = list(assetSoundsPath)
            ?.filter { it.endsWith(".mp3", true) || it.endsWith(".wav", true) }
            ?.sorted() ?: emptyList()
        Log.d(TAG, context.getString(R.string.log_asset_sound_files, assetSoundsPath, assetFiles.toString()))
        if (assetFiles.isEmpty()) {
            Log.w(TAG, context.getString(R.string.log_no_sound_files_in_assets_path, assetSoundsPath))
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
                    Log.d(TAG, context.getString(R.string.log_copied_sound_file_to, fileName, outputFile.absolutePath))
                }
            } catch (e: IOException) {
                Log.e(TAG, context.getString(R.string.log_failed_copy_sound_file, fileName), e)
                return false
            }
        }

        // Log directory contents
        logDirectoryContents(context, soundsDir)
        return true
    } catch (e: Exception) {
        Log.e(TAG, context.getString(R.string.log_error_copying_sound_files), e)
        return false
    }
}

fun logDirectoryContents(context: Context, directory: File) {
    if (!directory.exists()) {
        Log.d(TAG, context.getString(R.string.log_dir_not_exists, directory.absolutePath))
        return
    }

    val files = directory.listFiles()?.filter { it.isFile && it.canRead() } ?: emptyList()
    if (files.isEmpty()) {
        Log.d(TAG, context.getString(R.string.log_dir_is_empty, directory.absolutePath))
        return
    }

    Log.d(TAG, context.getString(R.string.log_dir_contents_list, directory.absolutePath))
    files.forEach { file ->
        Log.d(TAG, context.getString(R.string.log_file_details, file.name, file.length(), file.canRead()))
    }
}
package com.xc.air3xctaddon

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.xc.air3xctaddon.ui.MainScreen
import com.xc.air3xctaddon.ui.theme.AIR3XCTAddonTheme
import com.xc.air3xctaddon.utils.copySoundFilesFromAssets

class MainActivity : ComponentActivity() {
    companion object {
        private const val REQUEST_STORAGE_PERMISSION = 100
        private const val REQUEST_MANAGE_STORAGE = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        assets.copySoundFilesFromAssets(getExternalFilesDir(null))
        setContent {
            AIR3XCTAddonTheme {
                MainScreen()
            }
        }
        requestPermissions()
    }

    private fun requestPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), REQUEST_STORAGE_PERMISSION)
        } else {
            checkManageStoragePermission()
        }
    }

    private fun checkManageStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = android.net.Uri.parse("package:$packageName")
                startActivityForResult(intent, REQUEST_MANAGE_STORAGE)
            } catch (e: Exception) {
                Log.e("MainActivity", "Error requesting MANAGE_EXTERNAL_STORAGE", e)
            }
        } else {
            startLogMonitorService()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_STORAGE_PERMISSION && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            checkManageStoragePermission()
        } else {
            Log.w("MainActivity", "Permissions not granted: ${permissions.joinToString()}")
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_MANAGE_STORAGE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                startLogMonitorService()
            } else {
                Log.w("MainActivity", "MANAGE_EXTERNAL_STORAGE not granted")
            }
        }
    }

    private fun startLogMonitorService() {
        val intent = Intent(this, LogMonitorService::class.java)
        startService(intent)
        Log.d("MainActivity", "LogMonitorService started")
    }
}
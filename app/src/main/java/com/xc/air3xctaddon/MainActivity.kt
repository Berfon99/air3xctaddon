package com.xc.air3xctaddon

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
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
            Log.d("MainActivity", "Requesting permissions: ${permissionsToRequest.joinToString()}")
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), REQUEST_STORAGE_PERMISSION)
        } else {
            checkManageStoragePermission()
        }
    }

    private fun checkManageStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val isGranted = Environment.isExternalStorageManager()
            val appOpsGranted = checkAppOpsPermission()
            Log.d("MainActivity", "MANAGE_EXTERNAL_STORAGE granted: $isGranted, AppOps granted: $appOpsGranted")
            Toast.makeText(
                this,
                "Accès à tous les fichiers : ${if (isGranted && appOpsGranted) "accordé" else "refusé"}",
                Toast.LENGTH_LONG
            ).show()
            if (!isGranted || !appOpsGranted) {
                try {
                    Log.d("MainActivity", "Requesting MANAGE_EXTERNAL_STORAGE")
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = android.net.Uri.parse("package:$packageName")
                    startActivityForResult(intent, REQUEST_MANAGE_STORAGE)
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error requesting MANAGE_EXTERNAL_STORAGE", e)
                    Toast.makeText(this, "Erreur lors de la demande d'accès aux fichiers", Toast.LENGTH_LONG).show()
                }
            } else {
                startLogMonitorService()
                // Vérifier l'accès au dossier
                checkDirectoryAccess()
            }
        } else {
            startLogMonitorService()
        }
    }

    private fun checkAppOpsPermission(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                "android:manage_external_storage",
                applicationInfo.uid,
                packageName
            )
        } else {
            appOps.checkOpNoThrow(
                "android:manage_external_storage",
                applicationInfo.uid,
                packageName
            )
        }
        val isGranted = mode == android.app.AppOpsManager.MODE_ALLOWED
        Log.d("MainActivity", "AppOps check for MANAGE_EXTERNAL_STORAGE: $isGranted")
        return isGranted
    }

    private fun checkDirectoryAccess() {
        val logDir = java.io.File(
            Environment.getExternalStorageDirectory().path,
            "Android/data/org.xcontest.XCTrack/files/Log"
        )
        val canAccess = logDir.exists() && logDir.canRead()
        Log.d("MainActivity", "Log directory access: exists=${logDir.exists()}, readable=${logDir.canRead()}")
        if (!canAccess) {
            Toast.makeText(
                this,
                "Échec de l'accès au dossier de logs XCTrack. Vérifiez les permissions.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Log.d("MainActivity", "All requested permissions granted")
                checkManageStoragePermission()
            } else {
                Log.w("MainActivity", "Permissions not granted: ${permissions.joinToString()}")
                Toast.makeText(this, "Certaines permissions sont nécessaires pour fonctionner", Toast.LENGTH_LONG).show()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_MANAGE_STORAGE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val isGranted = Environment.isExternalStorageManager()
            val appOpsGranted = checkAppOpsPermission()
            Log.d("MainActivity", "MANAGE_EXTERNAL_STORAGE result: $isGranted, AppOps result: $appOpsGranted")
            Toast.makeText(
                this,
                "Accès à tous les fichiers : ${if (isGranted && appOpsGranted) "accordé" else "refusé"}",
                Toast.LENGTH_LONG
            ).show()
            if (isGranted && appOpsGranted) {
                Log.d("MainActivity", "Restarting LogMonitorService after permission granted")
                stopLogMonitorService()
                startLogMonitorService()
                checkDirectoryAccess()
            } else {
                Log.w("MainActivity", "MANAGE_EXTERNAL_STORAGE not granted")
                Toast.makeText(
                    this,
                    "L'accès à tous les fichiers est requis pour surveiller les logs XCTrack",
                    Toast.LENGTH_LONG
                ).show()
                checkManageStoragePermission()
            }
        }
    }

    private fun startLogMonitorService() {
        val intent = Intent(this, LogMonitorService::class.java)
        startService(intent)
        Log.d("MainActivity", "LogMonitorService started")
    }

    private fun stopLogMonitorService() {
        val intent = Intent(this, LogMonitorService::class.java)
        stopService(intent)
        Log.d("MainActivity", "LogMonitorService stopped")
    }
}
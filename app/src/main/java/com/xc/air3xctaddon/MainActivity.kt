package com.xc.air3xctaddon

import android.Manifest
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.xc.air3xctaddon.ui.MainScreen
import com.xc.air3xctaddon.ui.theme.AIR3XCTAddonTheme
import com.xc.air3xctaddon.utils.copySoundFilesFromAssets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.content.SharedPreferences
import androidx.documentfile.provider.DocumentFile

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_NOTIFICATION_PERMISSION = 100
        private const val XCTRACK_PROVIDER_URI = "content://org.xcontest.XCTrack.allfiles"
        private const val PREFS_NAME = "XCTrackPrefs"
        private const val PREF_LOG_FILE_URI = "log_file_uri"
    }

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var eventReceiver: XCTrackEventReceiver // Added for BroadcastReceiver
    private val selectLogFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            Log.d(TAG, "Selected log file URI: $uri")
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                sharedPreferences.edit()
                    .putString(PREF_LOG_FILE_URI, uri.toString())
                    .apply()
                Toast.makeText(this, "Emplacement des logs XCTrack sélectionné avec succès", Toast.LENGTH_SHORT).show()
                val currentLogFileUri = getCurrentLogFileUri() ?: uri
                startLogMonitorService(currentLogFileUri)
            } catch (e: Exception) {
                Log.e(TAG, "Error taking persistable URI permission", e)
                Toast.makeText(
                    this,
                    "Erreur lors de l'accès au fichier. Sélectionnez un fichier .log dans XCTrack > files > Log.",
                    Toast.LENGTH_LONG
                ).show()
            }
        } ?: run {
            Log.w(TAG, "No file selected")
            Toast.makeText(
                this,
                "Aucun fichier sélectionné. Sélectionnez un fichier .log dans XCTrack > files > Log.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        assets.copySoundFilesFromAssets(getExternalFilesDir(null))
        setContent {
            AIR3XCTAddonTheme {
                MainScreen()
            }
        }
        // Initialize and register BroadcastReceiver
        eventReceiver = XCTrackEventReceiver()
        val filter = IntentFilter().apply {
            // List all known events
            val knownEvents = arrayOf(
                "BATTERY50", "BATTERY40", "BATTERY30", "BATTERY20", "BATTERY10",
                "BATTERY5", "BATTERY_CHARGING", "BATTERY_DISCHARGING",
                "TAKEOFF", "LANDING", "START_THERMALING", "STOP_THERMALING",
                "COMP_SSS_CROSSED", "COMP_TURNPOINT_CROSSED", "COMP_ESS_CROSSED",
                "COMP_GOAL_CROSSED", "SYSTEM_GPS_OK", "AIRSPACE_CROSSED",
                "AIRSPACE_RED_WARN", "AIRSPACE_ORANGE_WARN", "BT_OK", "BT_KO",
                "LIVETRACK_MESSAGE", "AIRSPACE_CROSSED_SOON", "AIRSPACE_OBSTACLE",
                "CALL_REJECTED", "COMP_TURNPOINT_PREV", "LIVETRACK_ENABLED",
                "TEST", "_LANDING_CONFIRMATION_NEEDED", "BUTTON_CLICK"
            )
            knownEvents.forEach { addAction("${EventConstants.ACTION_PREFIX}$it") }
        }
        registerReceiver(eventReceiver, filter, "org.xcontest.XCTrack.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION", null)
        Log.d(TAG, "Registered XCTrackEventReceiver")
        requestPermissions()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unregister BroadcastReceiver
        unregisterReceiver(eventReceiver)
        Log.d(TAG, "Unregistered XCTrackEventReceiver")
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "Requesting POST_NOTIFICATIONS permission")
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_NOTIFICATION_PERMISSION
            )
        } else {
            checkAndSelectLogFile()
        }
    }

    private fun checkAndSelectLogFile() {
        val currentLogFileUri = getCurrentLogFileUri()
        if (currentLogFileUri != null) {
            val documentFile = DocumentFile.fromSingleUri(this, currentLogFileUri)
            if (documentFile != null && documentFile.canRead()) {
                Log.d(TAG, "Using log file URI for date ${getCurrentDate()}: $currentLogFileUri")
                startLogMonitorService(currentLogFileUri)
            } else {
                Log.w(TAG, "Current log file URI inaccessible: $currentLogFileUri")
                selectLogFile()
            }
        } else {
            selectLogFile()
        }
    }

    private fun getCurrentDate(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return dateFormat.format(Date())
    }

    private fun getCurrentLogFileUri(): Uri? {
        val storedUriString = sharedPreferences.getString(PREF_LOG_FILE_URI, null) ?: return null
        val storedUri = Uri.parse(storedUriString)
        val storedDocumentId = try {
            DocumentsContract.getDocumentId(storedUri)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Invalid stored URI: $storedUri", e)
            return null
        }
        val folderDocumentId = storedDocumentId.substringBeforeLast('/')
        val currentLogFileName = "${getCurrentDate()}.log"
        val currentDocumentId = "$folderDocumentId/$currentLogFileName"
        return try {
            DocumentsContract.buildDocumentUri(storedUri.authority, currentDocumentId)
        } catch (e: Exception) {
            Log.e(TAG, "Error constructing current log file URI", e)
            null
        }
    }

    private fun selectLogFile() {
        Log.d(TAG, "Launching log file selection")
        Toast.makeText(
            this,
            "Sélectionnez le fichier de log du jour (${getCurrentDate()}.log) dans XCTrack > files > Log si disponible, sinon un autre fichier .log (utilisez 'File Manager +' si possible).",
            Toast.LENGTH_LONG
        ).show()
        try {
            selectLogFileLauncher.launch(arrayOf("text/plain", "text/*", "application/octet-stream"))
        } catch (e: Exception) {
            Log.e(TAG, "Error launching file selection", e)
            Toast.makeText(
                this,
                "Erreur lors du lancement de la sélection. Assurez-vous que 'File Manager +' est installé.",
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
        if (requestCode == REQUEST_NOTIFICATION_PERMISSION) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Log.d(TAG, "POST_NOTIFICATIONS permission granted")
                checkAndSelectLogFile()
            } else {
                Log.w(TAG, "POST_NOTIFICATIONS permission not granted")
                Toast.makeText(this, "La permission de notification est nécessaire.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startLogMonitorService(logFileUri: Uri) {
        val intent = Intent(this, LogMonitorService::class.java)
        intent.putExtra("LOG_FILE_URI", logFileUri.toString())
        startService(intent)
        Log.d(TAG, "LogMonitorService started with URI: $logFileUri")
    }
}
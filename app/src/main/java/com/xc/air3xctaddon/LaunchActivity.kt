package com.xc.air3xctaddon

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity

class LaunchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val packageName = intent.getStringExtra("packageName")
        val configId = intent.getIntExtra("configId", -1)
        val launchInBackground = intent.getBooleanExtra("launchInBackground", true)
        Log.d("LaunchActivity", "Started with packageName=$packageName, configId=$configId, launchInBackground=$launchInBackground")

        if (packageName != null) {
            try {
                // Verify package visibility
                val packageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
                if (packageInfo == null) {
                    throw PackageManager.NameNotFoundException("Package $packageName not found")
                }

                // Initialize launch intent
                var launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                if (launchIntent != null) {
                    // Resolve main activity component
                    val resolveInfo = packageManager.resolveActivity(launchIntent, PackageManager.MATCH_DEFAULT_ONLY)
                    if (resolveInfo != null) {
                        val component = ComponentName(packageName, resolveInfo.activityInfo.name)
                        launchIntent.component = component
                        Log.d("LaunchActivity", "Resolved component for $packageName: $component")
                    } else {
                        Log.w("LaunchActivity", "No activity resolved for $packageName, using fallback intent")
                        launchIntent = Intent(Intent.ACTION_MAIN).apply {
                            setPackage(packageName)
                            addCategory(Intent.CATEGORY_LAUNCHER)
                        }
                    }

                    if (launchInBackground) {
                        // Background launch: Launch app and immediately restore XCTrack
                        launchIntent.addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_NO_USER_ACTION or
                                    Intent.FLAG_ACTIVITY_NO_ANIMATION
                        )
                        startActivity(launchIntent)
                        Log.d("LaunchActivity", "Launched app in background: $packageName")
                        // Restore XCTrack
                        val xcTrackIntent = packageManager.getLaunchIntentForPackage("org.xcontest.XCTrack")
                        if (xcTrackIntent != null) {
                            xcTrackIntent.addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                            )
                            startActivity(xcTrackIntent)
                            Log.d("LaunchActivity", "Restored XCTrack to foreground")
                        } else {
                            Log.w("LaunchActivity", "No launch intent found for org.xcontest.XCTrack")
                        }
                        finish()
                    } else {
                        // Foreground launch: Launch app with foreground flags
                        launchIntent.addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                        )
                        // Ensure no background flags interfere
                        launchIntent.removeFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                        Log.d("LaunchActivity", "Intent flags for foreground launch: ${launchIntent.flags.toString(16)}")
                        // Slight delay to stabilize task on MediaTek
                        Handler(Looper.getMainLooper()).postDelayed({
                            startActivity(launchIntent)
                            Log.d("LaunchActivity", "Launched app in foreground: $packageName")
                            // Finish and remove task to clean up
                            finishAndRemoveTask()
                        }, 50)
                    }
                } else {
                    Log.e("LaunchActivity", "No launch intent found for package: $packageName")
                    Toast.makeText(this, getString(R.string.cannot_launch_app, packageName), Toast.LENGTH_LONG).show()
                    finish()
                }
            } catch (e: PackageManager.NameNotFoundException) {
                Log.e("LaunchActivity", "Package not found: $packageName, error: ${e.message}")
                Toast.makeText(this, getString(R.string.app_not_found, packageName), Toast.LENGTH_LONG).show()
                finish()
            } catch (e: Exception) {
                Log.e("LaunchActivity", "Failed to launch app: $packageName, error: ${e.message}")
                Toast.makeText(this, getString(R.string.failed_to_launch_app, e.message), Toast.LENGTH_LONG).show()
                finish()
            }
        } else {
            Log.e("LaunchActivity", "No packageName provided")
            Toast.makeText(this, getString(R.string.no_app_specified_to_launch), Toast.LENGTH_LONG).show()
            finish()
        }
    }
}
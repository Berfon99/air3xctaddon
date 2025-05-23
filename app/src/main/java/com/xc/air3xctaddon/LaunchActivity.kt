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
                        // Background launch: Launch app and restore XCTrack
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
                        // Foreground launch: Launch in background first, then re-launch to foreground
                        // Step 1: Launch in background
                        launchIntent.addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_NO_USER_ACTION or
                                    Intent.FLAG_ACTIVITY_NO_ANIMATION
                        )
                        startActivity(launchIntent)
                        Log.d("LaunchActivity", "Initial background launch for foreground: $packageName")

                        // Step 2: Re-launch to bring to foreground
                        Handler(Looper.getMainLooper()).postDelayed({
                            try {
                                val foregroundIntent = packageManager.getLaunchIntentForPackage(packageName)
                                if (foregroundIntent != null) {
                                    // Re-resolve component for consistency
                                    val fgResolveInfo = packageManager.resolveActivity(foregroundIntent, PackageManager.MATCH_DEFAULT_ONLY)
                                    if (fgResolveInfo != null) {
                                        val fgComponent = ComponentName(packageName, fgResolveInfo.activityInfo.name)
                                        foregroundIntent.component = fgComponent
                                        Log.d("LaunchActivity", "Foreground re-launch component: $fgComponent")
                                    } else {
                                        Log.w("LaunchActivity", "No activity resolved for foreground re-launch, using fallback")
                                        foregroundIntent.action = Intent.ACTION_MAIN
                                        foregroundIntent.setPackage(packageName)
                                        foregroundIntent.addCategory(Intent.CATEGORY_LAUNCHER)
                                    }
                                    foregroundIntent.addFlags(
                                        Intent.FLAG_ACTIVITY_NEW_TASK or
                                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                                    )
                                    startActivity(foregroundIntent)
                                    Log.d("LaunchActivity", "Re-launched app to foreground: $packageName")
                                } else {
                                    Log.e("LaunchActivity", "No foreground intent found for $packageName")
                                    Toast.makeText(this, "Cannot bring app to foreground: $packageName", Toast.LENGTH_LONG).show()
                                }
                            } catch (e: Exception) {
                                Log.e("LaunchActivity", "Failed to re-launch app to foreground: $packageName, error: ${e.message}")
                                Toast.makeText(this, "Failed to bring app to foreground: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                            finish()
                        }, 100) // 100ms delay for MediaTek
                    }
                } else {
                    Log.e("LaunchActivity", "No launch intent found for package: $packageName")
                    Toast.makeText(this, "Cannot launch app: $packageName", Toast.LENGTH_LONG).show()
                    finish()
                }
            } catch (e: PackageManager.NameNotFoundException) {
                Log.e("LaunchActivity", "Package not found: $packageName, error: ${e.message}")
                Toast.makeText(this, "App not found: $packageName", Toast.LENGTH_LONG).show()
                finish()
            } catch (e: Exception) {
                Log.e("LaunchActivity", "Failed to launch app: $packageName, error: ${e.message}")
                Toast.makeText(this, "Failed to launch app: ${e.message}", Toast.LENGTH_LONG).show()
                finish()
            }
        } else {
            Log.e("LaunchActivity", "No packageName provided")
            Toast.makeText(this, "No app specified to launch", Toast.LENGTH_LONG).show()
            finish()
        }
    }
}
package com.xc.air3xctaddon

import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import com.xc.air3xctaddon.ui.theme.AIR3XCTAddonTheme

class AboutActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AIR3XCTAddonTheme {
                AboutScreen()
            }
        }
    }
}

@Composable
fun AboutScreen() {
    val context = LocalContext.current
    // Determine XCTrack status and version code
    val (xcTrackStatus, xcTrackVersionCode) = try {
        val packageInfo = context.packageManager.getPackageInfo("org.xcontest.XCTrack", 0)
        val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)
        Log.d("AboutScreen", "XCTrack detected, version code: $versionCode")
        if (versionCode >= 91230) {
            "XCTrack OK" to versionCode.toString()
        } else {
            "XCTrack KO" to versionCode.toString()
        }
    } catch (e: PackageManager.NameNotFoundException) {
        Log.e("AboutScreen", "XCTrack not found: ${e.message}")
        "XCTrack Not Installed" to ""
    } catch (e: Exception) {
        Log.e("AboutScreen", "Error checking XCTrack: ${e.message}")
        "XCTrack Error" to ""
    }

    // Create clickable text for XCTrack status
    val annotatedText = buildAnnotatedString {
        append("${stringResource(R.string.app_version)} ${BuildConfig.VERSION_NAME} - ")
        withStyle(style = SpanStyle()) {
            append(xcTrackStatus)
        }
        if (xcTrackVersionCode.isNotEmpty()) {
            append(" (VC $xcTrackVersionCode)")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.copyright),
            style = MaterialTheme.typography.body1,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = annotatedText,
            style = MaterialTheme.typography.body1,
            modifier = Modifier.clickable(
                enabled = xcTrackStatus == "XCTrack OK" || xcTrackStatus == "XCTrack KO",
                onClick = {
                    Toast.makeText(
                        context,
                        "This app only works with a recent version of XCTrack",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
        )
    }
}
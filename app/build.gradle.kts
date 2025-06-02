import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android") version "2.0.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0"
    id("kotlin-kapt")
}

android {
    namespace = "com.xc.air3xctaddon"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.xc.air3xctaddon"
        minSdk = 33
        targetSdk = 34
        versionCode = 106
        versionName = "1.0.6" // Telegram individual, tasks in settings
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        // Read bot token from local.properties
        val localProperties = Properties()
        val localPropertiesFile = project.rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localProperties.load(FileInputStream(localPropertiesFile))
            println("Loaded local.properties: $localProperties")
        } else {
            throw GradleException("local.properties not found at ${localPropertiesFile.absolutePath}")
        }
        val token = localProperties.getProperty("telegram.bot.token") ?: ""
        println("Telegram bot token: '$token'")
        if (token.isBlank()) {
            throw GradleException("Telegram bot token is missing or empty in local.properties")
        }
        buildConfigField("String", "TELEGRAM_BOT_TOKEN", "\"$token\"")
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            // Add this for debugging
            isDebuggable = true // Remove this after fixing
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_19
        targetCompatibility = JavaVersion.VERSION_19
    }
    kotlinOptions {
        jvmTarget = "19"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "2.0.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core Android dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.google.material)
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Jetpack Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.runtime.livedata)

    // Lifecycle and ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // OkHttp
    implementation(libs.okhttp)

    // Google Play Services (Location)
    implementation(libs.google.play.services.location)

    // JSON parsing (for TelegramBotHelper)
    implementation(libs.json)
    implementation("com.google.code.gson:gson:2.10.1")

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.material:material:1.7.3")
}

tasks.register<DefaultTask>("generateVersionHistory") {
    group = "Custom"
    description = "Generates a version history file from the versionName and its comment."

    doLast {
        // Debug: Check what files exist
        println("Project directory: ${project.projectDir}")
        println("Looking for build files...")

        val buildFileKts = project.file("build.gradle.kts")
        val buildFileGroovy = project.file("build.gradle")

        println("build.gradle.kts exists: ${buildFileKts.exists()}")
        println("build.gradle exists: ${buildFileGroovy.exists()}")

        val buildFile = when {
            buildFileKts.exists() -> buildFileKts
            buildFileGroovy.exists() -> buildFileGroovy
            else -> throw GradleException("Neither build.gradle nor build.gradle.kts found")
        }
        val versionHistoryFile = project.file("version_history.txt")
        val lastVersionFile = project.file("last_version.txt")

        if (!versionHistoryFile.exists()) {
            versionHistoryFile.createNewFile()
        }
        if (!lastVersionFile.exists()) {
            lastVersionFile.createNewFile()
        }

        val lines = buildFile.readLines()
        var versionNameLine: String? = null
        for (line in lines) {
            if (line.trimStart().startsWith("versionName")) {
                versionNameLine = line
                break
            }
        }

        if (versionNameLine != null) {
            val versionName = versionNameLine.substringAfter('"').substringBefore('"')
            val comment = if (versionNameLine.contains("//")) {
                versionNameLine.substringAfter("//").trim()
            } else {
                "No comment"
            }
            val currentVersionLine = "$versionName - $comment\n"

            val lastVersion = if (lastVersionFile.exists() && lastVersionFile.length() > 0) {
                lastVersionFile.readText().trim()
            } else {
                ""
            }

            if (currentVersionLine.trim() != lastVersion) {
                versionHistoryFile.appendText(currentVersionLine)
                lastVersionFile.writeText(currentVersionLine)
            }
        } else {
            println("versionName not found in build.gradle.kts")
        }
    }
}

tasks.configureEach {
    if (name == "build") {
        dependsOn(tasks.named("generateVersionHistory"))
    }
}
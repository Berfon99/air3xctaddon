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
        versionCode = 100
        versionName = "1.0.0" //Initialisation

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        buildConfig = true // Enable BuildConfig generation
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.compose.runtime:runtime-livedata")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("androidx.navigation:navigation-compose:2.8.3")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

// Custom task to extract versionName and comment
val generateVersionHistory = tasks.register("generateVersionHistory") {
    group = "Custom"
    description = "Generates a version history file from the versionName and its comment."

    doLast {
        val buildFile = project.file("build.gradle.kts")
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
            val comment = versionNameLine.substringAfter("//").trim()
            val currentVersionLine = "$versionName - $comment\n"

            val lastVersion = lastVersionFile.readText().trim()

            if (currentVersionLine.trim() != lastVersion.trim()) {
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
        dependsOn(generateVersionHistory)
    }
}
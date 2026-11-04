// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 NOSTOS, LLC

import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "market.nostos.timeline"
    compileSdk = 35

    defaultConfig {
        applicationId = "market.nostos.timeline"
        minSdk = 29
        targetSdk = 34
        versionCode = 190
        versionName = "1.9.0"
    }
    // Release builds are signed only when a keystore.properties file sits beside this project (it is never committed):
    //   storeFile=timeline-release.jks
    //   storePassword=...
    //   keyAlias=timeline
    //   keyPassword=...
    // Without one, assembleRelease produces an unsigned APK, and assembleDebug a debug-signed one for trying out.
    val keyFile = rootProject.file("keystore.properties")
    val keys = Properties().apply { keyFile.takeIf { it.isFile }?.inputStream()?.use { load(it) } }
    signingConfigs {
        if (keyFile.isFile) create("release") {
            storeFile = rootProject.file(keys.getProperty("storeFile", "timeline-release.jks"))
            storePassword = keys.getProperty("storePassword", "")
            keyAlias = keys.getProperty("keyAlias", "timeline")
            keyPassword = keys.getProperty("keyPassword", "")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("io.coil-kt:coil-compose:2.7.0")
    // Installs the startup profile shipped in the APK, so the first launches are already compiled.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

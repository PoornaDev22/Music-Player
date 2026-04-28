plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("kotlin-parcelize")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.simplemusicplayer"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.simplemusicplayer"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.media:media:1.7.0")
    implementation ("com.google.code.gson:gson:2.10.1")
    // Firebase BOM - manages all Firebase library versions automatically
    implementation(platform("com.google.firebase:firebase-bom:33.1.0"))

    // Firestore - stores song metadata (title, artist, url, etc.)
    implementation("com.google.firebase:firebase-firestore")

    // Storage - stores the actual .mp3 files
    implementation("com.google.firebase:firebase-storage")
}
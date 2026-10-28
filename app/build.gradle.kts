plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.divineit.convertor"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.divineit.convertor"
        minSdk = 29
        targetSdk = 34
        versionCode = 2
        versionName = "0.5.0"

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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.workmanager)
    implementation(libs.coil.compose)
    // PdfBox-Android for PDF text-layer extraction (Phase 3, device-only).
    // If Gradle cannot resolve this artifact, delete this line plus
    // PdfExtractor.kt and the PDF branch in ComparisonViewModel.
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    // ML Kit text recognition for OCR of scanned pages and images
    // (Phase 4, device-only). If Gradle cannot resolve this artifact,
    // delete this line plus MlKitOcr.kt and the OCR branches in
    // ComparisonViewModel.
    implementation("com.google.mlkit:text-recognition:16.0.0")
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
}

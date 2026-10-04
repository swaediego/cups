plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.cups.tasas"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.cups.tasas"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "1.2"
    }
    // Llave fija del proyecto (keystore/cups.keystore, fuera de git): todo APK que se publique
    // debe firmarse con ella para que Android permita actualizar encima de la versión instalada.
    signingConfigs {
        create("cups") {
            storeFile = rootProject.file("keystore/cups.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildTypes {
        debug { signingConfig = signingConfigs.getByName("cups") }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("cups")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(bom)
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
}

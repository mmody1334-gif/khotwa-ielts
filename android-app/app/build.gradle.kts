plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.khotwa.ielts"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.khotwa.ielts"
        minSdk = 24
        targetSdk = 34
        versionCode = (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1) + 100
        versionName = "2.0." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
    }
    // Release key comes from GitHub Secrets (KHOTWA_KEYSTORE_B64, KHOTWA_STORE_PASS). Without them the build falls back to the debug key.
    val ks = System.getenv("KHOTWA_KEYSTORE")?.let { file(it) }?.takeIf { it.exists() }
    signingConfigs {
        if (ks != null) create("khotwa") {
            storeFile = ks
            storePassword = System.getenv("KHOTWA_STORE_PASS")
            keyAlias = System.getenv("KHOTWA_KEY_ALIAS") ?: "khotwa"
            keyPassword = System.getenv("KHOTWA_KEY_PASS") ?: System.getenv("KHOTWA_STORE_PASS")
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = if (ks != null) signingConfigs.getByName("khotwa") else signingConfigs.getByName("debug")
        }
    }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.core:core-ktx:1.13.1")
}

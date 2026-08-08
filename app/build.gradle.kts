plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val telegramApiId = providers.environmentVariable("TELEGRAM_API_ID").orNull?.toIntOrNull() ?: 0
val telegramApiHash = providers.environmentVariable("TELEGRAM_API_HASH").orNull.orEmpty()

android {
    namespace = "com.retrofrost.lattice"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.retrofrost.lattice"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0-alpha.1"

        buildConfigField("int", "TELEGRAM_API_ID", telegramApiId.toString())
        buildConfigField("String", "TELEGRAM_API_HASH", "\"${telegramApiHash.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
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

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.google.zxing:core:3.5.4")

    // TDLib 1.8.62 packaged for Android. The app talks to the official TDLib JSON API.
    implementation("io.github.xephosbot:tdlib-kmp-android:1.8.62")

    debugImplementation("androidx.compose.ui:ui-tooling")
}

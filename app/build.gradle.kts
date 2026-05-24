import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use(::load)
    }
}

fun localProperty(name: String, defaultValue: String = ""): String =
    localProperties.getProperty(name, defaultValue)

fun buildConfigString(value: String): String =
    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

plugins {
    id("org.jetbrains.kotlin.android")
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

ksp {
    arg("room.schemaLocation", "${projectDir}/schemas")
    arg("room.incremental", "true")
}

android {
    namespace  = "com.healthmonitor.app"
    compileSdk = 36

    signingConfigs {
        create("release") {
            storeFile     = file("../health_monitor.jks")
            storePassword = localProperty("STORE_PASSWORD")
            keyAlias      = "health_monitor_key"
            keyPassword   = localProperty("KEY_PASSWORD")
        }
    }

    defaultConfig {
        applicationId             = "com.healthmonitor.app"
        minSdk                    = 26
        targetSdk                 = 36
        versionCode               = 5
        versionName               = "5.6.3"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Gemini AI — set GEMINI_API_KEY and optionally GEMINI_MODEL in local.properties
        buildConfigField("String", "PROXY_BASE_URL", buildConfigString(localProperty("PROXY_BASE_URL")))
        buildConfigField("String", "PROXY_APP_SECRET", buildConfigString(localProperty("PROXY_APP_SECRET")))
        buildConfigField("String", "GEMINI_MODEL",   buildConfigString(localProperty("GEMINI_MODEL", "gemini-2.5-flash")))
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
    }

    buildFeatures {
        compose     = true
        buildConfig = true
    }
}

dependencies {

    // ── Compose BOM ────────────────────────────────────────────────────────
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-text:1.11.1")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // ── Android Core ───────────────────────────────────────────────────────
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation("androidx.datastore:datastore-preferences:1.2.1")

    // ── Firebase / Crashlytics ─────────────────────────────────────────────
    implementation(platform("com.google.firebase:firebase-bom:33.1.0"))
    implementation("com.google.firebase:firebase-crashlytics-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")

    // ── Room ───────────────────────────────────────────────────────────────
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // ── Database encryption ────────────────────────────────────────────────
    implementation("net.zetetic:android-database-sqlcipher:4.5.4")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // ── Coroutines ─────────────────────────────────────────────────────────
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    // ── Serialization ──────────────────────────────────────────────────────
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // ── Hilt ───────────────────────────────────────────────────────────────
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // ── WorkManager ────────────────────────────────────────────────────────
    // Kept for future background tasks (alarm verification, export jobs, etc.)
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    implementation("androidx.hilt:hilt-work:1.3.0")
    ksp("androidx.hilt:hilt-compiler:1.3.0")

    // ── Ktor (used directly by GeminiHealthAiService) ──────────────────────
    val ktorVersion = "2.3.12"
    implementation("io.ktor:ktor-client-android:$ktorVersion")
    implementation("io.ktor:ktor-client-core:$ktorVersion")

    // ── ML Kit OCR ─────────────────────────────────────────────────────────
    implementation("com.google.mlkit:text-recognition:16.0.1")

    // ── CameraX ────────────────────────────────────────────────────────────
    val cameraVersion = "1.4.2"
    implementation("androidx.camera:camera-mlkit-vision:$cameraVersion")
    implementation("androidx.camera:camera-camera2:$cameraVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraVersion")
    implementation("androidx.camera:camera-view:$cameraVersion")

    // ── Testing ────────────────────────────────────────────────────────────
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
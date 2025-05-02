plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    kotlin("plugin.serialization") version "1.9.23" // Ensure version matches Kotlin
}

android {
    namespace = "com.example.korrent"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.korrent"
        minSdk = 26 // Android 8.0 Oreo
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.11" // Compatible with Kotlin 1.9.23
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation(platform("androidx.compose:compose-bom:2024.05.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
// Required for FlowRow and other layout components
    implementation("androidx.compose.foundation:foundation-layout:1.6.7")
    implementation("androidx.compose.material:material:1.6.7")
implementation("com.google.android.material:material:1.12.0") // Add standard Material Components library

    // ViewModel Compose for MVVM
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.1")

    // Ktor (Networking)
    val ktorVersion = "2.3.11"
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion") // Or ktor-client-okhttp
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")

    // Jsoup (HTML Parsing)
    implementation("org.jsoup:jsoup:1.17.2")

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")

    // Cloudflare Bypass Library REMOVED - Will implement WebView logic directly
    // implementation("com.github.darkryh:Cloudflare-Bypass:master-SNAPSHOT")

    // Ktor OkHttp engine (Still useful for general requests and cookie handling)
    implementation("io.ktor:ktor-client-okhttp:$ktorVersion")
    // implementation("io.ktor:ktor-client-cio:$ktorVersion") // Keep only one engine

    // Accompanist WebView for displaying challenge
    implementation("com.google.accompanist:accompanist-webview:0.34.0") // Check for latest version

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.05.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
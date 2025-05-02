# 11 · Optimizations

This document outlines the optimizations applied to the Korrent Android project to improve build performance, reduce application size, and enhance code maintainability.

## Table of Contents

- [Gradle Build Configuration](#gradle-build-configuration)
  - [Code Shrinking (Minification)](#code-shrinking-minification)
  - [Build Cache](#build-cache)
  - [Configuration Cache](#configuration-cache)
- [Dependency Management](#dependency-management)
  - [Ktor HTTP Client Engine](#ktor-http-client-engine)
- [Source Code Refinements](#source-code-refinements)
  - [Logging Consistency](#logging-consistency)
  - [Jsoup Selector Constants](#jsoup-selector-constants)
  - [URL Building Delegation](#url-building-delegation)
  - [Lazy Initialization](#lazy-initialization)

## Gradle Build Configuration

Several optimizations were applied to the Gradle build files (`build.gradle.kts` and `gradle.properties`).

### Code Shrinking (Minification)

Code shrinking and obfuscation (using R8/ProGuard) were enabled for the `release` build type in `app/build.gradle.kts`. This reduces the size of the final APK and makes reverse engineering more difficult.

```kotlin
// korrent-android/app/build.gradle.kts
android {
    // ...
    buildTypes {
        release {
            isMinifyEnabled = true // Enabled
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    // ...
}
```

### Build Cache

The Gradle build cache was enabled in `gradle.properties`. This allows Gradle to reuse outputs from previous builds, significantly speeding up build times, especially for unchanged modules.

```properties
# korrent-android/gradle.properties
# Enable Gradle build cache for faster builds
org.gradle.caching=true
```

### Configuration Cache

The Gradle configuration cache was enabled in `gradle.properties`. This experimental feature speeds up the configuration phase of the build by caching the result of the configuration tasks.

```properties
# korrent-android/gradle.properties
# Enable Gradle configuration cache for faster configuration phase (experimental but often stable)
org.gradle.configuration-cache=true
```

## Dependency Management

### Ktor HTTP Client Engine

The project included dependencies for both `ktor-client-cio` and `ktor-client-okhttp`. Since OkHttp is generally preferred for Android development due to better system integration, the `ktor-client-cio` dependency was removed from `app/build.gradle.kts`.

```kotlin
// korrent-android/app/build.gradle.kts
dependencies {
    // ...
    val ktorVersion = "2.3.11"
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    // implementation("io.ktor:ktor-client-cio:$ktorVersion") // Removed CIO engine
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-client-okhttp:$ktorVersion") // Using OkHttp engine
    // ...
}
```

## Source Code Refinements

Several changes were made to the Kotlin source code to improve maintainability and structure.

### Logging Consistency

A `TAG` constant was introduced in `NetworkModule.kt`, `TorrentService.kt`, and `SearchViewModel.kt` for consistent Logcat tagging, making debugging easier.

```kotlin
// Example from TorrentService.kt
class TorrentService {
    companion object {
        private const val TAG = "TorrentService"
        // ...
    }
    // ...
    suspend fun search(...) {
        // ...
        Log.d(TAG, "Searching URL: $url")
        // ...
    }
}
```

### Jsoup Selector Constants

The CSS selectors used by Jsoup for parsing HTML in `TorrentService.kt` were extracted into `const val` definitions within a `companion object`. This centralizes the selectors, making them easier to update if the 1337x website structure changes.

```kotlin
// korrent-android/app/src/main/java/com/example/korrent/data/remote/TorrentService.kt
class TorrentService {
    companion object {
        private const val TAG = "TorrentService"
        // Jsoup Selectors for Search Results Page
        private const val SELECTOR_SEARCH_TABLE_ROWS = "table.table-list tbody tr"
        // ... other selectors
    }
    // ...
    private fun parseTorrentList(html: String, currentPage: Int): TorrentResult {
        // ...
        val tableRows = document.select(SELECTOR_SEARCH_TABLE_ROWS) // Using constant
        // ...
    }
}
```

### URL Building Delegation

The logic for building search and info URLs, originally duplicated or placeholder in `SearchViewModel.kt`, was properly delegated.
1.  URL building methods were added to the `TorrentRepository` interface.
2.  `TorrentRepositoryImpl` implements these methods by calling the (now `internal`) methods in `TorrentService`.
3.  `SearchViewModel` now calls `repository.buildSearchUrl(...)` and `repository.buildInfoUrl(...)` when needed for the Cloudflare bypass logic.

This improves the separation of concerns, making the ViewModel less dependent on the specific implementation details of the `TorrentService`.

### Lazy Initialization

In `NetworkModule.kt`, the `CloudflareWebViewBypass` instance, which requires an application context, is now initialized using `by lazy { ... }`. This defers its creation until it's first accessed, ensuring the context is available and slightly improving startup behavior.

```kotlin
// korrent-android/app/src/main/java/com/example/korrent/data/remote/NetworkModule.kt
object NetworkModule {
    // ...
    // Lazily initialize the custom bypass utility using application context
    private val cloudflareBypass: CloudflareWebViewBypass by lazy {
        CloudflareWebViewBypass(KorrentApplication.appContext)
    }
    // ...
}
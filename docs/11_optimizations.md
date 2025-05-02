# Optimizations Summary

We did some tweaking to make the Korrent Android app build faster, run leaner, and be easier to maintain. Here's the gist:

## Build Stuff (Gradle)

*   **Made it Smaller:** Turned on code shrinking (`isMinifyEnabled = true`) for release builds. This makes the final app smaller and a bit harder to reverse-engineer. Found in `app/build.gradle.kts`.
*   **Made it Faster:** Enabled the Gradle build cache (`org.gradle.caching=true`) and configuration cache (`org.gradle.configuration-cache=true`) in `gradle.properties`. This helps Gradle reuse work from previous builds, speeding things up.

## Dependencies

*   **Cleaned Up Ktor:** We were using two Ktor network engines (`cio` and `okhttp`). We removed `cio` in `app/build.gradle.kts` since `okhttp` is generally better for Android.

## Code Tweaks

*   **Better Logging:** Added consistent `TAG`s for Logcat messages in the main classes (`NetworkModule`, `TorrentService`, `SearchViewModel`) so logs are easier to follow.
*   **Easier HTML Parsing:** Pulled out the Jsoup CSS selectors in `TorrentService` into constants. If the 1337x site changes its layout, it'll be easier to update the selectors in one place.
*   **Smarter URL Building:** Fixed how URLs are built for searches and torrent details. The ViewModel now asks the Repository for the URL, which asks the Service. Keeps things tidier and avoids duplicate logic.
*   **Lazy Loading:** Made the `CloudflareWebViewBypass` object in `NetworkModule` initialize lazily (`by lazy`). It only gets created when first needed, which is slightly better.

That's about it! These changes should make development a bit smoother and the app a bit better.
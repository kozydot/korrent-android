# Korrent Android

This is an Android application ported from the Python desktop project '[Korrent1337x](https://github.com/your-original-python-repo-link-here)'. It allows searching for torrents on the 1337x website directly from your Android device.

## Features

*   Search for torrents on 1337x.
*   Filter search results by category (Movies, TV, Games, Music, Apps, Anime, Docs, Other, XXX).
*   Sort search results by time, size, seeders, or leechers (ascending/descending).
*   View torrent details (name, category, type, language, size, seeders, leechers, upload date, etc.).
*   Copy magnet links to the clipboard.
*   Initiate torrent downloads by opening magnet links in a compatible torrent client app installed on the device (e.g., BitTorrent, uTorrent, Flud).
*   Handles Cloudflare challenges using an interactive WebView dialog.

## Tech Stack

*   **Language:** Kotlin
*   **UI:** Jetpack Compose
*   **Networking:** Ktor (with OkHttp engine)
*   **HTML Parsing:** Jsoup
*   **Asynchronous Operations:** Kotlin Coroutines
*   **Architecture:** MVVM (ViewModel, Repository, Service)
*   **Cloudflare Bypass:** Custom implementation using Accompanist WebView

## Original Project

This project is a direct port of the Python application 'Korrent1337x'. The core logic for interacting with 1337x (URL building, HTML parsing) was adapted from the Python implementation, which originally used the `py1337x` library.

## Building

1.  Clone this repository.
2.  Open the `KorrentAndroid` project in Android Studio (latest stable version recommended).
3.  Let Gradle sync the dependencies.
4.  Build the project using **Build** -> **Make Project** or run it directly on an emulator or device using **Run** -> **Run 'app'**.

## Generating an APK

To generate a debug APK for testing:
1.  Go to **Build** -> **Build Bundle(s) / APK(s)** -> **Build APK(s)**.
2.  The APK will be located in `KorrentAndroid/app/build/outputs/apk/debug/app-debug.apk`.

## Acknowledgements

The core logic for interacting with 1337x was heavily inspired by the excellent [py1337x](https://github.com/hemantapkh/1337x) Python library by Hemanta Pokharel.
## Notes

*   This application interacts directly with the 1337x website. Website structure changes may break functionality.
*   The Cloudflare bypass mechanism relies on the user solving the challenge in an embedded WebView. This might not always be successful or convenient.
*   The Accompanist WebView library used for the bypass is deprecated. It currently works but might require replacement in the future.
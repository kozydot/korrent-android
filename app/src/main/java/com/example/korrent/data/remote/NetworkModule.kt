package com.example.korrent.data.remote

import android.util.Log
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import com.example.korrent.data.remote.CloudflareWebViewBypass // custom bypass utility
import com.example.korrent.KorrentApplication // application class for context
import kotlinx.coroutines.runBlocking // for calling suspend func from interceptor

object NetworkModule {

    private const val TAG = "NetworkModule" // Define Log Tag constant

    // base url for 1337x (can be made configurable later)
    const val BASE_URL = "https://1337x.to" // use a known working domain

    // Lazily initialize the custom bypass utility using application context
    private val cloudflareBypass: CloudflareWebViewBypass by lazy {
        CloudflareWebViewBypass(KorrentApplication.appContext)
    }

    // configure okhttpclient with interceptor using bypass utility
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(90, TimeUnit.SECONDS) // longer timeouts for webview
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val originalRequest = chain.request()
            val requestBuilder = originalRequest.newBuilder()

            // get cached clearance from bypass utility
            val userAgent = cloudflareBypass.getUserAgent()
            val cookies = cloudflareBypass.getCookies()

            if (userAgent != null && cookies != null) {
                // apply cached clearance if available
                Log.d(TAG, "Interceptor: Applying cached clearance.")
                requestBuilder.header("User-Agent", userAgent)
                requestBuilder.header("Cookie", cookies)
            } else {
                // proceed with a default user-agent if no clearance is cached
                // the actual bypass attempt is now triggered elsewhere (e.g., viewmodel)
                Log.d(TAG, "Interceptor: No cached clearance found, using default UA.")
                requestBuilder.header("User-Agent", "mozilla/5.0 (windows nt 10.0; win64; x64) applewebkit/537.36 (khtml, like gecko) chrome/91.0.4472.124 safari/537.36")
            }

            chain.proceed(requestBuilder.build())
        }
        .build()

    // configure ktor client
    val client = HttpClient(OkHttp) { // okhttp engine
        engine {
            preconfigured = okHttpClient // use okhttpclient with interceptor
        }

        // optional: json serialization if needed (1337x is mostly html scraping)
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true // important for flexibility
            })
        }

        // optional: default request params (e.g., headers)
        install(DefaultRequest) {
            // headers added by okhttp interceptor based on clearance
        }

        // optional: logging
        // install(logging) {
        //     logger = logger.default
        //     level = loglevel.all
        // }

        // request timeout (match okhttp)
        install(HttpTimeout) {
            requestTimeoutMillis = 90000 // 90s
            connectTimeoutMillis = 90000 // 90s
            socketTimeoutMillis = 90000 // 90s
        }
    }
}
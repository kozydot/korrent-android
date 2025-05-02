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
// import the custom bypass utility
import com.example.korrent.data.remote.CloudflareWebViewBypass
import com.example.korrent.KorrentApplication // import application class for context
import kotlinx.coroutines.runBlocking // needed for calling suspend function from interceptor

object NetworkModule {

    // base url for 1337x (can be made configurable later)
    const val BASE_URL = "https://1337x.to" // use a known working domain

    // initialize the custom bypass utility using application context
    private val cloudflareBypass = CloudflareWebViewBypass(KorrentApplication.appContext)

    // configure okhttpclient with an interceptor that uses the bypass utility
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(90, TimeUnit.SECONDS) // increase timeouts significantly for webview
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val originalRequest = chain.request()
            val requestBuilder = originalRequest.newBuilder()

            // get cached clearance data from the bypass utility
            val userAgent = cloudflareBypass.getUserAgent()
            val cookies = cloudflareBypass.getCookies()

            if (userAgent != null && cookies != null) {
                // apply cached clearance if available
                Log.d("networkmodule", "interceptor: applying cached clearance.") // lowercase tag
                requestBuilder.header("User-Agent", userAgent)
                requestBuilder.header("Cookie", cookies)
            } else {
                // proceed with a default user-agent if no clearance is cached
                // the actual bypass attempt is now triggered elsewhere (e.g., viewmodel)
                Log.d("networkmodule", "interceptor: no cached clearance found, using default ua.") // lowercase tag
                requestBuilder.header("User-Agent", "mozilla/5.0 (windows nt 10.0; win64; x64) applewebkit/537.36 (khtml, like gecko) chrome/91.0.4472.124 safari/537.36")
            }

            chain.proceed(requestBuilder.build())
        }
        .build()

    // configure ktor client
    val client = HttpClient(OkHttp) { // use okhttp engine
        engine {
            preconfigured = okHttpClient // use the okhttpclient with the interceptor
        }

        // optional: configure json serialization if needed for any api endpoints (though 1337x is mostly html scraping)
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true // important for flexible parsing
            })
        }

        // optional: configure default request parameters (e.g., headers)
        install(DefaultRequest) {
            // headers added by the okhttp interceptor based on cloudflare clearance
        }

        // optional: logging
        // install(logging) {
        //     logger = logger.default
        //     level = loglevel.all
        // }

        // configure request timeout (match okhttp engine timeout)
        install(HttpTimeout) {
            requestTimeoutMillis = 90000 // 90 seconds
            connectTimeoutMillis = 90000 // 90 seconds
            socketTimeoutMillis = 90000 // 90 seconds
        }
    }
}
package com.example.korrent.data.remote

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.IOException
import java.util.concurrent.TimeoutException

// states for the bypass process
sealed class BypassState {
    object Idle : BypassState() // initial state, or after success/fail reset
    data class ChallengeRequired(val url: String) : BypassState() // need user interaction
    object Success : BypassState() // bypass worked, got cookies/ua
    data class Error(val message: String) : BypassState() // bypass failed
    object Processing : BypassState() // webview is trying to solve
}

/**
 * handles cloudflare bypass using a webview the user might see.
 */
class CloudflareWebViewBypass(private val context: Context) {

    private val tag = "cloudflarebypass"
    private val handler = Handler(Looper.getMainLooper())
    private var webView: WebView? = null
    private var currentContinuation: CancellableContinuation<Pair<String?, String?>>? = null
    private var currentChallengeUrl: String? = null
    private var bypassJob: Job? = null

    // --- state management ---
    private val _bypassState = MutableStateFlow<BypassState>(BypassState.Idle)
    val bypassState: StateFlow<BypassState> = _bypassState.asStateFlow()

    // --- cached clearance data ---
    private var cachedUserAgent: String? = null
    private var cachedCookies: String? = null

    fun getWebViewInstance(): WebView? {
        // make sure webview is created on main thread if needed
        if (webView == null && Looper.myLooper() == Looper.getMainLooper()) {
            setupWebView()
        } else if (webView == null) {
            // if called from background thread, post to main thread
            handler.post { setupWebView() }
        }
        return webView
    }

    fun getUserAgent(): String? = cachedUserAgent
    fun getCookies(): String? = cachedCookies

    /**
     * starts the cloudflare clearance process for a url.
     * updates bypassstate to challengerequired if needed.
     * returns true if cached clearance is available, false otherwise.
     */
    suspend fun prepareClearance(url: String): Boolean {
        // check cache first
        if (cachedUserAgent != null && cachedCookies != null) {
            Log.d(tag, "using cached clearance for $url")
            _bypassState.update { BypassState.Success } // signal success from cache
            return true
        }

        // cancel any previous job
        bypassJob?.cancel()
        cleanupWebView() // clean up old webview if any

        Log.d(tag, "no cached clearance. starting bypass process for: $url")
        currentChallengeUrl = url
        _bypassState.update { BypassState.ChallengeRequired(url) }
        // ui should now see this state and show the webview loading the url
        return false
    }

    // call this from ui when webview challenge seems solved
    // (e.g., went to target page, or user clicks "done")
    fun notifyChallengeSolved() {
        if (_bypassState.value is BypassState.Processing || _bypassState.value is BypassState.ChallengeRequired) {
             Log.i(tag, "challenge marked as solved by ui for url: $currentChallengeUrl")
             // re-check cookies after user did stuff
             val cookies = CookieManager.getInstance().getCookie(currentChallengeUrl)
             if (cookies != null && cookies.contains("cf_clearance")) {
                 cachedUserAgent = webView?.settings?.userAgentString
                 cachedCookies = cookies
                 Log.i(tag, "got clearance after user interaction.")
                 _bypassState.update { BypassState.Success }
             } else {
                 Log.w(tag, "challenge marked solved, but clearance cookie not found.")
                 _bypassState.update { BypassState.Error("failed to verify clearance after challenge.") }
             }
        } else {
             Log.w(tag, "notifychallengesolved called in unexpected state: ${_bypassState.value}")
        }
        cleanupWebView()
    }

     // call this if user cancels or it fails
    fun notifyChallengeFailed(reason: String = "user cancelled or failed") {
         Log.w(tag, "challenge failed: $reason")
         _bypassState.update { BypassState.Error(reason) }
         cleanupWebView()
    }


    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        if (webView != null) return // already setup

        Log.d(tag, "setting up webview on main thread.")
        webView = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            // use a common user agent
            settings.userAgentString = "mozilla/5.0 (windows nt 10.0; win64; x64) applewebkit/537.36 (khtml, like gecko) chrome/91.0.4472.124 safari/537.36"

            webViewClient = object : WebViewClient() {
                 private val clientTag = "cloudflareclient" // lowercase tag

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                     Log.d(clientTag, "webview onpagestarted: ${url ?: "null"}")
                     _bypassState.update { BypassState.Processing } // show loading
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    val urlString: String? = url
                    Log.d(clientTag, "webview onpagefinished: ${urlString ?: "null"}")

                    // guess: if url isn't a cloudflare check page anymore
                    // and we have the cookie, maybe it's solved?
                    // ui might need better checks based on page content/title.
                    val cookies = CookieManager.getInstance().getCookie(urlString)
                    if (cookies != null && cookies.contains("cf_clearance") && urlString != null && !urlString.contains("challenges.cloudflare.com")) {
                         Log.i(clientTag, "challenge likely passed automatically for $urlString")
                         cachedUserAgent = view?.settings?.userAgentString
                         cachedCookies = cookies
                         _bypassState.update { BypassState.Success }
                         // maybe cleanup webview here? or let ui do it?
                         // cleanupwebview()
                    } else {
                         // still in challenge or auto solve failed
                         Log.d(clientTag, "still processing challenge or automatic solve failed for $urlString")
                         // keep state as processing or challengerequired, let ui handle timeout/manual solve
                         if (_bypassState.value == BypassState.Processing) {
                              // maybe update state back to challengerequired?
                              // _bypassstate.update { bypassstate.challengerequired(currentchallengeurl!!) }
                         }
                    }
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true) {
                        val errorDesc: String? = error?.description?.toString()
                        Log.e(clientTag, "webview error loading ${request.url?.toString() ?: "null url"}: ${errorDesc ?: "unknown error"}")
                        // update state to error only if it's the main challenge url failing
                        if (request.url.toString() == currentChallengeUrl) {
                             _bypassState.update { BypassState.Error("webview error: ${errorDesc ?: "unknown error"}") }
                             cleanupWebView()
                        }
                    }
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    Log.d(clientTag, "webview shouldoverrideurlloading: ${request?.url}")
                    return false // load all urls in this webview
                }
            }
        }
    }

    // clean up webview stuff
    fun cleanupWebView() {
        Log.d(tag, "cleaning up webview")
        if (Looper.myLooper() == Looper.getMainLooper()) {
            webView?.stopLoading()
            webView?.destroy()
            webView = null
        } else {
            handler.post {
                webView?.stopLoading()
                webView?.destroy()
                webView = null
            }
        }
         // reset state only if not already success/error? or always reset?
         // if (_bypassstate.value !is bypassstate.success && _bypassstate.value !is bypassstate.error) {
         //      _bypassstate.update { bypassstate.idle }
         // }
         // maybe caller should reset state after handling success/error
    }
}
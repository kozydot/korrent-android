package com.example.korrent

import android.app.Application
import android.content.Context

class KorrentApplication : Application() {

    companion object {
        lateinit var appContext: Context
            private set
    }

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
    }
}
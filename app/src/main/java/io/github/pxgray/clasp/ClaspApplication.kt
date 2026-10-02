package io.github.pxgray.clasp

import android.app.Application

class ClaspApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

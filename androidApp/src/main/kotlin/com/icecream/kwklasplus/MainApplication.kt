package com.icecream.kwklasplus

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import com.icecream.kwklasplus.manager.AppLifecycleObserver

class MainApplication : Application() {
    val dependencies by lazy { AndroidAppDependencies(this) }

    override fun onCreate() {
        super.onCreate()
        
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            AppLifecycleObserver(this, dependencies.sessionKeepAlive),
        )
    }
}

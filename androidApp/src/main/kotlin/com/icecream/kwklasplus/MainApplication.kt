package com.icecream.kwklasplus

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import com.icecream.kwklasplus.manager.AppLifecycleObserver
import com.icecream.kwklasplus.feature.auth.LoginFunnelStatus

class MainApplication : Application() {
    val dependencies by lazy { AndroidAppDependencies(this) }

    override fun onCreate() {
        super.onCreate()
        LoginFunnelStatus.migrateLegacyInstall(appPreferences)
        dependencies.academicWidgets.start()
        
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            AppLifecycleObserver(this, dependencies.sessionKeepAlive),
        )
    }
}

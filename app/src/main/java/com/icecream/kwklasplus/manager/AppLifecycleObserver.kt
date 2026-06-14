package com.icecream.kwklasplus.manager

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.icecream.kwklasplus.LockActivity

class AppLifecycleObserver(private val context: Context) : DefaultLifecycleObserver, Application.ActivityLifecycleCallbacks {

    private var currentActivity: Activity? = null

    init {
        (context.applicationContext as Application).registerActivityLifecycleCallbacks(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        
        if (AppLockManager.isAppLockEnabled(context) && !AppLockManager.isUnlocked) {
            // Only start LockActivity if current top is not LockActivity
            if (currentActivity !is LockActivity) {
                val intent = Intent(context, LockActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    putExtra("MODE", "UNLOCK")
                }
                context.startActivity(intent)
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        // Reset unlocked state when app goes to background
        AppLockManager.isUnlocked = false
    }

    // ActivityLifecycleCallbacks implementation to track current activity
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) { currentActivity = activity }
    override fun onActivityResumed(activity: Activity) { currentActivity = activity }
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) { if (currentActivity == activity) currentActivity = null }
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}

package com.icecream.kwklasplus.widget

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.icecream.kwklasplus.LockActivity
import com.icecream.kwklasplus.MainActivity
import com.icecream.kwklasplus.manager.AppLockManager

class WidgetEntryActivity : AppCompatActivity() {
    private val unlock = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && AppLockManager.isUnlocked) openApp() else finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        if (AppLockManager.isAppLockEnabled(this) && !AppLockManager.isUnlocked) {
            unlock.launch(Intent(this, LockActivity::class.java).putExtra("MODE", "UNLOCK"))
        } else openApp()
    }

    private fun openApp() {
        startActivity(WidgetNavigation.forward(intent, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)))
        finish()
    }
}

package com.icecream.kwklasplus

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.icecream.kwklasplus.modal.LibraryQRModal

class LibraryQRWidgetActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val sharedPreferences = appPreferences
        val stdNumber = sharedPreferences.getString(AppPrefs.LIBRARY_STD_NUMBER, null)
        val phone = sharedPreferences.getString(AppPrefs.LIBRARY_PHONE, null)
        val password = getLibraryPassword()

        if (stdNumber.isNullOrBlank() || phone.isNullOrBlank() || password.isNullOrBlank()) {
            startActivity(Intent(this, HomeActivity::class.java).apply {
                action = HomeActivity.ACTION_OPEN_LIBRARY_SETTINGS
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
            finish()
            return
        }

        if (savedInstanceState != null) return
        val modal = LibraryQRModal.newInstance(true)
        modal.setStyle(DialogFragment.STYLE_NORMAL, R.style.RoundCornerBottomSheetDialogTheme)
        modal.show(supportFragmentManager, LibraryQRModal.TAG)
    }
}

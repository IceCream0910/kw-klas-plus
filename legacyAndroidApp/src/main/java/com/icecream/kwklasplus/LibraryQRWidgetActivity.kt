package com.icecream.kwklasplus

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.icecream.kwklasplus.modal.LibraryQRModal
import com.icecream.kwklasplus.getLibraryPassword

class LibraryQRWidgetActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val sharedPreferences = appPreferences
        val stdNumber = sharedPreferences.getString(AppPrefs.LIBRARY_STD_NUMBER, null)
        val phone = sharedPreferences.getString(AppPrefs.LIBRARY_PHONE, null)
        val password = getLibraryPassword()

        if(stdNumber == null || phone == null || password == null) {
            Toast.makeText(this, "먼저 앱에서 모바일 학생증 설정을 완료해주세요.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val modal = LibraryQRModal(true)
        modal.setStyle(DialogFragment.STYLE_NORMAL, R.style.RoundCornerBottomSheetDialogTheme)
        modal.show(supportFragmentManager, LibraryQRModal.TAG)
    }
}

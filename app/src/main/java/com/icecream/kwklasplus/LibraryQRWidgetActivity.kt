package com.icecream.kwklasplus

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.icecream.kwklasplus.modal.LibraryQRModal

class LibraryQRWidgetActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val modal = LibraryQRModal(true)
        modal.setStyle(DialogFragment.STYLE_NORMAL, R.style.RoundCornerBottomSheetDialogTheme)

        val sharedPreferences = appPreferences
        val stdNumber = sharedPreferences.getString(AppPrefs.LIBRARY_STD_NUMBER, null)
        val phone = sharedPreferences.getString(AppPrefs.LIBRARY_PHONE, null)
        val password = sharedPreferences.getString(AppPrefs.LIBRARY_PASSWORD, null)

        if(stdNumber == null || phone == null || password == null) {
            Toast.makeText(this, "먼저 앱에서 모바일 학생증 설정을 완료해주세요.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        modal.show(supportFragmentManager, LibraryQRModal.TAG)
    }
}

package com.icecream.kwqrcheckin

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.icecream.kwqrcheckin.modal.LibraryQRModal

class LibraryQRWidgetActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val modal = LibraryQRModal(true)
        modal.setStyle(DialogFragment.STYLE_NORMAL, R.style.RoundCornerBottomSheetDialogTheme)

        val sharedPreferences = this.getSharedPreferences("com.icecream.kwqrcheckin", Context.MODE_PRIVATE)
        val stdNumber = sharedPreferences?.getString("library_stdNumber", null)
        val phone = sharedPreferences?.getString("library_phone", null)
        val password = sharedPreferences?.getString("library_password", null)

        if(stdNumber == null || phone == null || password == null) {
            Toast.makeText(this, " 먼저 앱에서 모바일 학생증 설정을 완료해주세요.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        modal.show(supportFragmentManager, LibraryQRModal.TAG)
    }
}
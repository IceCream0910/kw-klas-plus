package com.icecream.kwklasplus.modal

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.icecream.kwklasplus.AppPrefs
import com.icecream.kwklasplus.appPreferences
import com.icecream.kwklasplus.encryptedPreferences
import com.icecream.kwklasplus.feature.library.LibraryQrSettingsContent
import com.icecream.kwklasplus.feature.library.LibraryQrSettingsUiState
import com.icecream.kwklasplus.getLibraryPassword
import com.icecream.kwklasplus.ui.theme.KlasPlusTheme

class LibraryQRSettingsBottomSheetDialog : KlasBottomSheetDialogFragment() {
    fun interface OnSaveCompleteListener {
        fun onSaveComplete()
    }

    private var onSaveCompleteListener: OnSaveCompleteListener? = null
    private var studentNumber by mutableStateOf("")
    private var password by mutableStateOf("")
    private var phone by mutableStateOf("")

    fun setOnSaveCompleteListener(listener: OnSaveCompleteListener) {
        onSaveCompleteListener = listener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferences = requireActivity().appPreferences
        studentNumber = preferences.getString(AppPrefs.LIBRARY_STD_NUMBER, "")
            ?.takeIf(String::isNotEmpty)
            ?: preferences.getString(AppPrefs.KW_ID, "").orEmpty()
        phone = preferences.getString(AppPrefs.LIBRARY_PHONE, "").orEmpty()
        password = requireActivity().getLibraryPassword().orEmpty()
    }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            KlasPlusTheme {
                LibraryQrSettingsContent(
                    state = LibraryQrSettingsUiState(studentNumber, password, phone),
                    onStudentNumberChange = { value ->
                        if (value.all(Char::isDigit)) studentNumber = value
                    },
                    onPasswordChange = { password = it },
                    onPhoneChange = { phone = it },
                    onSaveClick = ::save,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val bottomSheet = (dialog as? BottomSheetDialog)
            ?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?: return
        BottomSheetBehavior.from(bottomSheet).apply {
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    private fun save() {
        if (studentNumber.isBlank() || phone.isBlank() || password.isBlank()) return
        requireActivity().appPreferences.edit()
            .putString(AppPrefs.LIBRARY_STD_NUMBER, studentNumber)
            .putString(AppPrefs.LIBRARY_PHONE, phone)
            .remove(AppPrefs.LIBRARY_PASSWORD)
            .apply()
        requireActivity().encryptedPreferences.edit()
            .putString(AppPrefs.LIBRARY_PASSWORD, password)
            .apply()
        Toast.makeText(context, "저장되었습니다.", Toast.LENGTH_SHORT).show()
        onSaveCompleteListener?.onSaveComplete()
        dismiss()
    }

    override fun onDestroy() {
        password = ""
        super.onDestroy()
    }

    companion object {
        const val TAG = "LibraryQRSettingsBottomSheetDialog"

        fun newInstance(onSaveComplete: OnSaveCompleteListener) =
            LibraryQRSettingsBottomSheetDialog().apply {
                setOnSaveCompleteListener(onSaveComplete)
            }
    }
}

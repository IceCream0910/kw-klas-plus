package com.icecream.kwklasplus.modal

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.icecream.kwklasplus.R
import com.icecream.kwklasplus.components.AnimatedButton

class LibraryQRSettingsBottomSheetDialog() : BottomSheetDialogFragment() {

    private lateinit var stdNumberEditText: TextInputEditText
    private lateinit var passwordEditText: TextInputEditText
    private lateinit var phoneEditText: TextInputEditText
    private lateinit var saveButton: AnimatedButton

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_libraryqr_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeViews(view)
        setupInitialValues()
        setupSaveButton()
        fixTabletExpansionIssue(view)
    }

    private fun initializeViews(view: View) {
        stdNumberEditText = view.findViewById(R.id.stdNumber)
        passwordEditText = view.findViewById(R.id.password)
        phoneEditText = view.findViewById(R.id.phone)
        saveButton = view.findViewById(R.id.saveBtn)
    }

    private fun setupInitialValues() {
        val sharedPreferences = activity?.getSharedPreferences("com.icecream.kwklasplus", Context.MODE_PRIVATE)

        var stdNumber = sharedPreferences?.getString("library_stdNumber", "")
        if (stdNumber.isNullOrEmpty()) {
            stdNumber = sharedPreferences?.getString("kwID", "")
        }

        val phone = sharedPreferences?.getString("library_phone", "")
        val password = sharedPreferences?.getString("library_password", "")

        stdNumberEditText.setText(stdNumber)
        phoneEditText.setText(phone)
        passwordEditText.setText(password)
    }

    private fun setupSaveButton() {
        saveButton.setOnClickListener {
            val newStdNumber = stdNumberEditText.text.toString()
            val newPhone = phoneEditText.text.toString()
            val newPassword = passwordEditText.text.toString()

            if (newStdNumber.isEmpty() || newPhone.isEmpty() || newPassword.isEmpty()) {
                Snackbar.make(requireView(), "모든 항목을 입력해주세요.", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            saveUserData(newStdNumber, newPhone, newPassword)
            Toast.makeText(context, "저장되었습니다.", Toast.LENGTH_SHORT).show()
            dismiss()
        }
    }

    private fun saveUserData(stdNumber: String, phone: String, password: String) {
        val sharedPreferences = activity?.getSharedPreferences("com.icecream.kwklasplus", Context.MODE_PRIVATE)
        sharedPreferences?.edit()?.apply {
            putString("library_stdNumber", stdNumber)
            putString("library_phone", phone)
            putString("library_password", password)
            apply()
        }
    }

    private fun fixTabletExpansionIssue(view: View) {
        view.viewTreeObserver.addOnGlobalLayoutListener {
            val dialog = dialog as BottomSheetDialog?
            val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) as FrameLayout?
            val behavior = BottomSheetBehavior.from(bottomSheet!!)
            behavior.peekHeight = view.measuredHeight
        }
    }

    companion object {
        const val TAG = "LibraryQRSettingsBottomSheetDialog"

        fun newInstance(onSaveComplete: () -> Unit): LibraryQRSettingsBottomSheetDialog {
            return LibraryQRSettingsBottomSheetDialog()
        }
    }
}
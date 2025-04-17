package com.icecream.kwklasplus.modal

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity.MODE_PRIVATE
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.icecream.kwklasplus.HomeActivity
import com.icecream.kwklasplus.LoginActivity
import com.icecream.kwklasplus.R
import com.icecream.kwklasplus.SettingsActivity

class MenuBottomSheetDialog() : BottomSheetDialogFragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_select, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val title = view.findViewById<TextView>(R.id.title)
        val desc = view.findViewById<TextView>(R.id.desc)
        title.visibility = View.GONE
        desc.visibility = View.GONE

        val menuOptions = listOf("광운대학교 공식 앱", "중앙도서관 앱", "앱 설정", "로그아웃")
        val menuContainer = view.findViewById<LinearLayout>(R.id.optionsContainer)

        menuOptions.forEach { option ->
            val menuButton = layoutInflater.inflate(
                R.layout.item_modal_select,
                menuContainer,
                false
            ) as Button

            menuButton.text = option
            menuButton.setOnClickListener {
                when (option) {
                    "광운대학교 공식 앱" -> {
                        val intent =
                            requireContext().packageManager.getLaunchIntentForPackage("kr.ac.kw.SmartLearning")
                        if (intent != null) {
                            startActivity(intent)
                        } else {
                            val playStoreIntent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://play.google.com/store/apps/details?id=kr.ac.kw.SmartLearning")
                            )
                            startActivity(playStoreIntent)
                        }
                    }

                    "중앙도서관 앱" -> {
                        val intent =
                            requireContext().packageManager.getLaunchIntentForPackage("idoit.slpck.kwangwoon")
                        if (intent != null) {
                            startActivity(intent)
                        } else {
                            val playStoreIntent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://play.google.com/store/apps/details?id=idoit.slpck.kwangwoon")
                            )
                            startActivity(playStoreIntent)
                        }
                    }

                    "앱 설정" -> {
                        val intent = Intent(requireContext(), SettingsActivity::class.java)
                        requireActivity().startActivityForResult(intent, 7777)
                    }

                    "로그아웃" -> {
                        val activity = requireActivity() as HomeActivity
                        activity.logout()
                    }
                }
                dismiss()
            }
            menuContainer.addView(menuButton)
        }

        // FIX: 태블릿에서 완전히 펼쳐지지 않는 이슈
        view.viewTreeObserver.addOnGlobalLayoutListener {
            val dialog = dialog as BottomSheetDialog?
            val bottomSheet =
                dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) as FrameLayout?
            val behavior = BottomSheetBehavior.from(bottomSheet!!)
            behavior.peekHeight = view.measuredHeight
        }
    }

    companion object {
        const val TAG = "MenuBottomSheetDialog"
    }
}
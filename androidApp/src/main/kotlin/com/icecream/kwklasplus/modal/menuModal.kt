package com.icecream.kwklasplus.modal

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.icecream.kwklasplus.HomeActivity
import com.icecream.kwklasplus.SettingsActivity
import com.icecream.kwklasplus.ui.modal.SelectionBottomSheetContent
import com.icecream.kwklasplus.ui.modal.SelectionOption
import com.icecream.kwklasplus.ui.theme.KlasPlusTheme

class MenuBottomSheetDialog : KlasBottomSheetDialogFragment() {
    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            KlasPlusTheme {
                SelectionBottomSheetContent(options = menuOptions())
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

    private fun menuOptions() = listOf(
        SelectionOption("광운대학교 공식 앱") {
            openPackage("kr.ac.kw.SmartLearning")
        },
        SelectionOption("중앙도서관 앱") {
            openPackage("idoit.slpck.kwangwoon")
        },
        SelectionOption("앱 설정") {
            requireActivity().startActivityForResult(
                Intent(requireContext(), SettingsActivity::class.java),
                7777,
            )
            dismiss()
        },
        SelectionOption("로그아웃") {
            (requireActivity() as HomeActivity).logout()
            dismiss()
        },
    )

    private fun openPackage(packageName: String) {
        val intent = requireContext().packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=$packageName"),
            )
        startActivity(intent)
        dismiss()
    }

    companion object {
        const val TAG = "MenuBottomSheetDialog"
    }
}

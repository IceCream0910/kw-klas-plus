package com.icecream.kwklasplus.modal

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.icecream.kwklasplus.ui.modal.SelectionBottomSheetContent
import com.icecream.kwklasplus.ui.modal.SelectionOption
import com.icecream.kwklasplus.ui.theme.KlasPlusTheme

class YearHakgiBottomSheetDialog(
    private val yearHakgiList: Array<String>,
    private val isUpdate: Boolean = false,
) : KlasBottomSheetDialogFragment() {
    interface YearHakgiSelectionListener {
        fun onYearHakgiSelected(value: String)
    }

    private var listener: YearHakgiSelectionListener? = null

    fun setSpeedSelectionListener(listener: YearHakgiSelectionListener) {
        this.listener = listener
    }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            KlasPlusTheme {
                SelectionBottomSheetContent(
                    title = if (isUpdate) "새로운 학기를 찾았어요!" else "학기 선택",
                    description = "앱 실행 시 기본적으로 보여질 학기를 선택해주세요.",
                    options = yearHakgiList.map { value ->
                        SelectionOption(value.toDisplayLabel()) {
                            listener?.onYearHakgiSelected(value)
                            dismiss()
                        }
                    },
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

    companion object {
        const val TAG = "YearHakgiBottomSheetDialog"
    }
}

private fun String.toDisplayLabel(): String = replace(",3", ",하계계절")
    .replace(",4", ",동계계절")
    .replace(",", "년도 ") + "학기"

package com.icecream.kwklasplus.modal

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.icecream.kwklasplus.ui.modal.SelectionBottomSheetContent
import com.icecream.kwklasplus.ui.modal.SelectionOption
import com.icecream.kwklasplus.ui.theme.KlasPlusTheme

class SpeedBottomSheetDialog : BottomSheetDialogFragment() {
    interface SpeedSelectionListener {
        fun onSpeedSelected(speed: Double)
    }

    private var listener: SpeedSelectionListener? = null

    fun setSpeedSelectionListener(listener: SpeedSelectionListener) {
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
                    title = "재생 속도",
                    options = SPEED_OPTIONS.map { speed ->
                        SelectionOption("${speed}x") {
                            listener?.onSpeedSelected(speed)
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
        const val TAG = "SpeedBottomSheetDialog"
        private val SPEED_OPTIONS = listOf(0.25, 0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0)
    }
}

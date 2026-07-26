package com.icecream.kwklasplus.modal

import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

abstract class KlasBottomSheetDialogFragment : BottomSheetDialogFragment() {
    private var bottomSheetView: View? = null
    private var decorView: View? = null
    private var originalBottomSheetHeight = ViewGroup.LayoutParams.WRAP_CONTENT
    private var originalSoftInputMode = 0
    private var imeAnimationRunning = false

    override fun onStart() {
        super.onStart()
        val bottomSheetDialog = dialog as? BottomSheetDialog ?: return
        val window = bottomSheetDialog.window ?: return
        val bottomSheet = bottomSheetDialog.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet,
        ) ?: return
        val decor = window.decorView

        bottomSheetView = bottomSheet
        decorView = decor
        originalBottomSheetHeight = bottomSheet.layoutParams.height
        originalSoftInputMode = window.attributes.softInputMode
        window.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
        )
        ViewCompat.setOnApplyWindowInsetsListener(decor) { _, insets ->
            applyImeInsets(bottomSheet, insets)
            insets
        }
        ViewCompat.setWindowInsetsAnimationCallback(
            decor,
            object : WindowInsetsAnimationCompat.Callback(
                WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_CONTINUE_ON_SUBTREE,
            ) {
                override fun onPrepare(animation: WindowInsetsAnimationCompat) {
                    if (animation.typeMask and WindowInsetsCompat.Type.ime() != 0) {
                        imeAnimationRunning = true
                    }
                    super.onPrepare(animation)
                }

                override fun onProgress(
                    insets: WindowInsetsCompat,
                    runningAnimations: MutableList<WindowInsetsAnimationCompat>,
                ): WindowInsetsCompat {
                    applyImeInsets(bottomSheet, insets)
                    return insets
                }

                override fun onEnd(animation: WindowInsetsAnimationCompat) {
                    super.onEnd(animation)
                    if (animation.typeMask and WindowInsetsCompat.Type.ime() == 0) return
                    imeAnimationRunning = false
                    ViewCompat.getRootWindowInsets(decor)?.let { insets ->
                        applyImeInsets(bottomSheet, insets)
                    }
                }
            },
        )
        ViewCompat.requestApplyInsets(decor)
    }

    override fun onStop() {
        bottomSheetView?.let { bottomSheet ->
            bottomSheet.translationY = 0f
            bottomSheet.layoutParams = bottomSheet.layoutParams.apply {
                height = originalBottomSheetHeight
            }
        }
        decorView?.let { decor ->
            ViewCompat.setOnApplyWindowInsetsListener(decor, null)
            ViewCompat.setWindowInsetsAnimationCallback(decor, null)
        }
        bottomSheetView = null
        decorView = null
        imeAnimationRunning = false
        dialog?.window?.setSoftInputMode(originalSoftInputMode)
        super.onStop()
    }

    private fun applyImeInsets(
        bottomSheet: View,
        insets: WindowInsetsCompat,
    ) {
        val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
        if (!imeVisible && imeAnimationRunning) return
        val targetHeight = if (imeVisible) {
            ViewGroup.LayoutParams.MATCH_PARENT
        } else {
            originalBottomSheetHeight
        }
        if (bottomSheet.layoutParams.height != targetHeight) {
            bottomSheet.layoutParams = bottomSheet.layoutParams.apply {
                height = targetHeight
            }
            bottomSheet.requestLayout()
        }
        if (imeVisible) {
            BottomSheetBehavior.from(bottomSheet).state =
                BottomSheetBehavior.STATE_EXPANDED
        }
    }
}

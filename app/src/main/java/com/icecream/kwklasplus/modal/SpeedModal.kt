package com.icecream.kwklasplus.modal

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.icecream.kwklasplus.R

class SpeedBottomSheetDialog : BottomSheetDialogFragment() {
    interface SpeedSelectionListener {
        fun onSpeedSelected(speed: Double)
    }

    private var listener: SpeedSelectionListener? = null

    fun setSpeedSelectionListener(listener: SpeedSelectionListener) {
        this.listener = listener
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_speed, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val speedOptions = listOf(0.25, 0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0)
        val speedContainer = view.findViewById<LinearLayout>(R.id.speedContainer)

        speedOptions.forEach { speed ->
            val speedButton = layoutInflater.inflate(
                R.layout.item_speed_option,
                speedContainer,
                false
            ) as Button

            speedButton.text = "${speed}x"
            speedButton.setOnClickListener {
                listener?.onSpeedSelected(speed)
                dismiss()
            }
            speedContainer.addView(speedButton)
        }
    }

    companion object {
        const val TAG = "SpeedBottomSheetDialog"
    }
}
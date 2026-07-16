package com.icecream.kwklasplus.modal

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.icecream.kwklasplus.R

class YearHakgiBottomSheetDialog(yearHakgiList: Array<String>, isUpdate: Boolean = false) : BottomSheetDialogFragment() {
    val yearHakgiList = yearHakgiList
    val isUpdate = isUpdate
    interface YearHakgiSelectionListener {
        fun onYearHakgiSelected(value: String)
    }

    private var listener: YearHakgiSelectionListener? = null

    fun setSpeedSelectionListener(listener: YearHakgiSelectionListener) {
        this.listener = listener
    }

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

        if(isUpdate) {
            title.text = "새로운 학기를 찾았어요!"
            desc.text = "새로운 학기 정보가 등록됐어요. 앱 실행 시 기본적으로 보여질 학기를 선택해주세요."
        } else {
            title.text = "학기 선택"
            desc.text = "앱 실행 시 기본적으로 보여질 학기를 선택해주세요."
        }

        val optionsContainer = view.findViewById<LinearLayout>(R.id.optionsContainer)

        yearHakgiList.forEach { value ->
            val yearHakgiButton = layoutInflater.inflate(
                R.layout.item_modal_select,
                optionsContainer,
                false
            ) as Button

            yearHakgiButton.text = value.replace(",3", ",하계계절").replace(",4", ",동계계절").replace(",", "년도 ") + "학기"
            yearHakgiButton.setOnClickListener {
                listener?.onYearHakgiSelected(value)
                dismiss()
            }
            optionsContainer.addView(yearHakgiButton)
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
        const val TAG = "YearHakgiBottomSheetDialog"
    }
}
package com.icecream.kwqrcheckin.modal

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.icecream.kwqrcheckin.HomeActivity
import com.icecream.kwqrcheckin.MainActivity
import com.icecream.kwqrcheckin.R

class AdditionalSubjectModal(private val adapter: ArrayAdapter<String>) : BottomSheetDialogFragment()  {
    @SuppressLint("MissingInflatedId")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        super.onCreateView(inflater, container, savedInstanceState)

        val view = inflater.inflate(R.layout.additional_subject_modal, container, false)

        val listView = view.findViewById<ListView>(R.id.additionalSubjectListView)
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            (activity as HomeActivity).callbackAdditionalSubjectModal(position!!)
        }

        return view
    }

    companion object {
        const val TAG = "BasicBottomModalSheet"
    }
}
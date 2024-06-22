package com.icecream.kwqrcheckin.modal

import android.R.attr.bitmap
import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidmads.library.qrgenearator.QRGContents
import androidmads.library.qrgenearator.QRGEncoder
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.zxing.WriterException
import com.icecream.kwqrcheckin.R
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.io.IOException


class LibraryQRModal(isWidget: Boolean) : BottomSheetDialogFragment()  {
    private var originalBrightness: Float = 0f
    lateinit var qrImg : ImageView
    lateinit var qrProgressBar : ProgressBar
    private val isWidget = isWidget

    @SuppressLint("MissingInflatedId")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        originalBrightness = activity?.window?.attributes?.screenBrightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE

        val view = inflater.inflate(R.layout.library_qr_modal, container, false)
        val settingBtn = view.findViewById<TextView>(R.id.settingButton)
        qrImg = view.findViewById<ImageView>(R.id.qrImageView)
        val sharedPreferences = activity?.getSharedPreferences("com.icecream.kwqrcheckin", Context.MODE_PRIVATE)
        val stdNumber = sharedPreferences?.getString("library_stdNumber", null)
        val phone = sharedPreferences?.getString("library_phone", null)
        val password = sharedPreferences?.getString("library_password", null)
        qrProgressBar = view.findViewById<ProgressBar>(R.id.qrProgressBar)

        if(isWidget) {
            settingBtn.visibility = View.GONE
        }

        if (stdNumber == null || phone == null || password == null) {
            val dialogView = LayoutInflater.from(context).inflate(R.layout. library_qr_settings, null);
            dismiss()
            val builder = context?.let { it1 ->
                MaterialAlertDialogBuilder(it1)
                    .setTitle("모바일 학생증 설정")
                    .setView(dialogView)
                    .setNegativeButton("완료", DialogInterface.OnClickListener { dialog, which ->
                        val stdNumber = dialogView.findViewById<TextView>(R.id.stdNumber).text.toString()
                        val phone = dialogView.findViewById<TextView>(R.id.phone).text.toString()
                        val password = dialogView.findViewById<TextView>(R.id.password).text.toString()

                        if(stdNumber.isEmpty() || phone.isEmpty() || password.isEmpty()) {
                            //Toast.makeText(context, "모든 항목을 입력해주세요.", Toast.LENGTH_SHORT).show()
                            return@OnClickListener
                        } else {
                            val editor = sharedPreferences?.edit()
                            editor?.putString("library_stdNumber", stdNumber)
                            editor?.putString("library_phone", phone)
                            editor?.putString("library_password", password)
                            editor?.apply()

                            dialog.dismiss()
                        }

                    })
            }
            builder?.show()
        } else {
            displayQR(stdNumber, phone, password)
        }

        settingBtn.setOnClickListener {
            val dialogView = LayoutInflater.from(context).inflate(R.layout. library_qr_settings, null);
            val stdNumber = sharedPreferences?.getString("library_stdNumber", "")
            val phone = sharedPreferences?.getString("library_phone", "")
            val password = sharedPreferences?.getString("library_password", "")

            val stdNumberEditText = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.stdNumber)
            val phoneEditText = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.phone)
            val passwordEditText = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.password)

            stdNumberEditText.setText(stdNumber)
            phoneEditText.setText(phone)
            passwordEditText.setText(password)

            val builder = context?.let { it1 ->
                MaterialAlertDialogBuilder(it1)
                    .setTitle("모바일 학생증 설정")
                    .setView(dialogView)
                    .setNegativeButton("완료", DialogInterface.OnClickListener { dialog, which ->
                        val stdNumber = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.stdNumber).text.toString()
                        val phone = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.phone).text.toString()
                        val password = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.password).text.toString()

                        if(stdNumber.isEmpty() || phone.isEmpty() || password.isEmpty()) {
                            Toast.makeText(context, "모든 항목을 입력해주세요.", Toast.LENGTH_SHORT).show()
                            return@OnClickListener
                        } else {
                            val editor = sharedPreferences?.edit()
                            editor?.putString("library_stdNumber", stdNumber)
                            editor?.putString("library_phone", phone)
                            editor?.putString("library_password", password)
                            editor?.apply()

                            dialog.dismiss()
                            Snackbar.make(view, "모바일 학생증 설정이 완료되었습니다.", Snackbar.LENGTH_SHORT).show()
                        }
                    })
            }
            builder?.show()
        }

        // FIX: 태블릿에서 완전히 펼쳐지지 않는 이슈
        view.viewTreeObserver.addOnGlobalLayoutListener {
            val dialog = dialog as BottomSheetDialog?
            val bottomSheet = dialog!!.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) as FrameLayout?
            val behavior = BottomSheetBehavior.from(bottomSheet!!)
            behavior.peekHeight = view.measuredHeight
        }

        return view
    }

    private fun displayQR(stdNumber: String, phone: String, password: String) {
        val client = OkHttpClient()
        val json = JSONObject()
            .put("stdNumber", stdNumber)
            .put("phone", phone)
            .put("password", password)

        val requestBody = RequestBody.create("application/json".toMediaTypeOrNull(), json.toString())
        val request = Request.Builder()
            .url("https://kw-library-qr.yuntae.in/api/libraryQR")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                if (response.code == 400) {
                    activity?.runOnUiThread {
                        Toast.makeText(context, "도서관 회원 정보가 일치하지 않습니다. 설정 버튼을 눌러 정보를 다시 입력해주세요.", Toast.LENGTH_SHORT).show()
                    }
                } else if (!response.isSuccessful) {
                    throw IOException("Unexpected code $response")
                } else {
                    val responseData = response.body?.string()
                    val qrData = JSONObject(responseData).getString("qr_data")

                    // Load the QR code image from the URL
                    activity?.runOnUiThread {
                        val qrgEncoder = QRGEncoder(
                            qrData,
                            null,
                            QRGContents.Type.TEXT,
                            200
                        )
                        qrgEncoder.colorBlack = Color.BLACK
                        qrgEncoder.colorWhite = Color.WHITE
                        try {
                            val bitmap = qrgEncoder.getBitmap(0)
                            qrImg.setImageBitmap(bitmap)
                            qrProgressBar.visibility = View.GONE
                            qrImg.visibility = View.VISIBLE
                            // 화면 밝기 최대로
                            val layoutParams = activity?.window?.attributes
                            layoutParams?.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
                            activity?.window?.attributes = layoutParams
                        } catch (e: WriterException) {
                            Log.v(TAG, e.toString())
                        }
                    }
                }
            }
        })
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)

        // 화면 밝기 원래대로
        val layoutParams = activity?.window?.attributes
        layoutParams?.screenBrightness = originalBrightness
        activity?.window?.attributes = layoutParams

        if(isWidget) {
            activity?.finish()
        }
    }

    companion object {
        const val TAG = "BasicBottomModalSheet"
    }
}
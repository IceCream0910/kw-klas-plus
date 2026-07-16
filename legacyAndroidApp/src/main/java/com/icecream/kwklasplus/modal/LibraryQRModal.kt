package com.icecream.kwklasplus.modal

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import com.google.android.material.loadingindicator.LoadingIndicator
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidmads.library.qrgenearator.QRGContents
import androidmads.library.qrgenearator.QRGEncoder
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.zxing.WriterException
import com.icecream.kwklasplus.AppPrefs
import com.icecream.kwklasplus.LibraryQRWidget
import com.icecream.kwklasplus.R
import com.icecream.kwklasplus.appPreferences
import com.icecream.kwklasplus.getLibraryPassword
import com.icecream.kwklasplus.manager.LibraryManager
import com.icecream.kwklasplus.modal.LibraryQRSettingsBottomSheetDialog
import kotlinx.coroutines.*
import org.json.JSONObject

class LibraryQRModal(private var isWidget: Boolean) : BottomSheetDialogFragment() {
    private var originalBrightness: Float = 0f
    private lateinit var name: TextView
    private lateinit var numberAndDepartment: TextView
    private lateinit var qrImg: ImageView
    private lateinit var qrProgressBar: LoadingIndicator
    private lateinit var libraryManager: LibraryManager
    private var isRetry = false
    private lateinit var refreshButton: Button
    private lateinit var refreshButtonForWidget: Button
    private var countDownTimer: CountDownTimer? = null
    private val refreshInterval = 30000L

    @SuppressLint("MissingInflatedId")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        originalBrightness = activity?.window?.attributes?.screenBrightness
            ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE

        return inflater.inflate(R.layout.library_qr_modal, container, false)
    }

    private fun isWidgetAdded(): Boolean {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val widgetComponent = context?.let { ComponentName(it, LibraryQRWidget::class.java) }
        val widgetIds = appWidgetManager.getAppWidgetIds(widgetComponent)
        return widgetIds.isNotEmpty()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val settingBtn = view.findViewById<TextView>(R.id.settingButton)
        val addWidget = view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.addWidget)
        val refreshImageButtonForWidget =
            view.findViewById<ImageView>(R.id.refreshImageButtonForWidget)
        refreshButtonForWidget = view.findViewById(R.id.refreshButtonForWidget)
        refreshButton = view.findViewById(R.id.refreshButton)
        name = view.findViewById(R.id.name)
        numberAndDepartment = view.findViewById(R.id.numberAndDepartment)
        qrImg = view.findViewById(R.id.qrImageView)
        qrProgressBar = view.findViewById(R.id.qrProgressBar)
        libraryManager = LibraryManager(requireContext())

        if (isWidget) {
            settingBtn.visibility = View.GONE
            refreshImageButtonForWidget.visibility = View.VISIBLE
            refreshButtonForWidget.visibility = View.VISIBLE
            refreshButton.visibility = View.GONE
            addWidget.visibility = View.GONE
        } else {
            refreshImageButtonForWidget.visibility = View.GONE
            refreshButtonForWidget.visibility = View.GONE
            refreshButton.visibility = View.VISIBLE
            if(!isWidgetAdded()) {
                addWidget.visibility = View.VISIBLE
            }
        }
        addWidget.setOnClickListener {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val provider = context?.let { it1 -> ComponentName(it1, LibraryQRWidget::class.java) }

            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appWidgetManager.isRequestPinAppWidgetSupported){
                val intent = Intent(context, LibraryQRWidget::class.java)
                val successCallback = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
                if (provider != null) {
                    appWidgetManager.requestPinAppWidget(provider, null, successCallback)
                }
            }
        }

        refreshButton.setOnClickListener {
            lifecycleScope.launch {
                refreshQRCode()
            }
        }

        refreshButtonForWidget.setOnClickListener {
            lifecycleScope.launch {
                refreshQRCode()
            }
        }

        refreshImageButtonForWidget.setOnClickListener {
            lifecycleScope.launch {
                refreshQRCode()
            }
        }

        val sharedPreferences = activity?.appPreferences
        val stdNumber = sharedPreferences?.getString(AppPrefs.LIBRARY_STD_NUMBER, null)
        val phone = sharedPreferences?.getString(AppPrefs.LIBRARY_PHONE, null)
        val password = activity?.getLibraryPassword()

        if (stdNumber == null || phone == null || password == null) {
            showSettingsDialog()
            dismiss()
        } else {
            lifecycleScope.launch {
                displayQR(stdNumber, phone, password)
                startCountDownTimer()
            }
        }

        settingBtn.setOnClickListener {
            showSettingsDialog()
        }

        view.viewTreeObserver.addOnGlobalLayoutListener {
            val dialog = dialog as BottomSheetDialog?
            val bottomSheet =
                dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) as FrameLayout?
            if (bottomSheet != null) {
                val behavior = BottomSheetBehavior.from(bottomSheet)
                behavior.peekHeight = view.measuredHeight
            }
        }
    }

    private fun showSettingsDialog() {
        val settingsModal = LibraryQRSettingsBottomSheetDialog()
        settingsModal.setOnSaveCompleteListener {
            lifecycleScope.launch {
                refreshQRCode()
            }
        }
        settingsModal.show(parentFragmentManager, "LibraryQRSettingsModal")
    }

    private fun startCountDownTimer() {
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(refreshInterval, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsRemaining = millisUntilFinished / 1000
                if (isWidget) {
                    refreshButtonForWidget.text = "$secondsRemaining 초"
                } else {
                    refreshButton.text = " $secondsRemaining 초"
                }
            }

            override fun onFinish() {
                lifecycleScope.launch {
                    refreshQRCode()
                }
            }
        }.start()
    }

    private suspend fun refreshQRCode() {
        val sharedPreferences = activity?.appPreferences
        val stdNumber = sharedPreferences?.getString(AppPrefs.LIBRARY_STD_NUMBER, null)
        val phone = sharedPreferences?.getString(AppPrefs.LIBRARY_PHONE, null)
        val password = activity?.getLibraryPassword()

        if (stdNumber != null && phone != null && password != null) {
            qrProgressBar.visibility = View.VISIBLE
            qrImg.visibility = View.GONE
            displayQR(stdNumber, phone, password)
            startCountDownTimer()
        } else {
            Snackbar.make(requireView(), "QR 코드를 새로고침할 수 없습니다. 설정을 확인해주세요.", Snackbar.LENGTH_SHORT)
                .show()
        }
    }

    private suspend fun refreshQRCodeWithoutCache() {
        val sharedPreferences = activity?.appPreferences
        val stdNumber = sharedPreferences?.getString(AppPrefs.LIBRARY_STD_NUMBER, null)
        val phone = sharedPreferences?.getString(AppPrefs.LIBRARY_PHONE, null)
        val password = activity?.getLibraryPassword()

        if (stdNumber != null && phone != null && password != null) {
            libraryManager.clearCache(stdNumber, phone, password)
            refreshQRCode()
        } else {
            Snackbar.make(requireView(), "QR 코드를 새로고침할 수 없습니다. 설정을 확인해주세요.", Snackbar.LENGTH_SHORT).show()
        }
    }


    private suspend fun displayQR(stdNumber: String, phone: String, password: String) {
        val qrData = libraryManager.getLibraryQrData(stdNumber, phone, password)
        if (qrData != null) {
            displayQrCode(qrData)
        } else {
            if (!isRetry) {
                isRetry = true
                refreshQRCodeWithoutCache()
            } else {
                qrProgressBar.visibility = View.GONE
                qrImg.visibility = View.VISIBLE
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("오류")
                    .setMessage("모바일 학생증 정보를 가져올 수 없습니다.\n모바일 학생증 설정에서 입력한 정보가 올바른지 확인한 후 다시 시도해주세요.")
                    .setPositiveButton("확인") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
            }
        }
    }

    private fun displayQrCode(qrData: JSONObject) {
        if (!qrData.has("qr_code") || qrData.getString("qr_code").length < 5) {
             if (!isRetry) {
                isRetry = true
                lifecycleScope.launch {
                    refreshQRCodeWithoutCache()
                }
            } else {
                qrProgressBar.visibility = View.GONE
                qrImg.visibility = View.VISIBLE
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("오류")
                    .setMessage("모바일 학생증 정보를 가져올 수 없습니다.\n모바일 학생증 설정에서 입력한 정보가 올바른지 확인한 후 다시 시도해주세요.")
                    .setPositiveButton("확인") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
            }
        } else {
            isRetry = false
            val isDarkMode =
                resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

            val qrColor = if (isDarkMode) Color.WHITE else Color.BLACK
            val backgroundColor = Color.TRANSPARENT

            name.text = qrData.getString("user_name")
            numberAndDepartment.text = "광운대학교 ${qrData.getString("user_code").trim()}\n${qrData.getString("user_deptName")} ${qrData.getString("user_patName")}"

            val qrValue = qrData.getString("qr_code")
            val qrgEncoder = QRGEncoder(qrValue, null, QRGContents.Type.TEXT, 200)
            qrgEncoder.colorBlack = qrColor
            qrgEncoder.colorWhite = backgroundColor
            try {
                val bitmap = qrgEncoder.getBitmap(0)
                qrImg.setImageBitmap(bitmap)
                qrProgressBar.visibility = View.GONE
                qrImg.visibility = View.VISIBLE
                val layoutParams = activity?.window?.attributes
                layoutParams?.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
                activity?.window?.attributes = layoutParams
            } catch (e: WriterException) {
                Log.v(TAG, e.toString())
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        val layoutParams = activity?.window?.attributes
        layoutParams?.screenBrightness = originalBrightness
        activity?.window?.attributes = layoutParams

        if (isWidget) {
            activity?.finish()
        }
    }

    override fun onDestroyView() {
        countDownTimer?.cancel()
        super.onDestroyView()
    }

    companion object {
        const val TAG = "LibraryQRModal"
    }
}

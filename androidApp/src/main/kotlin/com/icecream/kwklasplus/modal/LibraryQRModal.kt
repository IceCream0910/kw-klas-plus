package com.icecream.kwklasplus.modal

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
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.lifecycleScope
import androidmads.library.qrgenearator.QRGContents
import androidmads.library.qrgenearator.QRGEncoder
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.icecream.kwklasplus.AppPrefs
import com.icecream.kwklasplus.LibraryQRWidget
import com.icecream.kwklasplus.appDependencies
import com.icecream.kwklasplus.appPreferences
import com.icecream.kwklasplus.core.library.AndroidLibraryService
import com.icecream.kwklasplus.core.library.LibraryQrData
import com.icecream.kwklasplus.core.library.LibraryQrResult
import com.icecream.kwklasplus.feature.library.LibraryQrContent
import com.icecream.kwklasplus.feature.library.LibraryQrUiState
import com.icecream.kwklasplus.getLibraryPassword
import com.icecream.kwklasplus.ui.theme.KlasPlusTheme
import kotlinx.coroutines.launch

class LibraryQRModal : KlasBottomSheetDialogFragment() {
    private val isWidget: Boolean
        get() = arguments?.getBoolean(ARG_IS_WIDGET) == true

    private var originalBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    private lateinit var libraryService: AndroidLibraryService
    private var isRetry = false
    private var countDownTimer: CountDownTimer? = null
    private var uiState by mutableStateOf(LibraryQrUiState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        libraryService = requireContext().appDependencies.libraryService
        uiState = LibraryQrUiState(
            isWidgetEntry = isWidget,
            canAddWidget = !isWidget && !isWidgetAdded(),
        )
    }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        originalBrightness = activity?.window?.attributes?.screenBrightness
            ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                KlasPlusTheme {
                    LibraryQrContent(
                        state = uiState,
                        onRefreshClick = { lifecycleScope.launch { refreshQrCode() } },
                        onSettingsClick = ::showSettingsDialog,
                        onAddWidgetClick = ::requestPinWidget,
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val account = loadAccount()
        if (account == null) {
            showSettingsDialog()
            dismiss()
            return
        }
        lifecycleScope.launch {
            displayQr(account)
            startCountDownTimer()
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

    private fun isWidgetAdded(): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        val component = context?.let { ComponentName(it, LibraryQRWidget::class.java) }
        return manager.getAppWidgetIds(component).isNotEmpty()
    }

    private fun requestPinWidget() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) return
        val provider = context?.let { ComponentName(it, LibraryQRWidget::class.java) } ?: return
        val callback = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, LibraryQRWidget::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        manager.requestPinAppWidget(provider, null, callback)
    }

    private fun showSettingsDialog() {
        LibraryQRSettingsBottomSheetDialog().apply {
            setOnSaveCompleteListener {
                lifecycleScope.launch { refreshQrCode() }
            }
        }.show(parentFragmentManager, LibraryQRSettingsBottomSheetDialog.TAG)
    }

    private fun startCountDownTimer() {
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(REFRESH_INTERVAL, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                uiState = uiState.copy(secondsRemaining = (millisUntilFinished / 1000).toInt())
            }

            override fun onFinish() {
                lifecycleScope.launch { refreshQrCode() }
            }
        }.start()
    }

    private suspend fun refreshQrCode() {
        val account = loadAccount()
        if (account == null) {
            Toast.makeText(
                requireContext(),
                "QR 코드를 새로고침할 수 없습니다. 설정을 확인해주세요.",
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        uiState.bitmap?.recycle()
        uiState = uiState.copy(loading = true, bitmap = null)
        displayQr(account)
        startCountDownTimer()
    }

    private suspend fun refreshQrCodeWithoutCache(account: LibraryAccount) {
        libraryService.clearCache(account.studentNumber, account.phone, account.password)
        refreshQrCode()
    }

    private suspend fun displayQr(account: LibraryAccount) {
        when (
            val result = libraryService.getLibraryQrData(
                account.studentNumber,
                account.phone,
                account.password,
            )
        ) {
            is LibraryQrResult.Success -> displayQrCode(result.data, account)
            else -> retryOrShowError(account)
        }
    }

    private fun displayQrCode(qrData: LibraryQrData, account: LibraryAccount) {
        val values = qrData.values
        val qrValue = values["qr_code"]?.takeIf { it.length >= 5 }
        val userName = values["user_name"]
        val userCode = values["user_code"]
        val department = values["user_deptName"]
        val patternName = values["user_patName"]
        if (
            qrValue == null || userName == null || userCode == null ||
            department == null || patternName == null
        ) {
            lifecycleScope.launch { retryOrShowError(account) }
            return
        }

        isRetry = false
        val darkMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        val encoder = QRGEncoder(qrValue, null, QRGContents.Type.TEXT, 220).apply {
            colorBlack = if (darkMode) Color.WHITE else Color.BLACK
            colorWhite = Color.TRANSPARENT
        }
        val bitmap = runCatching { encoder.getBitmap(0) }.getOrNull()
        if (bitmap == null) {
            lifecycleScope.launch { retryOrShowError(account) }
            return
        }

        uiState = uiState.copy(
            name = userName,
            details = "광운대학교 ${userCode.trim()}\n$department $patternName",
            bitmap = bitmap,
            loading = false,
        )
        activity?.window?.attributes = activity?.window?.attributes?.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
        }
    }

    private suspend fun retryOrShowError(account: LibraryAccount) {
        if (!isRetry) {
            isRetry = true
            refreshQrCodeWithoutCache(account)
            return
        }
        uiState = uiState.copy(loading = false, bitmap = null)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("오류")
            .setMessage(
                "모바일 학생증 정보를 가져올 수 없습니다.\n" +
                    "모바일 학생증 설정에서 입력한 정보가 올바른지 확인한 후 다시 시도해주세요.",
            )
            .setPositiveButton("확인", null)
            .show()
    }

    private fun loadAccount(): LibraryAccount? {
        val preferences = activity?.appPreferences ?: return null
        val studentNumber = preferences.getString(AppPrefs.LIBRARY_STD_NUMBER, null)
        val phone = preferences.getString(AppPrefs.LIBRARY_PHONE, null)
        val password = activity?.getLibraryPassword()
        if (studentNumber.isNullOrBlank() || phone.isNullOrBlank() || password.isNullOrBlank()) {
            return null
        }
        return LibraryAccount(studentNumber, phone, password)
    }

    override fun onDismiss(dialog: DialogInterface) {
        activity?.window?.attributes = activity?.window?.attributes?.apply {
            screenBrightness = originalBrightness
        }
        super.onDismiss(dialog)
        if (isWidget) activity?.finish()
    }

    override fun onDestroyView() {
        countDownTimer?.cancel()
        uiState.bitmap?.recycle()
        uiState = LibraryQrUiState()
        super.onDestroyView()
    }

    private data class LibraryAccount(
        val studentNumber: String,
        val phone: String,
        val password: String,
    )

    companion object {
        const val TAG = "LibraryQRModal"
        private const val ARG_IS_WIDGET = "isWidget"
        private const val REFRESH_INTERVAL = 30_000L

        fun newInstance(isWidget: Boolean) = LibraryQRModal().apply {
            arguments = Bundle().apply { putBoolean(ARG_IS_WIDGET, isWidget) }
        }
    }
}

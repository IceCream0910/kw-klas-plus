package com.icecream.kwklasplus

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MenuInflater
import android.view.MenuItem
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import android.window.OnBackInvokedDispatcher
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.annotation.Nullable
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.icecream.kwklasplus.core.platform.SecureKey
import com.icecream.kwklasplus.core.bridge.BridgeSurface
import com.icecream.kwklasplus.platform.web.AndroidBridgeMessageAdapter
import com.icecream.kwklasplus.platform.bridge.legacy.HomeLegacyBridgeCommandHandler
import com.icecream.kwklasplus.platform.web.AndroidWebSurface
import com.icecream.kwklasplus.platform.web.CalendarBottomSheetImeCoordinator
import com.icecream.kwklasplus.platform.navigation.openLectureRoute
import com.icecream.kwklasplus.platform.navigation.openWebRoute
import com.icecream.kwklasplus.platform.navigation.openTaskRoute
import com.icecream.kwklasplus.core.platform.openValidatedExternalDestination
import com.google.android.gms.common.util.DeviceProperties.isTablet
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.icecream.kwklasplus.core.academic.AcademicSubject
import com.icecream.kwklasplus.core.academic.AcademicTerm
import com.icecream.kwklasplus.core.academic.AcademicTermKey
import com.icecream.kwklasplus.core.academic.AcademicTermSelector
import com.icecream.kwklasplus.core.academic.AcademicTermsResult
import com.icecream.kwklasplus.core.academic.DeadlinesResult
import com.icecream.kwklasplus.core.academic.DeadlinesWebCodec
import com.icecream.kwklasplus.core.academic.TimetableResult
import com.icecream.kwklasplus.core.academic.TimetableWebCodec
import com.icecream.kwklasplus.core.attendance.QrAttendancePayloadCodec
import com.icecream.kwklasplus.core.attendance.QrPreparationRequest
import com.icecream.kwklasplus.core.attendance.QrPreparationResult
import com.icecream.kwklasplus.core.attendance.QrScanLaunchGuard
import com.icecream.kwklasplus.core.network.KlasUserAgent
import com.icecream.kwklasplus.core.platform.PlatformActionResult
import com.icecream.kwklasplus.core.profile.IdCardQrRequest
import com.icecream.kwklasplus.core.profile.IdCardQrResult
import com.icecream.kwklasplus.core.library.LibraryQrResult
import com.icecream.kwklasplus.core.security.SecretValue
import com.icecream.kwklasplus.core.web.JavaScriptArgument
import com.icecream.kwklasplus.core.web.KlasWebAutomationScripts
import com.icecream.kwklasplus.core.web.LegacyWebCallback
import com.icecream.kwklasplus.core.web.LegacyWebScripts
import com.icecream.kwklasplus.modal.LibraryQRModal
import com.icecream.kwklasplus.modal.LibraryQRSettingsBottomSheetDialog
import com.icecream.kwklasplus.modal.MenuBottomSheetDialog
import com.icecream.kwklasplus.modal.YearHakgiBottomSheetDialog
import com.icecream.kwklasplus.ui.theme.KlasPlusTheme
import com.icecream.kwklasplus.ui.dialog.ComposeLoadingDialog
import com.icecream.kwklasplus.ui.web.ComposePlatformViewHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.system.exitProcess

private const val VIEWPORT_LAYOUT_RETRY_LIMIT = 12

class HomeActivity : AppCompatActivity() {
    private val qrScanLaunchGuard = QrScanLaunchGuard()
    private val qrScanLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        qrScanLaunchGuard.release()
    }
    @SuppressLint("MissingInflatedId")
    lateinit var webView: WebView
    private lateinit var webViewContainer: FrameLayout
    internal var currentTab: String = "" // "feed", "timetable", "calendar", "menu"
    private var deadlineForWebview: String = ""
    private var timetableForWebview: String = ""
    lateinit var sessionIdForOtherClass: String
    lateinit var loadingDialog: ComposeLoadingDialog
    lateinit var yearHakgiList: Array<String>
    var yearHakgi: String = ""
    var isOpenWebViewBottomSheet: Boolean = false
    lateinit var onBackPressedCallback: OnBackPressedCallback
    var main: View? = null
    private var isInitialPageLoading by mutableStateOf(true)
    private var backPressedTime: Long = 0L
    private var originalBrightness: Float = -1f
    var isIdCardModalActive: Boolean = false
    private var isBrightnessCaptured: Boolean = false
    private var bridgeMessageAdapter: AndroidBridgeMessageAdapter? = null
    private var webSurface: AndroidWebSurface? = null
    private var isViewportSyncInProgress = false
    private var isViewportSyncPending = false
    private var isViewportSyncDisposed = false
    private var calendarBottomSheetImeCoordinator: CalendarBottomSheetImeCoordinator? = null

    private lateinit var appUpdateManager: AppUpdateManager
    private val MY_REQUEST_CODE = 1001

    private val installStateUpdatedListener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) {
            popupSnackbarForCompleteUpdate()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (com.icecream.kwklasplus.manager.AppLockManager.isAppLockEnabled(this) && !com.icecream.kwklasplus.manager.AppLockManager.isUnlocked) {
            val lockIntent = Intent(this, LockActivity::class.java).apply {
                putExtra("MODE", "UNLOCK")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
            startActivity(lockIntent)
        }

        onBackPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isOpenWebViewBottomSheet) {
                    webView.executeWebScript(KlasWebAutomationScripts.closeBottomSheet())
                } else {
                    if (System.currentTimeMillis() > backPressedTime + 2000) {
                        backPressedTime = System.currentTimeMillis()
                        Toast.makeText(this@HomeActivity, "한 번 더 뒤로 가면 앱이 꺼져요.", Toast.LENGTH_SHORT).show()
                    } else {
                        finishAffinity()
                        exitProcess(0)
                    }
                }
            }
        }
        onBackPressedDispatcher.addCallback(onBackPressedCallback)


        lockPortraitOnPhone()

        val sharedPreferences = appPreferences
        val sessionId = sharedPreferences.getString(AppPrefs.KW_SESSION, null)
        sessionIdForOtherClass = sessionId ?: ""
        if (sessionId == null) {
            showLoginErrorToast()
            finish()
            startActivity(Intent(this@HomeActivity, MainActivity::class.java))
            return
        }

        webView = WebView(this)
        webViewContainer = FrameLayout(this).apply {
            addView(
                webView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
        }
        enableEdgeToEdge()
        setContent {
            KlasPlusTheme {
                ComposePlatformViewHost(
                    contentView = webViewContainer,
                    isLoading = isInitialPageLoading,
                    contentTag = "compose_web_view",
                    applyImePadding = false,
                )
            }
        }
        main = findViewById(android.R.id.content)
        calendarBottomSheetImeCoordinator = main?.let { root ->
            CalendarBottomSheetImeCoordinator(
                rootView = root,
                density = resources.displayMetrics.density,
                onFooterInsetChanged = { insetCssPx ->
                    webView.executeWebScript(
                        KlasWebAutomationScripts.updateCalendarBottomSheetFooterInset(insetCssPx),
                    )
                },
            )
        }
        initSubjectList(sessionId)
        initLoadingDialog()

        // Play In-app Update
        appUpdateManager = AppUpdateManagerFactory.create(this)
        appUpdateManager.registerListener(installStateUpdatedListener)
        checkForUpdates()
    }

    private fun checkForUpdates() {
        val appUpdateInfoTask = appUpdateManager.appUpdateInfo
        appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
            ) {
                appUpdateManager.startUpdateFlowForResult(
                    appUpdateInfo,
                    this,
                    AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build(),
                    MY_REQUEST_CODE
                )
            }
        }
    }

    private fun popupSnackbarForCompleteUpdate() {
        val snackbar = Snackbar.make(
            main ?: webView,
            "업데이트 다운로드가 완료되었습니다.",
            Snackbar.LENGTH_INDEFINITE
        )
        snackbar.setAction("설치") {
            appUpdateManager.completeUpdate()
        }
        snackbar.show()
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 7777) { // 설정 창에서 이동한 경우 새로고침(변경사항 반영 필요)
            val savedYearHakgi = appPreferences.getString(AppPrefs.YEAR_HAKGI, "")
            if (!savedYearHakgi.isNullOrEmpty()) {
                updateYearHakgi(savedYearHakgi)
            }
        }
        if (requestCode == MY_REQUEST_CODE) {
            if (resultCode != RESULT_OK) {
                Log.d("HomeActivity", "Update flow failed! Result code: $resultCode")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hideLoading()

        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.installStatus() == InstallStatus.DOWNLOADED) {
                popupSnackbarForCompleteUpdate()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (isIdCardModalActive) {
            updateSecurityAndBrightness(false)
        }
    }

    override fun onDestroy() {
        isViewportSyncDisposed = true
        calendarBottomSheetImeCoordinator?.dispose()
        calendarBottomSheetImeCoordinator = null
        bridgeMessageAdapter?.dispose()
        bridgeMessageAdapter = null
        webSurface?.dispose()
        webSurface = null
        super.onDestroy()
        if (::appUpdateManager.isInitialized) {
            appUpdateManager.unregisterListener(installStateUpdatedListener)
        }
    }

    private fun initLoadingDialog() {
        loadingDialog = ComposeLoadingDialog(
            context = this,
            allowTouchesOutside = true,
        )
    }

    private fun showLoading() {
        if (!loadingDialog.isShowing) {
            try {
                loadingDialog.show()
            } catch (_: Exception) {
            }
        }
    }

    private fun hideLoading() {
        if (loadingDialog.isShowing) {
            try {
                loadingDialog.dismiss()
            } catch (_: Exception) {
            }
        }
    }

    fun switchToTab(tab: String) {
        if (currentTab == tab && currentTab.isNotEmpty()) return
        if (tab != "calendar") {
            calendarBottomSheetImeCoordinator?.setActive(false)
        }
        currentTab = tab
        val url = when (tab) {
            "feed" -> "${AppUrls.KLAS_PLUS_BASE}/feed?yearHakgi=${yearHakgi}"
            "timetable" -> "${AppUrls.KLAS_PLUS_BASE}/timetableTab?yearHakgi=${yearHakgi}"
            "calendar" -> "${AppUrls.KLAS_PLUS_BASE}/calendar?yearHakgi=${yearHakgi}"
            "menu" -> "${AppUrls.KLAS_PLUS_BASE}/profile"
            else -> "${AppUrls.KLAS_PLUS_BASE}/feed?yearHakgi=${yearHakgi}"
        }

        when (tab) {
            "timetable" -> setupTimetableWebViewClient()
            "calendar" -> setupCalendarWebViewClient()
            else -> setupDefaultWebViewClient()
        }
        webView.loadUrl(url)
        webView.executeWebScript(
            LegacyWebScripts.setLocalStorage("currentYearHakgi", yearHakgi),
        )

        runOnUiThread { webView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
    }

    internal fun setCalendarBottomSheetImeHandling(active: Boolean) {
        calendarBottomSheetImeCoordinator?.setActive(active && currentTab == "calendar")
    }

    fun getCurrentTab(): String {
        if (webView.url?.contains("feed") == true) {
            return "feed"
        } else if (webView.url?.contains("timetable") == true) {
            return "timetable"
        } else if (webView.url?.contains("calendar") == true) {
            return "calendar"
        } else if (webView.url?.contains("profile") == true) {
            return "menu"
        } else {
            return ""
        }
    }

    fun injectDataIntoWebView() {
        webView.executeWebScript(
            LegacyWebScripts.setLocalStorage("currentYearHakgi", yearHakgi),
        )

        hideLoading()
        currentTab = getCurrentTab()

        when (currentTab) {
            "feed" -> sendDeadlineAndTimetableToWebView()
            "timetable" -> {
                setupCalendarWebViewClient()
                val btnText = yearHakgi.replace(",3", ",여름").replace(",4", ",겨울")
                    .replace(",", "년도 ") + "학기"
                webView.executeWebScript(
                    LegacyWebScripts.call(
                        LegacyWebCallback.UPDATE_YEAR_SEMESTER_TEXT,
                        JavaScriptArgument.Text(btnText),
                    ),
                )

                if (timetableForWebview.isNotEmpty()) {
                    webView.executeWebScript(
                        LegacyWebScripts.call(
                            LegacyWebCallback.RECEIVE_TIMETABLE,
                            JavaScriptArgument.Text(timetableForWebview),
                        ),
                    )
                } else {
                    Toast.makeText(this@HomeActivity, "시간표를 불러오는데 실패했습니다.", Toast.LENGTH_SHORT)
                        .show()
                }
            }

            "calendar" -> setupCalendarWebViewClient()
            else -> setupDefaultWebViewClient()
        }
    }

    fun openYearHakgiBottomSheetDialog(isUpdate: Boolean = false) {
        val yearHakgiDialog = YearHakgiBottomSheetDialog(yearHakgiList, isUpdate).apply {
            setSpeedSelectionListener(object :
                YearHakgiBottomSheetDialog.YearHakgiSelectionListener {
                override fun onYearHakgiSelected(value: String) {
                    updateYearHakgi(value)
                }
            })
        }

        yearHakgiDialog.show(supportFragmentManager, YearHakgiBottomSheetDialog.TAG)
    }

    private fun initWebView() {
        webView.post(Runnable {
            val legacyFacade = JavaScriptInterface(this)
            webView.configureAppWebView(javaScriptInterface = legacyFacade)
            webSurface?.dispose()
            webSurface = AndroidWebSurface(webView)
            bridgeMessageAdapter?.dispose()
            bridgeMessageAdapter = AndroidBridgeMessageAdapter(
                webView,
                BridgeSurface.HOME,
                lifecycleScope,
                HomeLegacyBridgeCommandHandler(legacyFacade),
            ).also(AndroidBridgeMessageAdapter::install)
            webView.overScrollMode = WebView.OVER_SCROLL_NEVER

            try {
                val pInfo: PackageInfo =
                    baseContext.packageManager.getPackageInfo(baseContext.packageName, 0)
                val version = pInfo.longVersionCode
                webView.settings.userAgentString += " AndroidApp_v${version}"
            } catch (e: PackageManager.NameNotFoundException) {
                e.printStackTrace()
            }

            setupDefaultWebViewClient()
        })
    }

    private fun setupDefaultWebViewClient() {
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(
                view: WebView?,
                url: String?,
                favicon: android.graphics.Bitmap?
            ) {
                super.onPageStarted(view, url, favicon)
                url?.let { webSurface?.onPageStarted(it) }
                showLoading()
            }

            override fun onPageFinished(view: WebView, url: String) {
                webSurface?.onPageFinished(url)
                if (currentTab == "feed") {
                    sendDeadlineAndTimetableToWebView()
                }
                finishWebPageLoad()
            }

            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                if (url.isTrustedAppWebUrl()) {
                    return false
                } else {
                    if (openValidatedExternalDestination(url) !is PlatformActionResult.Success) {
                        Toast.makeText(
                            this@HomeActivity,
                            "이 링크를 열 수 없습니다.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return true
                }
            }
        }
    }

    private fun setupTimetableWebViewClient() {
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(
                view: WebView?,
                url: String?,
                favicon: android.graphics.Bitmap?
            ) {
                super.onPageStarted(view, url, favicon)
                url?.let { webSurface?.onPageStarted(it) }
            }

            override fun onPageFinished(view: WebView, url: String) {
                webSurface?.onPageFinished(url)
                if (timetableForWebview.isNotEmpty()) {
                    webView.executeWebScript(
                        LegacyWebScripts.call(
                            LegacyWebCallback.RECEIVE_TIMETABLE,
                            JavaScriptArgument.Text(timetableForWebview),
                        ),
                    )
                    showLoading()
                } else {
                    Toast.makeText(this@HomeActivity, "시간표를 불러오는데 실패했습니다.", Toast.LENGTH_SHORT)
                        .show()
                }
                finishWebPageLoad()
            }
        }
    }

    private fun setupCalendarWebViewClient() {
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(
                view: WebView?,
                url: String?,
                favicon: android.graphics.Bitmap?
            ) {
                super.onPageStarted(view, url, favicon)
                url?.let { webSurface?.onPageStarted(it) }
                showLoading()
            }

            override fun onPageFinished(view: WebView, url: String) {
                webSurface?.onPageFinished(url)
                finishWebPageLoad()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onJsAlert(
                view: WebView?,
                url: String?,
                message: String?,
                result: JsResult?
            ): Boolean {
                runOnUiThread {
                    val builder = MaterialAlertDialogBuilder(this@HomeActivity)
                    builder.setTitle("안내")
                        .setMessage(message)
                        .setPositiveButton("확인") { dialog, id ->
                            result?.confirm()
                        }
                        .setCancelable(false)
                        .show()
                }
                return true
            }

            override fun onJsConfirm(
                view: WebView?,
                url: String?,
                message: String?,
                result: JsResult?
            ): Boolean {
                runOnUiThread {
                    val builder = MaterialAlertDialogBuilder(this@HomeActivity)
                    builder.setTitle("안내")
                        .setMessage(message)
                        .setPositiveButton("확인") { dialog, id ->
                            result?.confirm()
                        }
                        .setNegativeButton("취소") { dialog, id ->
                            result?.cancel()
                        }
                        .setCancelable(false)
                        .show()
                }
                return true
            }
        }
    }

    private fun finishWebPageLoad() {
        requestWebViewportSync()
    }

    private fun requestWebViewportSync() {
        if (isViewportSyncDisposed) return
        if (isViewportSyncInProgress) {
            isViewportSyncPending = true
            return
        }
        isViewportSyncInProgress = true
        synchronizeWebViewport()
    }

    private fun synchronizeWebViewport(layoutAttempt: Int = 0) {
        webViewContainer.post {
            if (isViewportSyncDisposed) return@post
            val containerHeight = webViewContainer.height
            if (containerHeight <= 1) {
                if (layoutAttempt < VIEWPORT_LAYOUT_RETRY_LIMIT) {
                    webViewContainer.postOnAnimation {
                        synchronizeWebViewport(layoutAttempt + 1)
                    }
                } else {
                    completeWebViewportSync()
                }
                return@post
            }

            webViewContainer.postOnAnimation {
                if (isViewportSyncDisposed) return@postOnAnimation
                webView.layoutParams = (webView.layoutParams as FrameLayout.LayoutParams).apply {
                    height = containerHeight - 1
                }
                webView.requestLayout()
                webViewContainer.postOnAnimation {
                    if (isViewportSyncDisposed) return@postOnAnimation
                    webView.layoutParams = (webView.layoutParams as FrameLayout.LayoutParams).apply {
                        height = FrameLayout.LayoutParams.MATCH_PARENT
                    }
                    webView.requestLayout()
                    webViewContainer.postOnAnimation(::completeWebViewportSync)
                }
            }
        }
    }

    private fun completeWebViewportSync() {
        if (isViewportSyncDisposed) return
        webView.invalidate()
        webView.executeWebScript(KlasWebAutomationScripts.notifyViewportChanged())
        isInitialPageLoading = false
        hideLoading()
        isViewportSyncInProgress = false
        if (isViewportSyncPending) {
            isViewportSyncPending = false
            requestWebViewportSync()
        }
    }

    private fun sendDeadlineAndTimetableToWebView() {
        webView.executeWebScript(
            LegacyWebScripts.call(
                LegacyWebCallback.RECEIVE_DEADLINE,
                JavaScriptArgument.Text(deadlineForWebview),
            ),
        )
        webView.executeWebScript(
            LegacyWebScripts.call(
                LegacyWebCallback.RECEIVE_TIMETABLE,
                JavaScriptArgument.Text(timetableForWebview),
            ),
        )
        webView.executeWebScript(
            LegacyWebScripts.setLocalStorage("klasSessionToken", sessionIdForOtherClass),
        )
        webView.executeWebScript(
            LegacyWebScripts.setLocalStorage("currentYearHakgi", yearHakgi),
        )
    }


    private fun initSubjectList(sessionId: String) {
        fetchSubjectList(sessionId) { terms ->
            runOnUiThread {
                val listSize = terms.size
                if (listSize == 0) {
                    openWebRoute("${AppUrls.KLAS_PLUS_BASE}/notReady", sessionIdForOtherClass)
                    finish()
                    return@runOnUiThread
                }

                yearHakgiList = terms.map(AcademicTerm::value).toTypedArray()

                val sharedPreferences = appPreferences
                val savedYearHakgi = sharedPreferences.getString(AppPrefs.YEAR_HAKGI, "")
                val savedYearHakgiList = sharedPreferences.getString(AppPrefs.YEAR_HAKGI_LIST, "")

                val editor: SharedPreferences.Editor = sharedPreferences.edit()
                editor.putString(AppPrefs.YEAR_HAKGI_LIST, yearHakgiList.joinToString("&"))
                editor.apply()

                val selection = AcademicTermSelector.select(terms, savedYearHakgi)
                    ?: return@runOnUiThread

                // 학기 정보 변동 시 학기선택 모달 자동으로 띄우기
                if (!savedYearHakgiList.isNullOrEmpty() && yearHakgiList.joinToString("&") != savedYearHakgiList) {
                    openYearHakgiBottomSheetDialog(true)
                }

                val newSubjList = selection.term.subjects
                yearHakgi = selection.term.value
                editor.putString(AppPrefs.YEAR_HAKGI, yearHakgi)
                editor.apply()

                lifecycleScope.launch {
                    launch { getTimetableData(sessionId) }
                    launch { fetchDeadlines(sessionId, newSubjList) }
                }.invokeOnCompletion {
                    runOnUiThread {
                        initWebView()
                        webView.postDelayed({
                            switchToTab("feed")
                            loadingDialog.dismiss()
                        }, 100)
                    }
                }
            }
        }
    }

    private fun updateYearHakgi(selectedYearHakgi: String) {
        yearHakgi = selectedYearHakgi
        val sharedPreferences = appPreferences
        val editor: SharedPreferences.Editor = sharedPreferences.edit()
        editor.putString(AppPrefs.YEAR_HAKGI, yearHakgi)
        editor.apply()
        reloadData()
    }

    private fun reloadData() {
        showLoading()
        val sharedPreferences = appPreferences
        val sessionId = sharedPreferences.getString(AppPrefs.KW_SESSION, null)
        if (sessionId == null) {
            showLoginErrorToast()
            finish()
            startActivity(Intent(this@HomeActivity, MainActivity::class.java))
            return
        }

        fetchSubjectList(sessionId) { terms ->
            val selectedSubjList = resolveSelectedSubjects(terms)
            if (selectedSubjList == null) {
                runOnUiThread { hideLoading() }
                return@fetchSubjectList
            }
            lifecycleScope.launch {
                launch { getTimetableData(sessionId) }
                launch { fetchDeadlines(sessionId, selectedSubjList) }
            }.invokeOnCompletion {
                runOnUiThread {
                    reloadCurrentTab()
                    hideLoading()
                }
            }
        }
    }

    private fun reloadCurrentTab() {
        val currentTabTemp = currentTab
        currentTab = ""
        switchToTab(currentTabTemp)
    }

    fun reload() {
        val root = main ?: webView
        runOnUiThread { root.performHapticFeedback(HapticFeedbackConstants.TOGGLE_ON) }
        showLoading()
        val sharedPreferences = appPreferences
        val sessionId = sharedPreferences.getString(AppPrefs.KW_SESSION, null)
        if (sessionId == null) {
            showLoginErrorToast()
            finish()
            startActivity(Intent(this@HomeActivity, MainActivity::class.java))
            return
        }

        fetchSubjectList(sessionId) { terms ->
            val selectedSubjList = resolveSelectedSubjects(terms)
            if (selectedSubjList == null) {
                runOnUiThread { hideLoading() }
                return@fetchSubjectList
            }
            lifecycleScope.launch {
                launch { getTimetableData(sessionId) }
                launch { fetchDeadlines(sessionId, selectedSubjList) }
            }.invokeOnCompletion {
                runOnUiThread {
                    initWebView()
                    webView.postDelayed({
                        webView.reload()
                        hideLoading()
                    }, 100)
                }
            }
        }
    }

    private fun resolveSelectedSubjects(terms: List<AcademicTerm>): List<AcademicSubject>? {
        val selection = AcademicTermSelector.select(terms, yearHakgi) ?: return null
        if (selection.term.value != yearHakgi) {
            yearHakgi = selection.term.value
            appPreferences.edit().putString(AppPrefs.YEAR_HAKGI, yearHakgi).apply()
        }
        return selection.term.subjects
    }

    private suspend fun fetchDeadlines(sessionId: String, subjects: List<AcademicSubject>) {
        when (
            val result = appDependencies.deadlineRepository.fetch(
                session = SecretValue.of(sessionId),
                userAgent = KlasUserAgent.fromPlatform(WebSettings.getDefaultUserAgent(this)),
                yearSemester = yearHakgi,
                subjects = subjects,
            )
        ) {
            is DeadlinesResult.Success -> {
                deadlineForWebview = DeadlinesWebCodec().encode(result.subjects)
            }
            DeadlinesResult.SessionExpired -> withContext(Dispatchers.Main) {
                showSessionExpiredDialog()
            }
            else -> Unit
        }
    }

    private suspend fun getTimetableData(sessionId: String) {
        val term = AcademicTermKey.parse(yearHakgi) ?: return
        when (
            val result = appDependencies.timetableRepository.fetch(
                session = SecretValue.of(sessionId),
                userAgent = KlasUserAgent.fromPlatform(WebSettings.getDefaultUserAgent(this)),
                year = term.year,
                semester = term.semester,
            )
        ) {
            is TimetableResult.Success -> {
                timetableForWebview = TimetableWebCodec().encode(result.entriesBySubject)
            }
            TimetableResult.SessionExpired -> withContext(Dispatchers.Main) {
                showSessionExpiredDialog()
            }
            else -> Unit
        }
    }

    fun openQRActivity(sessionId: String, subjID: String, subjName: String) {
        if (!qrScanLaunchGuard.tryAcquire()) return
        showLoading()
        if (sessionId.isBlank() || subjID.isBlank() || subjName.isBlank()) {
            hideLoading()
            qrScanLaunchGuard.release()
            Toast.makeText(this, "출석 정보를 확인하지 못했습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            var scannerLaunched = false
            try {
                val result = appDependencies.attendanceRepository.prepareCheckIn(
                    session = SecretValue.of(sessionId),
                    userAgent = KlasUserAgent.fromPlatform(WebSettings.getDefaultUserAgent(this@HomeActivity)),
                    request = QrPreparationRequest(
                        year = getCurrentYear(),
                        semester = getCurrentSemester(),
                        subjectId = subjID,
                        subjectName = subjName,
                    ),
                )
                when (result) {
                    is QrPreparationResult.Success -> {
                        val intent = Intent(this@HomeActivity, QRScanActivity::class.java)
                        intent.putExtra(
                            IntentExtras.BODY_JSON,
                            QrAttendancePayloadCodec().encode(result.payload),
                        )
                        intent.putExtra(IntentExtras.SUBJECT_ID, subjID)
                        intent.putExtra(IntentExtras.SUBJECT_NAME, subjName)
                        intent.putExtra(IntentExtras.SESSION_ID, sessionId)
                        hideLoading()
                        qrScanLauncher.launch(intent)
                        scannerLaunched = true
                    }
                    QrPreparationResult.UnsupportedSubject -> Toast.makeText(
                        this@HomeActivity,
                        "QR출석이 지원되지 않는 강의입니다.",
                        Toast.LENGTH_SHORT,
                    ).show()
                    QrPreparationResult.SessionExpired -> showSessionExpiredDialog()
                    else -> Toast.makeText(
                        this@HomeActivity,
                        "출석 정보를 불러오지 못했습니다. 잠시 후 다시 시도해주세요.",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            } finally {
                if (!scannerLaunched) {
                    hideLoading()
                    qrScanLaunchGuard.release()
                }
            }
        }
    }

    fun openLectureActivity(
        sessionId: String, subjID: String, subjName: String
    ) {
        openLectureRoute(subjID, subjName, yearHakgi, sessionId)
    }

    private fun fetchSubjectList(sessionId: String, callback: (List<AcademicTerm>) -> Unit) {
        lifecycleScope.launch {
            when (
                val result = appDependencies.academicRepository.fetchTerms(
                    SecretValue.of(sessionId),
                    KlasUserAgent.fromPlatform(WebSettings.getDefaultUserAgent(this@HomeActivity)),
                )
            ) {
                is AcademicTermsResult.Success -> callback(result.terms)
                AcademicTermsResult.SessionExpired -> {
                    loadingDialog.dismiss()
                    showSessionExpiredDialog()
                }
                else -> {
                    loadingDialog.dismiss()
                    Toast.makeText(
                        this@HomeActivity,
                        "수강과목 정보를 불러오지 못했습니다. 잠시 후 다시 시도해주세요.",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    private fun getCurrentYear(): String {
        return AcademicTermKey.parse(yearHakgi)?.year
            ?: Calendar.getInstance().get(Calendar.YEAR).toString()
    }

    private fun getCurrentSemester(): String {
        AcademicTermKey.parse(yearHakgi)?.let { return it.semester }
        val currentMonth = Calendar.getInstance().get(Calendar.MONTH)
        return if (currentMonth < 7) "1" else "2" // 8월 기준
    }

    fun requestIdCardQRValue() {
        isIdCardModalActive = true
        updateSecurityAndBrightness(true)
        var idCardQR = "pending"
        var libraryQR = "pending"

        fun notifyWebView() {
            webView.executeWebScript(
                LegacyWebScripts.call(
                    LegacyWebCallback.RECEIVE_ID_CARD_QR,
                    JavaScriptArgument.Text(libraryQR),
                    JavaScriptArgument.Text(idCardQR),
                ),
            )
        }

        lifecycleScope.launch {
            launch {
                idCardQR = fetchIdCardQRFromWebView()
                notifyWebView()
            }

            launch {
                libraryQR = try {
                    fetchLibraryQRCodeValue()
                } catch (e: Exception) {
                    Log.e("HomeActivity", "fetchLibraryQRCodeValue error: ${e.message}")
                    ""
                }
                notifyWebView()
            }
        }
    }

    private suspend fun fetchIdCardQRFromWebView(): String = suspendCancellableCoroutine { continuation ->
        val bgWebView = WebView(this@HomeActivity)
        bgWebView.settings.javaScriptEnabled = true

        bgWebView.webViewClient = object : WebViewClient() {
            private var isFinished = false
            private var requestStarted = false

            override fun shouldInterceptRequest(
                view: WebView?,
                request: android.webkit.WebResourceRequest?
            ): android.webkit.WebResourceResponse? {
                val url = request?.url?.toString() ?: ""
                if (
                    !requestStarted &&
                    url.contains("myidv2_main.php") &&
                    url.contains("menu=qid")
                ) {
                    requestStarted = true
                    val cookies = android.webkit.CookieManager.getInstance().getCookie(url)
                    if (cookies.isNullOrBlank()) {
                        finishWith("")
                    } else {
                        lifecycleScope.launch {
                            val result = appDependencies.idCardQrRepository.fetch(
                                IdCardQrRequest(url, SecretValue.of(cookies)),
                            )
                            finishWith(
                                (result as? IdCardQrResult.Success)?.value.orEmpty(),
                            )
                        }
                    }
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onReceivedError(
                view: WebView?,
                request: android.webkit.WebResourceRequest?,
                error: android.webkit.WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    finishWith("")
                }
            }

            private fun finishWith(value: String) {
                if (!isFinished) {
                    isFinished = true
                    if (continuation.isActive) {
                        continuation.resume(value)
                    }
                    bgWebView.post { bgWebView.destroy() }
                }
            }
        }

        continuation.invokeOnCancellation {
            bgWebView.post { bgWebView.destroy() }
        }
        bgWebView.loadUrl("https://klas.kw.ac.kr/mst/sys/optrn/MyNumberQrStdPage.do")
    }

    private suspend fun fetchLibraryQRCodeValue(): String = withContext(Dispatchers.IO) {
        val sharedPreferences = appPreferences
        val stdNumber = sharedPreferences.getString(AppPrefs.LIBRARY_STD_NUMBER, null)
        val phone = sharedPreferences.getString(AppPrefs.LIBRARY_PHONE, null)
        val password = getLibraryPassword()

        if (stdNumber == null || phone == null || password == null) return@withContext ""

        val result = appDependencies.libraryService
            .getLibraryQrData(stdNumber, phone, password)
        (result as? LibraryQrResult.Success)?.data?.values?.get("qr_code").orEmpty()
    }

    fun openLibraryQRModal() {
        val modal = LibraryQRModal.newInstance(false)
        modal.setStyle(DialogFragment.STYLE_NORMAL, R.style.RoundCornerBottomSheetDialogTheme)
        modal.show(supportFragmentManager, LibraryQRModal.TAG)
    }


    private fun showLoginErrorToast() {
        Toast.makeText(this, "인증에 실패했습니다.", Toast.LENGTH_SHORT).show()
    }

    fun logout() {
        val builder = MaterialAlertDialogBuilder(this)
        builder.setTitle("로그아웃")
            .setMessage("정말 로그아웃할까요?")
            .setPositiveButton("확인") { _, _ ->
                lifecycleScope.launch {
                    appDependencies.sessionCoordinator.expire()
                    runCatching {
                        appDependencies.secureStore.remove(SecureKey.ENCRYPTED_KLAS_PASSWORD)
                    }
                    appPreferences.edit().clear().apply()
                    encryptedPreferences.edit().remove(AppPrefs.KW_PASSWORD).apply()
                    libraryQrCachePreferences.edit().clear().apply()
                    finish()
                    startActivity(Intent(this@HomeActivity, LoginActivity::class.java))
                }
            }
            .setNegativeButton("취소") { _, _ -> }
        builder.show()
    }

    private fun showSessionExpiredDialog() {
        val builder = MaterialAlertDialogBuilder(this)
        builder.setTitle("인증 오류")
            .setMessage("로그인 후 일정 시간이 지나 세션이 만료되었어요. 앱을 재시작하면 정상적으로 정보가 표시될 거예요.")
            .setPositiveButton(
                "종료"
            ) { _, _ ->
                lifecycleScope.launch {
                    appDependencies.sessionCoordinator.expire()
                    finish()
                }
            }
        builder.show()
    }

    fun showDatePicker(calendar: Calendar, isStart: Boolean) {
        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("날짜 선택")
            .setSelection(calendar.timeInMillis)
            .build()

        datePicker.show(supportFragmentManager, "DATE_PICKER")

        datePicker.addOnPositiveButtonClickListener { dateInMillis ->
            calendar.timeInMillis = dateInMillis
            showTimePicker(calendar, isStart)
        }
    }

    private fun showTimePicker(calendar: Calendar, isStart: Boolean) {
        val timePicker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setTitleText("시간 선택")
            .setHour(calendar.get(Calendar.HOUR_OF_DAY))
            .setMinute(calendar.get(Calendar.MINUTE))
            .build()

        timePicker.show(supportFragmentManager, "TIME_PICKER")

        timePicker.addOnPositiveButtonClickListener {
            calendar.set(Calendar.HOUR_OF_DAY, timePicker.hour)
            calendar.set(Calendar.MINUTE, timePicker.minute)

            val selectedDateTime =
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault()).format(calendar.time)
            webView.executeWebScript(
                LegacyWebScripts.call(
                    LegacyWebCallback.SET_DATE_TIME,
                    JavaScriptArgument.Text(selectedDateTime),
                    JavaScriptArgument.BooleanValue(isStart),
                ),
            )
        }
    }

    fun updateSecurityAndBrightness(enabled: Boolean) {
        runOnUiThread {
            val layoutParams = window.attributes
            if (enabled) {
                if (!isBrightnessCaptured) {
                    originalBrightness = layoutParams.screenBrightness
                    isBrightnessCaptured = true
                }
                layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            } else {
                if (isBrightnessCaptured) {
                    layoutParams.screenBrightness = originalBrightness
                    isBrightnessCaptured = false
                    originalBrightness = -1f
                }
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            window.attributes = layoutParams
        }
    }
}

class JavaScriptInterface(private val homeActivity: HomeActivity) {
    @JavascriptInterface
    fun changeTab(tab: String) {
        homeActivity.runOnUiThread {
            homeActivity.switchToTab(tab)
        }
    }

    @JavascriptInterface
    fun evaluate(url: String, yearHakgi: String, subj: String) {
        homeActivity.runOnUiThread {
            homeActivity.openTaskRoute(url, subj, yearHakgi, homeActivity.sessionIdForOtherClass)
        }
    }

    @JavascriptInterface
    fun openPage(url: String) {
        homeActivity.runOnUiThread {
            homeActivity.openWebRoute(url, homeActivity.sessionIdForOtherClass)
        }
    }

    @JavascriptInterface
    fun openExternalPage(url: String) {
        homeActivity.runOnUiThread {
            homeActivity.openValidatedExternalDestination(url)
        }
    }

    @JavascriptInterface
    fun completePageLoad() {
        homeActivity.runOnUiThread {
            homeActivity.webView.executeWebScript(
                LegacyWebScripts.call(
                    LegacyWebCallback.RECEIVE_TOKEN,
                    JavaScriptArgument.Text(homeActivity.sessionIdForOtherClass),
                ),
            )
            homeActivity.injectDataIntoWebView()
        }
    }

    @JavascriptInterface
    fun openLibraryQR() {
        homeActivity.runOnUiThread {
            homeActivity.openLibraryQRModal()
        }
    }

    @JavascriptInterface
    fun openLibraryQRSettingsModal() {
        homeActivity.runOnUiThread {
            val settingsModal = com.icecream.kwklasplus.modal.LibraryQRSettingsBottomSheetDialog()
            settingsModal.setOnSaveCompleteListener {
                homeActivity.requestIdCardQRValue()
            }
            settingsModal.show(homeActivity.supportFragmentManager, "LibraryQRSettingsModal")
        }
    }

    @JavascriptInterface
    fun openLectureActivity(subj: String, subjName: String) {
        homeActivity.runOnUiThread {
            homeActivity.loadingDialog.show()
            homeActivity.openLectureActivity(homeActivity.sessionIdForOtherClass, subj, subjName)
        }
    }

    @JavascriptInterface
    fun qrCheckIn(subjID: String, subjName: String) {
        homeActivity.runOnUiThread {
            homeActivity.openQRActivity(homeActivity.sessionIdForOtherClass, subjID, subjName)
        }
    }

    @JavascriptInterface
    fun openDateTimePicker(currentDateTime: String?, isStart: Boolean) {
        val calendar = Calendar.getInstance()

        if (!currentDateTime.isNullOrEmpty()) {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())
            dateFormat.parse(currentDateTime)?.let {
                calendar.time = it
            }
        }

        homeActivity.showDatePicker(calendar, isStart)
    }

    @JavascriptInterface
    fun openWebViewBottomSheet() {
        homeActivity.runOnUiThread {
            homeActivity.isOpenWebViewBottomSheet = true
            homeActivity.setCalendarBottomSheetImeHandling(true)
        }
    }

    @JavascriptInterface
    fun closeWebViewBottomSheet() {
        homeActivity.runOnUiThread {
            if (homeActivity.isIdCardModalActive) {
                homeActivity.updateSecurityAndBrightness(false)
                homeActivity.isIdCardModalActive = false
            }
            homeActivity.isOpenWebViewBottomSheet = false
            homeActivity.setCalendarBottomSheetImeHandling(false)
            try {
                homeActivity.webView.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @JavascriptInterface
    fun openOptionsMenu() {
        homeActivity.runOnUiThread {
            homeActivity.main?.let {
                MenuBottomSheetDialog().show(
                    homeActivity.supportFragmentManager,
                    MenuBottomSheetDialog.TAG
                )
            }
        }
    }

    @JavascriptInterface
    fun openYearHakgiBottomSheet() {
        homeActivity.runOnUiThread {
            homeActivity.openYearHakgiBottomSheetDialog()
        }
    }

    @JavascriptInterface
    fun reload() {
        homeActivity.runOnUiThread {
            homeActivity.reload()
        }
    }

    @JavascriptInterface
    fun performHapticFeedback(type: String) {
        homeActivity.runOnUiThread {
            homeActivity.appDependencies.haptics(homeActivity.webView).performLegacy(type)
        }
    }

    @JavascriptInterface
    fun requestIdCardQRValue() {
        homeActivity.runOnUiThread {
            homeActivity.requestIdCardQRValue()
        }
    }
}

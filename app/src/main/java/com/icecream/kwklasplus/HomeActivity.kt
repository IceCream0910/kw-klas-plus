package com.icecream.kwklasplus

import LibraryQRModal
import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
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
import android.widget.LinearLayout
import android.widget.PopupMenu
import com.google.android.material.loadingindicator.LoadingIndicator
import android.widget.TextView
import android.widget.Toast
import android.window.OnBackInvokedDispatcher
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.annotation.Nullable
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.DialogFragment
import com.github.tlaabs.timetableview.Schedule
import com.github.tlaabs.timetableview.Time
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
import com.icecream.kwklasplus.modal.MenuBottomSheetDialog
import com.icecream.kwklasplus.modal.WebViewBottomSheetDialog
import com.icecream.kwklasplus.modal.YearHakgiBottomSheetDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess


class HomeActivity : AppCompatActivity() {
    @SuppressLint("MissingInflatedId")
    lateinit var webView: WebView
    internal var currentTab: String = "" // "feed", "timetable", "calendar", "menu"
    private var deadlineForWebview: String = ""
    private var timetableForWebview: String = ""
    lateinit var sessionIdForOtherClass: String
    lateinit var loadingDialog: AlertDialog
    var subjList: JSONArray = JSONArray()
    lateinit var yearHakgiList: Array<String>
    var yearHakgi: String = ""
    var isKeyboardShowing = false
    var isOpenWebViewBottomSheet: Boolean = false
    lateinit var onBackPressedCallback: OnBackPressedCallback
    var main: androidx.appcompat.widget.LinearLayoutCompat? = null
    private var webViewOriginalHeight: Int = ViewGroup.LayoutParams.MATCH_PARENT

    private lateinit var appUpdateManager: AppUpdateManager
    private val MY_REQUEST_CODE = 1001

    private val installStateUpdatedListener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) {
            popupSnackbarForCompleteUpdate()
        }
    }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            val imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom + 80
            adjustWebViewHeightForIme(imeVisible, imeHeight)
            insets
        }

        main = findViewById(R.id.main)

        onBackPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isOpenWebViewBottomSheet) {
                    webView.evaluateJavascript("window.closeWebViewBottomSheet();", null)
                } else {
                    finishAffinity()
                    exitProcess(0)
                }
            }
        }
        onBackPressedDispatcher.addCallback(onBackPressedCallback)


        if (isTablet(this)) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        val sharedPreferences = getSharedPreferences("com.icecream.kwklasplus", MODE_PRIVATE)
        val sessionId = sharedPreferences.getString("kwSESSION", null)
        sessionIdForOtherClass = sessionId ?: ""
        if (sessionId == null) {
            showLoginErrorToast()
            finish()
            startActivity(Intent(this@HomeActivity, MainActivity::class.java))
            return
        }

        webViewOriginalHeight = ViewGroup.LayoutParams.MATCH_PARENT

        webView = findViewById(R.id.webView)
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
            findViewById(R.id.main),
            "업데이트 다운로드가 완료되었습니다.",
            Snackbar.LENGTH_INDEFINITE
        )
        snackbar.setAction("설치") {
            appUpdateManager.completeUpdate()
        }
        snackbar.show()
    }

    private fun adjustWebViewHeightForIme(visible: Boolean, imeHeight: Int) {
        // 태블릿은 레이아웃 변경 없이 리턴
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        val widthDp = metrics.widthPixels / metrics.density
        if (widthDp >= 600) return

        val lp = webView.layoutParams ?: return
        if (visible) {
            isKeyboardShowing = true
            lp.height = (webView.rootView.height - imeHeight)
            if (lp.height < 0) lp.height = webViewOriginalHeight
        } else {
            isKeyboardShowing = false
            lp.height = webViewOriginalHeight
        }
        webView.layoutParams = lp
        webView.requestLayout()
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 7777) { // 설정 창에서 이동한 경우 새로고침(변경사항 반영 필요)
            val savedYearHakgi = getSharedPreferences("com.icecream.kwklasplus", MODE_PRIVATE)
                .getString("yearHakgi", "")
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

    override fun onDestroy() {
        super.onDestroy()
        appUpdateManager.unregisterListener(installStateUpdatedListener)
    }

    private fun initLoadingDialog() {
        val builder = MaterialAlertDialogBuilder(this)
        builder.setView(R.layout.layout_loading_dialog)
        builder.setCancelable(false)
        loadingDialog = builder.create()
        loadingDialog.setOnShowListener {
            loadingDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            loadingDialog.window?.addFlags(
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
            )
        }
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
        currentTab = tab
        val url = when (tab) {
            "feed" -> "https://klasplus.yuntae.in/feed?yearHakgi=${yearHakgi}"
            "timetable" -> "https://klasplus.yuntae.in/timetableTab?yearHakgi=${yearHakgi}"
            "calendar" -> "https://klasplus.yuntae.in/calendar?yearHakgi=${yearHakgi}"
            "menu" -> "https://klasplus.yuntae.in/profile"
            else -> "https://klasplus.yuntae.in/feed?yearHakgi=${yearHakgi}"
        }

        Log.d("HomeActivity", "Switching to tab: $tab, URL: $url")
        webView.loadUrl(url)
        webView.evaluateJavascript(
            "javascript:window.localStorage.setItem('currentYearHakgi', '$yearHakgi')",
            null
        )

        runOnUiThread { webView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }

        when (tab) {
            "timetable" -> setupTimetableWebViewClient()
            "calendar" -> setupCalendarWebViewClient()
            else -> setupDefaultWebViewClient()
        }
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
        webView.evaluateJavascript(
            "javascript:window.localStorage.setItem('currentYearHakgi', '$yearHakgi')",
            null
        )

        hideLoading()
        currentTab = getCurrentTab()

        when (currentTab) {
            "feed" -> sendDeadlineAndTimetableToWebView()
            "timetable" -> {
                setupCalendarWebViewClient()
                val btnText = yearHakgi.replace(",3", ",여름").replace(",4", ",겨울")
                    .replace(",", "년도 ") + "학기"
                webView.evaluateJavascript(
                    "javascript:window.updateYearHakgiBtnText('${btnText}')",
                    null
                )

                if (timetableForWebview.isNotEmpty()) {
                    webView.evaluateJavascript(
                        "javascript:receiveTimetableData(`${timetableForWebview}`)",
                        null
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
            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true
            webView.isVerticalScrollBarEnabled = false
            webView.isHorizontalScrollBarEnabled = false
            webView.overScrollMode = WebView.OVER_SCROLL_NEVER
            webView.setBackgroundColor(0)
            webView.addJavascriptInterface(JavaScriptInterface(this), "Android")

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
            val webViewProgress = findViewById<LoadingIndicator>(R.id.progressBar_webview)
            override fun onPageStarted(
                view: WebView?,
                url: String?,
                favicon: android.graphics.Bitmap?
            ) {
                super.onPageStarted(view, url, favicon)
                showLoading()
            }

            override fun onPageFinished(view: WebView, url: String) {
                Log.d("HomeActivity", "Page finished loading: $url, currentTab: $currentTab")
                if (currentTab == "feed") {
                    sendDeadlineAndTimetableToWebView()
                }
                webView.visibility = View.VISIBLE
                webViewProgress.visibility = View.GONE
                hideLoading()
            }

            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                if (url.contains("klasplus.yuntae.in")) {
                    return false
                } else {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        startActivity(intent)
                    } catch (e: Exception) {
                        e.printStackTrace()
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
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (timetableForWebview.isNotEmpty()) {
                    webView.evaluateJavascript(
                        "javascript:receiveTimetableData(`${timetableForWebview}`)",
                        null
                    )
                    showLoading()
                } else {
                    Toast.makeText(this@HomeActivity, "시간표를 불러오는데 실패했습니다.", Toast.LENGTH_SHORT)
                        .show()
                }
                webView.visibility = View.VISIBLE
                hideLoading()
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
                showLoading()
            }

            override fun onPageFinished(view: WebView, url: String) {
                webView.visibility = View.VISIBLE
                hideLoading()
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

    private fun sendDeadlineAndTimetableToWebView() {
        webView.evaluateJavascript(
            "javascript:window.receiveDeadlineData(`$deadlineForWebview`)",
            null
        )
        webView.evaluateJavascript(
            "javascript:window.receiveTimetableData(`$timetableForWebview`)",
            null
        )
        webView.evaluateJavascript(
            "javascript:window.localStorage.setItem('klasSessionToken', '$sessionIdForOtherClass')",
            null
        )
        webView.evaluateJavascript(
            "javascript:window.localStorage.setItem('currentYearHakgi', '$yearHakgi')",
            null
        )
    }


    private fun initSubjectList(sessionId: String) {
        fetchSubjectList(sessionId) { jsonArray ->
            runOnUiThread {
                yearHakgiList = Array(jsonArray.length()) { "" }

                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    yearHakgiList[i] = jsonObject.getString("value")
                }

                if (yearHakgiList.isEmpty()) {
                    val intent = Intent(this, LinkViewActivity::class.java)
                    intent.putExtra("url", "https://klasplus.yuntae.in/notReady")
                    intent.putExtra("sessionID", sessionIdForOtherClass)
                    startActivity(intent)
                    finish()
                }

                val sharedPreferences =
                    getSharedPreferences("com.icecream.kwklasplus", MODE_PRIVATE)
                val savedYearHakgi = sharedPreferences.getString("yearHakgi", "")
                val savedYearHakgiList = sharedPreferences.getString("yearHakgiList", "")

                val editor: SharedPreferences.Editor = sharedPreferences.edit()
                editor.putString("yearHakgiList", yearHakgiList.joinToString("&"))
                editor.apply()

                var index = 0

                if (!savedYearHakgi.isNullOrEmpty()) {
                    index = yearHakgiList.indexOf(savedYearHakgi)
                }

                // 학기 정보 변동 시 학기선택 모달 자동으로 띄우기
                if (!savedYearHakgiList.isNullOrEmpty() && yearHakgiList.joinToString("&") != savedYearHakgiList) {
                    openYearHakgiBottomSheetDialog(true)
                }

                val jsonObject = jsonArray.getJSONObject(index)
                val newSubjList = jsonObject.getJSONArray("subjList")
                yearHakgi = jsonObject.getString("value")
                editor.putString("yearHakgi", yearHakgi)
                editor.apply()

                CoroutineScope(Dispatchers.IO).launch {
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
        val sharedPreferences = getSharedPreferences("com.icecream.kwklasplus", MODE_PRIVATE)
        val editor: SharedPreferences.Editor = sharedPreferences.edit()
        editor.putString("yearHakgi", yearHakgi)
        editor.apply()
        reloadData()
    }

    private fun reloadData() {
        showLoading()
        val sharedPreferences = getSharedPreferences("com.icecream.kwklasplus", MODE_PRIVATE)
        val sessionId = sharedPreferences.getString("kwSESSION", null)
        if (sessionId == null) {
            showLoginErrorToast()
            finish()
            startActivity(Intent(this@HomeActivity, MainActivity::class.java))
            return
        }

        fetchSubjectList(sessionId) { jsonArray ->
            val jsonObject = jsonArray.getJSONObject(0)
            val subjList = jsonObject.getJSONArray("subjList")
            CoroutineScope(Dispatchers.IO).launch {
                launch { getTimetableData(sessionId) }
                launch { fetchDeadlines(sessionId, subjList) }
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
        val root = findViewById<View>(R.id.main)
        runOnUiThread { root.performHapticFeedback(HapticFeedbackConstants.TOGGLE_ON) }
        showLoading()
        val sharedPreferences = getSharedPreferences("com.icecream.kwklasplus", MODE_PRIVATE)
        val sessionId = sharedPreferences.getString("kwSESSION", null)
        if (sessionId == null) {
            showLoginErrorToast()
            finish()
            startActivity(Intent(this@HomeActivity, MainActivity::class.java))
            return
        }

        fetchSubjectList(sessionId) { jsonArray ->
            val jsonObject = jsonArray.getJSONObject(0)
            val subjList = jsonObject.getJSONArray("subjList")
            CoroutineScope(Dispatchers.IO).launch {
                launch { getTimetableData(sessionId) }
                launch { fetchDeadlines(sessionId, subjList) }
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

    private suspend fun fetchDeadlines(sessionId: String, subjList: JSONArray) {
        val deadline = ArrayList<JSONObject>()
        val client = OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        val jobList = mutableListOf<Job>()

        for (i in 0 until subjList.length()) {
            val subjItem = subjList.getJSONObject(i)
            val subjID = subjItem.getString("value")
            val subjName = subjItem.getString("name")

            val job = CoroutineScope(Dispatchers.IO).launch {
                val json = JSONObject()
                    .put("selectChangeYn", "Y")
                    .put("selectYearhakgi", "${getCurrentYear()},${getCurrentSemester()}")
                    .put("selectSubj", subjID)
                val requestBody =
                    RequestBody.create("application/json".toMediaTypeOrNull(), json.toString())
                val urls = listOf(
                    "https://klas.kw.ac.kr/std/lis/evltn/SelectOnlineCntntsStdList.do",
                    "https://klas.kw.ac.kr/std/lis/evltn/TaskStdList.do",
                    "https://klas.kw.ac.kr/std/lis/evltn/PrjctStdList.do"
                )

                val subjDeadline = JSONObject()
                    .put("name", subjName)
                    .put("subj", subjID)
                    .put("onlineLecture", JSONArray())
                    .put("task", JSONArray())
                    .put("teamTask", JSONArray())

                for (j in urls.indices) {
                    val request = buildRequest(urls[j], sessionId, requestBody)
                    val response = client.newCall(request).execute()
                    val responseBody = response.body?.string()

                    if (responseBody != null) {
                        when (j) {
                            0 -> parseOnlineLecture(subjDeadline, responseBody)
                            1 -> parseHomework(subjDeadline, responseBody, "HW")
                            2 -> parseHomework(subjDeadline, responseBody, "TP")
                        }
                    }
                }
                synchronized(deadline) {
                    deadline.add(subjDeadline)
                }
            }
            jobList.add(job)
        }
        jobList.joinAll()
        deadlineForWebview = deadline.toString()
    }

    private fun parseOnlineLecture(
        subjDeadline: JSONObject,
        responseData: String
    ) {
        val nowDate = Date()
        val lectureArray = JSONArray(responseData)
        for (i in 0 until lectureArray.length()) {
            val lecture = lectureArray.getJSONObject(i)
            if (lecture.getString("evltnSe") == "lesson" && lecture.getInt("prog") < 100) {
                val endDate =
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(lecture.getString("endDate") + ":59")
                val hourGap = ((endDate.time - nowDate.time) / 3600000).toInt()

                if (hourGap >= 0) {
                    val onlineLectureItem = JSONObject()
                        .put("startDate", lecture.getString("startDate"))
                        .put("endDate", lecture.getString("endDate"))
                        .put("hourGap", hourGap)
                    subjDeadline.getJSONArray("onlineLecture").put(onlineLectureItem)
                }
            }
        }
    }

    private fun parseHomework(
        subjDeadline: JSONObject,
        responseData: String,
        homeworkType: String
    ) {
        val nowDate = Date()
        val homeworkArray = JSONArray(responseData)
        for (i in 0 until homeworkArray.length()) {
            val homework = homeworkArray.getJSONObject(i)
            if (homework.getString("submityn") != "Y") {
                val expireDateString = homework.getString("expiredate")
                val endDate = try {
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").parse(expireDateString)
                } catch (e: Exception) {
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(expireDateString)
                }
                val hourGap = ((endDate.time - nowDate.time) / 3600000).toInt()

                if (hourGap >= 0) {
                    val homeworkItem = JSONObject()
                        .put("startDate", homework.getString("startdate"))
                        .put("endDate", homework.getString("expiredate"))
                        .put("hourGap", hourGap)
                    val jsonArray =
                        if (homeworkType == "HW") subjDeadline.getJSONArray("task") else subjDeadline.getJSONArray(
                            "teamTask"
                        )
                    jsonArray.put(homeworkItem)
                }
            }
        }
    }

    private fun getTimetableData(sessionId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val client = OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val json = JSONObject()
                .put("list", JSONArray())
                .put("searchYear", getCurrentYear())
                .put("searchHakgi", getCurrentSemester())
                .put("atnlcYearList", JSONArray())
                .put("timeTableList", JSONArray())

            val requestBody =
                RequestBody.create("application/json".toMediaTypeOrNull(), json.toString())

            val request = buildRequest(
                "https://klas.kw.ac.kr/std/cps/atnlc/TimetableStdList.do",
                sessionId,
                requestBody
            )

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (responseBody != null) {
                val jsonArray = JSONArray(responseBody)
                val courseSchedulesMap = HashMap<String, ArrayList<Schedule>>()

                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val wtTime = jsonObject.getInt("wtTime")
                    if (jsonObject.getString("wtHasSchedule") == "N") {
                        continue
                    } else {
                        for (k in 1..6) { // 요일(월~토)
                            val subjKey = "wtSubj_$k"
                            val subjNmKey = "wtSubjNm_$k"
                            val locHnameKey = "wtLocHname_$k"
                            val profNmKey = "wtProfNm_$k"
                            val wtSpanKey = "wtSpan_$k"

                            if (jsonObject.has(subjKey) && jsonObject.has(subjNmKey) && jsonObject.has(
                                    locHnameKey
                                )
                            ) {
                                val courseTitle = jsonObject.getString(subjNmKey)
                                val courseKey = jsonObject.getString(subjKey)
                                val coursePlace = jsonObject.getString(locHnameKey)
                                val courseProfessor = jsonObject.getString(profNmKey)

                                val schedule = Schedule()
                                schedule.classTitle = courseTitle
                                schedule.classPlace = "$coursePlace/$courseProfessor"
                                schedule.professorName = courseKey
                                schedule.day = k - 1

                                val (startHour, startMinute) = getStartTime(wtTime)
                                schedule.startTime = Time(startHour, startMinute)

                                val wtSpan =
                                    if (jsonObject.has(wtSpanKey)) jsonObject.getInt(wtSpanKey) else 1
                                val (endHour, endMinute) = getEndTime(wtTime + wtSpan - 1)
                                schedule.endTime = Time(endHour, endMinute)

                                if (courseSchedulesMap.containsKey(courseKey)) {
                                    courseSchedulesMap[courseKey]?.add(schedule)
                                } else {
                                    val schedules = ArrayList<Schedule>()
                                    schedules.add(schedule)
                                    courseSchedulesMap[courseKey] = schedules
                                }
                            }
                        }
                    }
                }

                val jsonObject = JSONObject()
                for ((key, schedules) in courseSchedulesMap) {
                    val jsonArray = JSONArray()
                    for (schedule in schedules) {
                        val scheduleJson = JSONObject()
                        scheduleJson.put("title", schedule.classTitle)
                        scheduleJson.put("day", schedule.day)
                        scheduleJson.put(
                            "startTime",
                            "${schedule.startTime.hour}:${schedule.startTime.minute}"
                        )
                        scheduleJson.put(
                            "endTime",
                            "${schedule.endTime.hour}:${schedule.endTime.minute}"
                        )
                        scheduleJson.put("info", schedule.classPlace)
                        scheduleJson.put("subj", schedule.professorName)
                        jsonArray.put(scheduleJson)
                    }
                    jsonObject.put(key, jsonArray)
                }
                timetableForWebview = jsonObject.toString()
            }
        }
    }

    private fun getStartTime(index: Int): Pair<Int, Int> {
        val startHour = when (index) {
            0 -> 8
            1 -> 9
            2 -> 10
            3 -> 12
            4 -> 13
            5 -> 15
            6 -> 16
            7 -> 18
            8 -> 18
            9 -> 19
            10 -> 20
            11 -> 21
            else -> 0
        }
        val startMinute = when (index) {
            0, 1, 3, 5, 7 -> 0
            2, 4, 6, 11 -> 30
            8 -> 50
            9 -> 40
            10 -> 30
            else -> 0
        }
        return startHour to startMinute
    }

    private fun getEndTime(index: Int): Pair<Int, Int> {
        val endHour = when (index) {
            0 -> 8
            1 -> 10
            2 -> 11
            3 -> 13
            4 -> 14
            5 -> 16
            6 -> 17
            7 -> 18
            8 -> 19
            9 -> 20
            10 -> 21
            11 -> 22
            else -> 0
        }
        val endMinute = when (index) {
            0 -> 50
            1, 3, 5, 10 -> 15
            2, 4, 6, 7 -> 45
            8 -> 35
            9 -> 25
            11 -> 5
            else -> 0
        }
        return endHour to endMinute
    }

    fun openQRActivity(sessionId: String, subjID: String, subjName: String) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                fetchSubjectDetail(sessionId, subjName, subjID) { subjDetail2 ->
                    postTransformedData(sessionId, subjDetail2) { subjDetail3 ->
                        postRandomKey(sessionId, subjDetail3) { transformedJson ->
                            val intent = Intent(this@HomeActivity, QRScanActivity::class.java)
                            intent.putExtra("bodyJSON", transformedJson.toString())
                            intent.putExtra("subjID", subjID)
                            intent.putExtra("subjName", subjName)
                            intent.putExtra("sessionID", sessionId)
                            startActivity(intent)
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@HomeActivity,
                    "알 수 없는 오류가 발생했어요: ${e.message}",
                    Toast.LENGTH_SHORT
                )
                    .show()
            }
        }
    }

    fun openLectureActivity(
        sessionId: String, subjID: String, subjName: String
    ) {
        val intent = Intent(this, LectureActivity::class.java)
        intent.putExtra("subjID", subjID)
        intent.putExtra("subjName", subjName)
        intent.putExtra("sessionID", sessionId)
        intent.putExtra("yearHakgi", yearHakgi)
        startActivity(intent)
    }

    private fun fetchSubjectList(sessionId: String, callback: (JSONArray) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            val client = OkHttpClient()
            val request = buildRequest(
                "https://klas.kw.ac.kr/mst/cmn/frame/YearhakgiAtnlcSbjectList.do",
                sessionId,
                RequestBody.create(null, "{}")
            )

            try {
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                if (responseBody != null) {
                    if (responseBody.contains("<!DOCTYPE html>")) {
                        handleSessionExpired(sessionId)
                    } else {
                        val jsonArray = JSONArray(responseBody)
                        subjList = jsonArray
                        callback(jsonArray)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showSessionExpiredDialog()
                    loadingDialog.dismiss()
                }
            }
        }
    }

    private fun fetchSubjectDetail(
        sessionId: String,
        subjName: String,
        subjID: String,
        callback: (JSONObject) -> Unit
    ): JSONObject {
        CoroutineScope(Dispatchers.IO).launch {
            val client = OkHttpClient()

            val json = JSONObject()
                .put("list", JSONArray())
                .put("selectYear", getCurrentYear())
                .put("selectHakgi", getCurrentSemester())
                .put("openMajorCode", "")
                .put("openGrade", "")
                .put("openGwamokNo", "")
                .put("bunbanNo", "")
                .put("gwamokKname", "")
                .put("codeName1", "")
                .put("hakjumNum", "")
                .put("sisuNum", "")
                .put("memberName", "")
                .put("currentNum", "")
                .put("yoil", "")

            val requestBody =
                RequestBody.create("application/json".toMediaTypeOrNull(), json.toString())

            val request = buildRequest(
                "https://klas.kw.ac.kr/std/ads/admst/KwAttendStdGwakmokList.do",
                sessionId,
                requestBody
            )

            var found = false
            try {
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                if (responseBody != null) {
                    val jsonArray = JSONArray(responseBody)
                    for (i in 0 until jsonArray.length()) {
                        val jsonObject = jsonArray.getJSONObject(i)
                        if (jsonObject.getString("gwamokKname") == subjName) {
                            val transformedJson = JSONObject()
                                .put("list", JSONArray())
                                .put("selectYear", jsonObject.getString("thisYear"))
                                .put("selectHakgi", jsonObject.getString("hakgi"))
                                .put("openMajorCode", jsonObject.getString("openMajorCode"))
                                .put("openGrade", jsonObject.getString("openGrade"))
                                .put("openGwamokNo", jsonObject.getString("openGwamokNo"))
                                .put("bunbanNo", jsonObject.getString("bunbanNo"))
                                .put("gwamokKname", jsonObject.getString("gwamokKname"))
                                .put("codeName1", jsonObject.getString("codeName1"))
                                .put("hakjumNum", jsonObject.getString("hakjumNum"))
                                .put("sisuNum", jsonObject.getString("sisuNum"))
                                .put("memberName", jsonObject.getString("memberName"))
                                .put("currentNum", jsonObject.getString("currentNum"))
                                .put("yoil", jsonObject.getString("yoil"))
                                .put("subj", subjID)
                            callback(transformedJson)
                            found = true
                            break
                        }
                    }
                }
                if (!found) {
                    runOnUiThread {
                        Toast.makeText(
                            this@HomeActivity,
                            "QR출석이 지원되지 않는 강의입니다.",
                            Toast.LENGTH_SHORT
                        ).show()
                        loadingDialog.dismiss()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showSessionExpiredDialog()
                    loadingDialog.dismiss()
                }
            }
        }
        return JSONObject()
    }

    private fun postTransformedData(
        sessionId: String,
        transformedJson: JSONObject,
        callback: (JSONObject) -> Unit
    ): JSONObject {
        CoroutineScope(Dispatchers.IO).launch {
            val client = OkHttpClient()

            try {
                val requestBody = RequestBody.create(
                    "application/json".toMediaTypeOrNull(),
                    transformedJson.toString()
                )

                val request = buildRequest(
                    "https://klas.kw.ac.kr/mst/ads/admst/KwAttendStdAttendList.do",
                    sessionId,
                    requestBody
                )

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                if (responseBody != null) {
                    val responseJson = JSONArray(responseBody)
                    transformedJson.put("list", responseJson)
                    callback(transformedJson)
                } else {
                    callback(JSONObject())
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showSessionExpiredDialog()
                    loadingDialog.dismiss()
                }
                Log.e("postTransformedData", "Error: ${e.message}")
                callback(JSONObject())
            }
        }
        return JSONObject()
    }

    private fun postRandomKey(
        sessionId: String,
        transformedJson: JSONObject,
        callback: (JSONObject) -> Unit
    ): JSONObject {
        CoroutineScope(Dispatchers.IO).launch {
            val client = OkHttpClient()

            try {
                val requestBody = RequestBody.create(
                    "application/json".toMediaTypeOrNull(),
                    transformedJson.toString()
                )

                val request = buildRequest(
                    "https://klas.kw.ac.kr/std/lis/evltn/CertiPushSucStd.do",
                    sessionId,
                    requestBody
                )

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                if (responseBody != null) {
                    val responseJson = JSONObject(responseBody).getString("randomKey")
                    transformedJson.put("randomKey", responseJson)
                    callback(transformedJson)
                } else {
                    callback(JSONObject())
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showSessionExpiredDialog()
                    loadingDialog.dismiss()
                }
                callback(JSONObject())
            }
        }
        return JSONObject()
    }

    private fun getCurrentYear(): String {
        if (yearHakgi.isNotEmpty()) {
            return yearHakgi.split(",")[0]
        }
        return Calendar.getInstance().get(Calendar.YEAR).toString()
    }

    private fun getCurrentSemester(): String {
        if (yearHakgi.isNotEmpty()) {
            return yearHakgi.split(",")[1]
        }
        val currentMonth = Calendar.getInstance().get(Calendar.MONTH)
        return if (currentMonth < 7) "1" else "2" // 8월 기준
    }


    fun openLibraryQRModal() {
        val modal = LibraryQRModal(false)
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
                val sharedPreferences =
                    getSharedPreferences("com.icecream.kwklasplus", MODE_PRIVATE)
                val editor = sharedPreferences.edit()
                editor.clear()
                editor.apply()

                val sharedPreferences_library =
                    getSharedPreferences("LibraryQRCache", MODE_PRIVATE)
                val editor_library = sharedPreferences_library.edit()
                editor_library.clear()
                editor_library.apply()
                finish()
                startActivity(Intent(this@HomeActivity, LoginActivity::class.java))
            }
            .setNegativeButton("취소") { _, _ -> }
        builder.show()
    }

    private fun handleSessionExpired(sessionId: String) {
        runOnUiThread {
            showSessionExpiredDialog()
        }
    }

    private fun showSessionExpiredDialog() {
        val builder = MaterialAlertDialogBuilder(this)
        builder.setTitle("인증 오류")
            .setMessage("로그인 후 일정 시간이 지나 세션이 만료되었어요. 앱을 재시작하면 정상적으로 정보가 표시될 거예요.")
            .setPositiveButton(
                "확인"
            ) { _, _ ->
                finish()
                startActivity(Intent(this@HomeActivity, MainActivity::class.java))
            }
        builder.show()
    }

    private fun buildRequest(
        url: String,
        sessionId: String,
        requestBody: RequestBody? = null
    ): Request {
        val defaultUserAgent = WebSettings.getDefaultUserAgent(this)
        val requestBuilder = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .header("Cookie", "SESSION=$sessionId")
            .header(
                "User-Agent",
                "$defaultUserAgent NuriwareApp"
            )

        if (requestBody != null) {
            requestBuilder.post(requestBody)
        }

        return requestBuilder.build()
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
            webView.evaluateJavascript(
                "javascript:window.setDateTime('$selectedDateTime', $isStart);",
                null
            )
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
            val intent = Intent(homeActivity, TaskViewActivity::class.java)
            intent.putExtra("url", url)
            intent.putExtra("yearHakgi", yearHakgi)
            intent.putExtra("subj", subj)
            intent.putExtra("sessionID", homeActivity.sessionIdForOtherClass)
            homeActivity.startActivity(intent)
        }
    }

    @JavascriptInterface
    fun openPage(url: String) {
        homeActivity.runOnUiThread {
            val intent = Intent(homeActivity, LinkViewActivity::class.java)
            intent.putExtra("url", url)
            intent.putExtra("sessionID", homeActivity.sessionIdForOtherClass)
            homeActivity.startActivity(intent)
        }
    }

    @JavascriptInterface
    fun openExternalPage(url: String) {
        homeActivity.runOnUiThread {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            homeActivity.startActivity(intent)
        }
    }

    @JavascriptInterface
    fun completePageLoad() {
        homeActivity.runOnUiThread {
            homeActivity.webView.evaluateJavascript(
                "javascript:window.receiveToken('${homeActivity.sessionIdForOtherClass}')",
                null
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
    fun openLectureActivity(subj: String, subjName: String) {
        homeActivity.runOnUiThread {
            homeActivity.loadingDialog.show()
            homeActivity.openLectureActivity(homeActivity.sessionIdForOtherClass, subj, subjName)
        }
    }

    @JavascriptInterface
    fun qrCheckIn(subjID: String, subjName: String) {
        homeActivity.runOnUiThread {
            homeActivity.loadingDialog.show()
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
        }
    }

    @JavascriptInterface
    fun closeWebViewBottomSheet() {
        homeActivity.runOnUiThread {
            homeActivity.isOpenWebViewBottomSheet = false
            try {
                homeActivity.isKeyboardShowing = false
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
    fun openCustomBottomSheet(url: String, isCancelable: Boolean = true) {
        homeActivity.runOnUiThread {
            homeActivity.main?.let {
                WebViewBottomSheetDialog(
                    url,
                    isCancelable
                ).show(homeActivity.supportFragmentManager, MenuBottomSheetDialog.TAG)
            }
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
        val hapticType = when (type) {
            "CLOCK_TICK" -> HapticFeedbackConstants.CLOCK_TICK
            "KEYBOARD_TAP" -> HapticFeedbackConstants.KEYBOARD_TAP
            "KEYBOARD_RELEASE" -> HapticFeedbackConstants.KEYBOARD_RELEASE
            "LONG_PRESS" -> HapticFeedbackConstants.LONG_PRESS
            "VIRTUAL_KEY" -> HapticFeedbackConstants.VIRTUAL_KEY
            "VIRTUAL_KEY_RELEASE" -> HapticFeedbackConstants.VIRTUAL_KEY_RELEASE
            "TEXT_HANDLE_MOVE" -> HapticFeedbackConstants.TEXT_HANDLE_MOVE
            "CONFIRM" -> HapticFeedbackConstants.CONFIRM
            "REJECT" -> HapticFeedbackConstants.REJECT
            "DRAG_START" -> HapticFeedbackConstants.DRAG_START
            "GESTURE_START" -> HapticFeedbackConstants.GESTURE_START
            "GESTURE_END" -> HapticFeedbackConstants.GESTURE_END
            "TOGGLE_OFF" -> HapticFeedbackConstants.TOGGLE_OFF
            "TOGGLE_ON" -> HapticFeedbackConstants.TOGGLE_ON
            else -> HapticFeedbackConstants.CLOCK_TICK
        }
        homeActivity.runOnUiThread {
            homeActivity.webView.performHapticFeedback(hapticType)
        }
    }
}
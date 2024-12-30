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
import android.net.Uri
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
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
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.fragment.app.DialogFragment
import com.github.tlaabs.timetableview.Schedule
import com.github.tlaabs.timetableview.Time
import com.google.android.gms.common.util.DeviceProperties.isTablet
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
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
    lateinit var menuWebView: WebView
    lateinit var aiWebView: WebView
    lateinit var calendarWebView: WebView
    private lateinit var timetableWebView: WebView
    private var deadlineForWebview: String = ""
    private var timetableForWebview: String = ""
    lateinit var sessionIdForOtherClass: String
    lateinit var loadingDialog: AlertDialog
    lateinit var subjList: JSONArray
    lateinit var yearHakgiList: Array<String>
    lateinit var selectYearHakgiBtn: Button
    lateinit var selectYearHakgiBtnInDrawer: Button
    var yearHakgi: String = ""
    private var isKeyboardShowing = false

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT

        // 모바일에서는 세로 모드 고정
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

        val rootView = findViewById<View>(android.R.id.content)
        rootView.viewTreeObserver.addOnGlobalLayoutListener {
            // 태블릿에서는 무시
            val metrics = DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(metrics)
            val widthPixels = metrics.widthPixels
            val heightPixels = metrics.heightPixels
            val scaleFactor = metrics.density
            val widthDp = widthPixels / scaleFactor
            val isTablet: Boolean = widthDp >= 600
            if (isTablet) {
                return@addOnGlobalLayoutListener
            }

            val r = Rect()
            rootView.getWindowVisibleDisplayFrame(r)
            val screenHeight = rootView.height

            val keypadHeight = screenHeight - r.bottom
            val navBar = findViewById<NavigationBarView>(R.id.bottom_navigation)

            if (!isKeyboardShowing && keypadHeight > screenHeight * 0.15) {
                isKeyboardShowing = true
                aiWebView.layoutParams.height = screenHeight - keypadHeight - 300
                menuWebView.layoutParams.height = screenHeight - keypadHeight - 300
                calendarWebView.layoutParams.height = screenHeight - keypadHeight - 100
                navBar.visibility = View.GONE
            } else if (isKeyboardShowing && keypadHeight < screenHeight * 0.15) {
                isKeyboardShowing = false
                aiWebView.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                menuWebView.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                calendarWebView.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                navBar.visibility = View.VISIBLE
            }

        }

        webView = findViewById(R.id.webView)
        menuWebView = findViewById<WebView>(R.id.menuWebView)
        calendarWebView = findViewById<WebView>(R.id.calendarWebview)
        aiWebView = findViewById<WebView>(R.id.aiWebview)
        timetableWebView = findViewById(R.id.timetableWebview)
        initSubjectList(sessionId)
        initLoadingDialog()
        initNavigationMenu()
    }


    override fun onResume() {
        super.onResume()
        loadingDialog.dismiss()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun initLoadingDialog() {
        val builder = MaterialAlertDialogBuilder(this)
        builder.setView(R.layout.layout_loading_dialog)
        builder.setCancelable(false)
        loadingDialog = builder.create()
    }

    private fun initNavigationMenu() {
        val headerLayout = findViewById<LinearLayout>(R.id.headerLayout)
        val viewTitle = findViewById<TextView>(R.id.viewTitle)
        val menuBtn = findViewById<Button>(R.id.menuBtn)
        selectYearHakgiBtn = findViewById<Button>(R.id.selectYearHakgiBtn)
        val homeView = findViewById<androidx.appcompat.widget.LinearLayoutCompat>(R.id.homeView)
        val timetableView =
            findViewById<androidx.appcompat.widget.LinearLayoutCompat>(R.id.timetableView)
        val calendarView = findViewById<androidx.appcompat.widget.LinearLayoutCompat>(R.id.calendarView)
        val qrView = findViewById<androidx.appcompat.widget.LinearLayoutCompat>(R.id.qrView)
        val menuView = findViewById<androidx.appcompat.widget.LinearLayoutCompat>(R.id.menuView)
        val NavigationBarView =
            findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_navigation)


        menuBtn.setOnClickListener {
            showOptionsMenu(it)
        }

        selectYearHakgiBtn.setOnClickListener {
            openYearHakgiBottomSheetDialog()
        }

        NavigationBarView.setOnItemSelectedListener { item: MenuItem ->
            when (item.itemId) {
                R.id.item_1 -> {
                    headerLayout.visibility = View.VISIBLE
                    viewTitle.text = "KLAS+"
                    homeView.visibility = View.VISIBLE
                    timetableView.visibility = View.GONE
                    calendarView.visibility = View.GONE
                    qrView.visibility = View.GONE
                    menuView.visibility = View.GONE
                    menuBtn.visibility = View.VISIBLE
                    selectYearHakgiBtn.visibility = View.GONE
                    true
                }

                R.id.item_2 -> {
                    headerLayout.visibility = View.VISIBLE
                    viewTitle.text = "시간표"
                    homeView.visibility = View.GONE
                    timetableView.visibility = View.VISIBLE
                    calendarView.visibility = View.GONE
                    qrView.visibility = View.GONE
                    menuView.visibility = View.GONE
                    menuBtn.visibility = View.GONE
                    selectYearHakgiBtn.visibility = View.VISIBLE
                    true
                }
                R.id.item_5 -> {
                    headerLayout.visibility = View.GONE
                    viewTitle.text = "캘린더"
                    homeView.visibility = View.GONE
                    timetableView.visibility = View.GONE
                    calendarView.visibility = View.VISIBLE
                    qrView.visibility = View.GONE
                    menuView.visibility = View.GONE
                    menuBtn.visibility = View.VISIBLE
                    selectYearHakgiBtn.visibility = View.GONE
                    true
                }
                R.id.item_3 -> {
                    headerLayout.visibility = View.VISIBLE
                    viewTitle.text = "KLAS GPT"
                    homeView.visibility = View.GONE
                    timetableView.visibility = View.GONE
                    calendarView.visibility = View.GONE
                    qrView.visibility = View.VISIBLE
                    menuView.visibility = View.GONE
                    menuBtn.visibility = View.VISIBLE
                    selectYearHakgiBtn.visibility = View.GONE
                    true
                }

                R.id.item_4 -> {
                    headerLayout.visibility = View.VISIBLE
                    viewTitle.text = "전체"
                    homeView.visibility = View.GONE
                    timetableView.visibility = View.GONE
                    calendarView.visibility = View.GONE
                    qrView.visibility = View.GONE
                    menuView.visibility = View.VISIBLE
                    menuBtn.visibility = View.VISIBLE
                    selectYearHakgiBtn.visibility = View.GONE
                    true
                }

                else -> false
            }
        }

        val navigationView =
            findViewById<com.google.android.material.navigation.NavigationView>(R.id.navigation_drawer)
        val headerView = navigationView.getHeaderView(0)
        val viewTitleInDrawer = headerView.findViewById<TextView>(R.id.viewTitle)
        val menuBtnInDrawer = headerView.findViewById<Button>(R.id.menuBtn)
        selectYearHakgiBtnInDrawer = headerView.findViewById(R.id.selectYearHakgiBtn)

        selectYearHakgiBtnInDrawer.setOnClickListener {
            openYearHakgiBottomSheetDialog()
        }

        navigationView.setCheckedItem(R.id.item_1)

        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.item_1 -> {
                    viewTitleInDrawer.text = "KLAS+"
                    homeView.visibility = View.VISIBLE
                    timetableView.visibility = View.GONE
                    calendarView.visibility = View.GONE
                    qrView.visibility = View.GONE
                    menuView.visibility = View.GONE
                    menuBtnInDrawer.visibility = View.VISIBLE
                    selectYearHakgiBtnInDrawer.visibility = View.GONE
                    true
                }

                R.id.item_2 -> {
                    viewTitleInDrawer.text = "시간표"
                    homeView.visibility = View.GONE
                    timetableView.visibility = View.VISIBLE
                    calendarView.visibility = View.GONE
                    qrView.visibility = View.GONE
                    menuView.visibility = View.GONE
                    menuBtnInDrawer.visibility = View.GONE
                    selectYearHakgiBtnInDrawer.visibility = View.VISIBLE
                    true
                }
                R.id.item_5 -> {
                    viewTitleInDrawer.text = "캘린더"
                    homeView.visibility = View.GONE
                    timetableView.visibility = View.GONE
                    calendarView.visibility = View.VISIBLE
                    qrView.visibility = View.GONE
                    menuView.visibility = View.GONE
                    menuBtnInDrawer.visibility = View.VISIBLE
                    selectYearHakgiBtnInDrawer.visibility = View.GONE
                    true
                }
                R.id.item_3 -> {
                    viewTitleInDrawer.text = "KLAS GPT"
                    homeView.visibility = View.GONE
                    timetableView.visibility = View.GONE
                    calendarView.visibility = View.GONE
                    qrView.visibility = View.VISIBLE
                    menuView.visibility = View.GONE
                    menuBtnInDrawer.visibility = View.VISIBLE
                    selectYearHakgiBtnInDrawer.visibility = View.GONE
                    true
                }

                R.id.item_4 -> {
                    viewTitleInDrawer.text = "전체"
                    homeView.visibility = View.GONE
                    timetableView.visibility = View.GONE
                    calendarView.visibility = View.GONE
                    qrView.visibility = View.GONE
                    menuView.visibility = View.VISIBLE
                    menuBtnInDrawer.visibility = View.VISIBLE
                    selectYearHakgiBtnInDrawer.visibility = View.GONE
                    true
                }

                else -> false
            }
        }

        menuBtnInDrawer.setOnClickListener {
            showOptionsMenu(it)
        }

    }

    private fun openYearHakgiBottomSheetDialog(isUpdate: Boolean = false) {
        val yearHakgiDialog = YearHakgiBottomSheetDialog(yearHakgiList, isUpdate).apply {
            setSpeedSelectionListener(object : YearHakgiBottomSheetDialog.YearHakgiSelectionListener {
                override fun onYearHakgiSelected(value: String) {
                    updateYearHakgi(value)
                }
            })
        }

        yearHakgiDialog.show(supportFragmentManager, YearHakgiBottomSheetDialog.TAG)
    }

    private fun initWebView() {
        val webViewProgress = findViewById<ProgressBar>(R.id.progressBar_webview)
        webView.post(Runnable {
            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true
            webView.isVerticalScrollBarEnabled = false
            webView.isHorizontalScrollBarEnabled = false
            webView.setBackgroundColor(0)
            webView.addJavascriptInterface(JavaScriptInterface(this), "Android")

            try {
                val pInfo: PackageInfo =
                    baseContext.packageManager.getPackageInfo(baseContext.packageName, 0)
                val version = pInfo.versionName
                webView.settings.userAgentString += " AndroidApp_v${version}"
            } catch (e: PackageManager.NameNotFoundException) {
                e.printStackTrace()
            }

            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    sendDeadlineAndTimetableToWebView()

                    webView.visibility = View.VISIBLE
                    webViewProgress.visibility = View.GONE
                }
            }
        })

        menuWebView.post(Runnable {
            menuWebView.settings.javaScriptEnabled = true
            menuWebView.settings.domStorageEnabled = true
            menuWebView.isVerticalScrollBarEnabled = false
            menuWebView.isHorizontalScrollBarEnabled = false
            menuWebView.setBackgroundColor(0)
            menuWebView.addJavascriptInterface(JavaScriptInterface(this), "Android")
            try {
                val pInfo: PackageInfo =
                    baseContext.packageManager.getPackageInfo(baseContext.packageName, 0)
                val version = pInfo.versionName
                menuWebView.settings.userAgentString += " AndroidApp_v${version}"
            } catch (e: PackageManager.NameNotFoundException) {
                e.printStackTrace()
            }
            menuWebView.loadUrl("https://klasplus.yuntae.in/profile")
        })

        calendarWebView.post(Runnable {
            calendarWebView.settings.javaScriptEnabled = true
            calendarWebView.settings.domStorageEnabled = true
            calendarWebView.isVerticalScrollBarEnabled = false
            calendarWebView.isHorizontalScrollBarEnabled = false
            calendarWebView.setBackgroundColor(0)
            calendarWebView.addJavascriptInterface(JavaScriptInterface(this), "Android")
            calendarWebView.loadUrl("https://klasplus.yuntae.in/calendar?yearHakgi=${yearHakgi}")

            calendarWebView.webChromeClient = object : WebChromeClient() {
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
        })

        aiWebView.post(Runnable {
            aiWebView.settings.javaScriptEnabled = true
            aiWebView.settings.domStorageEnabled = true
            aiWebView.isVerticalScrollBarEnabled = false
            aiWebView.isHorizontalScrollBarEnabled = false
            aiWebView.setBackgroundColor(0)
            aiWebView.addJavascriptInterface(JavaScriptInterface(this), "Android")
            aiWebView.loadUrl("https://klasplus.yuntae.in/ai?yearHakgi=${yearHakgi}")
        })
    }

    private fun sendDeadlineAndTimetableToWebView() {
        webView.evaluateJavascript("javascript:window.receiveDeadlineData(`${deadlineForWebview}`)", null)
        webView.evaluateJavascript("javascript:window.receiveTimetableData(`${timetableForWebview}`)", null)
    }

    private fun initTimetable(sessionId: String) {
        timetableWebView.post(Runnable {
            timetableWebView.settings.javaScriptEnabled = true
            timetableWebView.settings.domStorageEnabled = true
            timetableWebView.isVerticalScrollBarEnabled = false
            timetableWebView.isHorizontalScrollBarEnabled = false
            timetableWebView.setBackgroundColor(0)
            timetableWebView.addJavascriptInterface(JavaScriptInterface(this), "Android")
            timetableWebView.loadUrl("https://klasplus.yuntae.in/timetable.html?yearHakgi=${yearHakgi}")

            timetableWebView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    if (timetableForWebview.isNotEmpty()) {
                        timetableWebView.evaluateJavascript(
                            "javascript:receiveTimetableData(`${timetableForWebview}`)",
                            null
                        )
                    } else {
                        Toast.makeText(this@HomeActivity, "시간표를 불러오는데 실패했습니다.", Toast.LENGTH_SHORT)
                            .show()
                    }
                }
            }
        })
    }

    private fun initSubjectList(sessionId: String) {
        fetchSubjectList(sessionId) { jsonArray ->
            runOnUiThread {
                yearHakgiList = Array(jsonArray.length()) { "" }
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    yearHakgiList[i] = jsonObject.getString("value")
                }

                val sharedPreferences = getSharedPreferences("com.icecream.kwklasplus", MODE_PRIVATE)
                val savedYearHakgi = sharedPreferences.getString("yearHakgi", "")
                val savedYearHakgiList = sharedPreferences.getString("yearHakgiList", "")

                val editor: SharedPreferences.Editor = sharedPreferences.edit()
                editor.putString("yearHakgiList", yearHakgiList.joinToString(","))
                editor.apply()

                var index = 0

                if(!savedYearHakgi.isNullOrEmpty()){
                    index = yearHakgiList.indexOf(savedYearHakgi)
                }

                if (!savedYearHakgiList.isNullOrEmpty() && yearHakgiList.joinToString(",") != savedYearHakgiList) {
                    openYearHakgiBottomSheetDialog(true)
                }

                val jsonObject = jsonArray.getJSONObject(index)
                val newSubjList = jsonObject.getJSONArray("subjList")
                yearHakgi = jsonObject.getString("value")
                val btnText = yearHakgi.replace(",3", ",하계계절").replace(",4", ",동계계절").replace(",", "년도 ") + "학기"
                selectYearHakgiBtn.text = btnText
                selectYearHakgiBtnInDrawer.text = btnText

                CoroutineScope(Dispatchers.IO).launch {
                    launch { getTimetableData(sessionId) }
                    launch { fetchDeadlines(sessionId, newSubjList) }
                    withContext(Dispatchers.Main) {
                        initWebView()
                        loadingDialog.dismiss()
                    }
                }.invokeOnCompletion {
                    runOnUiThread { sendDeadlineAndTimetableToWebView() }
                }
            }
        }
    }

    private fun updateYearHakgi(selectedYearHakgi: String) {
        val btnText = selectedYearHakgi.replace(",3", ",하계계절").replace(",4", ",동계계절").replace(",", "년도 ") + "학기"
        selectYearHakgiBtn.text = btnText
        selectYearHakgiBtnInDrawer.text = btnText

        yearHakgi = selectedYearHakgi
        val sharedPreferences = getSharedPreferences("com.icecream.kwklasplus", MODE_PRIVATE)
        val editor: SharedPreferences.Editor = sharedPreferences.edit()
        editor.putString("yearHakgi", yearHakgi)
        editor.apply()

        reload()
    }

    fun reload() {
        loadingDialog.show()
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
                withContext(Dispatchers.Main) {
                    initWebView()
                    loadingDialog.dismiss()
                }
            }.invokeOnCompletion {
                runOnUiThread { sendDeadlineAndTimetableToWebView() }
            }
        }

        initWebView()
    }

    private suspend fun fetchDeadlines(sessionId: String, subjList: JSONArray) {
        val deadline = ArrayList<JSONObject>()
        val client = OkHttpClient()
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
        webView.post(Runnable {
            webView.loadUrl("https://klasplus.yuntae.in/feed?yearHakgi=${yearHakgi}")
        })
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
            val client = OkHttpClient()
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

                                val (startHour, startMinute) = getStartTime(i + 1)
                                schedule.startTime = Time(startHour, startMinute)

                                val wtSpan =
                                    if (jsonObject.has(wtSpanKey)) jsonObject.getInt(wtSpanKey) else 1
                                val (endHour, endMinute) = getEndTime(i + wtSpan)
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
                initTimetable(sessionId)
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
            Toast.makeText(this@HomeActivity, "알 수 없는 오류가 발생했어요: ${e.message}", Toast.LENGTH_SHORT)
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

    private fun showOptionsMenu(view: View) {
        val popup = PopupMenu(this, view, Gravity.END, 0, R.style.popupOverflowMenu)
        val inflater: MenuInflater = popup.menuInflater
        inflater.inflate(R.menu.main_option_menu, popup.menu)
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem?.itemId) {
                R.id.originApp -> {
                    val intent = packageManager.getLaunchIntentForPackage("kr.ac.kw.SmartLearning")
                    if (intent != null) {
                        startActivity(intent)
                    } else {
                        val playStoreIntent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://play.google.com/store/apps/details?id=kr.ac.kw.SmartLearning")
                        )
                        startActivity(playStoreIntent)
                    }
                }

                R.id.libraryApp -> {
                    val intent = packageManager.getLaunchIntentForPackage("idoit.slpck.kwangwoon")
                    if (intent != null) {
                        startActivity(intent)
                    } else {
                        val playStoreIntent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://play.google.com/store/apps/details?id=idoit.slpck.kwangwoon")
                        )
                        startActivity(playStoreIntent)
                    }
                }

                R.id.logout -> {
                    val builder = MaterialAlertDialogBuilder(this)
                    builder.setTitle("로그아웃")
                        .setMessage("정말 로그아웃할까요?")
                        .setPositiveButton("확인") { _, _ ->
                            val sharedPreferences = getSharedPreferences("com.icecream.kwklasplus", MODE_PRIVATE)
                            val editor = sharedPreferences.edit()
                            editor.clear()
                            editor.apply()

                            val sharedPreferences_library = getSharedPreferences("LibraryQRCache", MODE_PRIVATE)
                            val editor_library = sharedPreferences_library.edit()
                            editor_library.clear()
                            editor_library.apply()
                            finish()
                            startActivity(Intent(this@HomeActivity, LoginActivity::class.java))
                        }
                        .setNegativeButton("취소") { _, _ -> }
                    builder.show()
                }

                R.id.info -> {
                    val builder = MaterialAlertDialogBuilder(this)
                    builder.setTitle("앱 정보")
                        .setMessage("광운대학교 KLAS 앱의 기능과 UI를 추가 및 수정한 안드로이드 앱입니다.\n\n⚠️ 주의 : 개인 사용 용도로 제작된 앱으로 학교의 공식 앱이 아닙니다. 불법적인 목적으로 사용 시 발생하는 불이익에 대해서 개발자는 어떠한 책임도 지지 않음을 밝힙니다.")
                        .setPositiveButton("닫기") { _, _ -> }
                        .setNeutralButton("GitHub") { _, _ ->
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/IceCream0910/kw-klas-plus")
                            )
                            startActivity(intent)
                        }
                    builder.show()
                }
            }
            true
        }
        popup.show()
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

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
            finishAffinity()
            exitProcess(0)
        }
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

            val selectedDateTime = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault()).format(calendar.time)
            calendarWebView.evaluateJavascript("javascript:window.setDateTime('$selectedDateTime', $isStart);", null)
        }
    }
}

class JavaScriptInterface(private val homeActivity: HomeActivity) {
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
            homeActivity.menuWebView.evaluateJavascript(
                "javascript:window.receiveToken('${homeActivity.sessionIdForOtherClass}')",
                null
            )
            homeActivity.aiWebView.evaluateJavascript(
                "javascript:window.receiveToken('${homeActivity.sessionIdForOtherClass}')",
                null
            )
            homeActivity.aiWebView.evaluateJavascript(
                "javascript:window.receiveSubjList('${homeActivity.subjList}')",
                null
            )
            homeActivity.calendarWebView.evaluateJavascript(
                "javascript:window.receiveToken('${homeActivity.sessionIdForOtherClass}')",
                null
            )
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
    fun reload() {
        homeActivity.runOnUiThread {
            homeActivity.reload()
        }
    }
}
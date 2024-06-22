package com.icecream.kwqrcheckin

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.icecream.kwqrcheckin.modal.LibraryQRModal
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class HomeActivity : AppCompatActivity() {
    @SuppressLint("MissingInflatedId")
    private lateinit var webView: WebView
    lateinit var menuWebView: WebView
    private lateinit var timetableWebView: WebView
    private lateinit var deadlineForWebview: String
    private lateinit var noticeForWebview: String
    private lateinit var timetableForWebview: String
    lateinit var sessionIdForOtherClass: String
    private lateinit var progressBar_home: ProgressBar
    lateinit var loadingDialog: AlertDialog

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT

        val sharedPreferences = getSharedPreferences("com.icecream.kwqrcheckin", MODE_PRIVATE)
        val sessionId = sharedPreferences.getString("kwSESSION", null)
        sessionIdForOtherClass = sessionId ?: ""
        if (sessionId == null) {
            showLoginErrorToast()
            finish()
            startActivity(Intent(this@HomeActivity, MainActivity::class.java))
        }

        initLoadingDialog()
        initNavigationMenu()
        initWebView()
        if (sessionId != null) {
            initTimetable(sessionId)
        }
        if (sessionId != null) {
            initSubjectList(sessionId)
        }
    }

    override fun onResume() {
        super.onResume()
        loadingDialog.dismiss()
    }

    override fun onDestroy() {
        super.onDestroy()
        val serviceIntent = Intent(this, UpdateSession::class.java)
        stopService(serviceIntent)
    }

    private fun initLoadingDialog() {
        val builder = MaterialAlertDialogBuilder(this)
        builder.setView(R.layout.layout_loading_dialog)
        builder.setCancelable(false)
        loadingDialog = builder.create()
    }

    private fun initNavigationMenu() {
        //bottom navigation
        val viewTitle = findViewById<TextView>(R.id.viewTitle)
        val menuBtn = findViewById<Button>(R.id.menuBtn)
        val homeView = findViewById<androidx.appcompat.widget.LinearLayoutCompat>(R.id.homeView)
        val timetableView =
            findViewById<androidx.appcompat.widget.LinearLayoutCompat>(R.id.timetableView)
        val qrView = findViewById<androidx.appcompat.widget.LinearLayoutCompat>(R.id.qrView)
        val menuView = findViewById<androidx.appcompat.widget.LinearLayoutCompat>(R.id.menuView)
        val NavigationBarView =
            findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_navigation)


        menuBtn.setOnClickListener {
            showOptionsMenu(it)
        }
        NavigationBarView.setOnItemSelectedListener { item: MenuItem ->
            when (item.itemId) {
                R.id.item_1 -> {
                    viewTitle.text = "KLAS+"
                    homeView.visibility = View.VISIBLE
                    timetableView.visibility = View.GONE
                    qrView.visibility = View.GONE
                    menuView.visibility = View.GONE
                    true
                }

                R.id.item_2 -> {
                    viewTitle.text = "시간표"
                    homeView.visibility = View.GONE
                    timetableView.visibility = View.VISIBLE
                    qrView.visibility = View.GONE
                    menuView.visibility = View.GONE
                    true
                }

                R.id.item_3 -> {
                    viewTitle.text = "출석"
                    homeView.visibility = View.GONE
                    timetableView.visibility = View.GONE
                    qrView.visibility = View.VISIBLE
                    menuView.visibility = View.GONE
                    true
                }

                R.id.item_4 -> {
                    viewTitle.text = "전체"
                    homeView.visibility = View.GONE
                    timetableView.visibility = View.GONE
                    qrView.visibility = View.GONE
                    menuView.visibility = View.VISIBLE
                    true
                }

                else -> false
            }
        }

        // drawer
        val navigationView =
            findViewById<com.google.android.material.navigation.NavigationView>(R.id.navigation_drawer)
        val headerView = navigationView.getHeaderView(0)
        val viewTitleInDrawer = headerView.findViewById<TextView>(R.id.viewTitle)
        val menuBtnInDrawer = headerView.findViewById<Button>(R.id.menuBtn)

        navigationView.setCheckedItem(R.id.item_1)

        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.item_1 -> {
                    viewTitle.text = "KLAS+"
                    homeView.visibility = View.VISIBLE
                    timetableView.visibility = View.GONE
                    qrView.visibility = View.GONE
                    menuView.visibility = View.GONE
                    true
                }

                R.id.item_2 -> {
                    viewTitle.text = "시간표"
                    homeView.visibility = View.GONE
                    timetableView.visibility = View.VISIBLE
                    qrView.visibility = View.GONE
                    menuView.visibility = View.GONE
                    true
                }

                R.id.item_3 -> {
                    viewTitle.text = "출석"
                    homeView.visibility = View.GONE
                    timetableView.visibility = View.GONE
                    qrView.visibility = View.VISIBLE
                    menuView.visibility = View.GONE
                    true
                }

                R.id.item_4 -> {
                    viewTitle.text = "전체"
                    homeView.visibility = View.GONE
                    timetableView.visibility = View.GONE
                    qrView.visibility = View.GONE
                    menuView.visibility = View.VISIBLE
                    true
                }

                else -> false
            }
        }

        menuBtnInDrawer.setOnClickListener {
            showOptionsMenu(it)
        }

    }

    private fun initWebView() {
        val webViewProgress = findViewById<ProgressBar>(R.id.progressBar_webview)
        webView = findViewById(R.id.webView)
        webView.settings.javaScriptEnabled = true
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false
        webView.setBackgroundColor(0)
        webView.addJavascriptInterface(JavaScriptInterface(this), "Android")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                webView.evaluateJavascript(
                    "javascript:receiveDeadlineData(`${deadlineForWebview}`)",
                    null
                )
                webView.evaluateJavascript(
                    "javascript:receiveNoticeData(`${noticeForWebview}`)",
                    null
                )
                webView.evaluateJavascript(
                    "javascript:receiveTimetableData(`${timetableForWebview}`)",
                    null
                )
                webView.visibility = View.VISIBLE
                webViewProgress.visibility = View.GONE
            }
        }

        menuWebView = findViewById<WebView>(R.id.menuWebView)
        menuWebView.settings.javaScriptEnabled = true
        menuWebView.isVerticalScrollBarEnabled = false
        menuWebView.isHorizontalScrollBarEnabled = false
        menuWebView.setBackgroundColor(0)
        menuWebView.addJavascriptInterface(JavaScriptInterface(this), "Android")
        menuWebView.loadUrl("https://kw-klas-plus-webview.vercel.app/profile")
    }

    private fun initTimetable(sessionId: String) {
        timetableWebView = findViewById(R.id.timetableWebview)
        timetableWebView.settings.javaScriptEnabled = true
        timetableWebView.isVerticalScrollBarEnabled = false
        timetableWebView.isHorizontalScrollBarEnabled = false
        timetableWebView.setBackgroundColor(0)
        timetableWebView.addJavascriptInterface(JavaScriptInterface(this), "Android")

        timetableWebView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                timetableWebView.evaluateJavascript(
                    "javascript:receiveTimetableData(`${timetableForWebview}`)",
                    null
                )
            }
        }
        getTimetableData(sessionId)
    }

    private fun initSubjectList(sessionId: String) {
        val subjectListView = findViewById<ListView>(R.id.subjectListView)
        var subjectList = JSONArray()
        val adapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1)
        subjectListView.adapter = adapter

        getFeedData(sessionId)

        fetchSubjectList(sessionId) { jsonArray ->
            runOnUiThread {
                adapter.clear()
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val subjList = jsonObject.getJSONArray("subjList")
                    subjectList = subjList
                    CoroutineScope(Dispatchers.IO).launch {
                        fetchDeadlines(sessionId, subjList)
                    }
                    for (j in 0 until subjList.length()) {
                        val subjItem = subjList.getJSONObject(j)
                        val label = subjItem.getString("name")
                        adapter.add(label)
                    }
                }
            }
        }

        subjectListView.setOnItemClickListener { _, _, position, _ ->
            val subjID = subjectList.getJSONObject(position).getString("value")
            val subjName = adapter.getItem(position)

            if (subjName != null) {
                loadingDialog.show()
                openQRActivity(sessionId, subjID, subjName)
            }
        }
    }

    private fun getFeedData(sessionId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val client = OkHttpClient()
            val request = buildRequest(
                "https://klas.kw.ac.kr/std/cmn/frame/StdHome.do",
                sessionId,
                RequestBody.create(null, "{\"searchYearhakgi\": null}")
            )

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (responseBody != null) {
                val jsonArray = JSONObject(responseBody)
                val noticeList = ArrayList<JSONObject>()

                val noticeArray = jsonArray.getJSONArray("subjNotiList")
                for (i in 0 until noticeArray.length()) {
                    val noticeItem = noticeArray.getJSONObject(i)
                    noticeList.add(noticeItem)
                }
                noticeForWebview = noticeList.toString()
            }
        }
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
            webView.loadUrl("file:///android_asset/index.html")
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
                val endDate =
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(homework.getString("expiredate"))
                val hourGap = ((endDate.time - nowDate.time) / 3600000).toInt()

                if (hourGap >= 0) {
                    val homeworkItem = JSONObject()
                        .put("expiredate", homework.getString("expiredate"))
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
                timetableWebView.post(Runnable {
                    timetableWebView.loadUrl("file:///android_asset/timetable.html")
                })
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
                val subjDetail2 = withContext(Dispatchers.IO) {
                    fetchSubjectDetail(sessionId, subjName, subjID) { it }
                }
                val subjDetail3 = withContext(Dispatchers.IO) {
                    postTransformedData(sessionId, subjDetail2) { it }
                }
                val transformedJson = withContext(Dispatchers.IO) {
                    postRandomKey(sessionId, subjDetail3) { it }
                }
                val intent = Intent(this@HomeActivity, QRScanActivity::class.java)
                intent.putExtra("bodyJSON", transformedJson.toString())
                intent.putExtra("subjID", subjID)
                intent.putExtra("subjName", subjName)
                intent.putExtra("sessionID", sessionId)
                startActivity(intent)
            } catch (e: Exception) {
                // 에러 처리
                Toast.makeText(this@HomeActivity, "오류가 발생했습니다: ${e.message}", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    fun openLectureActivity(
        sessionId: String, subjID: String, subjName: String
    ) {
        fetchSubjectDetail(sessionId, subjName, subjID) { subjDetail2 ->
            postTransformedData(sessionId, subjDetail2) { subjDetail3 ->
                postRandomKey(sessionId, subjDetail3) { transformedJson ->
                    val intent = Intent(this, LectureActivity::class.java)
                    intent.putExtra("bodyJSON", transformedJson.toString())
                    intent.putExtra("subjID", subjID)
                    intent.putExtra("subjName", subjName)
                    intent.putExtra("sessionID", sessionId)
                    startActivity(intent)
                }
            }
        }
    }

    private fun fetchSubjectList(sessionId: String, callback: (JSONArray) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            val client = OkHttpClient()
            val request = buildRequest(
                "https://klas.kw.ac.kr/mst/cmn/frame/YearhakgiAtnlcSbjectList.do",
                sessionId,
                RequestBody.create(null, "{}")
            )

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (responseBody != null) {
                if (responseBody.contains("<!DOCTYPE html>")) {
                    handleSessionExpired(sessionId)
                } else {
                    val jsonArray = JSONArray(responseBody)
                    callback(jsonArray)
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
                        break
                    }
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
            }
        }
        return JSONObject()
    }

    private fun getCurrentYear(): String {
        return Calendar.getInstance().get(Calendar.YEAR).toString()
    }

    private fun getCurrentSemester(): String {
        val currentMonth = Calendar.getInstance().get(Calendar.MONTH)
        return if (currentMonth < 7) "1" else "2" // 8월 기준
    }


    public fun openLibraryQRModal() {
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
                    startActivity(intent)
                }

                R.id.libraryApp -> {
                    val intent = packageManager.getLaunchIntentForPackage("idoit.slpck.kwangwoon")
                    startActivity(intent)
                }

                R.id.logout -> {
                    finish()
                    startActivity(Intent(this@HomeActivity, LoginActivity::class.java))
                }

                R.id.github -> {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/IceCream0910/kw-klas-plus")
                    )
                    startActivity(intent)
                }
            }
            true
        }
        popup.show()
    }

    private fun handleSessionExpired(sessionId: String) {
        runOnUiThread {
            Thread {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("https://klas.kw.ac.kr/usr/cmn/login/UpdateSession.do")
                    .header("Cookie", "SESSION=$sessionId")
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 10; SM-G960N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/88.0.4324.181 Mobile Safari/537.36 NuriwareApp"
                    )
                    .build()
                client.newCall(request).execute()
            }
            showSessionExpiredDialog()
        }
    }

    private fun showSessionExpiredDialog() {
        val builder = MaterialAlertDialogBuilder(this)
        builder.setTitle("인증 오류")
            .setMessage("세션이 만료되었습니다. 다시 로그인해주세요.")
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
        val requestBuilder = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .header("Cookie", "SESSION=$sessionId")
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 10; SM-G960N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/88.0.4324.181 Mobile Safari/537.36 NuriwareApp"
            )

        if (requestBody != null) {
            requestBuilder.post(requestBody)
        }

        return requestBuilder.build()
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
            homeActivity.startActivity(intent)
        }
    }

    @JavascriptInterface
    fun openPage(url: String) {
        homeActivity.runOnUiThread {
            val intent = Intent(homeActivity, LinkViewActivity::class.java)
            intent.putExtra("url", url)
            homeActivity.startActivity(intent)
        }
    }

    @JavascriptInterface
    fun completePageLoad() {
        homeActivity.runOnUiThread {
            homeActivity.menuWebView.evaluateJavascript(
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
}
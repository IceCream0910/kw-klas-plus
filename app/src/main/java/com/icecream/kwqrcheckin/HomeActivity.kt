package com.icecream.kwqrcheckin

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.get
import com.github.tlaabs.timetableview.Schedule
import com.github.tlaabs.timetableview.Time
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date


class HomeActivity : AppCompatActivity() {
    @SuppressLint("MissingInflatedId")
    lateinit var timetable: com.github.tlaabs.timetableview.TimetableView
    lateinit var webView: WebView
    lateinit var deadlineForWebview: String
    lateinit var noticeForWebview: String
    lateinit var timetableForWebview: String
    lateinit var sessionIdForOtherClass: String
    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT

        val sharedPreferences = getSharedPreferences("com.icecream.kwqrcheckin", MODE_PRIVATE)
        val sessionId = sharedPreferences.getString("kwSESSION", null)
        sessionIdForOtherClass = sessionId?:""
        if (sessionId == null) {
            Toast.makeText(this, "인증에 실패했습니다.", Toast.LENGTH_SHORT).show()
            finish()
            startActivity(Intent(this@HomeActivity, MainActivity::class.java))
        }

        val viewTitle = findViewById<TextView>(R.id.viewTitle)
        val homeView = findViewById<androidx.appcompat.widget.LinearLayoutCompat>(R.id.homeView)
        val timetableView =
            findViewById<androidx.appcompat.widget.LinearLayoutCompat>(R.id.timetableView)
        val qrView = findViewById<androidx.appcompat.widget.LinearLayoutCompat>(R.id.qrView)
        val NavigationBarView =
            findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_navigation)
        NavigationBarView.setOnItemSelectedListener { item: MenuItem ->
            when (item.itemId) {
                R.id.item_1 -> {
                    viewTitle.text = "KLAS+"
                    homeView.visibility = android.view.View.VISIBLE
                    timetableView.visibility = android.view.View.GONE
                    qrView.visibility = android.view.View.GONE
                    true
                }

                R.id.item_2 -> {
                    viewTitle.text = "시간표"
                    homeView.visibility = android.view.View.GONE
                    timetableView.visibility = android.view.View.VISIBLE
                    qrView.visibility = android.view.View.GONE
                    true
                }

                R.id.item_3 -> {
                    viewTitle.text = "QR 출석"
                    homeView.visibility = android.view.View.GONE
                    timetableView.visibility = android.view.View.GONE
                    qrView.visibility = android.view.View.VISIBLE
                    true
                }

                else -> false
            }
        }

        val webViewProgress = findViewById<ProgressBar>(R.id.progressBar_webview)
        webView = findViewById(R.id.webView)
        webView.settings.javaScriptEnabled = true
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false
        webView.setBackgroundColor(0);
        webView.addJavascriptInterface(JavaScriptInterface(this), "Android")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                webView.evaluateJavascript("javascript:receiveDeadlineData(`${deadlineForWebview}`)", null)
                webView.evaluateJavascript("javascript:receiveNoticeData(`${noticeForWebview}`)", null)
                webView.evaluateJavascript("javascript:receiveTimetableData(`${timetableForWebview}`)", null)
                webView.visibility = android.view.View.VISIBLE
                webViewProgress.visibility = android.view.View.GONE
            }
        }


        val logoutBtn = findViewById<Button>(R.id.logoutButton)
        logoutBtn.setOnClickListener {
            finish()
            startActivity(Intent(this@HomeActivity, LoginActivity::class.java))
        }

        timetable = findViewById<com.github.tlaabs.timetableview.TimetableView>(R.id.timetable)
        val subjectListView = findViewById<ListView>(R.id.subjectListView)
        var subjectList = JSONArray()
        val adapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1)
        subjectListView.adapter = adapter

        getFeedData(sessionId!!)
        getTimetableData(sessionId!!)
        timetable.setOnStickerSelectEventListener { idx, schedules ->
            openLectureActivity(
                sessionId!!,
                schedules[0].professorName,
                schedules[0].classTitle
            )

        }
        fetchSubjectList(sessionId!!) { jsonArray ->
            runOnUiThread {
                adapter.clear()
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val subjList = jsonObject.getJSONArray("subjList")
                    subjectList = subjList
                    CoroutineScope(Dispatchers.IO).launch {
                        fetchDeadlines(sessionId!!, subjList)
                    }
                    for (j in 0 until subjList.length()) {
                        val subjItem = subjList.getJSONObject(j)
                        val label = subjItem.getString("name")
                        adapter.add(label);
                    }
                }
            }
        }
        subjectListView.setOnItemClickListener { _, _, position, _ ->
            val subjID = subjectList.getJSONObject(position).getString("value")
            val subjName = adapter.getItem(position)

            if (subjName != null) {
                openQRActivity(sessionId!!, subjID, subjName)
            }
        }
    }

    fun getFeedData(sessionId: String) {
        Thread {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url("https://klas.kw.ac.kr/std/cmn/frame/StdHome.do\n")
                .header("Content-Type", "application/json")
                .header("Cookie", "SESSION=$sessionId")
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 10; SM-G960N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/88.0.4324.181 Mobile Safari/537.36 NuriwareApp"
                )
                .post(RequestBody.create(null, "{\"searchYearhakgi\": null}"))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (responseBody != null) {
                val jsonArray = JSONObject(responseBody)
                val notice = ArrayList<JSONObject>()

                val noticeArray = jsonArray.getJSONArray("subjNotiList")
                for (i in 0 until noticeArray.length()) {
                    val noticeItem = noticeArray.getJSONObject(i)
                    notice.add(noticeItem)
                }
                noticeForWebview = notice.toString()
            }
        }.start()
    }

    suspend fun fetchDeadlines(sessionId: String, subjList: JSONArray) {
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
                    .put("selectYearhakgi", getCurrentYear() + "," + getCurrentSemester())
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

                for (j in 0 until 3) {
                    val request = Request.Builder()
                        .url(urls[j])
                        .header("Content-Type", "application/json")
                        .header("Cookie", "SESSION=$sessionId")
                        .post(requestBody)
                        .build()

                    val response = client.newCall(request).execute()
                    val responseBody = response.body?.string()

                    if (responseBody != null) {
                        when (j) {
                            0 -> parseOnlineLecture(subjID, subjDeadline, responseBody)
                            1 -> parseHomework(subjID, subjDeadline, responseBody, "HW")
                            2 -> parseHomework(subjID, subjDeadline, responseBody, "TP")
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

    fun parseOnlineLecture(subjectCode: String, subjDeadline:JSONObject, responseData: String) {
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

    fun parseHomework(
        subjectCode: String,
        subjDeadline:JSONObject,
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

    fun getTimetableData(sessionId: String) {
        Thread {
            val client = OkHttpClient()
            val json = JSONObject()
                .put("list", JSONArray())
                .put("searchYear", getCurrentYear())
                .put("searchHakgi", getCurrentSemester())
                .put("atnlcYearList", JSONArray())
                .put("timeTableList", JSONArray())

            val requestBody =
                RequestBody.create("application/json".toMediaTypeOrNull(), json.toString())

            val request = Request.Builder()
                .url("https://klas.kw.ac.kr/std/cps/atnlc/TimetableStdList.do")
                .header("Content-Type", "application/json")
                .header("Cookie", "SESSION=$sessionId")
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 10; SM-G960N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/88.0.4324.181 Mobile Safari/537.36 NuriwareApp"
                )
                .post(requestBody)
                .build()

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
                        for (k in 1..5) {
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

                                val startHour = when (i + 1) {
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
                                val startMinute = when (i + 1) {
                                    0, 1, 3, 5, 7 -> 0
                                    2, 4, 6, 11 -> 30
                                    8 -> 50
                                    9 -> 40
                                    10 -> 30
                                    else -> 0
                                }
                                schedule.startTime = Time(startHour, startMinute)

                                val wtSpan =
                                    if (jsonObject.has(wtSpanKey)) jsonObject.getInt(wtSpanKey) else 1
                                val endHour = when (i + wtSpan) {
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
                                val endMinute = when (i + wtSpan) {
                                    0 -> 50
                                    1, 3, 5, 10 -> 15
                                    2, 4, 6, 7 -> 45
                                    8 -> 35
                                    9 -> 25
                                    11 -> 5
                                    else -> 0
                                }
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
                    timetable.add(schedules)
                    val jsonArray = JSONArray()
                    for (schedule in schedules) {
                        val scheduleJson = JSONObject()
                        // Assuming Schedule class has properties like 'title', 'time', etc.
                        scheduleJson.put("title", schedule.classTitle)
                        scheduleJson.put("day", schedule.day)
                        scheduleJson.put("startTime", schedule.startTime.hour.toString() + ":" + schedule.startTime.minute.toString())
                        scheduleJson.put("endTime", schedule.endTime.hour.toString() + ":" + schedule.endTime.minute.toString())
                        scheduleJson.put("info", schedule.classPlace)
                        scheduleJson.put("subj", schedule.professorName)
                        jsonArray.put(scheduleJson)
                    }
                    jsonObject.put(key, jsonArray)
                }

                timetableForWebview = jsonObject.toString()
            }
        }.start()
    }


    fun openQRActivity(
        sessionId: String, subjID: String, subjName: String
    ) {
        fetchSubjectDetail(sessionId, subjName, subjID) { subjDetail2 ->
            postTransformedData(sessionId, subjDetail2) { subjDetail3 ->
                postRandomKey(sessionId, subjDetail3) { transformedJson ->
                    val intent = Intent(this, QRScanActivity::class.java)
                    intent.putExtra("bodyJSON", transformedJson.toString())
                    intent.putExtra("subjID", subjID)
                    intent.putExtra("subjName", subjName)
                    intent.putExtra("sessionID", sessionId)
                    startActivity(intent)
                }
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

    fun fetchSubjectList(sessionId: String, callback: (JSONArray) -> Unit) {
        Thread {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url("https://klas.kw.ac.kr/mst/cmn/frame/YearhakgiAtnlcSbjectList.do")
                .header("Content-Type", "application/json")
                .header("Cookie", "SESSION=$sessionId")
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 10; SM-G960N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/88.0.4324.181 Mobile Safari/537.36 NuriwareApp"
                )
                .post(RequestBody.create(null, "{}"))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (responseBody != null) {
                if (responseBody.contains("<!DOCTYPE html>")) {
                    runOnUiThread {
                        Thread {
                            val request = Request.Builder()
                                .url("https://klas.kw.ac.kr/usr/cmn/login/UpdateSession.do")
                                .header("Cookie", "SESSION=$sessionId")
                                .header(
                                    "User-Agent",
                                    "Mozilla/5.0 (Linux; Android 10; SM-G960N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/88.0.4324.181 Mobile Safari/537.36 NuriwareApp"
                                )
                                .build()
                            val response = client.newCall(request).execute()
                        }
                        val builder = MaterialAlertDialogBuilder(this)
                        builder.setTitle("인증 오류")
                            .setMessage("세션이 만료되었습니다. 다시 로그인해주세요.")
                            .setPositiveButton("확인",
                                DialogInterface.OnClickListener { dialog, id ->
                                    finish()
                                    startActivity(
                                        Intent(
                                            this@HomeActivity,
                                            MainActivity::class.java
                                        )
                                    )
                                })
                        builder.show()
                    }
                } else {
                    val jsonArray = JSONArray(responseBody)
                    callback(jsonArray)
                }
            }
        }.start()
    }

    fun fetchSubjectDetail(
        sessionId: String,
        subjName: String,
        subjID: String,
        callback: (JSONObject) -> Unit
    ) {
        Thread {
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

            val request = Request.Builder()
                .url("https://klas.kw.ac.kr/std/ads/admst/KwAttendStdGwakmokList.do")
                .header("Content-Type", "application/json")
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 10; SM-G960N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/88.0.4324.181 Mobile Safari/537.36 NuriwareApp"
                )
                .header("Cookie", "SESSION=$sessionId")
                .post(requestBody)
                .build()

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
        }.start()
    }

    fun postTransformedData(
        sessionId: String,
        transformedJson: JSONObject,
        callback: (JSONObject) -> Unit
    ) {
        Thread {
            val client = OkHttpClient()

            val requestBody = RequestBody.create(
                "application/json".toMediaTypeOrNull(),
                transformedJson.toString()
            )

            val request = Request.Builder()
                .url("https://klas.kw.ac.kr/mst/ads/admst/KwAttendStdAttendList.do")
                .header("Content-Type", "application/json")
                .header("Cookie", "SESSION=$sessionId")
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 10; SM-G960N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/88.0.4324.181 Mobile Safari/537.36 NuriwareApp"
                )

                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (responseBody != null) {
                val responseJson = JSONArray(responseBody)
                transformedJson.put("list", responseJson)
                callback(transformedJson)
            }
        }.start()
    }

    fun postRandomKey(
        sessionId: String,
        transformedJson: JSONObject,
        callback: (JSONObject) -> Unit
    ) {
        Thread {
            val client = OkHttpClient()

            val requestBody = RequestBody.create(
                "application/json".toMediaTypeOrNull(),
                transformedJson.toString()
            )

            val request = Request.Builder()
                .url("https://klas.kw.ac.kr/std/lis/evltn/CertiPushSucStd.do")
                .header("Content-Type", "application/json")
                .header("Cookie", "SESSION=$sessionId")
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 10; SM-G960N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/88.0.4324.181 Mobile Safari/537.36 NuriwareApp"
                )

                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (responseBody != null) {
                val responseJson = JSONObject(responseBody).getString("randomKey")
                transformedJson.put("randomKey", responseJson)
                callback(transformedJson)
            }
        }.start()
    }

    fun getCurrentYear(): String {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        return currentYear.toString()
    }

    fun getCurrentSemester(): String {
        val currentMonth = Calendar.getInstance().get(Calendar.MONTH)

        return if (currentMonth < 7) "1" else "2" // 8월 기준
    }
}

class JavaScriptInterface(homeActivity: HomeActivity) {
    private val homeActivity: HomeActivity = homeActivity

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
    fun openLectureActivity(
        subj: String,
        subjName: String
    ) {
        homeActivity.runOnUiThread {
            homeActivity.openLectureActivity(homeActivity.sessionIdForOtherClass!!, subj, subjName)
        }
    }

    @JavascriptInterface
    fun qrCheckIn(subjID: String, subjName: String) {
        homeActivity.runOnUiThread {
            homeActivity.openQRActivity(homeActivity.sessionIdForOtherClass, subjID, subjName)
        }
    }

}

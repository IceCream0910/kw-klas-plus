package com.icecream.kwklasplus

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore.Video
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.gms.common.util.DeviceProperties.isTablet
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.icecream.kwklasplus.modal.SpeedBottomSheetDialog
import org.json.JSONObject
import org.jsoup.Jsoup
import kotlin.properties.Delegates

class VideoPlayerActivity : AppCompatActivity() {
    var isPlaying: Boolean = false
    lateinit var lectureNameTextView: TextView
    lateinit var lectureTimeTextView: TextView
    lateinit var timeProgressBar: Slider
    lateinit var KLASWebView: WebView
    lateinit var VideoWebView: WebView
    lateinit var listWebView: WebView
    lateinit var listLayout: SwipeRefreshLayout
    lateinit var KLASListLayout: LinearLayout
    lateinit var videoPlayerLayout: LinearLayout
    lateinit var subj: String
    lateinit var yearHakgi: String
    lateinit var sessionId: String
    var isViewer = false
    var onStopCalled by Delegates.notNull<Boolean>()
    var originVideoURL: String = ""
    var isLoadedKLASWebView = false
    lateinit var playPauseButton: com.google.android.material.button.MaterialButton
    lateinit var muteButton: com.google.android.material.button.MaterialButton
    lateinit var speedButton: Button
    var duration: Float = 1f
    var lastPlaytime: Float = 0f
    lateinit var seekbarCurrentTime: TextView
    lateinit var seekbarTotalTime: TextView
    var isFullscreen: Boolean = false

    companion object {
        private const val REQUEST_PLAY = 0
        private const val REQUEST_PAUSE = 1
        private const val REQUEST_FORWARD = 2
        private const val REQUEST_BACKWARD = 3
        private const val ACTION_MEDIA_CONTROL = "media_control"
        private const val EXTRA_CONTROL_TYPE = "control_type"
        private const val CONTROL_TYPE_PLAY = 0
        private const val CONTROL_TYPE_PAUSE = 1
        private const val CONTROL_TYPE_FORWARD = 2
        private const val CONTROL_TYPE_BACKWARD = 3
    }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intentFilter = IntentFilter(ACTION_MEDIA_CONTROL).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        registerReceiver(MediaControlReceiver, intentFilter, Context.RECEIVER_EXPORTED)

        setContentView(R.layout.activity_video_player)

        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 모바일에서는 세로 모드 고정
        if (isTablet(this)) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        lectureNameTextView = findViewById(R.id.lectureNameTextView)
        lectureTimeTextView = findViewById(R.id.lectureTimeTextView)
        timeProgressBar = findViewById(R.id.timeProgressBar)

        timeProgressBar.addOnChangeListener { slider, value, fromUser ->
            if (fromUser) {
                val seconds = value * duration
                VideoWebView.evaluateJavascript(
                    "bcPlayController._uniPlayerEventTarget.fire(VCPlayControllerEvent.SEEK_END, $seconds);",
                    null
                )
            }
        }

        timeProgressBar.setLabelFormatter { value ->
            val seconds = value * duration
            formatTime(seconds)
        }

        val pipButton = findViewById<Button>(R.id.pipButton)
        val closeButton = findViewById<Button>(R.id.closeButton)
        val fullScreenButton = findViewById<Button>(R.id.fullScreenButton)
        playPauseButton = findViewById(R.id.playPauseButton)
        val backwardButton = findViewById<Button>(R.id.backwardButton)
        val forwardButton = findViewById<Button>(R.id.forwardButton)
        muteButton = findViewById(R.id.muteButton)
        speedButton = findViewById(R.id.speedButton)
        seekbarCurrentTime = findViewById(R.id.seekbarCurrentTime)
        seekbarTotalTime = findViewById(R.id.seekbarTotalTime)

        playPauseButton.setOnClickListener {
            pressKey(KeyEvent.KEYCODE_SPACE)
        }

        backwardButton.setOnClickListener {
            pressKey(KeyEvent.KEYCODE_DPAD_LEFT)
        }

        forwardButton.setOnClickListener {
            pressKey(KeyEvent.KEYCODE_DPAD_RIGHT)
        }

        fullScreenButton.setOnClickListener {
            pressKey(KeyEvent.KEYCODE_F)
            showController()
        }

        muteButton.setOnClickListener {
            pressKey(KeyEvent.KEYCODE_M)
        }

        pipButton.setOnClickListener {
            startPIP()
        }

        lectureTimeTextView.setOnClickListener {
            VideoWebView.evaluateJavascript(
                "bcPlayController.getPlayController()._eventTarget.fire(VCPlayControllerEvent.SEEK_END, $lastPlaytime);",
                null
            )
        }

        speedButton.setOnClickListener {
            val speedDialog = SpeedBottomSheetDialog().apply {
                setSpeedSelectionListener(object : SpeedBottomSheetDialog.SpeedSelectionListener {
                    override fun onSpeedSelected(speed: Double) {
                        speedButton.text = "  ${speed}x"
                        VideoWebView.evaluateJavascript(
                            "javascript:bcPlayController.getPlayController()._eventTarget.fire(VCPlayControllerEvent.CHANGE_PLAYBACK_RATE, Number($speed))",
                            null
                        )
                    }
                })
            }

            speedDialog.show(supportFragmentManager, SpeedBottomSheetDialog.TAG)
        }

        closeButton.setOnClickListener {
            val builder = MaterialAlertDialogBuilder(this)
            builder.setTitle("강의 종료")
                .setMessage("정말 강의 수강을 종료할까요?")
                .setPositiveButton("확인") { dialog, id ->
                    finish()
                }
                .setNegativeButton("취소") { dialog, id ->
                    dialog.dismiss()
                }
                .show()
        }

        subj = intent.getStringExtra("subj").toString()
        yearHakgi = intent.getStringExtra("yearHakgi").toString()
        sessionId = intent.getStringExtra("sessionID").toString()

        listWebView = findViewById<BackgroundWebView>(R.id.listWebView)
        KLASWebView = findViewById<BackgroundWebView>(R.id.KLASWebView)
        VideoWebView = findViewById<BackgroundWebView>(R.id.VideoWebView)

        listWebView.settings.javaScriptEnabled = true
        listWebView.settings.domStorageEnabled = true
        listWebView.settings.allowFileAccess = true
        listWebView.settings.allowContentAccess = true
        listWebView.settings.supportMultipleWindows()
        listWebView.setBackgroundColor(0)
        listWebView.settings.javaScriptCanOpenWindowsAutomatically = true
        listWebView.addJavascriptInterface(WebAppInterface(this), "Android")
        listWebView.loadUrl("https://klasplus.yuntae.in/onlineLecture")

        KLASWebView.settings.javaScriptEnabled = true
        KLASWebView.settings.domStorageEnabled = true
        KLASWebView.settings.allowFileAccess = true
        KLASWebView.settings.allowContentAccess = true
        KLASWebView.settings.supportMultipleWindows()
        KLASWebView.setBackgroundColor(0)
        KLASWebView.settings.javaScriptCanOpenWindowsAutomatically = true
        KLASWebView.addJavascriptInterface(WebAppInterface(this), "Android")
        KLASWebView.loadUrl("https://klas.kw.ac.kr/std/lis/evltn/OnlineCntntsStdPage.do")

        VideoWebView.settings.javaScriptEnabled = true
        VideoWebView.settings.domStorageEnabled = true
        VideoWebView.settings.allowFileAccess = true
        VideoWebView.settings.allowContentAccess = true
        VideoWebView.settings.mediaPlaybackRequiresUserGesture = false
        VideoWebView.settings.supportMultipleWindows()
        VideoWebView.setBackgroundColor(0)
        VideoWebView.settings.javaScriptCanOpenWindowsAutomatically = true
        VideoWebView.addJavascriptInterface(WebAppInterface(this), "Android")


        listLayout = findViewById(R.id.listLayout)
        KLASListLayout = findViewById(R.id.KLASListLayout)
        videoPlayerLayout = findViewById(R.id.videoPlayerLayout)

        listLayout.setOnRefreshListener {
            listWebView.reload()
            listLayout.isRefreshing = false
        }

        var isScriptExecuted = false

        KLASWebView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                KLASWebView.evaluateJavascript(
                    "javascript:(function() {" +
                            "var style = document.createElement('style');" +
                            "style.innerHTML = 'header { display: none; } .selectsemester { display: none; } .card { border-radius: 15px !important; } .contsubtitle { display: none; } " +
                            ".container { margin-top: -10px } button { border-radius: 10px !important } .board_view_header { border: none !important; border-radius: 15px; }';" +
                            "document.head.appendChild(style);" +
                            "document.querySelector('#appModule').childNodes[2].style.display = 'none';" +
                            "window.scroll(0, 0);" +
                            "})()", null
                )

                listLayout.isRefreshing = false

                if (!isScriptExecuted) {
                    KLASWebView.evaluateJavascript(
                        "javascript:localStorage.setItem('selectYearhakgi', '$yearHakgi');",
                        null
                    )
                    KLASWebView.evaluateJavascript(
                        "javascript:localStorage.setItem('selectSubj', '$subj');",
                        null
                    )
                    KLASWebView.reload()
                    isScriptExecuted = true
                } else {
                    isLoadedKLASWebView = true
                }

                if (!url.contains("/OnlineCntntsStdPage")) { // 강의 시청 페이지
                    isViewer = true
                    val params = VideoWebView.layoutParams
                    params.height = (resources.displayMetrics.heightPixels * 0.25).toInt() + 50
                    VideoWebView.layoutParams = params
                    KLASWebView.evaluateJavascript(
                        "javascript:(function() {" +
                                "var style = document.createElement('style');" +
                                "style.innerHTML = '.antopbak { display: none; } #appHeaderSubj { display: none; }';" +
                                "document.head.appendChild(style);" +
                                "window.scroll(0, 0);" +
                                "})()", null
                    )
                    KLASWebView.evaluateJavascript(
                        "var progress = document.querySelector('.antopbak').children[0].innerHTML;" +
                                "var time = document.querySelector('.antopbak').children[1].innerHTML;" +
                                "Android.receiveVideoData(progress, time);" +
                                "setInterval(() => {" +
                                "var progress = document.querySelector('.antopbak').children[0].innerHTML;" +
                                "var time = document.querySelector('.antopbak').children[1].innerHTML;" +
                                "Android.receiveVideoData(progress, time);" +
                                "}, 10000);", null
                    )
                    KLASWebView.evaluateJavascript(
                        "const videoURL = chkOpen.toString().split('<EMBED src =\"')[1].split('\"')[0];" +
                                "Android.receiveVideoURL(videoURL);", null
                    )
                } else {
                    isViewer = false;
                }
            }
        }

        listWebView.webChromeClient = object : WebChromeClient() {
            override fun onCloseWindow(window: WebView?) {
                super.onCloseWindow(window)
                finish()
            }

            override fun onJsAlert(
                view: WebView?,
                url: String?,
                message: String?,
                result: JsResult?
            ): Boolean {
                runOnUiThread {
                    val builder = MaterialAlertDialogBuilder(this@VideoPlayerActivity)
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

        }

        VideoWebView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                Log.d("VideoWebView", "onPageFinished called for URL: $url")
                if (!url.contains("kw.ac.kr")) return;
                VideoWebView.evaluateJavascript(
                    """
            (function() {
                var currSpeed = ${'$'}(".vc-pctrl-playback-rate-toggle-btn").text().replace('x ', '');
                Android.receiveInitSpeed(currSpeed);
                setInterval(() => {
                    ${'$'}("#content-metadata").remove();
                    var currTime = bcPlayController.getPlayController()._currTime;
                    var duration = bcPlayController.getPlayController()._duration;
                    var isMuted = bcPlayController.getPlayController()._isMuted;
                    var isPlaying = bcPlayController.getPlayController()._isPlaying;
                    var isFullscreen = bcPlayController.getPlayController()._isFullScreen;
                    Android.receivePlayerStates(currTime, duration, isMuted, isPlaying, isFullscreen);
                }, 200);
            })();
            """, null
                )
                hideController()
            }
        }

        VideoWebView.webChromeClient = object : WebChromeClient() {
            private var customView: View? = null
            private var customViewCallback: CustomViewCallback? = null
            private var originalOrientation: Int = 0

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (customView != null) {
                    onHideCustomView()
                    return
                }

                customView = view
                originalOrientation = requestedOrientation
                customViewCallback = callback

                (window.decorView as FrameLayout).addView(
                    customView, FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )

                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }

            override fun onHideCustomView() {
                (window.decorView as FrameLayout).removeView(customView)
                customView = null

                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
                requestedOrientation = originalOrientation

                customViewCallback?.onCustomViewHidden()
                customViewCallback = null
            }

            override fun onCloseWindow(window: WebView?) {
                super.onCloseWindow(window)
                finish()
            }

            override fun onJsAlert(
                view: WebView?,
                url: String?,
                message: String?,
                result: JsResult?
            ): Boolean {
                runOnUiThread {
                    val builder = MaterialAlertDialogBuilder(this@VideoPlayerActivity)
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

        }
    }

    fun formatTime(seconds: Float): String {
        val hours = (seconds / 3600).toInt()
        val minutes = ((seconds % 3600) / 60).toInt()
        val secs = (seconds % 60).toInt()
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format("%02d:%02d", minutes, secs)
        }
    }

    private fun pressKey(keyCode: Int) {
        val eventTime = SystemClock.uptimeMillis()
        VideoWebView.dispatchKeyEvent(
            KeyEvent(
                eventTime,
                eventTime,
                KeyEvent.ACTION_DOWN,
                keyCode,
                0
            )
        )
        VideoWebView.dispatchKeyEvent(
            KeyEvent(
                eventTime,
                eventTime,
                KeyEvent.ACTION_UP,
                keyCode,
                0
            )
        )
    }

    private fun startPIP() {
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            if (!isFullscreen) {
                pressKey(KeyEvent.KEYCODE_F)
            }
            hideController()


            val actions = listOf(
                RemoteAction(
                    Icon.createWithResource(this, R.drawable.baseline_replay_10_24),
                    "Backward",
                    "Backward",
                    PendingIntent.getBroadcast(
                        this,
                        REQUEST_BACKWARD,
                        Intent(ACTION_MEDIA_CONTROL).putExtra(
                            EXTRA_CONTROL_TYPE,
                            CONTROL_TYPE_BACKWARD
                        ),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                ),
                RemoteAction(
                    Icon.createWithResource(
                        this,
                        if (isPlaying) R.drawable.baseline_pause_24 else R.drawable.baseline_play_arrow_24
                    ),
                    if (isPlaying) "Pause" else "Play",
                    if (isPlaying) "Pause" else "Play",
                    PendingIntent.getBroadcast(
                        this,
                        if (isPlaying) REQUEST_PAUSE else REQUEST_PLAY,
                        Intent(ACTION_MEDIA_CONTROL).putExtra(
                            EXTRA_CONTROL_TYPE,
                            if (isPlaying) CONTROL_TYPE_PAUSE else CONTROL_TYPE_PLAY
                        ),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                ),
                RemoteAction(
                    Icon.createWithResource(this, R.drawable.baseline_forward_10_24),
                    "Forward",
                    "Forward",
                    PendingIntent.getBroadcast(
                        this,
                        REQUEST_FORWARD,
                        Intent(ACTION_MEDIA_CONTROL).putExtra(
                            EXTRA_CONTROL_TYPE,
                            CONTROL_TYPE_FORWARD
                        ),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                )
            )

            val params = PictureInPictureParams.Builder()
                .setActions(actions)
                .build()
            enterPictureInPictureMode(params)
        }
    }

    fun updatePipActions() {
        if (isFinishing || isDestroyed) return
        val actions = listOf(
            RemoteAction(
                Icon.createWithResource(this, R.drawable.baseline_replay_10_24),
                "Backward",
                "Backward",
                PendingIntent.getBroadcast(
                    this,
                    REQUEST_BACKWARD,
                    Intent(ACTION_MEDIA_CONTROL).putExtra(
                        EXTRA_CONTROL_TYPE,
                        CONTROL_TYPE_BACKWARD
                    ),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            ),
            RemoteAction(
                Icon.createWithResource(
                    this,
                    if (isPlaying) R.drawable.baseline_pause_24 else R.drawable.baseline_play_arrow_24
                ),
                if (isPlaying) "Pause" else "Play",
                if (isPlaying) "Pause" else "Play",
                PendingIntent.getBroadcast(
                    this,
                    if (isPlaying) REQUEST_PAUSE else REQUEST_PLAY,
                    Intent(ACTION_MEDIA_CONTROL).putExtra(
                        EXTRA_CONTROL_TYPE,
                        if (isPlaying) CONTROL_TYPE_PAUSE else CONTROL_TYPE_PLAY
                    ),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            ),
            RemoteAction(
                Icon.createWithResource(this, R.drawable.baseline_forward_10_24),
                "Forward",
                "Forward",
                PendingIntent.getBroadcast(
                    this,
                    REQUEST_FORWARD,
                    Intent(ACTION_MEDIA_CONTROL).putExtra(EXTRA_CONTROL_TYPE, CONTROL_TYPE_FORWARD),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
        )

        val params = PictureInPictureParams.Builder()
            .setActions(actions)
            .build()
        setPictureInPictureParams(params)
    }

    private val MediaControlReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(EXTRA_CONTROL_TYPE, 0)) {
                CONTROL_TYPE_PLAY -> {
                    VideoWebView.evaluateJavascript(
                        "bcPlayController._uniPlayerEventTarget.fire(VCPlayControllerEvent.PLAY);",
                        null
                    )
                }

                CONTROL_TYPE_PAUSE -> {
                    VideoWebView.evaluateJavascript(
                        "bcPlayController._uniPlayerEventTarget.fire(VCPlayControllerEvent.PAUSE);",
                        null
                    )
                }

                CONTROL_TYPE_FORWARD -> {
                    VideoWebView.evaluateJavascript(
                        " var a = bcPlayController.getPlayController();\n" +
                                "        if (a._duration) {\n" +
                                "            var b = (a._currTime + VCPlayControllerMedia.MOVING_TIME);\n" +
                                "        b = (b > a._duration) ? a._duration : b;\n" +
                                "        if (this._seekLimit) {\n" +
                                "            b = b > a._limitTime ? a._limitTime : b\n" +
                                "        }\n" +
                                "        a.changeCurrTimeManually(b, VCPlayControllerEvent.SEEK_END)\n" +
                                "        }\n" +
                                "        \n" +
                                "   ", null
                    )
                }

                CONTROL_TYPE_BACKWARD -> {
                    VideoWebView.evaluateJavascript(
                        " var a = bcPlayController.getPlayController();\n" +
                                "        if (a._duration) {\n" +
                                "            var b = (a._currTime - VCPlayControllerMedia.MOVING_TIME);\n" +
                                "        b = (b < 0) ? 0 : b;\n" +
                                "        a.changeCurrTimeManually(b, VCPlayControllerEvent.SEEK_END)\n" +
                                "        }",
                        null
                    )
                }
            }
        }
    }

    fun hideController() {
        VideoWebView.evaluateJavascript(
            """
    document.head.appendChild(Object.assign(document.createElement('style'), { textContent: `
        #play-controller {
            display: none !important;   
        }
    ` }));
    ${'$'}(".vc-pctrl-playback-rate-toggle-btn").remove();
""".trimIndent(), null
        )
    }

    fun showController() {
        VideoWebView.evaluateJavascript(
            """
    document.head.appendChild(Object.assign(document.createElement('style'), { textContent: `
        #play-controller {
            display: block !important;
        }
    ` }));
""".trimIndent(), null
        )
    }

    override fun onStop() {
        super.onStop()
        onStopCalled = true
        //TODO: pip 상태에서 화면 끄면 재생 중단 이슈
    }

    override fun onResume() {
        super.onResume()
        onStopCalled = false
        if (isFullscreen) {
            VideoWebView.evaluateJavascript(
                "bcPlayController.getPlayController()._eventTarget.fire(VCPlayControllerEvent.CLOSE_FULL_SCREEN);",
                null
            )
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (!isInPictureInPictureMode) {
            if (onStopCalled) {
                finish()
            }
        } else {
            updatePipActions()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // WebView 종료
        VideoWebView?.let {
            it.stopLoading()
            it.clearHistory()
            it.clearCache(true)
            it.loadUrl("about:blank")
            it.onPause()
            it.removeAllViews()
            it.destroyDrawingCache()
            it.destroy()
        }
        unregisterReceiver(MediaControlReceiver)

    }

    override fun onBackPressed() {
        if (isViewer) {
            if (!isInPictureInPictureMode) {
                startPIP()
            } else {
                super.onBackPressed()
            }
        } else {
            if (listWebView.canGoBack()) {
                listWebView.goBack()
            } else if (KLASWebView.canGoBack()) {
                KLASWebView.goBack()
            } else {
                super.onBackPressed()
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isViewer) {
            startPIP()
        }
    }

    override fun onPause() {
        super.onPause()
    }
}

class WebAppInterface(private val videoPlayerActivity: VideoPlayerActivity) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun completePageLoad() {
        videoPlayerActivity.runOnUiThread {
            videoPlayerActivity.listWebView.evaluateJavascript(
                "javascript:window.receivedData('${videoPlayerActivity.sessionId}', '${videoPlayerActivity.subj}', '${videoPlayerActivity.yearHakgi}')",
                null
            )
        }
    }

    @JavascriptInterface
    fun openExternalLink(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        videoPlayerActivity.startActivity(intent)
    }

    @JavascriptInterface
    fun openInKLAS() {
        mainHandler.post {
            videoPlayerActivity.listLayout.visibility = View.GONE
            videoPlayerActivity.KLASListLayout.visibility = View.VISIBLE
        }
    }

    @JavascriptInterface
    fun requestOnlineLecture(jsonData: String) {
        mainHandler.post {
            try {
                val data = JSONObject(jsonData)
                val grcode = data.optString("grcode")
                val subj = data.optString("subj")
                val year = data.optString("year")
                val hakgi = data.optString("hakgi")
                val bunban = data.optString("bunban")
                val module = data.optString("module")
                val lesson = data.optString("lesson")
                val oid = data.optString("oid")
                val starting = data.optString("starting")
                val contentsType = data.optString("contentsType")
                val weekNo = data.optInt("weekNo")
                val weeklyseq = data.optInt("weeklyseq")
                val width = data.optInt("width")
                val height = data.optInt("height")
                val today = data.optString("today")
                val sdate = data.optString("sdate")
                val edate = data.optString("edate")
                val ptype = data.optString("ptype")
                val learnTime = data.optString("learnTime")
                val prog = data.optInt("prog")
                val ptime = data.optString("ptime")
                var jsCode: String? = null

                if (!videoPlayerActivity.isLoadedKLASWebView) {
                    Toast.makeText(
                        videoPlayerActivity,
                        "아직 강의 정보를 불러오는 중이에요. 몇 초 후에 다시 시도해주세요.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@post
                }

                if (prog == 100) {
                    jsCode = """
                javascript:appModule.goViewCntnts(
                    '$grcode', '$subj', '$year', '$hakgi', '$bunban', '$module', '$lesson', '$oid', '$starting',
                    '$contentsType', $weekNo, $weeklyseq, $width, $height, '$today', '$sdate', '$edate',
                    '$ptype', '$learnTime', $prog, '$ptime'
                )
                """.trimIndent()
                } else {
                    jsCode = """
                javascript:lrnCerti.checkCerti(
                    '$grcode', '$subj', '$year', '$hakgi', '$bunban', '$module', '$lesson', '$oid', '$starting',
                    '$contentsType', $weekNo, $weeklyseq, $width, $height, '$today', '$sdate', '$edate',
                    '$ptype', '$learnTime', $prog, 'C', '$ptime'
                )
                """.trimIndent()
                }

                videoPlayerActivity.KLASWebView.evaluateJavascript(jsCode, null)
                videoPlayerActivity.listLayout.visibility = View.GONE
                videoPlayerActivity.KLASListLayout.visibility = View.VISIBLE
            } catch (e: Exception) {
                e.printStackTrace()
                videoPlayerActivity.runOnUiThread {
                    MaterialAlertDialogBuilder(videoPlayerActivity)
                        .setTitle("안내")
                        .setMessage("강의를 불러오는 중 오류가 발생했습니다.")
                        .setPositiveButton("확인") { dialog, _ -> dialog.dismiss() }
                        .show()
                }
            }

        }
    }

    @JavascriptInterface
    fun receivePlayerStates(
        currTime: String,
        duration: String,
        isMuted: String,
        isPlaying: String,
        isFullscreen: String
    ) {
        mainHandler.post {
            videoPlayerActivity.isPlaying = (isPlaying == "true")
            videoPlayerActivity.updatePipActions()
            if (videoPlayerActivity.isFinishing || videoPlayerActivity.isDestroyed) return@post
            videoPlayerActivity.isFullscreen = (isFullscreen == "true")

            if(isFullscreen != "true") {
                videoPlayerActivity.hideController()
            }
            videoPlayerActivity.playPauseButton.setIconResource(
                if (isPlaying == "true") R.drawable.baseline_pause_24 else R.drawable.baseline_play_arrow_24
            )
            videoPlayerActivity.muteButton.setIconResource(
                if (isMuted == "true") R.drawable.baseline_volume_off_24 else R.drawable.baseline_volume_up_24
            )

            val currTimeFloat = currTime.toFloatOrNull() ?: 0f
            val durationFloat = duration.toFloatOrNull() ?: 1f
            val currSeekbarPercent = currTimeFloat / durationFloat
            videoPlayerActivity.timeProgressBar.value = currSeekbarPercent
            videoPlayerActivity.seekbarCurrentTime.text =
                videoPlayerActivity.formatTime(currTimeFloat)
            videoPlayerActivity.seekbarTotalTime.text =
                videoPlayerActivity.formatTime(durationFloat)
            videoPlayerActivity.duration = durationFloat
        }
    }

    @JavascriptInterface
    fun receiveInitSpeed(currSpeed: String) {
        mainHandler.post {
            if (currSpeed.isNullOrEmpty()) {
                videoPlayerActivity.speedButton.text = "  1.0x"
            } else {
                videoPlayerActivity.speedButton.text = "  ${currSpeed}x"

            }
        }
    }

    @JavascriptInterface
    fun receiveVideoData(progress: String, time: String) {
        var progressText = "${
            progress.replace("<span id=\"lrnPer\">", "").replace("</span>", "").replace(">", "")
        }";
        var timeText = "${
            time.replace("<span id=\"lrnmin\">", "").replace("</span>", "").replace("<span>", "")
                .replace(">", "")
        }"

        val time = timeText.replace("학습시간 ", "").split("/")[0]
        val minutes = time.split("분")[0].trim().toInt()
        val seconds = minutes * 60
        videoPlayerActivity.lastPlaytime = seconds.toFloat()

        mainHandler.post {
            videoPlayerActivity.lectureTimeTextView.text =
                "$progressText, $timeText"
        }
    }

    @JavascriptInterface
    fun receiveVideoURL(videoURL: String) {
        mainHandler.post {
            videoPlayerActivity.originVideoURL = videoURL
            videoPlayerActivity.VideoWebView.loadUrl(videoURL)
            videoPlayerActivity.videoPlayerLayout.visibility = View.VISIBLE
            videoPlayerActivity.listLayout.visibility = View.GONE
            videoPlayerActivity.KLASListLayout.visibility = View.GONE

            Thread {
                try {
                    val doc = Jsoup.connect(videoURL).get()
                    val title = doc.title()
                    mainHandler.post {
                        videoPlayerActivity.lectureNameTextView.text = title
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }.start()
        }
    }
}
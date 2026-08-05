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
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore.Video
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.google.android.material.loadingindicator.LoadingIndicator
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.gms.common.util.DeviceProperties.isTablet
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.icecream.kwklasplus.modal.SpeedBottomSheetDialog
import com.icecream.kwklasplus.core.web.JavaScriptArgument
import com.icecream.kwklasplus.core.web.LegacyWebCallback
import com.icecream.kwklasplus.core.web.LegacyWebScripts
import com.icecream.kwklasplus.core.web.OnlineContentDecodeResult
import com.icecream.kwklasplus.core.web.PlayerBridgeCodec
import com.icecream.kwklasplus.core.web.KlasWebAutomationScripts
import com.icecream.kwklasplus.core.web.PlayerPlaybackCommand
import com.icecream.kwklasplus.core.web.PlayerSeekDirection
import com.icecream.kwklasplus.core.web.PlayerWebScripts
import com.icecream.kwklasplus.core.media.MediaMetadataResult
import com.icecream.kwklasplus.core.bridge.BridgeSurface
import com.icecream.kwklasplus.core.platform.PictureInPictureState
import com.icecream.kwklasplus.platform.web.AndroidBridgeMessageAdapter
import com.icecream.kwklasplus.platform.web.AndroidWebSurface
import com.icecream.kwklasplus.platform.web.AndroidWebSurfaceClient
import com.icecream.kwklasplus.platform.bridge.legacy.VideoLegacyBridgeCommandHandler
import com.icecream.kwklasplus.core.platform.openValidatedExternalDestination
import com.icecream.kwklasplus.core.platform.PlatformActionResult
import com.icecream.kwklasplus.feature.player.VideoPlayerScreen
import com.icecream.kwklasplus.feature.player.VideoPlayerUiState
import com.icecream.kwklasplus.ui.theme.KlasPlusTheme
import kotlinx.coroutines.launch

class VideoPlayerActivity : AppCompatActivity() {
    var isPlaying: Boolean = false
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
    internal var isPlayerVisible by mutableStateOf(false)
    var originVideoURL: String = ""
    var isLoadedKLASWebView = false
    internal var playerUiState by mutableStateOf(VideoPlayerUiState())
    var duration: Float = 1f
    var lastPlaytime: Float = 0f
    var isFullscreen: Boolean = false
    private var restoreAfterPictureInPicture = false
    private val playerBridgeCodec = PlayerBridgeCodec()
    private val bridgeMessageAdapters = mutableListOf<AndroidBridgeMessageAdapter>()
    private val webSurfaces = mutableListOf<AndroidWebSurface>()

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

        lockPortraitOnPhone()

        subj = intent.getStringExtra(IntentExtras.SUBJECT).toString()
        yearHakgi = intent.getStringExtra(IntentExtras.YEAR_HAKGI).toString()
        sessionId = intent.getStringExtra(IntentExtras.SESSION_ID).toString()

        listWebView = BackgroundWebView(this)
        KLASWebView = BackgroundWebView(this)
        VideoWebView = BackgroundWebView(this)
        listLayout = SwipeRefreshLayout(this).apply {
            addView(
                listWebView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
        }
        KLASListLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            addView(
                KLASWebView,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                ),
            )
        }
        videoPlayerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            addView(
                VideoWebView,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                ),
            )
        }
        val webContainer = FrameLayout(this).apply {
            addView(listLayout, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            addView(KLASListLayout, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            addView(videoPlayerLayout, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        setContent {
            KlasPlusTheme {
                VideoPlayerScreen(
                    webContainer = webContainer,
                    state = playerUiState,
                    isPlayerVisible = isPlayerVisible,
                    onSeek = { progress -> seekToProgress(progress) },
                    onPlayPauseClick = { pressKey(KeyEvent.KEYCODE_SPACE) },
                    onBackwardClick = { pressKey(KeyEvent.KEYCODE_DPAD_LEFT) },
                    onForwardClick = { pressKey(KeyEvent.KEYCODE_DPAD_RIGHT) },
                    onMuteClick = { pressKey(KeyEvent.KEYCODE_M) },
                    onFullscreenClick = {
                        pressKey(KeyEvent.KEYCODE_F)
                        showController()
                    },
                    onPictureInPictureClick = ::startPIP,
                    onSpeedClick = ::openSpeedSelector,
                    onCloseClick = ::confirmClose,
                    onLectureTimeClick = ::seekToLastPlaytime,
                )
            }
        }
        val listSurface = AndroidWebSurface(listWebView).also(webSurfaces::add)
        val klasSurface = AndroidWebSurface(KLASWebView).also(webSurfaces::add)
        val videoSurface = AndroidWebSurface(VideoWebView).also(webSurfaces::add)
        val bridgeDelegate = VideoBridgeDelegate(this)

        listWebView.configureAppWebView(
            allowFileAccess = true,
            allowContentAccess = true,
            javaScriptCanOpenWindowsAutomatically = true,
            disableScrollBars = false
        )
        bridgeMessageAdapters += createBridgeMessageAdapter(listWebView, bridgeDelegate)
        listWebView.loadUrl(AppUrls.ONLINE_LECTURE)

        KLASWebView.configureAppWebView(
            allowFileAccess = true,
            allowContentAccess = true,
            javaScriptCanOpenWindowsAutomatically = true,
            disableScrollBars = false
        )
        bridgeMessageAdapters += createBridgeMessageAdapter(KLASWebView, bridgeDelegate)
        KLASWebView.loadUrl(AppUrls.KLAS_ONLINE_CONTENTS)

        VideoWebView.configureAppWebView(
            allowFileAccess = true,
            allowContentAccess = true,
            javaScriptCanOpenWindowsAutomatically = true,
            disableScrollBars = false,
            mediaPlaybackRequiresUserGesture = false
        )
        bridgeMessageAdapters += createBridgeMessageAdapter(VideoWebView, bridgeDelegate)
        listLayout.setOnRefreshListener {
            listWebView.reload()
            listLayout.isRefreshing = false
        }

        var isScriptExecuted = false

        listWebView.webViewClient = AndroidWebSurfaceClient(listSurface)

        KLASWebView.webViewClient = object : AndroidWebSurfaceClient(klasSurface) {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                KLASWebView.executeWebScript(KlasWebAutomationScripts.styleOnlineContentsPage())

                listLayout.isRefreshing = false

                if (!isScriptExecuted) {
                    KLASWebView.executeWebScript(LegacyWebScripts.setLocalStorage("selectYearhakgi", yearHakgi))
                    KLASWebView.executeWebScript(LegacyWebScripts.setLocalStorage("selectSubj", subj))
                    KLASWebView.reload()
                    isScriptExecuted = true
                } else {
                    isLoadedKLASWebView = true
                }

                if (!url.contains("/OnlineCntntsStdPage")) { // 강의 시청 페이지
                    isViewer = true
                    KLASWebView.executeWebScript(KlasWebAutomationScripts.styleViewerPage())
                    KLASWebView.executeWebScript(KlasWebAutomationScripts.monitorLectureProgress())
                    KLASWebView.executeWebScript(KlasWebAutomationScripts.reportViewerVideoUrl())
                } else {
                    isViewer = false
                    isPlayerVisible = false
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

        VideoWebView.webViewClient = object : AndroidWebSurfaceClient(videoSurface) {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                if (!url.isTrustedKlasContentUrl()) return
                VideoWebView.executeWebScript(PlayerWebScripts.monitorState())
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

    private fun seekToProgress(progress: Float) {
        val seconds = progress * duration
        if (seconds.isFinite() && seconds >= 0f) {
            VideoWebView.executeWebScript(PlayerWebScripts.seekTo(seconds.toDouble()))
        }
    }

    private fun seekToLastPlaytime() {
        if (lastPlaytime.isFinite() && lastPlaytime >= 0f) {
            VideoWebView.executeWebScript(PlayerWebScripts.seekTo(lastPlaytime.toDouble()))
        }
    }

    private fun openSpeedSelector() {
        SpeedBottomSheetDialog().apply {
            setSpeedSelectionListener(object : SpeedBottomSheetDialog.SpeedSelectionListener {
                override fun onSpeedSelected(speed: Double) {
                    playerUiState = playerUiState.copy(speedText = "${speed}x")
                    VideoWebView.executeWebScript(PlayerWebScripts.changePlaybackRate(speed))
                }
            })
        }.show(supportFragmentManager, SpeedBottomSheetDialog.TAG)
    }

    private fun confirmClose() {
        MaterialAlertDialogBuilder(this)
            .setTitle("강의 종료")
            .setMessage("정말 강의 수강을 종료할까요?")
            .setPositiveButton("확인") { _, _ -> finish() }
            .setNegativeButton("취소", null)
            .show()
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
        if (
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) &&
            !isInPictureInPictureMode
        ) {
            preparePlayerForPictureInPicture()

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

            val result = appDependencies.pictureInPicture(this).enterNow(pictureInPictureState(), actions)
            if (result !is PlatformActionResult.Success) {
                restorePlayerAfterPictureInPicture()
            }
        }
    }

    private fun preparePlayerForPictureInPicture() {
        if (!isFullscreen) {
            pressKey(KeyEvent.KEYCODE_F)
        }
        hideController()
        restoreAfterPictureInPicture = true
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

        appDependencies.pictureInPicture(this).update(
            state = pictureInPictureState(),
            actions = actions,
            autoEnterEnabled = isPlayerVisible,
        )
    }

    private fun pictureInPictureState() = PictureInPictureState(
        isPlaying = isPlaying,
        aspectRatioWidth = 16,
        aspectRatioHeight = 9,
    )

    private val MediaControlReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(EXTRA_CONTROL_TYPE, 0)) {
                CONTROL_TYPE_PLAY -> {
                    VideoWebView.executeWebScript(PlayerWebScripts.playback(PlayerPlaybackCommand.PLAY))
                }

                CONTROL_TYPE_PAUSE -> {
                    VideoWebView.executeWebScript(PlayerWebScripts.playback(PlayerPlaybackCommand.PAUSE))
                }

                CONTROL_TYPE_FORWARD -> {
                    VideoWebView.executeWebScript(PlayerWebScripts.move(PlayerSeekDirection.FORWARD))
                }

                CONTROL_TYPE_BACKWARD -> {
                    VideoWebView.executeWebScript(PlayerWebScripts.move(PlayerSeekDirection.BACKWARD))
                }
            }
        }
    }

    fun hideController() {
        VideoWebView.executeWebScript(PlayerWebScripts.setControllerVisible(false))
    }

    fun showController() {
        VideoWebView.executeWebScript(PlayerWebScripts.setControllerVisible(true))
    }

    override fun onResume() {
        super.onResume()
        if (!isInPictureInPictureMode) {
            restorePlayerAfterPictureInPicture()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (!isInPictureInPictureMode) {
            restorePlayerAfterPictureInPicture()
        } else {
            updatePipActions()
        }
    }

    private fun restorePlayerAfterPictureInPicture() {
        if (!restoreAfterPictureInPicture) return
        restoreAfterPictureInPicture = false
        VideoWebView.executeWebScript(PlayerWebScripts.closeFullScreenIfAvailable())
        isFullscreen = false
        hideController()
        lockPortraitOnPhone()
    }

    override fun onDestroy() {
        webSurfaces.forEach(AndroidWebSurface::dispose)
        webSurfaces.clear()
        bridgeMessageAdapters.forEach(AndroidBridgeMessageAdapter::dispose)
        bridgeMessageAdapters.clear()
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

    private fun createBridgeMessageAdapter(
        webView: WebView,
        bridgeDelegate: VideoBridgeDelegate,
    ) = AndroidBridgeMessageAdapter(
        webView,
        BridgeSurface.VIDEO,
        lifecycleScope,
        VideoLegacyBridgeCommandHandler(bridgeDelegate),
    ).also { it.install() }

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
        if (!isPlayerVisible || isInPictureInPictureMode) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            preparePlayerForPictureInPicture()
            updatePipActions()
        } else {
            startPIP()
        }
    }

    override fun onPause() {
        super.onPause()
    }
}

class VideoBridgeDelegate(private val videoPlayerActivity: VideoPlayerActivity) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val playerBridgeCodec = PlayerBridgeCodec()

    fun completePageLoad() {
        videoPlayerActivity.runOnUiThread {
            videoPlayerActivity.listWebView.executeWebScript(
                LegacyWebScripts.call(
                    LegacyWebCallback.RECEIVE_DATA,
                    JavaScriptArgument.Text(videoPlayerActivity.sessionId),
                    JavaScriptArgument.Text(videoPlayerActivity.subj),
                    JavaScriptArgument.Text(videoPlayerActivity.yearHakgi),
                ),
            )
        }
    }

    fun openExternalLink(url: String) {
        videoPlayerActivity.openValidatedExternalDestination(url)
    }

    fun openInKLAS() {
        mainHandler.post {
            videoPlayerActivity.isPlayerVisible = false
            videoPlayerActivity.updatePipActions()
            videoPlayerActivity.listLayout.visibility = View.GONE
            videoPlayerActivity.KLASListLayout.visibility = View.VISIBLE
        }
    }

    fun requestOnlineLecture(jsonData: String) {
        mainHandler.post {
            val decoded = playerBridgeCodec.decodeOnlineContent(jsonData)
            if (decoded !is OnlineContentDecodeResult.Success) {
                MaterialAlertDialogBuilder(videoPlayerActivity)
                    .setTitle("안내")
                    .setMessage("강의를 불러오는 중 오류가 발생했습니다.")
                    .setPositiveButton("확인") { dialog, _ -> dialog.dismiss() }
                    .show()
                return@post
            }
            if (!videoPlayerActivity.isLoadedKLASWebView) {
                Toast.makeText(
                    videoPlayerActivity,
                    "아직 강의 정보를 불러오는 중이에요. 몇 초 후에 다시 시도해주세요.",
                    Toast.LENGTH_SHORT
                ).show()
                return@post
            }

            videoPlayerActivity.KLASWebView.executeWebScript(
                PlayerWebScripts.openOnlineContent(decoded.request),
            )
            videoPlayerActivity.isPlayerVisible = false
            videoPlayerActivity.updatePipActions()
            videoPlayerActivity.listLayout.visibility = View.GONE
            videoPlayerActivity.KLASListLayout.visibility = View.VISIBLE
        }
    }

    fun receivePlayerStates(
        currTime: String,
        duration: String,
        isMuted: String,
        isPlaying: String,
        isFullscreen: String
    ) {
        mainHandler.post {
            val state = playerBridgeCodec.playerState(
                currTime,
                duration,
                isMuted,
                isPlaying,
                isFullscreen,
            )
            videoPlayerActivity.isPlaying = state.isPlaying
            videoPlayerActivity.updatePipActions()
            if (videoPlayerActivity.isFinishing || videoPlayerActivity.isDestroyed) return@post
            videoPlayerActivity.isFullscreen = state.isFullscreen

            if (!state.isFullscreen) {
                videoPlayerActivity.hideController()
            }
            videoPlayerActivity.duration = state.durationSeconds
            videoPlayerActivity.playerUiState = videoPlayerActivity.playerUiState.copy(
                progress = state.progressFraction,
                currentTime = playerBridgeCodec.formatTime(state.currentSeconds),
                totalTime = playerBridgeCodec.formatTime(state.durationSeconds),
                isPlaying = state.isPlaying,
                isMuted = state.isMuted,
            )
        }
    }

    fun receiveInitSpeed(currSpeed: String) {
        mainHandler.post {
            if (currSpeed.isNullOrEmpty()) {
                videoPlayerActivity.playerUiState = videoPlayerActivity.playerUiState.copy(speedText = "1.0x")
            } else {
                videoPlayerActivity.playerUiState = videoPlayerActivity.playerUiState.copy(speedText = "${currSpeed}x")

            }
        }
    }

    fun receiveVideoData(progress: String, time: String) {
        val parsed = playerBridgeCodec.lectureProgress(progress, time) ?: return
        videoPlayerActivity.lastPlaytime = parsed.playedSeconds.toFloat()

        mainHandler.post {
            videoPlayerActivity.playerUiState = videoPlayerActivity.playerUiState.copy(
                lectureTime = parsed.displayText,
            )
        }
    }

    fun receiveVideoURL(videoURL: String) {
        mainHandler.post {
            if (!videoURL.isTrustedKlasContentUrl()) {
                Toast.makeText(
                    videoPlayerActivity,
                    "강의 영상 주소를 확인하지 못했습니다.",
                    Toast.LENGTH_SHORT,
                ).show()
                return@post
            }
            videoPlayerActivity.originVideoURL = videoURL
            videoPlayerActivity.VideoWebView.loadUrl(videoURL)
            videoPlayerActivity.isPlayerVisible = true
            videoPlayerActivity.updatePipActions()
            videoPlayerActivity.videoPlayerLayout.visibility = View.VISIBLE
            videoPlayerActivity.listLayout.visibility = View.GONE
            videoPlayerActivity.KLASListLayout.visibility = View.GONE

            videoPlayerActivity.lifecycleScope.launch {
                when (
                    val result = videoPlayerActivity.appDependencies.mediaMetadataRepository
                        .fetchTitle(videoURL)
                ) {
                    is MediaMetadataResult.Success -> {
                        videoPlayerActivity.playerUiState = videoPlayerActivity.playerUiState.copy(
                            lectureName = result.title,
                        )
                    }
                    else -> Unit
                }
            }
        }
    }

    fun performHapticFeedback(type: String) {
        videoPlayerActivity.runOnUiThread {
            videoPlayerActivity.appDependencies.haptics(videoPlayerActivity.listWebView).performLegacy(type)
        }
    }
}

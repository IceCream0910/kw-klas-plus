package com.icecream.kwklasplus

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.os.PowerManager
import androidx.lifecycle.Lifecycle
import com.icecream.kwklasplus.feature.player.PipPlaybackLifecycle
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.PictureInPictureUiState
import android.graphics.Rect
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
import androidx.core.view.WindowInsetsControllerCompat
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
import com.icecream.kwklasplus.feature.player.SingleLecturePlayback
import com.icecream.kwklasplus.feature.player.LectureCertificationContinuation
import com.icecream.kwklasplus.core.web.WebScript
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
    internal var isPipPresentation by mutableStateOf(false)
    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
    private var inlineOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private var deferredInlineOrientation = false
    internal val certificationContinuation = LectureCertificationContinuation<WebScript>()
    private var pipSourceRect: Rect? = null
    internal var lectureNavigationRevision = 0
    private var replacementDialog: androidx.appcompat.app.AlertDialog? = null
    private var klasAlertDialog: androidx.appcompat.app.AlertDialog? = null
    private var selectedLecture: PlayerWebScripts.OnlineContentRequest? = null
    internal var hasPlaybackSession = false
    private val pipPlaybackLifecycle = PipPlaybackLifecycle()
    internal var loadedViewerRevision = -1
    private var pendingLecture: PlayerWebScripts.OnlineContentRequest? = null
    internal var stoppingPlayback = false
        private set
    private var playbackStopped: (() -> Unit)? = null
    private var playbackStopFailed: (() -> Unit)? = null
    private val stopTimeout = Runnable {
        if (stoppingPlayback) {
            val failed = playbackStopFailed
            playbackStopped = null
            disposePlayerWebViews()
            finish()
            playbackGate.release(this)
            failed?.invoke()
        }
    }
    private var playerWebViewsDisposed = false
    private val playerBridgeCodec = PlayerBridgeCodec()
    private val bridgeMessageAdapters = mutableListOf<AndroidBridgeMessageAdapter>()
    private val webSurfaces = mutableListOf<AndroidWebSurface>()

    companion object {
        private val playbackGate = SingleLecturePlayback<VideoPlayerActivity>()
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
        private const val CONTROL_TYPE_CLOSE = 4
    }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intentFilter = IntentFilter(ACTION_MEDIA_CONTROL).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        registerReceiver(MediaControlReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)

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
                    isPictureInPicture = isPipPresentation,
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
        VideoWebView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updatePipActions() }
        listLayout.setOnRefreshListener {
            listWebView.reload()
            listLayout.isRefreshing = false
        }

        var isScriptExecuted = false

        listWebView.webViewClient = AndroidWebSurfaceClient(listSurface)

        KLASWebView.webViewClient = object : AndroidWebSurfaceClient(klasSurface) {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                lectureNavigationRevision += 1
                if (url?.contains("/viewer/") == true || url?.contains("Login") == true) certificationContinuation.clear()
            }

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

                if (!url.contains("/OnlineCntntsStdPage") && !url.contains("Certi")) {
                    isViewer = true
                    KLASWebView.executeWebScript(KlasWebAutomationScripts.styleViewerPage())
                    KLASWebView.executeWebScript(KlasWebAutomationScripts.monitorLectureProgress())
                    KLASWebView.executeWebScript(KlasWebAutomationScripts.reportViewerVideoUrl())
                } else if (url.contains("/OnlineCntntsStdPage")) {
                    isViewer = false
                    isPlayerVisible = false
                    pendingLecture?.let { request ->
                        pendingLecture = null
                        executeLectureRequest(request)
                    }
                }
            }
        }

        KLASWebView.webChromeClient = object : WebChromeClient() {
            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                if (view !== KLASWebView || url?.isTrustedKlasContentUrl() != true ||
                    Uri.parse(url).host != "klas.kw.ac.kr"
                ) return false
                val script = certificationContinuation.onAlert(message.orEmpty())
                val page = KLASWebView.url
                val revision = lectureNavigationRevision
                klasAlertDialog?.dismiss()
                var completed = false
                klasAlertDialog = MaterialAlertDialogBuilder(this@VideoPlayerActivity)
                    .setTitle("안내")
                    .setMessage(message)
                    .setPositiveButton("확인") { _, _ ->
                        completed = true
                        result?.confirm()
                        if (script != null) {
                            // 학교 자체 이동을 우선하고, 같은 문서에 남았을 때만 이어서 실행한다.
                            KLASWebView.postDelayed({
                                if (!isFinishing && !isDestroyed && lectureNavigationRevision == revision &&
                                    KLASWebView.url == page && !isPlayerVisible
                                ) KLASWebView.executeWebScript(script)
                            }, 250)
                        }
                    }
                    .setOnDismissListener {
                        if (!completed) result?.confirm()
                        klasAlertDialog = null
                    }
                    .setCancelable(false)
                    .show()
                return true
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
                if (url == "about:blank" && stoppingPlayback) {
                    VideoWebView.removeCallbacks(stopTimeout)
                    val completion = playbackStopped
                    playbackStopped = null
                    playbackStopFailed = null
                    completion?.invoke()
                    return
                }
                if (stoppingPlayback || !url.isTrustedKlasContentUrl()) return
                VideoWebView.executeWebScript(PlayerWebScripts.monitorStateWhenReady())
                hideController()
            }
        }

        VideoWebView.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (fullscreenView != null || view == null) {
                    callback?.onCustomViewHidden()
                    return
                }
                fullscreenView = view
                inlineOrientation = requestedOrientation
                fullscreenCallback = callback
                (window.decorView as FrameLayout).addView(
                    view, FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                    ),
                )
                setFullscreenBars(true)
                if (!isInPictureInPictureMode && !isPipPresentation) {
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
                view.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updatePipActions() }
            }

            override fun onHideCustomView() {
                val view = fullscreenView ?: return
                fullscreenView = null
                (window.decorView as FrameLayout).removeView(view)
                val callback = fullscreenCallback
                fullscreenCallback = null
                if (isInPictureInPictureMode || isPipPresentation) {
                    deferredInlineOrientation = true
                } else {
                    setFullscreenBars(false)
                    requestedOrientation = inlineOrientation
                }
                callback?.onCustomViewHidden()
                updatePipActions()
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

    internal fun selectLecture(request: PlayerWebScripts.OnlineContentRequest) {
        if (playbackGate.owner === this && selectedLecture?.let { lectureIdentity(it) } == lectureIdentity(request) &&
            !stoppingPlayback
        ) {
            if (originVideoURL.isEmpty()) return
            isPlayerVisible = true
            videoPlayerLayout.visibility = View.VISIBLE
            KLASListLayout.visibility = View.GONE
            listLayout.visibility = View.GONE
            return
        }
        withSinglePlayback {
            selectedLecture = request
            originVideoURL = ""
            hasPlaybackSession = false
            loadedViewerRevision = -1
            isPlaying = false
            playerUiState = VideoPlayerUiState()
            lastPlaytime = 0f
            isPlayerVisible = false
            videoPlayerLayout.visibility = View.GONE
            listLayout.visibility = View.GONE
            KLASListLayout.visibility = View.VISIBLE
            updatePipActions()
            if (KLASWebView.url?.contains("/OnlineCntntsStdPage") != true || isViewer) {
                pendingLecture = request
                isLoadedKLASWebView = false
                KLASWebView.loadUrl(AppUrls.KLAS_ONLINE_CONTENTS)
            } else {
                executeLectureRequest(request)
            }
        }
    }

    private fun lectureIdentity(request: PlayerWebScripts.OnlineContentRequest) = listOf(
        request.subjectId, request.year, request.semester, request.classNumber,
        request.module, request.lesson, request.objectId,
        request.weekNumber.toString(), request.weeklySequence.toString(),
    )

    private fun executeLectureRequest(request: PlayerWebScripts.OnlineContentRequest) {
        if (playbackGate.owner !== this || isFinishing || isDestroyed) return
        lectureNavigationRevision += 1
        certificationContinuation.begin(PlayerWebScripts.openOnlineContentViewer(request))
        KLASWebView.executeWebScript(PlayerWebScripts.openOnlineContent(request))
    }

    internal fun ownsPlayback() = playbackGate.owner === this && !stoppingPlayback

    internal fun onPlayerReady(durationSeconds: Float) {
        if (!ownsPlayback()) return
        if (durationSeconds > 0 && durationSeconds.isFinite()) hasPlaybackSession = true
    }

    internal fun withSinglePlayback(onAccepted: () -> Unit) {
        if (isFinishing || isDestroyed || replacementDialog?.isShowing == true || playbackGate.isReplacing) return
        val previous = playbackGate.owner
        val replace = {
            if (playbackGate.owner !== previous) {
                VideoWebView.post { withSinglePlayback(onAccepted) }
            } else if (!isFinishing && !isDestroyed && playbackGate.begin(this, previous)) {
                val start = {
                    if (!isFinishing && !isDestroyed && playbackGate.complete(this)) {
                        stoppingPlayback = false
                        onAccepted()
                    } else {
                        playbackGate.cancel(this)
                    }
                }
                if (previous != null && !previous.playerWebViewsDisposed) {
                    previous.stopForReplacement(
                        onFailed = {
                            playbackGate.cancel(this)
                            Toast.makeText(this, "강의 전환에 실패했습니다. 강의를 다시 선택해주세요.", Toast.LENGTH_SHORT).show()
                        },
                        onStopped = {
                            if (previous !== this) {
                                previous.disposePlayerWebViews()
                                previous.finish()
                            }
                            start()
                        },
                    )
                } else {
                    start()
                }
            }
        }
        if (previous == null || !previous.hasPlaybackSession) {
            replace()
            return
        }
        replacementDialog = MaterialAlertDialogBuilder(this)
            .setTitle("강의 전환")
            .setMessage("지금 재생 중인 강의를 종료하고 선택한 강의를 재생할까요?")
            .setPositiveButton("종료하고 재생") { _, _ -> replace() }
            .setNegativeButton("취소", null)
            .setOnDismissListener { replacementDialog = null }
            .show()
    }

    private fun stopForReplacement(onFailed: () -> Unit, onStopped: () -> Unit) {
        stoppingPlayback = true
        certificationContinuation.clear()
        pendingLecture = null
        lectureNavigationRevision += 1
        isPlayerVisible = false
        updatePipActions()
        playbackStopped = onStopped
        playbackStopFailed = onFailed
        VideoWebView.executeWebScript(PlayerWebScripts.playback(PlayerPlaybackCommand.PAUSE))
        VideoWebView.stopLoading()
        VideoWebView.loadUrl("about:blank")
        VideoWebView.postDelayed(stopTimeout, 3_000)
    }

    private fun disposePlayerWebViews() {
        if (playerWebViewsDisposed) return
        playerWebViewsDisposed = true
        hasPlaybackSession = false
        klasAlertDialog?.dismiss()
        certificationContinuation.clear()
        pendingLecture = null
        playbackStopped = null
        playbackStopFailed = null
        VideoWebView.removeCallbacks(stopTimeout)
        webSurfaces.forEach(AndroidWebSurface::dispose)
        webSurfaces.clear()
        bridgeMessageAdapters.forEach(AndroidBridgeMessageAdapter::dispose)
        bridgeMessageAdapters.clear()
        listOf(listWebView, KLASWebView, VideoWebView).forEach { view ->
            view.stopLoading()
            (view.parent as? android.view.ViewGroup)?.removeView(view)
            view.removeAllViews()
            view.destroy()
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
        if (!isPlayerVisible) return
        if (
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) &&
            !isInPictureInPictureMode
        ) {
            capturePipSourceRect()
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

            val result = appDependencies.pictureInPicture(this).enterNow(pictureInPictureState(), actions, pipSourceRect, closePipAction())
            if (result !is PlatformActionResult.Success) {
                isPipPresentation = false
                if (fullscreenView != null) showController()
                Toast.makeText(this, "PIP를 시작하지 못했습니다. 다시 시도해주세요.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun capturePipSourceRect() {
        if (isInPictureInPictureMode || isPipPresentation || !isPlayerVisible) return
        val bounds = Rect()
        if ((fullscreenView ?: VideoWebView).getGlobalVisibleRect(bounds) && !bounds.isEmpty) {
            pipSourceRect = bounds
        }
    }

    private fun setFullscreenBars(fullscreen: Boolean) {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (fullscreen) hide(WindowInsetsCompat.Type.systemBars())
            else show(WindowInsetsCompat.Type.systemBars())
        }
    }

    fun updatePipActions() {
        if (isFinishing || isDestroyed || playerWebViewsDisposed) return
        capturePipSourceRect()
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
            sourceRectHint = pipSourceRect,
            closeAction = closePipAction(),
        )
    }

    private fun closePipAction() = RemoteAction(
        Icon.createWithResource(this, R.drawable.baseline_close_24),
        "강의 종료",
        "강의 종료",
        PendingIntent.getBroadcast(
            this, CONTROL_TYPE_CLOSE,
            Intent(ACTION_MEDIA_CONTROL).setPackage(packageName)
                .putExtra(EXTRA_CONTROL_TYPE, CONTROL_TYPE_CLOSE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        ),
    )

    internal fun closePlaybackFromPip() {
        if (playerWebViewsDisposed) return
        stoppingPlayback = true
        isPlaying = false
        isPlayerVisible = false
        playbackGate.release(this)
        disposePlayerWebViews()
        finish()
    }

    private fun pictureInPictureState() = PictureInPictureState(
        isPlaying = isPlaying,
        aspectRatioWidth = 16,
        aspectRatioHeight = 9,
    )

    private val MediaControlReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!ownsPlayback() || playerWebViewsDisposed) return
            when (intent.getIntExtra(EXTRA_CONTROL_TYPE, 0)) {
                CONTROL_TYPE_CLOSE -> closePlaybackFromPip()
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
        if (playerWebViewsDisposed) return
        VideoWebView.executeWebScript(PlayerWebScripts.setControllerVisible(false))
    }

    fun showController() {
        if (playerWebViewsDisposed) return
        VideoWebView.executeWebScript(PlayerWebScripts.setControllerVisible(true))
    }

    override fun onResume() {
        super.onResume()
        pipPlaybackLifecycle.onResumed(isInPictureInPictureMode)
        if (!isInPictureInPictureMode) {
            restorePlayerAfterPictureInPicture()
        }
    }

    override fun onStop() {
        super.onStop()
        if (pipPlaybackLifecycle.shouldCloseOnStop(
                interactive = getSystemService(PowerManager::class.java).isInteractive,
                locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked,
                changingConfiguration = isChangingConfigurations,
            )) closePlaybackFromPip()
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pipPlaybackLifecycle.onPipModeChanged(isInPictureInPictureMode)
        if (!isInPictureInPictureMode) {
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) restorePlayerAfterPictureInPicture()
        } else {
            isPipPresentation = true
            hideController()
            updatePipActions()
        }
    }

    private fun restorePlayerAfterPictureInPicture() {
        if (!isPipPresentation || playerWebViewsDisposed || isFinishing) return
        isPipPresentation = false
        if (deferredInlineOrientation) {
            deferredInlineOrientation = false
            requestedOrientation = inlineOrientation
        }
        setFullscreenBars(fullscreenView != null)
        if (fullscreenView != null) showController() else hideController()
        VideoWebView.post { updatePipActions() }
    }

    override fun onPictureInPictureUiStateChanged(pipState: PictureInPictureUiState) {
        super.onPictureInPictureUiStateChanged(pipState)
        if (Build.VERSION.SDK_INT >= 35 && pipState.isTransitioningToPip) {
            capturePipSourceRect()
            isPipPresentation = true
            hideController()
        }
    }

    override fun onDestroy() {
        replacementDialog?.dismiss()
        val completion = playbackStopped
        playbackGate.release(this)
        disposePlayerWebViews()
        completion?.invoke()
        unregisterReceiver(MediaControlReceiver)
        super.onDestroy()
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
        if (playbackGate.isReplacing) return
        pendingLecture = null
        certificationContinuation.clear()
        lectureNavigationRevision += 1
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

            videoPlayerActivity.selectLecture(decoded.request)
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
            if (!videoPlayerActivity.ownsPlayback()) return@post
            val state = playerBridgeCodec.playerState(
                currTime,
                duration,
                isMuted,
                isPlaying,
                isFullscreen,
            )
            videoPlayerActivity.isPlaying = state.isPlaying
            videoPlayerActivity.onPlayerReady(state.durationSeconds)
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
                durationSeconds = state.durationSeconds,
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
            if (videoPlayerActivity.stoppingPlayback || videoPlayerActivity.isFinishing || videoPlayerActivity.isDestroyed) return@post
            if (videoPlayerActivity.ownsPlayback() && videoPlayerActivity.originVideoURL.isNotEmpty() &&
                videoPlayerActivity.loadedViewerRevision == videoPlayerActivity.lectureNavigationRevision
            ) return@post
            val loadVideo = {
                videoPlayerActivity.certificationContinuation.clear()
                videoPlayerActivity.originVideoURL = videoURL
                videoPlayerActivity.loadedViewerRevision = videoPlayerActivity.lectureNavigationRevision
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
                            if (videoPlayerActivity.originVideoURL == videoURL && videoPlayerActivity.ownsPlayback()) {
                                videoPlayerActivity.playerUiState = videoPlayerActivity.playerUiState.copy(
                                    lectureName = result.title,
                                )
                            }
                        }
                        else -> Unit
                    }
                }
            }
            if (videoPlayerActivity.ownsPlayback() &&
                (videoPlayerActivity.originVideoURL.isEmpty() || videoPlayerActivity.originVideoURL == videoURL)
            ) loadVideo()
            else videoPlayerActivity.withSinglePlayback { loadVideo() }

        }
    }

    fun performHapticFeedback(type: String) {
        videoPlayerActivity.runOnUiThread {
            videoPlayerActivity.appDependencies.haptics(videoPlayerActivity.listWebView).performLegacy(type)
        }
    }
}

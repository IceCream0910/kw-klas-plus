package com.icecream.kwklasplus

import android.app.Activity
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.webkit.WebSettings
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.icecream.kwklasplus.core.attendance.QrAttendancePayload
import com.icecream.kwklasplus.core.attendance.QrAttendancePayloadCodec
import com.icecream.kwklasplus.core.attendance.QrAttendancePayloadDecodeResult
import com.icecream.kwklasplus.core.attendance.QrCheckInResult
import com.icecream.kwklasplus.core.network.KlasUserAgent
import com.icecream.kwklasplus.core.platform.QrScanResult
import com.icecream.kwklasplus.core.security.SecretValue
import com.icecream.kwklasplus.feature.attendance.QrCheckInLoadingScreen
import com.icecream.kwklasplus.ui.theme.KlasPlusTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class QRScanActivity : AppCompatActivity() {
    private lateinit var payload: QrAttendancePayload
    private lateinit var session: SecretValue
    private var scanInProgress = false
    private var scannerSurfaceWasShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KlasPlusTheme {
                QrCheckInLoadingScreen()
            }
        }

        val bodyJson = intent.getStringExtra(IntentExtras.BODY_JSON)
        val sessionId = intent.getStringExtra(IntentExtras.SESSION_ID)
        val decoded = bodyJson?.let(QrAttendancePayloadCodec()::decode)
        if (decoded !is QrAttendancePayloadDecodeResult.Success || sessionId.isNullOrBlank()) {
            showDialog("오류 발생", "출석 정보를 불러오지 못했습니다. 앱을 재시작한 후 다시 시도해보세요.")
            return
        }
        payload = decoded.payload
        session = SecretValue.of(sessionId)

        startQrScanner()
    }

    private fun startQrScanner() {
        scanInProgress = true
        scannerSurfaceWasShown = false
        lifecycleScope.launch {
            when (val result = appDependencies.qrScanner(this@QRScanActivity).scan()) {
                is QrScanResult.Success -> qrScanComplete(result.value)
                QrScanResult.Cancelled -> finishAsCancelled()
                QrScanResult.PermissionRequired -> showScannerFailure("scanner_camera_permission_required")
                is QrScanResult.Failed -> {
                    if (scannerSurfaceWasShown && result.reason.startsWith("scanner_start_failed")) {
                        finishAsCancelled()
                    } else {
                        showScannerFailure(result.reason)
                    }
                }
            }
        }
    }

    override fun onStop() {
        if (scanInProgress) scannerSurfaceWasShown = true
        super.onStop()
    }

    private fun finishAsCancelled() {
        scanInProgress = false
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    private fun showScannerFailure(reason: String) {
        scanInProgress = false
        MaterialAlertDialogBuilder(this)
            .setTitle("QR 스캔 실패")
            .setMessage(scannerFailureMessage(reason))
            .setPositiveButton("확인") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun scannerFailureMessage(reason: String): String = when (reason) {
        "play_services_too_old" ->
            "QR 스캔을 위해 Google Play 서비스를 업데이트해주세요."
        "scanner_camera_permission_required" ->
            "Google Play 서비스의 카메라 권한을 허용해주세요."
        "scanner_module_unavailable" ->
            "QR 스캐너 구성요소를 설치하지 못했습니다. 네트워크 연결을 확인한 후 다시 시도해주세요."
        else -> "QR 스캔 중 오류가 발생했습니다: $reason"
    }

    private fun qrScanComplete(qr: String) {
        lifecycleScope.launch {
            val result = try {
                appDependencies.attendanceRepository.checkIn(
                    session = session,
                    userAgent = KlasUserAgent.fromPlatform(
                        WebSettings.getDefaultUserAgent(this@QRScanActivity),
                    ),
                    payload = payload,
                    scannedCode = SecretValue.of(qr),
                )
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: Exception) {
                showCheckInError("내부 오류가 발생했습니다: ${failureLocation(cause)}")
                return@launch
            }
            when (result) {
                QrCheckInResult.Success -> {
                    performResultHaptic(HapticFeedbackConstants.CONFIRM)
                    showDialog("출석 체크 성공", "정상적으로 출석 처리 되었습니다.")
                }
                is QrCheckInResult.Rejected -> {
                    performResultHaptic(HapticFeedbackConstants.REJECT)
                    showDialog("출석 체크 실패", result.messages.joinToString(" "))
                }
                QrCheckInResult.SessionExpired -> showCheckInError(
                    "로그인 세션이 만료되었습니다. 앱을 재시작한 후 다시 시도해보세요.",
                )
                QrCheckInResult.Timeout -> showCheckInError("서버 응답 시간이 초과되었습니다.")
                QrCheckInResult.NetworkFailure -> showCheckInError("네트워크 연결을 확인해주세요.")
                is QrCheckInResult.HttpFailure -> showCheckInError("서버 오류: ${result.statusCode}")
                QrCheckInResult.EmptyResponse -> showCheckInError("응답 내용이 비어있습니다.")
                QrCheckInResult.MalformedResponse -> showCheckInError("서버 응답을 처리하지 못했습니다.")
            }
        }
    }

    private fun performResultHaptic(feedbackConstant: Int) {
        window.decorView.performHapticFeedback(feedbackConstant)
    }

    private fun showCheckInError(message: String) {
        showDialog("오류 발생", "출석 체크 중 오류가 발생했습니다. $message")
    }

    private fun failureLocation(cause: Exception): String {
        val frame = cause.stackTrace.firstOrNull {
            it.className.startsWith("com.icecream.kwklasplus")
        } ?: cause.stackTrace.firstOrNull()
        return buildString {
            append(cause.javaClass.simpleName)
            frame?.let {
                append("_")
                append(it.className.substringAfterLast('.'))
                append("_")
                append(it.methodName)
                append("_")
                append(it.lineNumber)
            }
        }
    }

    private fun showDialog(title: String, message: String) {
        if (!isFinishing) {
            MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("확인") { _, _ -> finish() }
                .show()
        }
    }

}

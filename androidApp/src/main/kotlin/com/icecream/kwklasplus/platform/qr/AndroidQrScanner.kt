package com.icecream.kwklasplus.platform.qr

import android.app.Activity
import android.util.Log
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.icecream.kwklasplus.core.platform.QrScanResult
import com.icecream.kwklasplus.core.platform.QrScanner
import kotlinx.coroutines.CancellableContinuation
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class AndroidQrScanner(
    private val activity: Activity,
) : QrScanner {
    override suspend fun scan(): QrScanResult = suspendCancellableCoroutine { continuation ->
        try {
            val options = GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .enableAutoZoom()
                .build()
            val scanner = GmsBarcodeScanning.getClient(activity, options)
            startScan(scanner, continuation)
        } catch (cause: RuntimeException) {
            Log.e(TAG, "Google Code Scanner 초기화 실패", cause)
            if (continuation.isActive) {
                continuation.resume(QrScanResult.Failed(initializationFailureReason(cause)))
            }
        }
    }

    private fun startScan(
        scanner: GmsBarcodeScanner,
        continuation: CancellableContinuation<QrScanResult>,
    ) {
        try {
            scanner.startScan()
                .addOnSuccessListener { barcode ->
                    if (!continuation.isActive) return@addOnSuccessListener
                    val value = barcode.rawValue
                    continuation.resume(
                        if (barcode.format != Barcode.FORMAT_QR_CODE) {
                            QrScanResult.Failed("unsupported_barcode_format")
                        } else if (value.isNullOrBlank()) {
                            QrScanResult.Failed("empty_qr_value")
                        } else {
                            QrScanResult.Success(value)
                        },
                    )
                }
                .addOnCanceledListener {
                    if (continuation.isActive) continuation.resume(QrScanResult.Cancelled)
                }
                .addOnFailureListener { cause ->
                    completeFailure(continuation, cause, "scanner_start_failed")
                }
        } catch (cause: RuntimeException) {
            if (continuation.isActive) {
                continuation.resume(
                    QrScanResult.Failed("scanner_start_failed_${cause.javaClass.simpleName}"),
                )
            }
        }
    }

    private fun completeFailure(
        continuation: CancellableContinuation<QrScanResult>,
        cause: Exception,
        fallbackReason: String,
    ) {
        if (!continuation.isActive) return
        val result = when ((cause as? MlKitException)?.errorCode) {
            MlKitException.CODE_SCANNER_CANCELLED -> QrScanResult.Cancelled
            MlKitException.CODE_SCANNER_CAMERA_PERMISSION_NOT_GRANTED -> QrScanResult.PermissionRequired
            MlKitException.CODE_SCANNER_UNAVAILABLE -> QrScanResult.Failed("scanner_module_unavailable")
            MlKitException.CODE_SCANNER_GOOGLE_PLAY_SERVICES_VERSION_TOO_OLD ->
                QrScanResult.Failed("play_services_too_old")
            else -> QrScanResult.Failed(fallbackReason)
        }
        continuation.resume(result)
    }

    private fun initializationFailureReason(cause: RuntimeException): String {
        val frame = cause.stackTrace.firstOrNull {
            it.className.startsWith("com.google.android.gms") ||
                it.className.startsWith("com.google.mlkit")
        }
        val location = frame?.let {
            "_${it.className.substringAfterLast('.')}_${it.methodName}_${it.lineNumber}"
        }.orEmpty()
        return "scanner_initialization_failed_${cause.javaClass.simpleName}$location"
    }

    private companion object {
        const val TAG = "AndroidQrScanner"
    }
}

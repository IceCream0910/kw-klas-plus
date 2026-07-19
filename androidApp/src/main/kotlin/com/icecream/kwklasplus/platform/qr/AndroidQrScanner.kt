package com.icecream.kwklasplus.platform.qr

import android.app.Activity
import android.util.Log
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
                    continuation.resume(
                        AndroidQrScanResultMapper.fromBarcode(barcode.format, barcode.rawValue),
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
                    AndroidQrScanResultMapper.fromFailure(
                        cause,
                        "scanner_start_failed_${cause.javaClass.simpleName}",
                    ),
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
        continuation.resume(AndroidQrScanResultMapper.fromFailure(cause, fallbackReason))
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

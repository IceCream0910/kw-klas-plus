package com.icecream.kwklasplus.platform.qr

import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.barcode.common.Barcode
import com.icecream.kwklasplus.core.platform.QrScanResult
import java.util.concurrent.CancellationException

internal object AndroidQrScanResultMapper {
    fun fromBarcode(format: Int, value: String?): QrScanResult = when {
        value.isNullOrBlank() -> QrScanResult.Cancelled
        format != Barcode.FORMAT_QR_CODE -> QrScanResult.Failed("unsupported_barcode_format")
        else -> QrScanResult.Success(value)
    }

    fun fromFailure(cause: Throwable, fallbackReason: String): QrScanResult {
        if (causeChain(cause).any(::isCancellation)) return QrScanResult.Cancelled
        val mlKitException = causeChain(cause).filterIsInstance<MlKitException>().firstOrNull()
        return when (mlKitException?.errorCode) {
            MlKitException.CODE_SCANNER_CAMERA_PERMISSION_NOT_GRANTED ->
                QrScanResult.PermissionRequired
            MlKitException.CODE_SCANNER_UNAVAILABLE ->
                QrScanResult.Failed("scanner_module_unavailable")
            MlKitException.CODE_SCANNER_GOOGLE_PLAY_SERVICES_VERSION_TOO_OLD ->
                QrScanResult.Failed("play_services_too_old")
            else -> QrScanResult.Failed(fallbackReason)
        }
    }

    private fun isCancellation(cause: Throwable): Boolean =
        cause is CancellationException ||
            cause is ApiException && cause.statusCode == CommonStatusCodes.CANCELED ||
            cause is MlKitException && isMlKitCancellationCode(cause.errorCode)

    internal fun isMlKitCancellationCode(errorCode: Int): Boolean =
        errorCode == MlKitException.CODE_SCANNER_CANCELLED

    private fun causeChain(cause: Throwable): Sequence<Throwable> =
        generateSequence(cause) { current -> current.cause?.takeUnless { it === current } }
}

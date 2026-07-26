package com.icecream.kwklasplus.platform.qr

import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Status
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.barcode.common.Barcode
import com.icecream.kwklasplus.core.platform.QrScanResult
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.CancellationException

class AndroidQrScanResultMapperTest {
    @Test
    fun emptyScanValueIsTreatedAsSilentCancellation() {
        assertEquals(
            QrScanResult.Cancelled,
            AndroidQrScanResultMapper.fromBarcode(Barcode.FORMAT_QR_CODE, null),
        )
        assertEquals(
            QrScanResult.Cancelled,
            AndroidQrScanResultMapper.fromBarcode(Barcode.FORMAT_QR_CODE, ""),
        )
    }

    @Test
    fun cancellationVariantsAreNormalized() {
        val variants = listOf(
            CancellationException(),
            ApiException(Status(CommonStatusCodes.CANCELED)),
            IllegalStateException("wrapped", CancellationException()),
        )

        variants.forEach { cause ->
            assertEquals(
                QrScanResult.Cancelled,
                AndroidQrScanResultMapper.fromFailure(cause, "scanner_start_failed"),
            )
        }
        assertEquals(
            true,
            AndroidQrScanResultMapper.isMlKitCancellationCode(
                MlKitException.CODE_SCANNER_CANCELLED,
            ),
        )
    }

    @Test
    fun realScannerFailureRemainsVisible() {
        assertEquals(
            QrScanResult.Failed("scanner_start_failed"),
            AndroidQrScanResultMapper.fromFailure(
                IllegalStateException("failed"),
                "scanner_start_failed",
            ),
        )
    }
}

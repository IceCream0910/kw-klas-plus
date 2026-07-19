package com.icecream.kwklasplus

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GoogleCodeScannerConfigurationTest {
    @Test
    fun barcodeUiDependencyIsDeclaredOnScannerActivity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val activityInfo = context.packageManager.getActivityInfo(
            ComponentName(context, QRScanActivity::class.java),
            PackageManager.GET_META_DATA,
        )

        assertEquals(
            "barcode_ui",
            activityInfo.metaData.getString("com.google.mlkit.vision.DEPENDENCIES"),
        )
    }

    @Test
    fun mlKitInitializationProviderIsRegistered() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val providers = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PROVIDERS,
        ).providers.orEmpty()

        assertEquals(
            true,
            providers.any {
                it.name == "com.google.mlkit.common.internal.MlKitInitProvider"
            },
        )
    }
}

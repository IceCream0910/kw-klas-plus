package com.icecream.kwklasplus

import android.content.ComponentName
import android.content.pm.ActivityInfo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.icecream.kwklasplus.modal.LibraryQRModal
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryQrModalRecreationTest {
    @Test
    fun fragmentManagerCanRecreateModalWithNoArgConstructor() {
        val recreated = LibraryQRModal::class.java.getDeclaredConstructor().newInstance()

        assertNotNull(recreated)
        assertNotNull(LibraryQRModal.newInstance(isWidget = true).arguments)
    }
    @Test
    fun widgetHostUsesSeparateTaskOutsideRecents() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.packageManager
        val widget = manager.getActivityInfo(ComponentName(context, LibraryQRWidgetActivity::class.java), 0)
        val home = manager.getActivityInfo(ComponentName(context, HomeActivity::class.java), 0)

        assertNotEquals(home.taskAffinity, widget.taskAffinity)
        assertEquals(ActivityInfo.LAUNCH_SINGLE_TASK, widget.launchMode)
        assertTrue(widget.flags and ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS != 0)
    }
}

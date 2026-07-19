package com.icecream.kwklasplus

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.icecream.kwklasplus.modal.LibraryQRModal
import org.junit.Assert.assertNotNull
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
}

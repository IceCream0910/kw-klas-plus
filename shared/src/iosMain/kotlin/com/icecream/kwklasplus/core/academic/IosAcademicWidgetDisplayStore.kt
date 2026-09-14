package com.icecream.kwklasplus.core.academic

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileProtectionCompleteUntilFirstUserAuthentication
import platform.Foundation.NSFileProtectionKey
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDefaults
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Foundation.stringWithContentsOfFile

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosAcademicWidgetDisplayStore(
    private val fileManager: NSFileManager = NSFileManager.defaultManager,
    private val defaults: NSUserDefaults? = NSUserDefaults(suiteName = APP_GROUP),
) {
    fun read(): AcademicWidgetDisplay? {
        val marker = defaults?.stringForKey(OWNER_KEY).orEmpty()
        if (marker.isBlank()) return null
        val path = filePath() ?: return null
        val json = NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null) ?: return null
        return AcademicWidgetDisplayCodec.decode(json)?.takeIf { it.owner == marker }
    }

    fun write(display: AcademicWidgetDisplay) {
        val path = filePath() ?: return
        val payload = AcademicWidgetDisplayCodec.encode(display)
        val data = NSString.create(string = payload).dataUsingEncoding(NSUTF8StringEncoding) ?: return
        val attributes = mapOf<Any?, Any?>(
            NSFileProtectionKey to NSFileProtectionCompleteUntilFirstUserAuthentication,
        )
        val tmp = "$path.tmp"
        fileManager.removeItemAtPath(tmp, null)
        if (!fileManager.createFileAtPath(tmp, data, attributes)) return
        fileManager.removeItemAtPath(path, null)
        fileManager.moveItemAtPath(tmp, toPath = path, error = null)
        defaults?.setObject(display.owner, OWNER_KEY)
        defaults?.synchronize()
    }

    fun clear() {
        filePath()?.let { path ->
            fileManager.removeItemAtPath(path, null)
            fileManager.removeItemAtPath("$path.tmp", null)
        }
        defaults?.removeObjectForKey(OWNER_KEY)
        defaults?.synchronize()
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun filePath(): String? {
        val root = fileManager.containerURLForSecurityApplicationGroupIdentifier(APP_GROUP)
            ?.path ?: return null
        return "$root/$DISPLAY_FILE"
    }

    companion object {
        const val APP_GROUP = "group.com.icecream.kwklasplus"
        const val DISPLAY_FILE = "academic_widget_display_v1.json"
        const val OWNER_KEY = "academic_widget_owner"
    }
}

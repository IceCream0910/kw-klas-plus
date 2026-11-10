package com.icecream.kwklasplus.core.academic

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSBundle
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
    private val defaults: NSUserDefaults? = APP_GROUP?.let { NSUserDefaults(suiteName = it) },
    private val containerPath: String? = null,
) {
    fun read(): AcademicWidgetDisplay? {
        val demo = defaults?.boolForKey("academic_widget_demo") == true
        val marker = if (demo) defaults?.stringForKey("academic_widget_demo_owner").orEmpty() else defaults?.stringForKey(OWNER_KEY).orEmpty()
        if (marker.isBlank()) return null
        val path = (if (demo) filePath()?.replace(DISPLAY_FILE, "academic_widget_demo_v1.json") else filePath()) ?: return null
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

    fun writeDemo(display: AcademicWidgetDisplay) {
        val path = filePath()?.replace(DISPLAY_FILE, "academic_widget_demo_v1.json") ?: return
        val data = NSString.create(string = AcademicWidgetDisplayCodec.encode(display)).dataUsingEncoding(NSUTF8StringEncoding) ?: return
        val tmp = "$path.tmp"
        fileManager.removeItemAtPath(tmp, null)
        if (!fileManager.createFileAtPath(tmp, data, null)) return
        fileManager.removeItemAtPath(path, null)
        if (!fileManager.moveItemAtPath(tmp, toPath = path, error = null)) return
        defaults?.setObject(display.owner, "academic_widget_demo_owner")
        defaults?.setBool(true, "academic_widget_demo")
        defaults?.synchronize()
    }

    fun clearDemo() {
        defaults?.removeObjectForKey("academic_widget_demo")
        defaults?.removeObjectForKey("academic_widget_demo_owner")
        filePath()?.replace(DISPLAY_FILE, "academic_widget_demo_v1.json")?.let { fileManager.removeItemAtPath(it, null) }
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
        val root = containerPath
            ?: APP_GROUP?.let { fileManager.containerURLForSecurityApplicationGroupIdentifier(it)?.path }
            ?: return null
        return "$root/$DISPLAY_FILE"
    }

    companion object {
        const val APP_GROUP_INFO_KEY = "KLASAppGroupIdentifier"
        val APP_GROUP: String? by lazy {
            resolveAppGroup(NSBundle.mainBundle.objectForInfoDictionaryKey(APP_GROUP_INFO_KEY) as? String)
        }
        const val DISPLAY_FILE = "academic_widget_display_v1.json"
        const val OWNER_KEY = "academic_widget_owner"

        internal fun resolveAppGroup(configured: String?): String? =
            configured?.trim()?.takeIf { it.startsWith("group.") }
    }
}

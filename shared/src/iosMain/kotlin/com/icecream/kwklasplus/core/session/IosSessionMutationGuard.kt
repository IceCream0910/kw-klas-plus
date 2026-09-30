package com.icecream.kwklasplus.core.session

import com.icecream.kwklasplus.core.academic.IosAcademicWidgetDisplayStore
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.delay
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile
import platform.posix.EWOULDBLOCK
import platform.posix.LOCK_EX
import platform.posix.LOCK_NB
import platform.posix.LOCK_UN
import platform.posix.O_CREAT
import platform.posix.O_RDWR
import platform.posix.close
import platform.posix.errno
import platform.posix.flock
import platform.posix.open

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosSessionMutationGuard(
    private val containerPath: String? = null,
) : SessionMutationGuard {
    private var descriptor = -1
    private var root: String? = null

    override suspend fun acquire() {
        check(descriptor == -1)
        val directory = containerPath ?: NSFileManager.defaultManager
            .containerURLForSecurityApplicationGroupIdentifier(IosAcademicWidgetDisplayStore.APP_GROUP)?.path
            ?: error("Shared session container unavailable")
        val fd = open("$directory/academic_session.lock", O_CREAT or O_RDWR, 384)
        check(fd >= 0) { "Shared session lock unavailable" }
        try {
            while (flock(fd, LOCK_EX or LOCK_NB) != 0) {
                check(errno == EWOULDBLOCK) { "Shared session lock failed" }
                delay(10)
            }
            root = directory
            descriptor = fd
        } catch (cause: Throwable) {
            close(fd)
            throw cause
        }
    }

    override fun release() {
        val fd = descriptor
        descriptor = -1
        root = null
        if (fd >= 0) {
            flock(fd, LOCK_UN)
            close(fd)
        }
    }

    private fun state(): Pair<Long, Boolean> {
        val path = generationPath()
        if (!NSFileManager.defaultManager.fileExistsAtPath(path)) return 0L to true
        val parts = NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null)?.split(":")
            ?: error("Shared session generation unavailable")
        check(parts.size == 2 && parts[1] in setOf("0", "1"))
        val generation = parts[0].toLongOrNull()?.takeIf { it >= 0 }
            ?: error("Shared session generation invalid")
        return generation to (parts[1] == "1")
    }

    override fun generation(): Long = state().first

    override fun backgroundAuthenticationAllowed(): Boolean = state().second

    override fun advanceGeneration(backgroundAuthenticationAllowed: Boolean) {
        val next = generation() + 1L
        check(next > 0)
        val state = "$next:${if (backgroundAuthenticationAllowed) 1 else 0}"
        check(NSString.create(string = state).writeToFile(
            generationPath(), atomically = true, encoding = NSUTF8StringEncoding, error = null,
        )) { "Shared session generation write failed" }
    }

    private fun generationPath(): String {
        check(descriptor >= 0)
        return "${requireNotNull(root)}/academic_session_generation"
    }
}

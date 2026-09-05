package com.icecream.kwklasplus.feature.player

import java.lang.ref.WeakReference

internal class SingleLecturePlayback<T : Any> {
    private var active = WeakReference<T>(null)
    private var replacement = WeakReference<T>(null)
    val owner: T? get() = active.get()
    val isReplacing: Boolean get() = replacement.get() != null

    fun begin(requester: T, expectedOwner: T?): Boolean {
        if (isReplacing || owner !== expectedOwner) return false
        replacement = WeakReference(requester)
        return true
    }

    fun complete(requester: T): Boolean {
        if (replacement.get() !== requester) return false
        active = WeakReference(requester)
        replacement.clear()
        return true
    }

    fun cancel(requester: T) {
        if (replacement.get() === requester) replacement.clear()
    }

    fun release(owner: T) {
        if (active.get() === owner) active.clear()
        cancel(owner)
    }
}

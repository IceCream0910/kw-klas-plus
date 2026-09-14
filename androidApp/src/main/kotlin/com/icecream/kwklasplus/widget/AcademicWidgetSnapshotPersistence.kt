package com.icecream.kwklasplus.widget

import com.icecream.kwklasplus.core.academic.AcademicWidgetSnapshot
import com.icecream.kwklasplus.core.academic.AcademicWidgetSnapshotStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class AcademicWidgetSnapshotPersistence(
    private val store: AcademicWidgetSnapshotStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()

    suspend fun read(): AcademicWidgetSnapshot? = mutex.withLock {
        withContext(ioDispatcher) { store.read() }
    }

    suspend fun writeIf(snapshot: AcademicWidgetSnapshot, isCurrent: () -> Boolean): Boolean = mutex.withLock {
        if (!isCurrent()) false
        else {
            withContext(ioDispatcher) { store.write(snapshot) }
            true
        }
    }

    suspend fun clear() = mutex.withLock {
        withContext(ioDispatcher) { store.clear() }
    }
}

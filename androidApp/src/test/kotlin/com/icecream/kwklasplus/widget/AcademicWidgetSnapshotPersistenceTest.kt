package com.icecream.kwklasplus.widget

import com.icecream.kwklasplus.core.academic.AcademicWidgetSnapshot
import com.icecream.kwklasplus.core.academic.AcademicWidgetSnapshotStore
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList

class AcademicWidgetSnapshotPersistenceTest {
    @Test fun fileOperationsRunOffCallerThread() = runBlocking {
        val caller = Thread.currentThread()
        val threads = CopyOnWriteArrayList<Thread>()
        val store = object : AcademicWidgetSnapshotStore {
            override fun read(): AcademicWidgetSnapshot? { threads += Thread.currentThread(); return null }
            override fun write(snapshot: AcademicWidgetSnapshot) { threads += Thread.currentThread() }
            override fun clear() { threads += Thread.currentThread() }
        }
        val persistence = AcademicWidgetSnapshotPersistence(store)
        persistence.read()
        persistence.writeIf(AcademicWidgetSnapshot("owner", "term")) { true }
        persistence.clear()
        assertEquals(3, threads.size)
        assertTrue(threads.all { it != caller })
    }

    @Test fun logoutClearFollowsInFlightWriteAndRejectsStaleWrite() = runBlocking {
        val enteredWrite = CountDownLatch(1)
        val releaseWrite = CountDownLatch(1)
        val operations = CopyOnWriteArrayList<String>()
        val store = object : AcademicWidgetSnapshotStore {
            override fun read(): AcademicWidgetSnapshot? = null
            override fun write(snapshot: AcademicWidgetSnapshot) {
                operations += "write"
                enteredWrite.countDown()
                assertTrue(releaseWrite.await(5, TimeUnit.SECONDS))
            }
            override fun clear() { operations += "clear" }
        }
        val persistence = AcademicWidgetSnapshotPersistence(store)
        val snapshot = AcademicWidgetSnapshot("owner", "term")
        val first = async(start = CoroutineStart.UNDISPATCHED) { persistence.writeIf(snapshot) { true } }
        try {
            assertTrue(enteredWrite.await(5, TimeUnit.SECONDS))
            val clear = async(start = CoroutineStart.UNDISPATCHED) { persistence.clear() }
            val stale = async(start = CoroutineStart.UNDISPATCHED) { persistence.writeIf(snapshot) { false } }
            releaseWrite.countDown()
            assertTrue(first.await())
            clear.await()
            assertFalse(stale.await())
            assertEquals(listOf("write", "clear"), operations)
        } finally {
            releaseWrite.countDown()
        }
    }
}

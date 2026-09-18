package com.icecream.kwklasplus.core.academic

import com.icecream.kwklasplus.core.IosSharedDependencies
import com.icecream.kwklasplus.core.auth.LoginTokenEncryptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class IosAcademicWidgetCalendarRefresh(
    private val widgets: IosAcademicWidgets,
    private val gate: CalendarWidgetRefreshGate = CalendarWidgetRefreshGate(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val mutex = Mutex()
    private val waiters = mutableListOf<() -> Unit>()
    private var inFlight = false

    fun refresh(userAgent: String, onDone: () -> Unit) {
        scope.launch {
            val start = mutex.withLock {
                waiters += onDone
                if (inFlight) {
                    false
                } else {
                    inFlight = true
                    true
                }
            }
            if (!start) return@launch
            try {
                gate.run(
                    refresh = { widgets.syncCalendar(userAgent) },
                    onTimeoutOrRetry = {},
                    finish = {},
                )
            } finally {
                val callbacks = mutex.withLock {
                    inFlight = false
                    waiters.toList().also { waiters.clear() }
                }
                callbacks.forEach { runCatching(it) }
            }
        }
    }

    companion object {
        fun create(
            dependencies: IosSharedDependencies,
            tokenEncryptor: LoginTokenEncryptor,
            reloader: AcademicWidgetTimelineReloader,
        ): IosAcademicWidgetCalendarRefresh = IosAcademicWidgetCalendarRefresh(
            IosAcademicWidgets.create(
                dependencies = dependencies,
                tokenEncryptor = tokenEncryptor,
                reloader = reloader,
            ),
        )
    }
}

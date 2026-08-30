package com.icecream.kwklasplus.core.network

import com.icecream.kwklasplus.core.auth.KlasAuthRepository
import com.icecream.kwklasplus.core.attendance.AttendanceRepository
import com.icecream.kwklasplus.core.academic.AcademicRepository
import com.icecream.kwklasplus.core.academic.TimetableRepository
import com.icecream.kwklasplus.core.academic.AndroidDeadlineDateParsers
import com.icecream.kwklasplus.core.academic.DeadlineRepository
import com.icecream.kwklasplus.core.library.LibraryGateway
import com.icecream.kwklasplus.core.library.LibraryHttpGateway
import com.icecream.kwklasplus.core.media.MediaMetadataRepository
import com.icecream.kwklasplus.core.profile.IdCardQrRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

private fun createAndroidKlasHttpClient(timeoutMillis: Long = 15_000): HttpClient =
    createKlasHttpClient(OkHttp.create(), timeoutMillis)

class AndroidCoreNetworkDependencies {
    private val defaultClient by lazy { createAndroidKlasHttpClient() }
    private val longRunningClient by lazy { createAndroidKlasHttpClient(timeoutMillis = 30_000) }
    private val libraryClient by lazy { createAndroidKlasHttpClient(timeoutMillis = 10_000) }

    val authRepository: KlasAuthRepository by lazy { KlasAuthRepository(defaultClient) }
    val sessionLeaseGateway by lazy { KlasSessionLeaseHttpGateway(defaultClient) }
    val attendanceRepository: AttendanceRepository by lazy {
        AttendanceRepository(KlasSessionHttpClient(defaultClient))
    }
    val academicRepository: AcademicRepository by lazy {
        AcademicRepository(KlasSessionHttpClient(defaultClient))
    }
    val timetableRepository: TimetableRepository by lazy {
        TimetableRepository(KlasSessionHttpClient(longRunningClient))
    }
    val deadlineRepository: DeadlineRepository by lazy {
        DeadlineRepository(
            transport = KlasSessionHttpClient(longRunningClient),
            clock = { System.currentTimeMillis() },
            onlineLectureEndParser = AndroidDeadlineDateParsers.onlineLecture,
            assignmentEndParser = AndroidDeadlineDateParsers.assignment,
        )
    }
    val libraryGateway: LibraryGateway by lazy { LibraryHttpGateway(libraryClient) }
    val idCardQrRepository: IdCardQrRepository by lazy { IdCardQrRepository(defaultClient) }
    val mediaMetadataRepository: MediaMetadataRepository by lazy {
        MediaMetadataRepository(defaultClient)
    }
}

package com.icecream.kwklasplus.core.academic

import com.icecream.kwklasplus.core.network.AuthenticatedKlasEndpoint
import com.icecream.kwklasplus.core.network.KlasAuthenticatedResult
import com.icecream.kwklasplus.core.network.KlasAuthenticatedTransport
import com.icecream.kwklasplus.core.network.KlasUserAgent
import com.icecream.kwklasplus.core.security.SecretValue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

class AcademicRepositoryTest {
    @Test
    fun termSelectorPreservesSavedTermAndFallsBackToFirst() {
        val terms = listOf(
            AcademicTerm("2026,1", emptyList()),
            AcademicTerm("2025,2", emptyList()),
        )

        assertEquals(AcademicTermSelection(1, terms[1]), AcademicTermSelector.select(terms, "2025,2"))
        assertEquals(AcademicTermSelection(0, terms[0]), AcademicTermSelector.select(terms, "missing"))
        assertEquals(AcademicTermSelection(0, terms[0]), AcademicTermSelector.select(terms, ""))
        assertEquals(null, AcademicTermSelector.select(emptyList(), "2026,1"))
    }

    @Test
    fun mapsLegacyTermAndSubjectFields() = runBlocking {
        var endpoint: AuthenticatedKlasEndpoint? = null
        val repository = AcademicRepository(
            KlasAuthenticatedTransport { requestedEndpoint, _, _, _ ->
                endpoint = requestedEndpoint
                KlasAuthenticatedResult.Success(
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("value", "2026,1")
                                put(
                                    "subjList",
                                    buildJsonArray {
                                        add(
                                            buildJsonObject {
                                                put("value", "SUBJECT-ID")
                                                put("name", "공통 테스트")
                                            },
                                        )
                                    },
                                )
                            },
                        )
                    },
                )
            },
        )

        val result = repository.fetchTerms(
            SecretValue.of("session"),
            KlasUserAgent.fromPlatform("UA"),
        )

        assertEquals(AuthenticatedKlasEndpoint.ACADEMIC_TERM_SUBJECTS, endpoint)
        assertEquals(
            AcademicTermsResult.Success(
                listOf(
                    AcademicTerm(
                        "2026,1",
                        listOf(AcademicSubject("SUBJECT-ID", "공통 테스트")),
                    ),
                ),
            ),
            result,
        )
    }

    @Test
    fun emptyArrayIsAValidEmptyTermList() = runBlocking {
        val result = repositoryReturning(
            KlasAuthenticatedResult.Success(buildJsonArray {}),
        ).fetchTerms(SecretValue.of("session"), KlasUserAgent.fromPlatform("UA"))

        assertEquals(AcademicTermsResult.Success(emptyList()), result)
    }

    @Test
    fun malformedTermOrSubjectIsRejected() = runBlocking {
        val missingSubjects = buildJsonArray {
            add(buildJsonObject { put("value", "2026,1") })
        }
        val blankSubjectId = buildJsonArray {
            add(
                buildJsonObject {
                    put("value", "2026,1")
                    put(
                        "subjList",
                        buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("value", "")
                                    put("name", "강의")
                                },
                            )
                        },
                    )
                },
            )
        }

        suspend fun resultFor(body: kotlinx.serialization.json.JsonElement) =
            repositoryReturning(KlasAuthenticatedResult.Success(body)).fetchTerms(
                SecretValue.of("session"),
                KlasUserAgent.fromPlatform("UA"),
            )

        assertEquals(AcademicTermsResult.MalformedResponse, resultFor(missingSubjects))
        assertEquals(AcademicTermsResult.MalformedResponse, resultFor(blankSubjectId))
    }

    @Test
    fun transportFailuresRemainDistinct() = runBlocking {
        suspend fun mapped(result: KlasAuthenticatedResult) =
            repositoryReturning(result).fetchTerms(
                SecretValue.of("session"),
                KlasUserAgent.fromPlatform("UA"),
            )

        assertEquals(AcademicTermsResult.SessionExpired, mapped(KlasAuthenticatedResult.SessionExpired))
        assertEquals(AcademicTermsResult.Timeout, mapped(KlasAuthenticatedResult.Timeout))
        assertEquals(AcademicTermsResult.NetworkFailure, mapped(KlasAuthenticatedResult.NetworkFailure))
        assertEquals(
            AcademicTermsResult.HttpFailure(503),
            mapped(KlasAuthenticatedResult.HttpFailure(503)),
        )
    }

    private fun repositoryReturning(result: KlasAuthenticatedResult) =
        AcademicRepository(KlasAuthenticatedTransport { _, _, _, _ -> result })
}

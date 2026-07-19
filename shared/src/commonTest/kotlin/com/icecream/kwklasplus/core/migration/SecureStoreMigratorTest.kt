package com.icecream.kwklasplus.core.migration

import com.icecream.kwklasplus.core.platform.SecureKey
import com.icecream.kwklasplus.core.platform.SecureStore
import com.icecream.kwklasplus.core.security.SecretValue
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class SecureStoreMigratorTest {
    private val reference = LegacySecretRef(LegacyStoreId.PREFERENCES, "kwPWD")
    private val migration = SecretMigration(reference, SecureKey.ENCRYPTED_KLAS_PASSWORD)
    private val secret = SecretValue.of("encrypted-password")

    @Test
    fun removesLegacyValueOnlyAfterVerifiedWrite() = runBlocking {
        val source = FakeLegacySource(mutableMapOf(reference to secret))
        val target = FakeSecureStore()

        val result = SecureStoreMigrator(source, target).migrate(migration)

        assertEquals(SecretMigrationStatus.MIGRATED, result.status)
        assertEquals(secret, target.values[SecureKey.ENCRYPTED_KLAS_PASSWORD])
        assertEquals(null, source.values[reference])
    }

    @Test
    fun preservesLegacyValueWhenWriteOrVerificationFails() = runBlocking {
        val writeFailureSource = FakeLegacySource(mutableMapOf(reference to secret))
        val verificationFailureSource = FakeLegacySource(mutableMapOf(reference to secret))

        val writeFailure = SecureStoreMigrator(
            writeFailureSource,
            FakeSecureStore(failWrite = true),
        ).migrate(migration)
        val verificationFailure = SecureStoreMigrator(
            verificationFailureSource,
            FakeSecureStore(discardWrite = true),
        ).migrate(migration)

        assertEquals(SecretMigrationStatus.FAILED, writeFailure.status)
        assertEquals(SecretMigrationStatus.FAILED, verificationFailure.status)
        assertEquals(secret, writeFailureSource.values[reference])
        assertEquals(secret, verificationFailureSource.values[reference])
    }

    @Test
    fun removesDuplicateButPreservesConflictingLegacyValue() = runBlocking {
        val duplicateSource = FakeLegacySource(mutableMapOf(reference to secret))
        val duplicateTarget = FakeSecureStore(
            mutableMapOf(SecureKey.ENCRYPTED_KLAS_PASSWORD to secret),
        )
        val conflictingSource = FakeLegacySource(mutableMapOf(reference to secret))
        val conflictingTarget = FakeSecureStore(
            mutableMapOf(SecureKey.ENCRYPTED_KLAS_PASSWORD to SecretValue.of("other-value")),
        )

        val duplicate = SecureStoreMigrator(duplicateSource, duplicateTarget).migrate(migration)
        val conflict = SecureStoreMigrator(conflictingSource, conflictingTarget).migrate(migration)

        assertEquals(SecretMigrationStatus.ALREADY_MIGRATED, duplicate.status)
        assertEquals(null, duplicateSource.values[reference])
        assertEquals(SecretMigrationStatus.CONFLICT, conflict.status)
        assertEquals(secret, conflictingSource.values[reference])
    }

    private class FakeLegacySource(
        val values: MutableMap<LegacySecretRef, SecretValue>,
    ) : LegacySecretSource {
        override suspend fun read(reference: LegacySecretRef): SecretValue? = values[reference]
        override suspend fun remove(reference: LegacySecretRef) {
            values.remove(reference)
        }
    }

    private class FakeSecureStore(
        val values: MutableMap<SecureKey, SecretValue> = mutableMapOf(),
        private val failWrite: Boolean = false,
        private val discardWrite: Boolean = false,
    ) : SecureStore {
        override suspend fun read(key: SecureKey): SecretValue? = values[key]
        override suspend fun write(key: SecureKey, value: SecretValue) {
            if (failWrite) error("write failed")
            if (!discardWrite) values[key] = value
        }
        override suspend fun remove(key: SecureKey) {
            values.remove(key)
        }
    }
}

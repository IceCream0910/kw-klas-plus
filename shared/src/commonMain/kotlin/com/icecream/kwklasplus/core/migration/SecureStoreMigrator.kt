package com.icecream.kwklasplus.core.migration

import com.icecream.kwklasplus.core.platform.SecureKey
import com.icecream.kwklasplus.core.platform.SecureStore
import com.icecream.kwklasplus.core.security.SecretValue

enum class LegacyStoreId {
    PREFERENCES,
    ENCRYPTED_PREFERENCES,
    LIBRARY_ENCRYPTED_PREFERENCES,
}

data class LegacySecretRef(
    val store: LegacyStoreId,
    val key: String,
) {
    init {
        require(key.isNotBlank())
    }
}

interface LegacySecretSource {
    suspend fun read(reference: LegacySecretRef): SecretValue?
    suspend fun remove(reference: LegacySecretRef)
}

data class SecretMigration(
    val source: LegacySecretRef,
    val target: SecureKey,
)

enum class SecretMigrationStatus {
    MIGRATED,
    ALREADY_MIGRATED,
    SOURCE_MISSING,
    CONFLICT,
    FAILED,
}

data class SecretMigrationResult(
    val migration: SecretMigration,
    val status: SecretMigrationStatus,
)

class SecureStoreMigrator(
    private val source: LegacySecretSource,
    private val target: SecureStore,
) {
    suspend fun migrate(migrations: List<SecretMigration>): List<SecretMigrationResult> =
        migrations.map { migrate(it) }

    suspend fun migrate(migration: SecretMigration): SecretMigrationResult {
        return try {
            val legacyValue = source.read(migration.source)
                ?: return result(migration, SecretMigrationStatus.SOURCE_MISSING)
            val storedValue = target.read(migration.target)
            if (storedValue != null) {
                if (storedValue == legacyValue) {
                    source.remove(migration.source)
                    result(migration, SecretMigrationStatus.ALREADY_MIGRATED)
                } else {
                    result(migration, SecretMigrationStatus.CONFLICT)
                }
            } else {
                target.write(migration.target, legacyValue)
                if (target.read(migration.target) != legacyValue) {
                    result(migration, SecretMigrationStatus.FAILED)
                } else {
                    source.remove(migration.source)
                    result(migration, SecretMigrationStatus.MIGRATED)
                }
            }
        } catch (_: Throwable) {
            result(migration, SecretMigrationStatus.FAILED)
        }
    }

    private fun result(migration: SecretMigration, status: SecretMigrationStatus) =
        SecretMigrationResult(migration, status)
}

package com.icecream.kwklasplus.core.migration

import com.icecream.kwklasplus.core.legacy.LegacyPreferenceKeys
import com.icecream.kwklasplus.core.platform.SecureKey

val androidLoginCredentialMigrations = listOf(
    SecretMigration(
        LegacySecretRef(LegacyStoreId.ENCRYPTED_PREFERENCES, LegacyPreferenceKeys.KW_PASSWORD),
        SecureKey.ENCRYPTED_KLAS_PASSWORD,
    ),
    SecretMigration(
        LegacySecretRef(LegacyStoreId.PREFERENCES, LegacyPreferenceKeys.KW_PASSWORD),
        SecureKey.ENCRYPTED_KLAS_PASSWORD,
    ),
)

val androidFixedSecretMigrations = androidLoginCredentialMigrations + listOf(
    SecretMigration(
        LegacySecretRef(LegacyStoreId.PREFERENCES, LegacyPreferenceKeys.KW_SESSION),
        SecureKey.SESSION_TOKEN,
    ),
    SecretMigration(
        LegacySecretRef(LegacyStoreId.PREFERENCES, "p_w_h"),
        SecureKey.APP_LOCK_HASH,
    ),
    SecretMigration(
        LegacySecretRef(LegacyStoreId.PREFERENCES, "p_w_s"),
        SecureKey.APP_LOCK_SALT,
    ),
)

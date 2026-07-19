package com.icecream.kwklasplus.core.migration

import android.content.SharedPreferences
import com.icecream.kwklasplus.core.security.SecretValue

class AndroidLegacySecretSource(
    private val preferences: (LegacyStoreId) -> SharedPreferences,
) : LegacySecretSource {
    override suspend fun read(reference: LegacySecretRef): SecretValue? {
        val value = preferences(reference.store).getString(reference.key, null) ?: return null
        return value.takeIf(String::isNotBlank)?.let(SecretValue::of)
    }

    override suspend fun remove(reference: LegacySecretRef) {
        check(preferences(reference.store).edit().remove(reference.key).commit())
    }
}

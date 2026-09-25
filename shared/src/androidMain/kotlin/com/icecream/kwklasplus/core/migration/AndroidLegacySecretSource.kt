package com.icecream.kwklasplus.core.migration

import android.content.SharedPreferences
import com.icecream.kwklasplus.core.security.SecretValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidLegacySecretSource(
    private val preferences: (LegacyStoreId) -> SharedPreferences,
) : LegacySecretSource {
    override suspend fun read(reference: LegacySecretRef): SecretValue? = withContext(Dispatchers.IO) {
        val value = preferences(reference.store).getString(reference.key, null) ?: return@withContext null
        value.takeIf(String::isNotBlank)?.let(SecretValue::of)
    }

    override suspend fun remove(reference: LegacySecretRef) = withContext(Dispatchers.IO) {
        check(preferences(reference.store).edit().remove(reference.key).commit())
    }
}

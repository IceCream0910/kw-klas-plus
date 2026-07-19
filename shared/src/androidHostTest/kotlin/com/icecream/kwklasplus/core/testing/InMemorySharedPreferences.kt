package com.icecream.kwklasplus.core.testing

import android.content.SharedPreferences

class InMemorySharedPreferences : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()
    override fun getString(key: String?, defaultValue: String?): String? =
        values[key] as? String ?: defaultValue
    override fun getStringSet(key: String?, defaultValues: MutableSet<String>?): MutableSet<String>? =
        (values[key] as? Set<*>)?.filterIsInstance<String>()?.toMutableSet() ?: defaultValues
    override fun getInt(key: String?, defaultValue: Int): Int = values[key] as? Int ?: defaultValue
    override fun getLong(key: String?, defaultValue: Long): Long = values[key] as? Long ?: defaultValue
    override fun getFloat(key: String?, defaultValue: Float): Float = values[key] as? Float ?: defaultValue
    override fun getBoolean(key: String?, defaultValue: Boolean): Boolean =
        values[key] as? Boolean ?: defaultValue
    override fun contains(key: String?): Boolean = values.containsKey(key)
    override fun edit(): SharedPreferences.Editor = Editor()
    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit

    private inner class Editor : SharedPreferences.Editor {
        private val updates = mutableMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var clear = false

        override fun putString(key: String?, value: String?) = update(key, value)
        override fun putStringSet(key: String?, values: MutableSet<String>?) = update(key, values?.toSet())
        override fun putInt(key: String?, value: Int) = update(key, value)
        override fun putLong(key: String?, value: Long) = update(key, value)
        override fun putFloat(key: String?, value: Float) = update(key, value)
        override fun putBoolean(key: String?, value: Boolean) = update(key, value)

        override fun remove(key: String?): SharedPreferences.Editor = apply {
            requireNotNull(key)
            removals += key
            updates -= key
        }

        override fun clear(): SharedPreferences.Editor = apply { clear = true }
        override fun commit(): Boolean {
            applyChanges()
            return true
        }
        override fun apply() = applyChanges()

        private fun update(key: String?, value: Any?): SharedPreferences.Editor = apply {
            requireNotNull(key)
            updates[key] = value
            removals -= key
        }

        private fun applyChanges() {
            if (clear) values.clear()
            removals.forEach(values::remove)
            updates.forEach { (key, value) ->
                if (value == null) values.remove(key) else values[key] = value
            }
        }
    }
}

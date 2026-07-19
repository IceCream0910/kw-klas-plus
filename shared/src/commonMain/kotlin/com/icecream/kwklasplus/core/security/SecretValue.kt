package com.icecream.kwklasplus.core.security

class SecretValue private constructor(private val value: String) {
    fun reveal(): String = value

    override fun equals(other: Any?): Boolean = other is SecretValue && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = "[REDACTED]"

    companion object {
        fun of(value: String): SecretValue {
            require(value.isNotBlank())
            return SecretValue(value)
        }
    }
}

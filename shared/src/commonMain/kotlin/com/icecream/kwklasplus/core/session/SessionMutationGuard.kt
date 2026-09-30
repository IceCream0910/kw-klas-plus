package com.icecream.kwklasplus.core.session

interface SessionMutationGuard {
    suspend fun acquire()
    fun release()
    fun generation(): Long
    fun backgroundAuthenticationAllowed(): Boolean
    fun advanceGeneration(backgroundAuthenticationAllowed: Boolean)
}

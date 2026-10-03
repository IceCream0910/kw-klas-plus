package com.icecream.kwklasplus.core.notification

import kotlinx.serialization.Serializable

@Serializable
data class ReminderIdentity(val owner: String, val term: String, val ready: Boolean, val canConfigure: Boolean = ready)
@Serializable
data class ReminderDeadline(val key: String, val startsAt: Long?, val dueAt: Long, val subjectId: String = "", val subjectName: String = "", val kind: String = "task")
@Serializable
data class ReminderClaim(val ownerHash: String, val day: Long, val keys: Set<String>, val batchId: String)
@Serializable
data class ReminderLedger(
    val schemaVersion: Int = 1,
    val ownerHash: String = "",
    val generation: Long = 0,
    val revision: Long = 0,
    val deadlineEnabled: Boolean = false,
    val deadlineStatus: String = "DISABLED",
    val deadlineFetchedAt: Long = 0,
    val claims: List<ReminderClaim> = emptyList(),
)

interface ReminderStore {
    suspend fun read(): String?
    suspend fun write(value: String)
}
interface ReminderPlatform {
    suspend fun permission(kind: String): String
    suspend fun postDetailed(id: String, kind: String, generation: Long, additional: Boolean, message: ReminderMessage): Boolean
    suspend fun cancel(id: String)
    suspend fun cancelAll()
    fun hash(value: String): String
}
fun interface ReminderIdentityProvider { fun current(): ReminderIdentity }
fun interface ReminderDeliveryGate { fun canPost(): Boolean }
fun interface ReminderSettingsUi { fun open(attempt: Long): Boolean }
data class DeadlineNotificationSettings(val enabled: Boolean, val ready: Boolean, val permission: String, val status: String, val fetchedAt: Long)
sealed interface ReminderSourceResult<out T> {
    data class Success<T>(val value: T) : ReminderSourceResult<T>
    data object NeedsLogin : ReminderSourceResult<Nothing>
    data object Retry : ReminderSourceResult<Nothing>
    data object UnverifiedSource : ReminderSourceResult<Nothing>
}
interface ReminderDataSource {
    suspend fun deadlines(term: String): ReminderSourceResult<List<ReminderDeadline>>
}

object ReminderTime {
    const val DAY = 86_400_000L
    const val SEOUL_OFFSET = 9 * 3_600_000L
    fun day(now: Long): Long = (now + SEOUL_OFFSET) / DAY
    fun parse(value: String): Long? = runCatching {
        val compact = Regex("[0-9]{8}([0-9]{2}){0,3}")
        val expanded = if (compact.matches(value)) {
            val v = value.padEnd(14, '0')
            "${v.take(4)}-${v.substring(4,6)}-${v.substring(6,8)} ${v.substring(8,10)}:${v.substring(10,12)}:${v.substring(12,14)}"
        } else value
        val m = Regex("([0-9]{4})-([0-9]{2})-([0-9]{2})(?:[ T]([0-9]{2}):([0-9]{2})(?::([0-9]{2})(?:\\.[0-9]{1,3})?)?)?(Z|[+-][0-9]{2}:?[0-9]{2})?").matchEntire(expanded) ?: return null
        fun n(i: Int) = m.groupValues[i].ifEmpty { "0" }.toInt()
        val y=n(1); val mo=n(2); val d=n(3); val h=n(4); val mi=n(5); val se=n(6)
        require(y in 1970..2200 && mo in 1..12 && d in 1..monthDays(y,mo) && h in 0..23 && mi in 0..59 && se in 0..59)
        val days=(1970 until y).sumOf { if (leap(it)) 366L else 365L } + (1 until mo).sumOf { monthDays(y,it).toLong() } + d-1
        val z=m.groupValues[7]
        val offset=when { z=="Z" -> 0L; z.isEmpty() -> SEOUL_OFFSET; else -> {
            val digits=z.drop(1).replace(":", ""); val zh=digits.take(2).toInt(); val zm=digits.takeLast(2).toInt()
            require(zh<=18 && zm<60 && (zh<18 || zm==0))
            (zh*60L+zm)*60_000*(if(z[0]=='-') -1 else 1)
        } }
        days*DAY + (h*3600L+mi*60+se)*1000-offset
    }.getOrNull()
    fun date(epoch: Long): String {
        var days=day(epoch); var y=1970
        while(days >= if(leap(y))366 else 365) { days-=if(leap(y))366 else 365; y++ }
        var m=1
        while(days>=monthDays(y,m)) { days-=monthDays(y,m); m++ }
        return "$y-${m.toString().padStart(2,'0')}-${(days+1).toString().padStart(2,'0')}"
    }
    private fun leap(y: Int)=y%4==0 && (y%100!=0 || y%400==0)
    private fun monthDays(y: Int,m: Int)=when(m) { 2 -> if(leap(y))29 else 28; 4,6,9,11 ->30; else ->31 }
}

data class ReminderDeadlineTicket(val ownerHash: String, val generation: Long, val revision: Long, val term: String, val startedAt: Long, val requestId: Long = 0)
data class ReminderMessage(val title: String, val body: String)

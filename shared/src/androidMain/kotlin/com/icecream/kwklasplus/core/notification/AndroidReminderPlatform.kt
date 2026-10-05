package com.icecream.kwklasplus.core.notification

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.AtomicFile
import java.io.File
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class AndroidReminderStore(context: Context): ReminderStore {
    private val file=AtomicFile(File(context.noBackupFilesDir,"academic_reminders_v1.json"))
    override suspend fun read(): String? = withContext(Dispatchers.IO) {
        try {
            if(file.baseFile.exists() || File(file.baseFile.path+".bak").exists())file.openRead().use { it.readBytes().decodeToString() } else null
        } catch(cause: Exception) { if(cause is CancellationException)throw cause;throw IllegalStateException("STORAGE_FAILED") }
    }
    override suspend fun write(value: String) = withContext(Dispatchers.IO) {
        try {
            val out=file.startWrite()
            try { out.write(value.encodeToByteArray());file.finishWrite(out) } catch(cause: Throwable) { file.failWrite(out);throw cause }
        } catch(cause: Exception) { if(cause is CancellationException)throw cause;throw IllegalStateException("STORAGE_FAILED") }
    }
}
class AndroidReminderPlatform(
    private val context: Context,
    private val entryClass: Class<*>,
    private val smallIcon: Int = context.applicationInfo.icon,
): ReminderPlatform {
    private val manager=context.getSystemService(NotificationManager::class.java)
    private val salt: ByteArray by lazy {
        val f=AtomicFile(File(context.noBackupFilesDir,"academic_reminders_hash_key"))
        if(f.baseFile.exists()) f.openRead().use { it.readBytes() } else {
            val bytes=ByteArray(32).also { SecureRandom().nextBytes(it) };val out=f.startWrite()
            try { out.write(bytes);f.finishWrite(out) } catch(cause: Throwable) { f.failWrite(out);throw cause };bytes
        }
    }
    init {
        manager.createNotificationChannel(NotificationChannel("deadline_digest_v1","마감 임박 알림",NotificationManager.IMPORTANCE_DEFAULT))
        manager.deleteNotificationChannel("calendar_reminder_v1")
    }
    override fun hash(value: String): String {
        val mac=Mac.getInstance("HmacSHA256");mac.init(SecretKeySpec(salt,"HmacSHA256"))
        return mac.doFinal(value.encodeToByteArray()).joinToString("") { "%02x".format(it) }
    }
    override suspend fun permission(kind: String): String {
        if(Build.VERSION.SDK_INT>=33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return "denied"
        if(!manager.areNotificationsEnabled())return "denied"
        val channel="deadline_digest_v1"
        return if(manager.getNotificationChannel(channel)?.importance==NotificationManager.IMPORTANCE_NONE)"denied" else "authorized"
    }
    override suspend fun postDetailed(id: String,kind: String,generation: Long,additional: Boolean,message: ReminderMessage): Boolean {
        if(permission(kind)!="authorized" || ReminderTime.isQuietHours(System.currentTimeMillis()))return false
        val click=PendingIntent.getActivity(context,0,Intent(context,entryClass).apply { data=Uri.parse("kwklasplus-internal://notification/$id");putExtra("reminder_kind",kind);putExtra("reminder_generation",generation) },PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n=Notification.Builder(context,"deadline_digest_v1")
            .setSmallIcon(smallIcon).setContentTitle(message.title).setContentText(message.body).setStyle(Notification.BigTextStyle().bigText(message.body)).setVisibility(Notification.VISIBILITY_PRIVATE)
            .setContentIntent(click).setAutoCancel(true).setOnlyAlertOnce(true).build()
        return runCatching { manager.notify("academic_reminders:$id",0,n) }.isSuccess
    }
    override suspend fun cancel(id: String) { manager.cancel("academic_reminders:$id",0) }
    override suspend fun cancelAll() {
        manager.activeNotifications.filter { it.tag?.startsWith("academic_reminders:")==true }.forEach { manager.cancel(it.tag,it.id) }
    }
}
